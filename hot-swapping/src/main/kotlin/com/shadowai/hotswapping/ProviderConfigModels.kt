package com.shadowai.hotswapping

import com.shadowai.core.ProviderId
import com.shadowai.core.security.SecretBytes
import com.shadowai.provideradapters.ProviderAdapterConfig
import java.util.Arrays

/**
 * Supported config formats for hot-swap definitions.
 */
enum class ConfigFormat {
    JSON,
    YAML
}

/**
 * Provider configuration loaded from disk or UI.
 */
data class ProviderConfig(
    val providerId: ProviderId,
    val name: String,
    val baseUrl: String,
    val modelId: String? = null,
    val isEnabled: Boolean = true,
    val apiKeySecret: String? = null, // Store as encrypted string for hot-swapping
    val isLocal: Boolean = false,
    val capabilities: List<String> = emptyList()
) {
    /**
     * Converts configuration to adapter config.
     */
    fun toAdapterConfig(): ProviderAdapterConfig {
        val secret = apiKeySecret?.let { rawSecret ->
            val rawBytes = rawSecret.toByteArray(Charsets.UTF_8)
            try {
                SecretBytes.fromByteArray(rawBytes)
            } finally {
                Arrays.fill(rawBytes, 0.toByte())
            }
        }
        return ProviderAdapterConfig(
            providerId = providerId,
            baseUrl = baseUrl,
            apiKeySecret = secret,
            modelId = modelId
        )
    }
}

/**
 * Versioned snapshot of provider configs.
 */
data class ProviderConfigSnapshot(
    val version: Int,
    val providers: List<ProviderConfig>,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Validation issue for provider configs.
 */
data class ProviderConfigValidationIssue(
    val providerId: ProviderId,
    val code: String,
    val message: String
)
