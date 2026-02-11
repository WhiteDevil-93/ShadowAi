package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.providers.ContractApiStyle
import com.shadowai.core.providers.Provider
import com.shadowai.core.providers.ProviderRepository
import com.shadowai.core.security.discoveredApiKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val crudRepository: ProviderCrudRepository,
    private val secretRepository: ProviderSecretRepository,
    private val applicationScope: CoroutineScope
) : ProviderRepository {

    private val _providersFlow = MutableStateFlow<List<ActiveProviderConfig>>(emptyList())

    init {
        applicationScope.launch {
            _providersFlow.value = getAllProviders()
        }
    }

    override val providersFlow: Flow<List<ActiveProviderConfig>> = _providersFlow.asStateFlow()

    override val enabledProvidersFlow: Flow<List<ActiveProviderConfig>> = providersFlow.map { providers ->
        providers.filter { it.isEnabled }
    }

    override suspend fun saveProvider(config: ActiveProviderConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val provider = crudRepository.getProviderById(config.providerId)
            val updatedProvider = (provider ?: Provider(
                id = config.providerId,
                name = config.displayName,
                enabled = config.isEnabled,
                baseUrl = config.baseUrl ?: "",
                auth = com.shadowai.core.providers.ProviderAuth()
            )).copy(
                name = config.displayName,
                enabled = config.isEnabled,
                baseUrl = config.baseUrl ?: "",
                selectedModels = listOf(config.modelId)
            )
            crudRepository.saveProvider(updatedProvider)
            _providersFlow.value = getAllProviders()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeProvider(providerId: ProviderId): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            crudRepository.updateProviderListSync { providers ->
                providers.removeAll { provider -> provider.id == providerId }
            }
            secretRepository.clearApiKey(providerId.name)
            _providersFlow.value = getAllProviders()
        }
    }

    override suspend fun getProvider(providerId: ProviderId): ActiveProviderConfig? {
        return crudRepository.getProviderById(providerId)?.toActiveProviderConfig()
    }

    override suspend fun getAllProviders(): List<ActiveProviderConfig> {
        return crudRepository.getAllProviders().map { it.toActiveProviderConfig() }
    }

    override suspend fun getEnabledProviders(): List<ActiveProviderConfig> {
        return getAllProviders().filter { it.isEnabled }
    }

    override suspend fun setProviderEnabled(providerId: ProviderId, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            crudRepository.setProviderEnabledSync(providerId, enabled)
            _providersFlow.value = getAllProviders()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isProviderEnabled(providerId: ProviderId): Boolean {
        return getProvider(providerId)?.isEnabled ?: false
    }

    override suspend fun storeApiKey(providerId: ProviderId, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            discoveredApiKey(apiKey).use { secret ->
                secretRepository.saveApiKey(providerId.name, secret)
            }
        }
    }

    override suspend fun getApiKey(providerId: ProviderId): String? = withContext(Dispatchers.IO) {
        secretRepository.getApiKey(providerId.name)?.withSecretBytes { bytes ->
            String(bytes, Charsets.UTF_8)
        }
    }

    override suspend fun clearApiKey(providerId: ProviderId): Result<Unit> = withContext(Dispatchers.IO) {
        secretRepository.clearApiKey(providerId.name)
        Result.success(Unit)
    }

    override suspend fun setSelectedModel(providerId: ProviderId, modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val provider = crudRepository.getProviderById(providerId)
            if (provider != null) {
                crudRepository.saveProvider(provider.copy(selectedModels = listOf(modelId)))
                _providersFlow.value = getAllProviders()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Provider not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableModels(providerId: ProviderId): List<String> {
        return crudRepository.getProviderById(providerId)?.models?.map { it.id } ?: emptyList()
    }

    override suspend fun refreshModelList(providerId: ProviderId): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = crudRepository.getProviderById(providerId)
                ?: throw IllegalArgumentException("Provider not found: $providerId")

            val refreshedModels = ProviderModelCatalog.getModels(providerId).ifEmpty { provider.models }
            val selectedModels = provider.selectedModels?.takeIf { it.isNotEmpty() }
                ?: refreshedModels.firstOrNull()?.let { model -> listOf(model.id) }

            crudRepository.saveProvider(
                provider.copy(
                    models = refreshedModels,
                    selectedModels = selectedModels
                )
            )

            _providersFlow.value = getAllProviders()
            refreshedModels.map { model -> model.id }
        }
    }

    override suspend fun hasApiKey(providerId: ProviderId): Boolean {
        return getApiKey(providerId) != null
    }

    override suspend fun getProviderCount(): Int {
        return getAllProviders().size
    }

    override suspend fun getEnabledProviderCount(): Int {
        return getEnabledProviders().size
    }

    override suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ProviderId.entries.forEach { providerId ->
                secretRepository.clearApiKey(providerId.name)
            }
            crudRepository.saveProviders(emptyList())
            _providersFlow.value = emptyList()
        }
    }

    private fun Provider.toActiveProviderConfig(): ActiveProviderConfig {
        return ActiveProviderConfig(
            providerId = this.id,
            modelId = this.selectedModels?.firstOrNull() ?: this.models.firstOrNull()?.id ?: "",
            displayName = this.name,
            apiStyle = ContractApiStyle.CUSTOM, // Simplified for now
            baseUrl = this.baseUrl,
            isEnabled = this.enabled
        )
    }
}
