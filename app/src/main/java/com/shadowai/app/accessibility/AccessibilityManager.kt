package com.shadowai.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import java.util.Locale
import java.util.UUID

/**
 * Accessibility manager for enhanced screen reader support and alternative input methods.
 */
class AccessibilityManager(private val context: Context) {

    companion object {
        private const val TAG = "AccessibilityManager"
    }

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val accessibilityManager: AccessibilityManager by lazy {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    
    data class AccessibilitySettings(
        var isScreenReaderEnabled: Boolean = false,
        var isVoiceOutputEnabled: Boolean = false,
        var isHighContrastEnabled: Boolean = false,
        var isLargeTextEnabled: Boolean = false,
        var isSwitchAccessEnabled: Boolean = false,
        var speakConfirmation: Boolean = true,
        var speechRate: Float = 1.0f,
        var pitch: Float = 1.0f
    )
    
    private val settings = AccessibilitySettings()
    
    /**
     * Check if accessibility services are enabled.
     */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager.isEnabled
    }
    
    /**
     * Get a list of enabled accessibility services.
     */
    fun getEnabledServices(): List<String> {
        val enabledServices = mutableListOf<String>()
        
        try {
            val serviceInfoList = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )

            serviceInfoList.forEach { serviceInfo ->
                enabledServices.add(serviceInfo.resolveInfo.serviceInfo.packageName)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception accessing accessibility services", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Accessibility service in invalid state", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Null service info encountered", e)
        }
        
        return enabledServices
    }
    
    /**
     * Initialize text-to-speech for voice output.
     */
    fun initializeTts(onInitialized: (Boolean) -> Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            isTtsInitialized = status == TextToSpeech.SUCCESS
            
            if (isTtsInitialized) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setSpeechRate(settings.speechRate)
                textToSpeech?.setPitch(settings.pitch)
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS completed: $utteranceId")
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error (deprecated): $utteranceId")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS error: utteranceId=$utteranceId, errorCode=$errorCode")
                    }
                })
            }
            
            onInitialized(isTtsInitialized)
        }
    }
    
    /**
     * Speak text using TTS.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!settings.isVoiceOutputEnabled || !isTtsInitialized) return
        
        val utteranceId = UUID.randomUUID().toString()
        textToSpeech?.speak(text, queueMode, null, utteranceId)
    }
    
    /**
     * Stop speaking.
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
    }
    
    /**
     * Check if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true
    }
    
    /**
     * Set speech rate for TTS.
     */
    fun setSpeechRate(rate: Float) {
        settings.speechRate = rate.coerceIn(0.5f, 2.0f)
        textToSpeech?.setSpeechRate(settings.speechRate)
    }
    
    /**
     * Set pitch for TTS.
     */
    fun setPitch(pitch: Float) {
        settings.pitch = pitch.coerceIn(0.5f, 2.0f)
        textToSpeech?.setPitch(settings.pitch)
    }
    
    /**
     * Announce for screen readers.
     */
    fun announceForScreenReader(text: String) {
        if (!settings.isScreenReaderEnabled) return
        
        // This would typically use the AccessibilityNodeInfo to announce
        // For now, we'll use TTS as a fallback
        speak(text)
    }
    
    /**
     * Get current accessibility settings.
     */
    fun getSettings(): AccessibilitySettings {
        return settings.copy()
    }
    
    /**
     * Update accessibility settings.
     */
    fun updateSettings(newSettings: AccessibilitySettings) {
        settings.apply {
            isScreenReaderEnabled = newSettings.isScreenReaderEnabled
            isVoiceOutputEnabled = newSettings.isVoiceOutputEnabled
            isHighContrastEnabled = newSettings.isHighContrastEnabled
            isLargeTextEnabled = newSettings.isLargeTextEnabled
            isSwitchAccessEnabled = newSettings.isSwitchAccessEnabled
            speakConfirmation = newSettings.speakConfirmation
            speechRate = newSettings.speechRate.coerceIn(0.5f, 2.0f)
            pitch = newSettings.pitch.coerceIn(0.5f, 2.0f)
        }
        
        // Apply speech settings
        setSpeechRate(settings.speechRate)
        setPitch(settings.pitch)
    }
    
    /**
     * Shutdown TTS when done.
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsInitialized = false
    }
}

/**
 * Alternative input method handler for switch access and other input methods.
 */
class AlternativeInputHandler(private val context: Context) {
    
    data class InputAction(
        val action: String,
        val description: String,
        val shortcut: String? = null
    )
    
    private val availableActions = mutableListOf<InputAction>()
    
    init {
        // Define available input actions
        availableActions.addAll(listOf(
            InputAction("SELECT", "Select current item", "Enter/Space"),
            InputAction("NEXT", "Move to next item", "Tab/Right"),
            InputAction("PREVIOUS", "Move to previous item", "Shift+Tab/Left"),
            InputAction("SCROLL_UP", "Scroll up", "Page Up"),
            InputAction("SCROLL_DOWN", "Scroll down", "Page Down"),
            InputAction("HOME", "Go to home", "Home"),
            InputAction("BACK", "Go back", "Back/Esc"),
            InputAction("MENU", "Open menu", "Menu"),
            InputAction("HELP", "Get help", "F1")
        ))
    }
    
    /**
     * Handle input from alternative input methods.
     */
    fun handleInput(inputCode: String): InputAction? {
        return availableActions.find { action ->
            action.shortcut?.contains(inputCode, ignoreCase = true) == true ||
                    action.action.equals(inputCode, ignoreCase = true)
        }
    }
    
    /**
     * Get all available input actions.
     */
    fun getAvailableActions(): List<InputAction> {
        return availableActions.toList()
    }
    
    /**
     * Register a custom input action.
     */
    fun registerAction(action: InputAction) {
        if (!availableActions.any { it.action == action.action }) {
            availableActions.add(action)
        }
    }
    
    /**
     * Unregister an input action.
     */
    fun unregisterAction(actionName: String) {
        availableActions.removeAll { it.action.equals(actionName, ignoreCase = true) }
    }
    
    /**
     * Get help text for using alternative input.
     */
    fun getInputHelpText(): String {
        return buildString {
            appendLine("Alternative Input Methods:")
            appendLine()
            availableActions.forEach { action ->
                appendLine("${action.action}: ${action.description}")
                action.shortcut?.let { shortcut ->
                    appendLine("  Shortcut: $shortcut")
                }
                appendLine()
            }
        }
    }
}

/**
 * Accessibility service for monitoring accessibility events.
 */
class AccessibilityMonitor : AccessibilityService() {
    
    private var eventListener: AccessibilityEventListener? = null
    
    interface AccessibilityEventListener {
        fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?)
    }
    
    fun setEventListener(listener: AccessibilityEventListener?) {
        eventListener = listener
    }
    
    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        accessibilityInfo = info
    }
    
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        eventListener?.onAccessibilityEvent(event)
    }
    
    override fun onInterrupt() {
        // Service interrupted
    }
    
    private var accessibilityInfo: AccessibilityServiceInfo? = null
}
