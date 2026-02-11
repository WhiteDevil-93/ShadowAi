package com.shadowai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.core.ProviderId
import com.shadowai.hotswapping.ConfigFormat
import com.shadowai.hotswapping.ProviderConfig
import com.shadowai.hotswapping.ProviderHotSwapManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View model for provider hot-swapping.
 */
@HiltViewModel
class HotSwapViewModel @Inject constructor(
    private val manager: ProviderHotSwapManager
) : ViewModel() {

    val snapshot = manager.snapshot

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            manager.refresh().onFailure { _statusMessage.value = it.message }
        }
    }

    /**
     * Adds or updates a provider configuration.
     */
    fun saveProvider(config: ProviderConfig) {
        viewModelScope.launch {
            val result = if (snapshot.value.providers.any { it.providerId == config.providerId }) {
                manager.updateProvider(config)
            } else {
                manager.addProvider(config)
            }
            result.onFailure { _statusMessage.value = it.message }
        }
    }

    /**
     * Removes a provider configuration.
     */
    fun removeProvider(providerId: ProviderId) {
        viewModelScope.launch {
            manager.removeProvider(providerId).onFailure { _statusMessage.value = it.message }
        }
    }

    /**
     * Toggles provider enabled state.
     */
    fun toggleProvider(providerId: ProviderId, enabled: Boolean) {
        viewModelScope.launch {
            manager.setProviderEnabled(providerId, enabled).onFailure { _statusMessage.value = it.message }
        }
    }

    /**
     * Creates a backup of the current configuration.
     */
    fun createBackup() {
        viewModelScope.launch {
            manager.backup(ConfigFormat.JSON)
                .onSuccess { _statusMessage.value = "Backup created: ${it.name}" }
                .onFailure { _statusMessage.value = it.message }
        }
    }

    /**
     * Restores the most recent backup.
     */
    fun restoreLatestBackup() {
        viewModelScope.launch {
            manager.restoreLatestBackup().onFailure { _statusMessage.value = it.message }
        }
    }

    /**
     * Clears status message.
     */
    fun clearStatus() {
        _statusMessage.value = null
    }

    /**
     * Exports the current configuration.
     */
    fun exportConfig(): String = manager.export(ConfigFormat.JSON)
}
