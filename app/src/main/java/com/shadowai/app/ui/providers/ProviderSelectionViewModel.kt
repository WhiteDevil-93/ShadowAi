package com.shadowai.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.admin.AdminContract
import com.shadowai.app.providers.ModelInfo
import com.shadowai.app.providers.Provider
import com.shadowai.core.ProviderId
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.app.providers.AuthType
import com.shadowai.app.providers.Capability
import com.shadowai.app.providers.ProviderAuth
// REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
import com.shadowai.provideradapters.ProviderCrudRepository
import com.shadowai.provideradapters.ProviderSecretRepository
import com.shadowai.provideradapters.ProviderModelRepository
import com.shadowai.provideradapters.ProviderModelDiscovery
import com.shadowai.core.security.discoveredApiKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProviderSelectionUiState(
    val providers: List<Provider> = emptyList(),
    val activeProviderId: ProviderId? = null,
    val hasApiKey: Map<ProviderId, Boolean> = emptyMap(),
    val providerModels: Map<ProviderId, List<ModelInfo>> = emptyMap(), // Available models for discovery
    val isLoading: Boolean = false,
    val isLoadingModels: Map<ProviderId, Boolean> = emptyMap(),
    val errorMessage: String? = null
)

@HiltViewModel
class ProviderSelectionViewModel @Inject constructor(
    // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
    private val crudRepository: ProviderCrudRepository,
    private val secretRepository: ProviderSecretRepository,
    private val modelRepository: ProviderModelRepository,
    private val modelDiscovery: ProviderModelDiscovery,
    private val localInferenceEngine: LocalInferenceEngine,
    private val adminContract: AdminContract
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderSelectionUiState())
    val uiState: StateFlow<ProviderSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    // ... (rest of the file content remains similar, but skipping for brevity as I'm correcting imports and extensions)

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
            val providers = crudRepository.getAllProviders().map { it.toAppProvider() }
            val activeId = adminContract.getActiveProvider()

            // Load key presence only (do not expose plaintext keys in UI state)
            val keyPresence = providers.associate { provider ->
                provider.id to (secretRepository.getApiKey(provider.id.name)?.use { _ -> true } ?: false)
            }

            // Pre-populate default models from provider definitions
            val defaultModels = providers.associate { provider ->
                provider.id to if (provider.id == ProviderId.LIQUID) emptyList() else provider.models
            }

            _uiState.value = _uiState.value.copy(
                providers = providers,
                activeProviderId = activeId,
                hasApiKey = keyPresence,
                providerModels = defaultModels,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    fun toggleProviderEnabled(providerId: ProviderId, enabled: Boolean) {
        viewModelScope.launch {
            // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
            crudRepository.setProviderEnabledSync(providerId, enabled)
            loadProviders() // Refresh
        }
    }

    fun setActiveProvider(providerId: ProviderId) {
        viewModelScope.launch {
            adminContract.setActiveProvider(providerId)
            _uiState.value = _uiState.value.copy(activeProviderId = providerId)
        }
    }

    fun saveApiKey(providerId: ProviderId, apiKey: String) {
        viewModelScope.launch {
            // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
            discoveredApiKey(apiKey).use { secret ->
                secretRepository.saveApiKey(providerId.name, secret)
            }
            // Update key presence only
            val newPresence = _uiState.value.hasApiKey.toMutableMap()
            newPresence[providerId] = apiKey.isNotBlank()
            _uiState.value = _uiState.value.copy(hasApiKey = newPresence)
            loadProviders()
        }
    }

    fun saveLocalConfig(providerId: ProviderId, url: String) {
        viewModelScope.launch {
            // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
            val provider = crudRepository.getProvider(providerId)?.toAppProvider() ?: return@launch
            val updated = provider.copy(baseUrl = url)
            crudRepository.saveProvider(updated.toCoreProvider())
            loadProviders()
        }
    }

    fun fetchModels(providerId: ProviderId) {
        viewModelScope.launch {
            val loadingMap = _uiState.value.isLoadingModels.toMutableMap()
            loadingMap[providerId] = true
            _uiState.value = _uiState.value.copy(isLoadingModels = loadingMap, errorMessage = null)

            try {
                // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
                val models = modelDiscovery.fetchProviderModels(providerId).map { it.toAppModelInfo() }
                val modelsMap = _uiState.value.providerModels.toMutableMap()
                modelsMap[providerId] = models
                _uiState.value = _uiState.value.copy(providerModels = modelsMap)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to fetch models: ${e.message}")
            } finally {
                val doneLoading = _uiState.value.isLoadingModels.toMutableMap()
                doneLoading[providerId] = false
                _uiState.value = _uiState.value.copy(isLoadingModels = doneLoading)
            }
        }
    }

    fun addModelToProvider(providerId: ProviderId, modelId: String) {
        viewModelScope.launch {
            val normalizedModelId = normalizeModelSelection(providerId, modelId)

            // REPOSITORY ADAPTER CLEANUP: Direct split repository access - facade removed
            val provider = crudRepository.getProvider(providerId)?.toAppProvider() ?: return@launch
            val currentModels = provider.customModels ?: emptyList()
            val newSelection = (currentModels + normalizedModelId).distinct()
            modelRepository.saveCustomModels(providerId, newSelection)

            val currentSelected = modelRepository.getSelectedModels(providerId).toMutableList()
            if (!currentSelected.contains(normalizedModelId)) {
                currentSelected.add(normalizedModelId)
                modelRepository.setSelectedModels(providerId, currentSelected)
            }

            // Sync to admin repository for local model path consistency
            if (providerId == ProviderId.LIQUID) {
                adminContract.setLocalTextModelPath(normalizedModelId)
            }

            loadProviders()
        }
    }

    private suspend fun normalizeModelSelection(providerId: ProviderId, modelId: String): String {
        if (providerId != ProviderId.LIQUID) return modelId

        if (File(modelId).isAbsolute) {
            return modelId
        }

        val availableModels = runCatching { localInferenceEngine.getAvailableModels() }
            .getOrDefault(emptyList())

        return availableModels.firstOrNull { path ->
            File(path).name.equals(modelId, ignoreCase = true)
        } ?: modelId
    }
}

// Extension functions to convert between Core and App types (from removed facade)
private fun com.shadowai.core.providers.Provider.toAppProvider(): Provider {
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

private fun Provider.toCoreProvider(): com.shadowai.core.providers.Provider {
    return com.shadowai.core.providers.Provider(
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

private fun com.shadowai.core.providers.ModelInfo.toAppModelInfo(): ModelInfo {
    return ModelInfo(
        id = id,
        displayName = displayName,
        provider = provider,
        tier = tier,
        notes = notes,
        capabilities = capabilities.map { it.toAppCapability() }.toSet()
    )
}

private fun ModelInfo.toCoreModelInfo(): com.shadowai.core.providers.ModelInfo {
    return com.shadowai.core.providers.ModelInfo(
        id = id,
        displayName = displayName,
        provider = provider,
        tier = tier,
        notes = notes,
        capabilities = capabilities.map { it.toCoreCapability() }.toSet()
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
