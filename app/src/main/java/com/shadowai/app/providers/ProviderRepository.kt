package com.shadowai.app.providers

import android.content.Context
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.Provider as CoreProvider
import com.shadowai.core.providers.ModelInfo as CoreModelInfo
import com.shadowai.core.providers.ApiStyle as CoreApiStyle
import com.shadowai.provideradapters.ProviderConfigurationService
import com.shadowai.provideradapters.ProviderCrudRepository
import com.shadowai.provideradapters.ProviderModelDiscovery
import com.shadowai.provideradapters.ProviderModelRepository
import com.shadowai.provideradapters.ProviderNetworkTester
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.core.security.SecretBytes
import com.shadowai.core.security.discoveredApiKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backward-compatible provider repository used by legacy app call sites.
 *
 * This facade delegates storage/network work to the provider-adapters module while keeping
 * the older app-facing types and method names stable during the migration to core contracts.
 */
@Singleton
class ProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crudRepository: ProviderCrudRepository,
    private val modelRepository: ProviderModelRepository,
    private val modelDiscovery: ProviderModelDiscovery,
    private val networkTester: ProviderNetworkTester,
    private val secretRepository: ProviderSecretRepository
) {
    fun listProviders(): List<Provider> {
        val providers = crudRepository.listProviders()
        return ensureDefaults(providers).map { it.toAppProvider() }
    }

    fun getProvider(providerId: ProviderId): Provider? {
        return crudRepository.getProvider(providerId)?.toAppProvider()
    }

    suspend fun getAllProviders(): List<Provider> {
        val providers = crudRepository.getAllProviders()
        val normalized = ensureDefaults(providers)
        if (providers.isEmpty() && normalized.isNotEmpty()) {
            crudRepository.saveProviders(normalized)
        }
        return normalized.map { it.toAppProvider() }
    }

    suspend fun saveProvider(provider: Provider) {
        crudRepository.saveProvider(provider.toCoreProvider())
    }

    suspend fun saveProviders(providers: List<Provider>) {
        val toPersist = if (providers.isEmpty()) {
            ProviderConfigurationService.getDefaultProviders(context)
        } else {
            providers.map { it.toCoreProvider() }
        }
        crudRepository.saveProviders(toPersist)
    }

    suspend fun setProviderEnabled(providerId: ProviderId, enabled: Boolean) {
        crudRepository.setProviderEnabledSync(providerId, enabled)
    }

    fun saveApiKey(providerId: ProviderId, apiKey: SecretBytes) {
        secretRepository.saveApiKey(providerId.name, apiKey)
    }

    fun saveApiKey(providerId: ProviderId, apiKey: String) {
        discoveredApiKey(apiKey).use { secret ->
            secretRepository.saveApiKey(providerId.name, secret)
        }
    }

    fun clearApiKey(providerId: ProviderId) {
        secretRepository.clearApiKey(providerId.name)
    }

    suspend fun getApiKey(providerId: ProviderId): SecretBytes? = withContext(Dispatchers.IO) {
        secretRepository.getApiKey(providerId.name)
    }

    fun getApiStyle(providerId: ProviderId): ApiStyle {
        return ProviderConfigurationService.getApiStyle(providerId).toAppApiStyle()
    }

    fun getSelectedModels(providerId: ProviderId): List<String> {
        return modelRepository.getSelectedModels(providerId)
    }

    fun setSelectedModels(providerId: ProviderId, models: List<String>) {
        modelRepository.setSelectedModels(providerId, models)
    }

    fun saveCustomModels(providerId: ProviderId, modelIds: List<String>) {
        modelRepository.saveCustomModels(providerId, modelIds)
    }

    suspend fun fetchProviderModels(providerId: ProviderId): List<ModelInfo> {
        return modelDiscovery.fetchProviderModels(providerId).map { it.toAppModelInfo() }
    }

    suspend fun discoverModels(provider: Provider): List<ModelInfo> {
        return modelDiscovery.discoverModels(provider.toCoreProvider()).map { it.toAppModelInfo() }
    }

    suspend fun testConnection(provider: Provider): Boolean {
        return networkTester.testConnection(provider.toCoreProvider())
    }

    private fun ensureDefaults(providers: List<CoreProvider>): List<CoreProvider> {
        return if (providers.isNotEmpty()) {
            providers
        } else {
            ProviderConfigurationService.getDefaultProviders(context)
        }
    }

    private fun Provider.toCoreProvider(): CoreProvider {
        return CoreProvider(
            id = id,
            name = name,
            enabled = enabled,
            baseUrl = baseUrl,
            auth = com.shadowai.core.providers.ProviderAuth(
                type = when (auth.type) {
                    AuthType.API_KEY -> com.shadowai.core.providers.AuthType.API_KEY
                    AuthType.OAUTH -> com.shadowai.core.providers.AuthType.OAUTH
                    AuthType.NONE -> com.shadowai.core.providers.AuthType.NONE
                },
                credentialAlias = auth.credentialAlias,
                hasCredential = auth.hasCredential
            ),
            capabilities = capabilities.map { it.toCoreCapability() },
            models = models.map { it.toCoreModelInfo() },
            selectedModels = selectedModels,
            customModels = customModels
        )
    }

    private fun CoreProvider.toAppProvider(): Provider {
        return Provider(
            id = id,
            name = name,
            enabled = enabled,
            baseUrl = baseUrl,
            auth = ProviderAuth(
                type = when (auth.type) {
                    com.shadowai.core.providers.AuthType.API_KEY -> AuthType.API_KEY
                    com.shadowai.core.providers.AuthType.OAUTH -> AuthType.OAUTH
                    com.shadowai.core.providers.AuthType.NONE -> AuthType.NONE
                },
                credentialAlias = auth.credentialAlias,
                hasCredential = auth.hasCredential
            ),
            capabilities = capabilities.map { it.toAppCapability() }.distinct(),
            models = models.map { it.toAppModelInfo() },
            selectedModels = selectedModels,
            customModels = customModels
        )
    }

    private fun ModelInfo.toCoreModelInfo(): CoreModelInfo {
        return CoreModelInfo(
            id = id,
            displayName = displayName,
            provider = provider,
            tier = tier,
            notes = notes,
            capabilities = capabilities.map { it.toCoreCapability() }.toSet()
        )
    }

    private fun CoreModelInfo.toAppModelInfo(): ModelInfo {
        return ModelInfo(
            id = id,
            displayName = displayName,
            provider = provider,
            tier = tier,
            notes = notes,
            capabilities = capabilities.map { it.toAppCapability() }.toSet()
        )
    }

    private fun Capability.toCoreCapability(): com.shadowai.core.Capability {
        return when (this) {
            Capability.TEXT -> com.shadowai.core.Capability.TEXT
            Capability.VISION -> com.shadowai.core.Capability.VISION
            Capability.IMAGE_GEN -> com.shadowai.core.Capability.IMAGE_GEN
            Capability.FUNCTION_CALLS -> com.shadowai.core.Capability.FUNCTION_CALLING
            Capability.VOICE -> com.shadowai.core.Capability.AUDIO_SYNTHESIZE
        }
    }

    private fun com.shadowai.core.Capability.toAppCapability(): Capability {
        return when (this) {
            com.shadowai.core.Capability.VISION -> Capability.VISION
            com.shadowai.core.Capability.IMAGE_GEN,
            com.shadowai.core.Capability.IMAGE_GEN_FAST,
            com.shadowai.core.Capability.IMAGE_GEN_HIGH_RES,
            com.shadowai.core.Capability.IMAGE_EDIT -> Capability.IMAGE_GEN
            com.shadowai.core.Capability.FUNCTION_CALLING -> Capability.FUNCTION_CALLS
            com.shadowai.core.Capability.AUDIO_TRANSCRIBE,
            com.shadowai.core.Capability.AUDIO_SYNTHESIZE,
            com.shadowai.core.Capability.AUDIO_UNDERSTAND -> Capability.VOICE
            else -> Capability.TEXT
        }
    }

    private fun CoreApiStyle.toAppApiStyle(): ApiStyle {
        return ApiStyle.valueOf(name)
    }
}
