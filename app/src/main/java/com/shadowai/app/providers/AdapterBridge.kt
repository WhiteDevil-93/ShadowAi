package com.shadowai.app.providers

import com.shadowai.core.Artifact
import com.shadowai.core.Transform
import com.shadowai.core.security.SecretBytes
import com.shadowai.provideradapters.ProviderAdapterConfig
import com.shadowai.provideradapters.ProviderAdapterFactory
import com.shadowai.provideradapters.ProviderAdapter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the app-specific [ActiveProviderConfig] to the unified [ProviderAdapter] system.
 * Leverages [ShadowAiArtifactSystem] for I/O normalization.
 */
@Singleton
class AdapterBridge @Inject constructor(
    private val factory: ProviderAdapterFactory
) {

    /**
     * Converts an [ActiveProviderConfig] to a [ProviderAdapterConfig].
     */
    fun convertConfig(appConfig: ActiveProviderConfig): ProviderAdapterConfig {
        return ProviderAdapterConfig(
            providerId = appConfig.providerId,
            baseUrl = appConfig.baseUrl?.toString() ?: "",
            apiKeySecret = appConfig.authHeader?.removePrefix("Bearer ")?.trim()?.let { SecretBytes.fromByteArray(it.toByteArray()) },
            modelId = appConfig.modelId
        )
    }

    /**
     * Retrieves an adapter for the given [ActiveProviderConfig].
     */
    fun getAdapter(appConfig: ActiveProviderConfig): ProviderAdapter {
        return factory.getAdapter(convertConfig(appConfig))
    }

    /**
     * Unified execution method using Artifacts for normalized I/O.
     */
    suspend fun execute(
        appConfig: ActiveProviderConfig,
        transform: Transform,
        input: Artifact,
        parameters: Map<String, Any> = emptyMap()
    ): Result<Any> {
        val adapter = getAdapter(appConfig)

        // 1. Initialize if needed
        if (!adapter.isAvailable()) {
            val initSuccess = adapter.initialize()
            if (!initSuccess) {
                return Result.failure(IllegalStateException("Failed to initialize adapter for ${appConfig.providerId}"))
            }
        }

        // 2. Execute directly with core Artifact contract
        return adapter.execute(transform, input, parameters).mapCatching { output ->
            when (output) {
                is Artifact.Text -> output.content
                is Artifact.Image -> output.uri.toString()
                is Artifact.Audio -> output.uri.toString()
                is Artifact.Video -> output.uri.toString()
                is Artifact.Json -> output.jsonString
                is Artifact.Binary -> output.data
                is Artifact.Empty -> ""
                is Artifact.Error -> throw IllegalStateException(output.message)
            }
        }
    }
}
