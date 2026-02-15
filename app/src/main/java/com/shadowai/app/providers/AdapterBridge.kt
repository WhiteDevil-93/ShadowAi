package com.shadowai.app.providers

import android.util.Log
import com.shadowai.core.Artifact
import com.shadowai.core.Transform
import com.shadowai.core.providers.ActiveProviderConfig
// REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.provideradapters.ProviderAdapterConfig
import com.shadowai.provideradapters.ProviderAdapterFactory
import com.shadowai.provideradapters.ProviderAdapter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the app-specific [ActiveProviderConfig] to the unified [ProviderAdapter] system.
 */
@Singleton
class AdapterBridge @Inject constructor(
    private val factory: ProviderAdapterFactory,
    // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
    private val secretRepository: ProviderSecretRepository
) {
    // M-11: Standardized logging
    companion object {
        private const val TAG = "AdapterBridge"
    }

    /**
     * Converts an [ActiveProviderConfig] to a [ProviderAdapterConfig].
     */
    suspend fun convertConfig(appConfig: ActiveProviderConfig): ProviderAdapterConfig {
        // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
        Log.d(TAG, "Converting config for provider: ${appConfig.providerId}")
        val apiKeySecret = secretRepository.getApiKey(appConfig.providerId.name)
        return ProviderAdapterConfig(
            providerId = appConfig.providerId,
            baseUrl = appConfig.baseUrl,
            apiKeySecret = apiKeySecret,
            modelId = appConfig.modelId
        ).also {
            Log.d(TAG, "Config converted: providerId=${it.providerId}, modelId=${it.modelId}")
        }
    }

    /**
     * Retrieves an adapter for the given [ActiveProviderConfig].
     */
    suspend fun getAdapter(appConfig: ActiveProviderConfig): ProviderAdapter {
        return factory.getAdapter(convertConfig(appConfig))
    }

    /**
     * Unified execution method using Artifacts for normalized I/O.
     * M-7 FIXED: Returns Artifact directly to eliminate double conversion.
     * Callers can extract the primitive value themselves using artifact extensions.
     */
    suspend fun execute(
        appConfig: ActiveProviderConfig,
        transform: Transform,
        input: Artifact,
        parameters: Map<String, Any> = emptyMap()
    ): Result<Artifact> {
        val adapter = getAdapter(appConfig)

        // 1. Initialize if needed
        if (!adapter.isAvailable()) {
            Log.d(TAG, "Initializing adapter for ${appConfig.providerId}")
            val initSuccess = adapter.initialize()
            if (!initSuccess) {
                Log.e(TAG, "Failed to initialize adapter for ${appConfig.providerId}")
                return Result.failure(IllegalStateException("Failed to initialize adapter for ${appConfig.providerId}"))
            }
            Log.d(TAG, "Adapter initialized for ${appConfig.providerId}")
        }

        // 2. Execute and return Artifact directly (no conversion)
        // M-7: Eliminated double artifact conversion - return Artifact as-is
        Log.d(TAG, "Executing transform ${transform.javaClass.simpleName} on ${appConfig.providerId}")
        return adapter.execute(transform, input, parameters).also { result ->
            result.onSuccess { Log.d(TAG, "Execution successful on ${appConfig.providerId}") }
            result.onFailure { Log.e(TAG, "Execution failed on ${appConfig.providerId}: ${it.message}") }
        }
    }
}
