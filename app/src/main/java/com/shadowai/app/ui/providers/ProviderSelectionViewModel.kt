package com.shadowai.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.admin.AdminContract
import com.shadowai.app.providers.ModelInfo
import com.shadowai.app.providers.Provider
import com.shadowai.core.ProviderId
import com.shadowai.app.providers.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val providerRepository: ProviderRepository,
    private val adminContract: AdminContract
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderSelectionUiState())
    val uiState: StateFlow<ProviderSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val providers = providerRepository.getAllProviders()
            val activeId = adminContract.getActiveProvider()

            // Load key presence only (do not expose plaintext keys in UI state)
            val keyPresence = providers.associate { provider ->
                provider.id to (providerRepository.getApiKey(provider.id)?.use { true } ?: false)
            }
            
            // Pre-populate default models from provider definitions
            val defaultModels = providers.associate { it.id to it.models }
            
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
            providerRepository.setProviderEnabled(providerId, enabled)
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
            providerRepository.saveApiKey(providerId, apiKey)
            // Update key presence only
            val newPresence = _uiState.value.hasApiKey.toMutableMap()
            newPresence[providerId] = apiKey.isNotBlank()
            _uiState.value = _uiState.value.copy(hasApiKey = newPresence)
            loadProviders()
        }
    }

    fun saveLocalConfig(providerId: ProviderId, url: String) {
        viewModelScope.launch {
            val provider = providerRepository.getProvider(providerId) ?: return@launch
            val updated = provider.copy(baseUrl = url)
            providerRepository.saveProvider(updated)
            loadProviders()
        }
    }

    fun fetchModels(providerId: ProviderId) {
        viewModelScope.launch {
            val loadingMap = _uiState.value.isLoadingModels.toMutableMap()
            loadingMap[providerId] = true
            _uiState.value = _uiState.value.copy(isLoadingModels = loadingMap, errorMessage = null)

            try {
                val models = providerRepository.fetchProviderModels(providerId)
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
            val provider = providerRepository.getProvider(providerId) ?: return@launch
            val currentModels = provider.customModels ?: emptyList()
            val newSelection = (currentModels + modelId).distinct()
            providerRepository.saveCustomModels(providerId, newSelection)

            val currentSelected = providerRepository.getSelectedModels(providerId).toMutableList()
            if (!currentSelected.contains(modelId)) {
                currentSelected.add(modelId)
                providerRepository.setSelectedModels(providerId, currentSelected)
            }

            // Sync to admin repository for local model path consistency
            if (providerId == ProviderId.LIQUID) {
                adminContract.setLocalTextModelPath(modelId)
            }

            loadProviders()
        }
    }
}
