/**
 * Architecture Decision Records (ADR) References:
 * - ADR-001: Provider Adapter Architecture - Core provider abstraction pattern
 * - ADR-008: Provider Adapter Architecture v2 - Capability-driven routing and fallback chains
 *
 * @see docs/architecture/adr/ADR-001-Provider-Adapter-Architecture.md
 * @see docs/architecture/adr/ADR-008-Provider-Adapter-Architecture-v2.md
 */
package com.shadowai.app.providers

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.providers.ApiStyle as CoreApiStyle
import com.shadowai.core.Capability as CoreCapability
import com.shadowai.provideradapters.ProviderCrudRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a provider in the fallback chain with its priority.
 */
data class FallbackProvider(
    val config: ActiveProviderConfig,
    val priority: Int,  // Lower = higher priority
    val attemptCount: Int = 0,
    val lastError: Throwable? = null
)

/**
 * Result of attempting a fallback chain.
 */
sealed class FallbackResult {
    data class Success(val config: ActiveProviderConfig, val attempts: Int) : FallbackResult()
    data class Exhausted(val attempted: List<FallbackProvider>, val errors: List<Throwable>) : FallbackResult()
}

@Singleton
class ProviderSelector @Inject constructor(
    private val activeProviderManager: ActiveProviderManager,
    private val adminRepository: AdminRepository,
    // REPOSITORY ADAPTER CLEANUP: Replaced ProviderRepository facade with split repositories
    private val crudRepository: ProviderCrudRepository
) {
    private val indexMutex = Mutex()
    private var localIndex = 0
    private var cloudIndex = 0

    /**
     * Gets the currently active provider configuration based on admin preferences.
     */
    suspend fun getActiveConfig(): ActiveProviderConfig? {
        return activeProviderManager.getActiveConfig()
    }

    /**
     * Selects the next available provider for a task, preferring preferred provider if set.
     */
    suspend fun nextForTask(taskType: TaskType): ActiveProviderConfig? {
        // Try to get next cloud provider, fallback to local
        return nextCloud(taskType) ?: nextLocal(taskType)
    }

    suspend fun nextLocal(taskType: TaskType): ActiveProviderConfig? {
        val configs = getProviders(taskType, local = true)
        return pickConfig(configs, local = true)
    }

    suspend fun nextCloud(taskType: TaskType): ActiveProviderConfig? {
        val configs = getProviders(taskType, local = false)
        return pickConfig(configs, local = false)
    }

    suspend fun getLocalProviders(taskType: TaskType): List<ActiveProviderConfig> {
        return getProviders(taskType, local = true)
    }

    suspend fun getCloudProviders(taskType: TaskType): List<ActiveProviderConfig> {
        return getProviders(taskType, local = false)
    }

    /**
     * H-2: Get multi-provider fallback chain for a task.
     * Returns a prioritized list of providers to try in sequence.
     *
     * Priority order:
     * 1. Admin-selected preferred provider (if available and enabled)
     * 2. Providers sorted by capability match score
     * 3. Round-robin within same priority level
     *
     * @param taskType The type of task to execute
     * @param local Whether to get local or cloud providers
     * @param excludeProviderId Optional provider to exclude (e.g., one that just failed)
     * @return List of providers in priority order for fallback chain
     */
    suspend fun getFallbackChain(
        taskType: TaskType,
        local: Boolean,
        excludeProviderId: ProviderId? = null
    ): List<FallbackProvider> {
        val configs = getProviders(taskType, local)
            .filter { it.providerId != excludeProviderId }

        if (configs.isEmpty()) return emptyList()

        val preferred = adminRepository.getActiveProvider()
        // Check if preferred is null before using it

        // Build prioritized list
        val prioritized = configs.sortedWith(compareBy(
            // First: preferred provider first
            { it.providerId != preferred },
            // Second: sort by capability match priority (lower = better)
            { getCapabilityPriority(it, taskType) }
        )).mapIndexed { index, config ->
            FallbackProvider(
                config = config,
                priority = index,
                attemptCount = 0,
                lastError = null
            )
        }

        return prioritized
    }

    /**
     * H-2: Execute with multi-provider fallback chain.
     * Attempts the fallback chain until a provider succeeds or all fail.
     *
     * @param taskType The type of task
     * @param local Whether to use local or cloud providers
     * @param attemptFn Function to attempt execution with a provider
     * @return Pair of FallbackResult and result of attemptFn
     */
    suspend fun <T> executeWithFallback(
        taskType: TaskType,
        local: Boolean,
        attemptFn: suspend (ActiveProviderConfig) -> Result<T>
    ): Pair<FallbackResult, T?> {
        val chain = getFallbackChain(taskType, local)
        val attempted = mutableListOf<FallbackProvider>()
        val errors = mutableListOf<Throwable>()

        for (provider in chain) {
            val result = attemptFn(provider.config)
            if (result.isSuccess) {
                return FallbackResult.Success(provider.config, attempted.size + 1) to result.getOrNull()
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error")
                errors.add(error)
                attempted.add(provider.copy(attemptCount = 1, lastError = error))

                // If provider failed with quota/credential error, disable it temporarily
                if (isProviderFailureIrrecoverable(error)) {
                    disableProvider(provider.config.providerId)
                }
            }
        }

        return FallbackResult.Exhausted(attempted, errors) to null
    }

    private fun isProviderFailureIrrecoverable(error: Throwable): Boolean {
        return when (error) {
            is ProviderQuotaException -> true
            is ProviderAuthException -> true
            else -> false
        }
    }

    private fun getCapabilityPriority(config: ActiveProviderConfig, taskType: TaskType): Int {
        val requiredCap = requiredCapability(taskType)
        val hasExactCapability = requiredCap in config.capabilities

        // Provider has exact capability match = highest priority (0)
        // Otherwise prioritize based on capability count (fewer = more specialized = preferred)
        return if (hasExactCapability) 0 else config.capabilities.size
    }

    private suspend fun getProviders(taskType: TaskType, local: Boolean): List<ActiveProviderConfig> {
        val neededCapability = requiredCapability(taskType)
        return activeProviderManager.getActiveConfigs(null)
            .asSequence()
            .filter { config -> isLocalStyle(config.apiStyle) == local }
            .filter { config -> neededCapability in config.capabilities }
            .toList()
    }

    private suspend fun pickConfig(configs: List<ActiveProviderConfig>, local: Boolean): ActiveProviderConfig? {
        if (configs.isEmpty()) return null
        val preferred = adminRepository.getActiveProvider()
        val preferredConfig = preferred?.let { id -> configs.firstOrNull { it.providerId == id } }
        if (preferredConfig != null) return preferredConfig

        // FIX: Access configs inside mutex lock to prevent race condition (H-1 verified)
        return indexMutex.withLock {
            val next = if (local) localIndex else cloudIndex
            val normalized = if (next < 0) 0 else next
            val idx = normalized % configs.size
            val updated = if (normalized == Int.MAX_VALUE) 0 else normalized + 1
            if (local) localIndex = updated else cloudIndex = updated
            configs[idx]
        }
    }


    private fun requiredCapability(taskType: TaskType): CoreCapability {
        return when (taskType) {
            TaskType.IMAGE_GEN -> CoreCapability.IMAGE_GEN
            TaskType.AUDIO_GEN -> CoreCapability.AUDIO_SYNTHESIZE
            TaskType.VIDEO_GEN -> CoreCapability.VIDEO_GEN
            else -> CoreCapability.TEXT
        }
    }

    private fun isLocalStyle(style: CoreApiStyle): Boolean {
        return style == CoreApiStyle.LOCAL_IMAGE ||
               style == CoreApiStyle.LOCAL_TEXT ||
               style == CoreApiStyle.LIQUID
    }

    /**
     * Disable a provider by ID.
     * REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
     *
     * @param providerId The provider to disable
     */
    suspend fun disableProvider(providerId: ProviderId) {
        crudRepository.setProviderEnabledSync(providerId, false)
    }
}

/**
 * Exception thrown when provider quota is exceeded.
 */
class ProviderQuotaException(message: String, val providerId: ProviderId? = null) : Exception(message)

/**
 * Exception thrown when provider authentication fails.
 */
class ProviderAuthException(message: String, val providerId: ProviderId? = null) : Exception(message)
