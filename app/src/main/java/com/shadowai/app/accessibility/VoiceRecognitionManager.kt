package com.shadowai.app.accessibility

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.shadowai.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Voice Recognition Manager for hotword detection and voice input.
 * Supports "Hey Shadow" hotword detection with local processing when possible.
 *
 * @Singleton ensures the same instance is used throughout the app
 */
@Singleton
class VoiceRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "VoiceRecognitionManager"
        const val HOTWORD = "Hey Shadow"
        const val HOTWORD_ALT = "Shadow"
        const val DEFAULT_HOTWORD_THRESHOLD = 0.7f // Default confidence threshold for hotword detection
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isHotwordActive = false
    private var voicePreferences: VoicePreferences? = null

    // Settings
    private val _isHotwordEnabled = MutableStateFlow(false)
    val isHotwordEnabled: StateFlow<Boolean> = _isHotwordEnabled

    private val _hotwordPhrase = MutableStateFlow(HOTWORD)
    val hotwordPhrase: StateFlow<String> = _hotwordPhrase

    private val _hotwordSensitivity = MutableStateFlow(DEFAULT_HOTWORD_THRESHOLD)
    val hotwordSensitivity: StateFlow<Float> = _hotwordSensitivity

    private val _isListeningState = MutableStateFlow(false)
    val isListeningState: StateFlow<Boolean> = _isListeningState

    private val _recognitionConfidence = MutableStateFlow(0f)
    val recognitionConfidence: StateFlow<Float> = _recognitionConfidence

    // Callbacks
    private var onHotwordDetected: (() -> Unit)? = null
    private var onVoiceResult: ((String, Float) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onPartialResult: ((String) -> Unit)? = null
    private var onListeningStateChanged: ((Boolean) -> Unit)? = null

    /**
     * Initialize or reinitialize the speech recognizer.
     */
    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                        _isListeningState.value = true
                        onListeningStateChanged?.invoke(true)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // RMS (root mean square) can be used for visual feedback
                        // We'll map this to a 0-1 range for visual indication
                        _recognitionConfidence.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        Log.d(TAG, "Buffer received")
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech")
                        _isListeningState.value = false
                        onListeningStateChanged?.invoke(false)
                    }

                    override fun onError(error: Int) {
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Unknown error"
                        }
                        Log.e(TAG, "Speech recognition error: $errorMessage")
                        _isListeningState.value = false
                        onListeningStateChanged?.invoke(false)
                        onError?.invoke(errorMessage)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            val confidence = scores?.getOrNull(0) ?: 0f

                            Log.d(TAG, "Speech result: $text (confidence: $confidence)")

                            // Check if this is a hotword (with confidence threshold)
                            if (isHotwordActive && checkHotword(text, confidence)) {
                                onHotwordDetected?.invoke()
                                isHotwordActive = false
                                // Continue listening for command
                                startVoiceInput()
                            } else {
                                onVoiceResult?.invoke(text, confidence)
                            }
                        }

                        _isListeningState.value = false
                        onListeningStateChanged?.invoke(false)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty() && matches[0].isNotEmpty()) {
                            val partialText = matches[0]
                            Log.d(TAG, "Partial result: $partialText")
                            onPartialResult?.invoke(partialText)

                            // Update confidence state
                            _recognitionConfidence.value = 0.8f
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        Log.d(TAG, "Event: $eventType")
                    }
                })
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - missing RECORD_AUDIO permission", e)
            onError?.invoke(context.getString(R.string.voice_error_permission))
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Speech recognizer already in use", e)
            onError?.invoke(context.getString(R.string.voice_error_recognizer_busy))
        } catch (e: NullPointerException) {
            Log.e(TAG, "Speech recognition service unavailable", e)
            onError?.invoke(context.getString(R.string.voice_error_init))
        }
    }

    /**
     * Check if the detected text matches the hotword.
     * Uses the configured sensitivity threshold for confidence.
     */
    private fun checkHotword(text: String, confidence: Float = 1.0f): Boolean {
        val normalizedText = text.lowercase().trim()
        val hotwordLower = _hotwordPhrase.value.lowercase().trim()
        val hotwordAltLower = HOTWORD_ALT.lowercase().trim()

        val phraseMatch = normalizedText.contains(hotwordLower) || normalizedText.contains(hotwordAltLower)
        val confidenceMatch = confidence >= _hotwordSensitivity.value

        return phraseMatch && confidenceMatch
    }

    /**
     * Check if we have the required permissions.
     */
    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start listening for the hotword.
     * This runs continuously in the background when enabled.
     */
    fun startHotwordDetector() {
        if (!_isHotwordEnabled.value) {
            Log.d(TAG, "Hotword detection is disabled")
            return
        }

        if (!hasPermissions()) {
            onError?.invoke("Microphone permission is required for voice recognition")
            return
        }

        initSpeechRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            isHotwordActive = true
            speechRecognizer?.startListening(intent)
            _isListeningState.value = true
            Log.d(TAG, "Started hotword detector")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied starting hotword detector", e)
            onError?.invoke(context.getString(R.string.voice_error_permission))
            _isListeningState.value = false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Speech recognizer busy - cannot start hotword", e)
            onError?.invoke(context.getString(R.string.voice_error_recognizer_busy))
            _isListeningState.value = false
        }
    }

    /**
     * Stop listening for the hotword.
     */
    fun stopHotwordDetector() {
        isHotwordActive = false
        stopListening()
        Log.d(TAG, "Stopped hotword detector")
    }

    /**
     * Initialize the VoiceRecognitionManager with VoicePreferences for persistence.
     * Call this after construction to load settings from DataStore.
     */
    suspend fun initializeWithPreferences(preferences: VoicePreferences) {
        voicePreferences = preferences

        // Load persisted settings once. Runtime updates flow through setHotword* APIs.
        _isHotwordEnabled.value = preferences.hotwordEnabled.first()
        _hotwordPhrase.value = preferences.customHotwordPhrase.first().ifBlank { HOTWORD }
        _hotwordSensitivity.value = preferences.hotwordSensitivity.first()
    }

    /**
     * Save current settings to DataStore.
     */
    private suspend fun saveSettings() {
        voicePreferences?.let { prefs ->
            prefs.saveHotwordEnabled(_isHotwordEnabled.value)
            prefs.saveCustomHotwordPhrase(
                if (_hotwordPhrase.value == HOTWORD) "" else _hotwordPhrase.value
            )
            prefs.saveHotwordSensitivity(_hotwordSensitivity.value)
        }
    }

    /**
     * Start voice input for a single command/query.
     * This is triggered after hotword detection or manual activation.
     */
    fun startVoiceInput() {
        if (!hasPermissions()) {
            onError?.invoke("Microphone permission is required for voice input")
            return
        }

        initSpeechRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Shadow")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListeningState.value = true
            Log.d(TAG, "Started voice input")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied starting voice input", e)
            onError?.invoke(context.getString(R.string.voice_error_permission))
            _isListeningState.value = false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Speech recognizer busy - cannot start voice input", e)
            onError?.invoke(context.getString(R.string.voice_error_recognizer_busy))
            _isListeningState.value = false
        }
    }

    /**
     * Stop listening immediately.
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListeningState.value = false
        onListeningStateChanged?.invoke(false)
        _recognitionConfidence.value = 0f
        Log.d(TAG, "Stopped listening")
    }

    /**
     * Cancel any ongoing recognition.
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isHotwordActive = false
        _isListeningState.value = false
        onListeningStateChanged?.invoke(false)
        _recognitionConfidence.value = 0f
        Log.d(TAG, "Cancelled recognition")
    }

    /**
     * Enable or disable the hotword detector.
     */
    fun setHotwordEnabled(enabled: Boolean, scope: kotlinx.coroutines.CoroutineScope? = null) {
        _isHotwordEnabled.value = enabled
        scope?.launch {
            saveSettings()
        }
        if (enabled && !_isListeningState.value) {
            startHotwordDetector()
        } else if (!enabled) {
            stopHotwordDetector()
        }
    }

    /**
     * Customize the hotword phrase.
     */
    fun setHotwordPhrase(phrase: String, scope: kotlinx.coroutines.CoroutineScope? = null) {
        _hotwordPhrase.value = phrase.trim()
        scope?.launch {
            saveSettings()
        }
    }

    /**
     * Set the hotword detection sensitivity threshold.
     * @param threshold Value between 0.0 (very sensitive) and 1.0 (strict)
     */
    fun setHotwordSensitivity(threshold: Float, scope: kotlinx.coroutines.CoroutineScope? = null) {
        _hotwordSensitivity.value = threshold.coerceIn(0.0f, 1.0f)
        scope?.launch {
            saveSettings()
        }
    }

    /**
     * Set callbacks for recognition events.
     */
    fun setCallbacks(
        onHotwordDetected: (() -> Unit)? = null,
        onVoiceResult: ((String, Float) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onPartialResult: ((String) -> Unit)? = null,
        onListeningStateChanged: ((Boolean) -> Unit)? = null
    ) {
        this.onHotwordDetected = onHotwordDetected
        this.onVoiceResult = onVoiceResult
        this.onError = onError
        this.onPartialResult = onPartialResult
        this.onListeningStateChanged = onListeningStateChanged
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onHotwordDetected = null
        onVoiceResult = null
        onError = null
        onPartialResult = null
        onListeningStateChanged = null
        Log.d(TAG, "Destroyed VoiceRecognitionManager")
    }
}

/**
 * UI state for voice recognition feedback.
 */
data class VoiceRecognitionState(
    val isListening: Boolean = false,
    val confidence: Float = 0f,
    val partialText: String = "",
    val hotwordEnabled: Boolean = false
)
