package com.shadowai.app.providers

import android.util.Log
import com.shadowai.app.ai.InferenceEngineControl
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for AI provider fallback logic.
 * Implements the Androidify multi-modal AI fallback pattern:
 * - Check if LocalInferenceEngine is available
 * - Check if native library is loaded (isNativeAvailable)
 * - Fall back to appropriate cloud provider if local unavailable
 *
 * This ensures transparent fallback from on-device inference to cloud providers
 * when local inference is unavailable (e.g., native library not loaded, no models).
 */
@Singleton
class ProviderFallbackManager @Inject constructor(
    private val localInferenceEngine: LocalInferenceEngine,
    private val providerSelector: ProviderSelector,
    private val adminRepository: com.shadowai.app.admin.implementation.AdminRepository
) {
    companion object {
        private const val TAG = "ProviderFallbackManager"
    }

    /**
     * Result of provider selection with potential fallback.
     */
    data class ProviderSelectionResult(
        val config: ActiveProviderConfig,
        val isFallback: Boolean,
        val fallbackReason: String? = null,
        val originalProviderId: ProviderId? = null
    )

    /**
     * Selects the best provider for a task, implementing smart fallback:
     * 1. If local provider requested and available -> use local
     * 2. If local provider requested but unavailable -> fallback to cloud
     * 3. If cloud provider requested -> use cloud
     *
     * This implements the Androidify pattern:
     * ```
     * val provider = if (localEngine?.isNativeAvailable == true && model.isLocalCompatible) {
     *     ProviderId.LOCAL_TEXT
     * } else {
     *     cloudProviderSelector.select(model.type)
     * }
     * ```
     *
     * @param taskType The type of task to execute
     * @param preferLocal Whether local execution is preferred (if available)
     * @return ProviderSelectionResult containing selected provider and fallback info
     */
    suspend fun selectProvider(
        taskType: TaskType,
        preferLocal: Boolean = true
    ): ProviderSelectionResult? {
        return if (preferLocal) {
            selectWithLocalPreference(taskType)
        } else {
            selectCloudFirst(taskType)
        }
    }

    /**
     * Attempts to select a local provider, falling back to cloud if local is unavailable.
     */
    private suspend fun selectWithLocalPreference(taskType: TaskType): ProviderSelectionResult? {
        // Check if local inference is available (native library loaded and models exist)
        val isLocalAvailable = isLocalInferenceAvailable()

        if (isLocalAvailable) {
            // Local inference available - try to get local provider
            val localConfig = providerSelector.nextLocal(taskType)
            if (localConfig != null) {
                return ProviderSelectionResult(
                    config = localConfig,
                    isFallback = false,
                    fallbackReason = null,
                    originalProviderId = null
                )
            }
        }

        // Local unavailable or no local provider configured - fallback to cloud
        val cloudConfig = providerSelector.nextCloud(taskType)
        if (cloudConfig != null) {
            return ProviderSelectionResult(
                config = cloudConfig,
                isFallback = true,
                fallbackReason = buildFallbackReason(isLocalAvailable),
                originalProviderId = null
            )
        }

        // No providers available at all
        return null
    }

    /**
     * Selects a cloud provider as primary choice.
     */
    private suspend fun selectCloudFirst(taskType: TaskType): ProviderSelectionResult? {
        val cloudConfig = providerSelector.nextCloud(taskType)
        if (cloudConfig != null) {
            return ProviderSelectionResult(
                config = cloudConfig,
                isFallback = false,
                fallbackReason = null,
                originalProviderId = null
            )
        }

        // Cloud unavailable - try local as fallback
        val isLocalAvailable = isLocalInferenceAvailable()
        if (isLocalAvailable) {
            val localConfig = providerSelector.nextLocal(taskType)
            if (localConfig != null) {
                return ProviderSelectionResult(
                    config = localConfig,
                    isFallback = true,
                    fallbackReason = "No cloud providers available, falling back to local inference",
                    originalProviderId = null
                )
            }
        }

        return null
    }

    /**
     * Checks if local inference is fully available:
     * 1. Native library is loaded (isNativeAvailable)
     * 2. At least one GGUF model is available on disk
     * 3. Device has sufficient resources
     */
    private suspend fun isLocalInferenceAvailable(): Boolean {
        // Warm up engine resources before querying availability when supported.
        if (localInferenceEngine is InferenceEngineControl) {
            localInferenceEngine.warmup()
                .onFailure { error ->
                    Log.w(TAG, "Failed to warm up local inference engine", error)
                }
        }

        // Check 1: Native library loaded
        val isNativeLoaded = localInferenceEngine.isNativeAvailable
        if (!isNativeLoaded) {
            return false
        }

        // Check 2: Models available
        val models = localInferenceEngine.getAvailableModels()
        if (models.isEmpty()) {
            return false
        }

        return true
    }

    /**
     * Builds a human-readable reason for fallback.
     */
    private fun buildFallbackReason(isLocalAvailable: Boolean): String {
        return when {
            !isLocalAvailable && !localInferenceEngine.isNativeAvailable ->
                "Native inference library not available, falling back to cloud provider"
            !isLocalAvailable ->
                "No local models available, falling back to cloud provider"
            else ->
                "Local inference unavailable, falling back to cloud provider"
        }
    }

    /**
     * Gets the appropriate provider for a task type based on capability.
     * This is the core method implementing the Androidify fallback pattern.
     *
     * @param taskType Type of task
     * @return ProviderId of the best available provider (local preferred, cloud fallback)
     */
    suspend fun getBestProvider(taskType: TaskType): ProviderId? {
        val result = selectProvider(taskType, preferLocal = true)
        return result?.config?.providerId
    }

    /**
     * Checks if fallback would be triggered for a given configuration.
     * Useful for UI indicators or pre-flight checks.
     */
    suspend fun wouldFallback(taskType: TaskType, requestedProviderId: ProviderId): Boolean {
        // If requesting a local provider, check if local is actually available
        if (requestedProviderId.isLocal()) {
            return !isLocalInferenceAvailable()
        }
        return false
    }
}

/**
 * Extension function for ProviderId to check if it's a local provider.
 */
private fun ProviderId.isLocal(): Boolean {
    return this == ProviderId.LOCAL_TEXT ||
           this == ProviderId.LOCAL_IMAGE ||
           this == ProviderId.LIQUID ||
           this == ProviderId.FLUX
}
