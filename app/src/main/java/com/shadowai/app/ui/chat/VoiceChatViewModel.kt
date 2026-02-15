package com.shadowai.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.app.accessibility.VoicePreferences
import com.shadowai.app.accessibility.VoiceRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing voice input in chat screen.
 * Integrates with VoiceRecognitionManager for hotword detection and voice input.
 * Persists settings via VoicePreferences.
 */
@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    private val voiceRecognitionManager: VoiceRecognitionManager,
    private val voicePreferences: VoicePreferences
) : ViewModel() {

    // Track if permissions have been requested
    private var hasRequestedPermission = false

    data class VoiceChatUiState(
        val isListening: Boolean = false,
        val confidence: Float = 0f,
        val partialText: String = "",
        val hasHotwordPermission: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(VoiceChatUiState())
    val uiState: StateFlow<VoiceChatUiState> = _uiState.asStateFlow()

    init {
        // Load persisted settings and initialize VoiceRecognitionManager
        viewModelScope.launch {
            // Check if permissions were previously requested
            voicePreferences.recordAudioPermissionRequested.collect { requested ->
                hasRequestedPermission = requested
            }
        }

        // Initialize VoiceRecognitionManager with persisted preferences
        viewModelScope.launch {
            voiceRecognitionManager.initializeWithPreferences(voicePreferences)
        }

        // Initialize callbacks
        voiceRecognitionManager.setCallbacks(
            onVoiceResult = { text, confidence ->
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    confidence = confidence,
                    partialText = "",
                    errorMessage = null
                )
                // Notify callback about voice result
                onVoiceTextReceived?.invoke(text)
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    isListening = false,
                    confidence = 0f,
                    partialText = "",
                    errorMessage = error
                )
            },
            onPartialResult = { partial ->
                _uiState.value = _uiState.value.copy(
                    partialText = partial
                )
            },
            onListeningStateChanged = { listening ->
                _uiState.value = _uiState.value.copy(
                    isListening = listening
                )
            },
            onHotwordDetected = {
                // Hotword detected - trigger voice input
                _uiState.value = _uiState.value.copy(
                    partialText = "Hey Shadow detected!"
                )
                // Auto-start voice input after hotword
                startListening()
            }
        )

        // Collect state from VoiceRecognitionManager
        viewModelScope.launch {
            voiceRecognitionManager.isListeningState.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }

        viewModelScope.launch {
            voiceRecognitionManager.recognitionConfidence.collect { confidence ->
                _uiState.value = _uiState.value.copy(confidence = confidence)
            }
        }

        // Check permissions
        _uiState.value = _uiState.value.copy(
            hasHotwordPermission = voiceRecognitionManager.hasPermissions()
        )
    }

    // Callback for when voice text is received
    private var onVoiceTextReceived: ((String) -> Unit)? = null

    /**
     * Set callback for voice text results
     */
    fun setVoiceTextCallback(callback: (String) -> Unit) {
        onVoiceTextReceived = callback
    }

    /**
     * Toggle voice input (start/stop listening)
     */
    fun toggleVoiceInput() {
        if (_uiState.value.isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    /**
     * Start voice input listening
     * @param onPermissionRequest Optional callback to request permission when not granted
     */
    fun startListening(onPermissionRequest: (() -> Unit)? = null) {
        if (!voiceRecognitionManager.hasPermissions()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Microphone permission required. Please enable it in settings."
            )
            // Signal that permission request is needed
            onPermissionRequest?.invoke()
            return
        }

        voiceRecognitionManager.startVoiceInput()
        _uiState.value = _uiState.value.copy(
            isListening = true,
            errorMessage = null
        )
    }

    /**
     * Check if this is the first time requesting permission
     */
    fun shouldShowPermissionRationale(): Boolean {
        return !hasRequestedPermission
    }

    /**
     * Mark that permission has been requested
     */
    fun markPermissionRequested() {
        viewModelScope.launch {
            voicePreferences.markRecordAudioPermissionRequested()
            hasRequestedPermission = true
        }
    }

    /**
     * Stop voice input listening
     */
    fun stopListening() {
        voiceRecognitionManager.stopListening()
        _uiState.value = _uiState.value.copy(
            isListening = false,
            partialText = ""
        )
    }

    /**
     * Start hotword detection (background listening)
     */
    fun startHotwordDetection() {
        if (!voiceRecognitionManager.hasPermissions()) {
            return
        }
        voiceRecognitionManager.startHotwordDetector()
    }

    /**
     * Stop hotword detection
     */
    fun stopHotwordDetection() {
        voiceRecognitionManager.stopHotwordDetector()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognitionManager.destroy()
    }
}