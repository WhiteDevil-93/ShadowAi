package com.shadowai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.security.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // Biometric settings
    val requireBiometricForDownloads: StateFlow<Boolean> = preferencesManager.requireBiometricForDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val requireBiometricForHistory: StateFlow<Boolean> = preferencesManager.requireBiometricForHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val requireBiometricForSettings: StateFlow<Boolean> = preferencesManager.requireBiometricForSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Screenshot protection
    val preventScreenshots: StateFlow<Boolean> = preferencesManager.preventScreenshots
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Auto-lock settings
    val autoLockEnabled: StateFlow<Boolean> = preferencesManager.autoLockEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val autoLockTimeout: StateFlow<AutoLockTimeout> = preferencesManager.autoLockTimeout
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AutoLockTimeout.FIVE_MINUTES
        )

    fun setRequireBiometricForDownloads(require: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveRequireBiometricForDownloads(require)
        }
    }

    fun setRequireBiometricForHistory(require: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveRequireBiometricForHistory(require)
        }
    }

    fun setRequireBiometricForSettings(require: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveRequireBiometricForSettings(require)
        }
    }

    fun setPreventScreenshots(prevent: Boolean) {
        viewModelScope.launch {
            preferencesManager.savePreventScreenshots(prevent)
        }
    }

    fun setAutoLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveAutoLockEnabled(enabled)
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            preferencesManager.saveAutoLockTimeout(timeout)
        }
    }

    enum class AutoLockTimeout(val minutes: Int, val displayName: String) {
        ONE_MINUTE(1, "1 minute"),
        FIVE_MINUTES(5, "5 minutes"),
        FIFTEEN_MINUTES(15, "15 minutes"),
        THIRTY_MINUTES(30, "30 minutes"),
        NEVER(0, "Never")
    }
}