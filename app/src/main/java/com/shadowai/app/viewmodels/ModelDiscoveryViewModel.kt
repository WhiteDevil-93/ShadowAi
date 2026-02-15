package com.shadowai.app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderId
import com.shadowai.modelcatalog.ModelDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * UI state representation for model discovery.
 */
data class DiscoveryUiState(
    val discoveredModels: List<ModelDescriptor> = emptyList(),
    val isScanning: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel that bridges ModelDiscovery (from model-catalog module) to the UI.
 * Provides StateFlow-based reactive state for Compose integration.
 *
 * M-15: Uses dedicated thread pool for async model discovery to avoid blocking UI.
 */
@HiltViewModel
class ModelDiscoveryViewModel @Inject constructor(
    private val modelDiscovery: ModelDiscovery
) : ViewModel() {

    companion object {
        private const val TAG = "ModelDiscoveryViewModel"
    }

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    // M-15: Dedicated thread pool for model discovery I/O operations
    // Using fixed thread pool to limit concurrency and resource consumption
    private val discoveryDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    override fun onCleared() {
        // M-15: Clean up thread pool when ViewModel is destroyed
        (discoveryDispatcher as kotlinx.coroutines.ExecutorCoroutineDispatcher).close()
        super.onCleared()
    }

    init {
        // Automatically scan on initialization to populate the catalog
        scanForModels()
    }

    /**
     * Scans all configured sources for models.
     * Updates the UI state with discovered models.
     * M-15: Uses dedicated dispatcher for I/O operations.
     */
    fun scanForModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null
            )

            try {
                Log.d(TAG, "Starting model discovery scan...")
                // M-15: Run discovery on dedicated thread pool
                val models = withContext(discoveryDispatcher) {
                    modelDiscovery.discoverFromAllSources()
                }
                Log.d(TAG, "Discovered ${models.size} models")

                _uiState.value = _uiState.value.copy(
                    discoveredModels = models,
                    isScanning = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Model discovery failed", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "Failed to scan for models: ${e.message}"
                )
            }
        }
    }

    /**
     * Forces a rescan of models.
     * Useful when models have been imported via SAF or directories have changed.
     * M-15: Uses dedicated dispatcher for I/O operations.
     */
    fun rescan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null
            )

            try {
                Log.d(TAG, "Starting forced model rescan...")
                // M-15: Run rescan on dedicated thread pool
                val models = withContext(discoveryDispatcher) {
                    modelDiscovery.rescan()
                }
                Log.d(TAG, "Rescan discovered ${models.size} models")

                _uiState.value = _uiState.value.copy(
                    discoveredModels = models,
                    isScanning = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Model rescan failed", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "Failed to rescan for models: ${e.message}"
                )
            }
        }
    }

    /**
     * Gets all discovered models for a specific provider.
     *
     * @param providerId The provider to filter models by
     * @return List of ModelDescriptor instances matching the provider
     */
    fun getModelsForProvider(providerId: ProviderId): List<ModelDescriptor> {
        return _uiState.value.discoveredModels.filter { model ->
            model.providerId == providerId
        }
    }

    /**
     * Gets the file system path for a specific model.
     * This is used for configuring adapter settings with local model paths.
     *
     * @param modelId The unique identifier of the model
     * @return The file path string if found in metadata, null otherwise
     */
    fun getModelPath(modelId: String): String? {
        val model = _uiState.value.discoveredModels.find { it.id == modelId }
        return model?.metadata?.get("modelPath") as? String
    }

    /**
     * Gets a model by its ID.
     *
     * @param modelId The unique identifier of the model
     * @return The ModelDescriptor if found, null otherwise
     */
    fun getModelById(modelId: String): ModelDescriptor? {
        return _uiState.value.discoveredModels.find { it.id == modelId }
    }

    /**
     * Discovers models from specific local directories.
     * Useful when user adds custom model directories.
     * M-15: Uses dedicated dispatcher for I/O operations.
     *
     * @param directories List of directory paths to scan
     */
    fun scanCustomDirectories(directories: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null
            )

            try {
                Log.d(TAG, "Scanning custom directories: $directories")
                // M-15: Run directory scan on dedicated thread pool
                val models = withContext(discoveryDispatcher) {
                    modelDiscovery.discoverFromAllSources(
                        localModelDirs = directories,
                        jsonConfigFiles = emptyList()
                    )
                }
                Log.d(TAG, "Discovered ${models.size} models from custom directories")

                // Merge with existing models, deduplicating by ID
                val existingIds = _uiState.value.discoveredModels.map { it.id }.toSet()
                val newModels = models.filter { it.id !in existingIds }
                val mergedModels = _uiState.value.discoveredModels + newModels

                _uiState.value = _uiState.value.copy(
                    discoveredModels = mergedModels,
                    isScanning = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Custom directory scan failed", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "Failed to scan custom directories: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears the error message from the UI state.
     * Call this after displaying an error to the user.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
