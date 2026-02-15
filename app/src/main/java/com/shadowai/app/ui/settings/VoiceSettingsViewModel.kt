package com.shadowai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.accessibility.VoicePreferences
import com.shadowai.app.accessibility.VoiceRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for voice settings screen.
 * Manages voice recognition settings with persistence.
 */
@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val voicePreferences: VoicePreferences,
    private val voiceRecognitionManager: VoiceRecognitionManager
) : ViewModel() {

    data class VoiceSettingsUiState(
        val hotwordEnabled: Boolean = false,
        val customHotwordPhrase: String = "",
        val hotwordSensitivity: Float = 0.7f,
        val isListening: Boolean = false,
        val recognitionConfidence: Float = 0f
    )

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    init {
        // Load persisted settings from DataStore
        viewModelScope.launch {
            voicePreferences.hotwordEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(hotwordEnabled = enabled)
            }
        }

        viewModelScope.launch {
            voicePreferences.customHotwordPhrase.collect { phrase ->
                _uiState.value = _uiState.value.copy(
                    customHotwordPhrase = phrase.ifBlank { VoiceRecognitionManager.HOTWORD }
                )
            }
        }

        viewModelScope.launch {
            voicePreferences.hotwordSensitivity.collect { sensitivity ->
                _uiState.value = _uiState.value.copy(hotwordSensitivity = sensitivity)
            }
        }

        // Also sync with VoiceRecognitionManager state
        viewModelScope.launch {
            voiceRecognitionManager.isListeningState.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }

        viewModelScope.launch {
            voiceRecognitionManager.recognitionConfidence.collect { confidence ->
                _uiState.value = _uiState.value.copy(recognitionConfidence = confidence)
            }
        }
    }

    /**
     * Enable or disable hotword detection with persistence.
     */
    fun setHotwordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            voicePreferences.saveHotwordEnabled(enabled)
        }
        voiceRecognitionManager.setHotwordEnabled(enabled, viewModelScope)
        _uiState.value = _uiState.value.copy(hotwordEnabled = enabled)
    }

    /**
     * Set custom hotword phrase with persistence.
     * Empty string reverts to default.
     */
    fun setCustomHotwordPhrase(phrase: String) {
        val effectivePhrase = phrase.trim().ifBlank {
            VoiceRecognitionManager.HOTWORD
        }
        viewModelScope.launch {
            voicePreferences.saveCustomHotwordPhrase(
                if (phrase.trim().isBlank()) "" else phrase.trim()
            )
        }
        voiceRecognitionManager.setHotwordPhrase(effectivePhrase, viewModelScope)
        _uiState.value = _uiState.value.copy(customHotwordPhrase = effectivePhrase)
    }

    /**
     * Set hotword sensitivity threshold with persistence.
     */
    fun setHotwordSensitivity(sensitivity: Float) {
        val coerced = sensitivity.coerceIn(0.0f, 1.0f)
        viewModelScope.launch {
            voicePreferences.saveHotwordSensitivity(coerced)
        }
        voiceRecognitionManager.setHotwordSensitivity(coerced, viewModelScope)
        _uiState.value = _uiState.value.copy(hotwordSensitivity = coerced)
    }
}
