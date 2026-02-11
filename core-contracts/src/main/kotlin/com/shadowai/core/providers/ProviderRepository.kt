package com.shadowai.core.providers

import com.shadowai.core.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing provider configuration and credentials.
 */
interface ProviderRepository {
    val providersFlow: Flow<List<ActiveProviderConfig>>
    val enabledProvidersFlow: Flow<List<ActiveProviderConfig>>

    suspend fun saveProvider(config: ActiveProviderConfig): Result<Unit>
    suspend fun removeProvider(providerId: ProviderId): Result<Unit>
    suspend fun getProvider(providerId: ProviderId): ActiveProviderConfig?
    suspend fun getAllProviders(): List<ActiveProviderConfig>
    suspend fun getEnabledProviders(): List<ActiveProviderConfig>
    suspend fun setProviderEnabled(providerId: ProviderId, enabled: Boolean): Result<Unit>
    suspend fun isProviderEnabled(providerId: ProviderId): Boolean

    suspend fun storeApiKey(providerId: ProviderId, apiKey: String): Result<Unit>
    suspend fun getApiKey(providerId: ProviderId): String?
    suspend fun clearApiKey(providerId: ProviderId): Result<Unit>

    suspend fun setSelectedModel(providerId: ProviderId, modelId: String): Result<Unit>
    suspend fun getAvailableModels(providerId: ProviderId): List<String>
    suspend fun refreshModelList(providerId: ProviderId): Result<List<String>>

    suspend fun hasApiKey(providerId: ProviderId): Boolean
    suspend fun getProviderCount(): Int
    suspend fun getEnabledProviderCount(): Int
    suspend fun clearAll(): Result<Unit>
}

/**
 * Active provider configuration persisted by repository implementations.
 */
data class ActiveProviderConfig(
    val providerId: ProviderId,
    val modelId: String,
    val displayName: String,
    val apiStyle: ContractApiStyle,
    val baseUrl: String? = null,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val customParameters: Map<String, String> = emptyMap()
)

/**
 * Supported API protocol styles.
 */
enum class ContractApiStyle {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
    LIQUID,
    LOCAL_TEXT,
    LOCAL_IMAGE,
    LOCAL_VIDEO,
    LOCAL_AUDIO,
    OLLAMA,
    CUSTOM
}
