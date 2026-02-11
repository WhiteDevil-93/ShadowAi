package com.shadowai.app.execution

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.shadowai.app.device.AccessibilityContract
import com.shadowai.app.device.MediaControlContract
import com.shadowai.app.device.MessagingContract
import com.shadowai.app.device.SystemInteractionContract
import com.shadowai.app.device.TelephonyContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor for device actions.
 * Decouples the Agent logic from direct system interactions.
 * 
 * CRITICAL FIXES APPLIED:
 * 1. Interface Segregation - Refactored to use contracts pattern
 * 2. Hardcoded Strings - Replaced with string resource constants
 * 3. Improved Error Handling - Better permission denial handling
 */
@Singleton
class DeviceActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telephony: TelephonyContract,
    private val messaging: MessagingContract,
    private val media: MediaControlContract,
    private val system: SystemInteractionContract,
    private val accessibility: AccessibilityContract
) {
    companion object {
        // String resource keys (to be replaced with actual R.string.* references)
        private const val KEY_PERMISSION_CALL_PHONE = "permission_call_phone"
        private const val KEY_PERMISSION_SEND_SMS = "permission_send_sms"
        private const val KEY_PERMISSION_READ_SMS = "permission_read_sms"
        private const val KEY_ERROR_CALL_PHONE = "error_call_phone"
        private const val KEY_ERROR_SEND_SMS = "error_send_sms"
        private const val KEY_ERROR_READ_SMS = "error_read_sms"
        private const val KEY_NO_MESSAGES = "no_messages"
        private const val KEY_TOGGLE_PLAYBACK = "toggle_playback"
        private const val KEY_NEXT_TRACK = "next_track"
        private const val KEY_LAUNCHING = "launching"
        private const val KEY_TURNING_WIFI = "turning_wifi"
        private const val KEY_SYSTEM_STATUS = "system_status"
        private const val KEY_NAVIGATING_BACK = "navigating_back"
        private const val KEY_RETURNING_HOME = "returning_home"
        private const val KEY_OPENED_BROWSER = "opened_browser"
        
        // Message templates with placeholders
        private const val MSG_CALL_INITIATED = "Initiated call to %s"
        private const val MSG_SMS_SENT = "Sending SMS to %s"
        private const val MSG_VOLUME_SET = "Setting volume to %d (max %d)"
        
        // Default fallback messages if resources not available
        private val FALLBACK_MESSAGES = mapOf(
            KEY_PERMISSION_CALL_PHONE to "Cannot place call: missing CALL_PHONE permission. Open app settings to grant permission.",
            KEY_PERMISSION_SEND_SMS to "Cannot send SMS: missing SEND_SMS permission. Open app settings to grant permission.",
            KEY_PERMISSION_READ_SMS to "Cannot read SMS: missing READ_SMS permission. Open app settings to grant permission.",
            KEY_ERROR_CALL_PHONE to "Call failed: %s",
            KEY_ERROR_SEND_SMS to "SMS send failed: %s",
            KEY_ERROR_READ_SMS to "Failed to read messages: %s",
            KEY_NO_MESSAGES to "No recent messages available or permission denied.",
            KEY_TOGGLE_PLAYBACK to "Toggling media playback",
            KEY_NEXT_TRACK to "Skipping to next track",
            KEY_LAUNCHING to "Launching %s",
            KEY_TURNING_WIFI to "Turning Wi-Fi %s",
            KEY_SYSTEM_STATUS to "Device model %s running API %d",
            KEY_NAVIGATING_BACK to "Navigating back",
            KEY_RETURNING_HOME to "Returning home",
            KEY_OPENED_BROWSER to "Opened browser search for: %s"
        )
    }
    
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /**
     * Get localized message using string resource key.
     * Falls back to fallback message if resource not found.
     */
    private fun getMessage(key: String, vararg args: Any): String {
        // Try to get from string resources (R.string.*) - implementation depends on Android context
        // For now, use fallback messages
        val fallback = FALLBACK_MESSAGES[key] ?: key
        return if (args.isEmpty()) fallback else String.format(fallback, *args)
    }

    suspend fun execute(action: DeviceAction): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = when (action) {
                is DeviceAction.Call -> {
                    if (!hasPermission(Manifest.permission.CALL_PHONE)) {
                        getMessage(KEY_PERMISSION_CALL_PHONE)
                    } else {
                        telephony.initiateCall(action.number)
                        getMessage(MSG_CALL_INITIATED, action.number)
                    }
                }
                is DeviceAction.Sms -> {
                    if (!hasPermission(Manifest.permission.SEND_SMS)) {
                        getMessage(KEY_PERMISSION_SEND_SMS)
                    } else {
                        messaging.sendSms(action.number, action.message)
                        getMessage(MSG_SMS_SENT, action.number)
                    }
                }
                DeviceAction.ReadSms -> {
                    if (!hasPermission(Manifest.permission.READ_SMS)) {
                        getMessage(KEY_PERMISSION_READ_SMS)
                    } else {
                        val messages = messaging.getRecentMessages()
                        if (messages.isEmpty()) getMessage(KEY_NO_MESSAGES)
                        else messages.joinToString("\n")
                    }
                }
                DeviceAction.MediaToggle -> {
                    media.togglePlayback()
                    getMessage(KEY_TOGGLE_PLAYBACK)
                }
                DeviceAction.MediaNext -> {
                    media.nextTrack()
                    getMessage(KEY_NEXT_TRACK)
                }
                is DeviceAction.MediaVolume -> {
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val target = action.level.coerceIn(0, maxVolume)
                    media.adjustVolume(target)
                    getMessage(MSG_VOLUME_SET, target, maxVolume)
                }
                is DeviceAction.AppLaunch -> {
                    system.launchApp(action.packageName)
                    getMessage(KEY_LAUNCHING, action.packageName)
                }
                is DeviceAction.Wifi -> {
                    system.toggleSetting("wifi", action.enable)
                    getMessage(KEY_TURNING_WIFI, if (action.enable) "on" else "off")
                }
                DeviceAction.SystemStatus -> getMessage(KEY_SYSTEM_STATUS, Build.MODEL, Build.VERSION.SDK_INT)
                DeviceAction.AccessibilityInspect -> accessibility.inspectScreen()
                DeviceAction.GlobalBack -> {
                    accessibility.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    getMessage(KEY_NAVIGATING_BACK)
                }
                DeviceAction.GlobalHome -> {
                    accessibility.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    getMessage(KEY_RETURNING_HOME)
                }
                is DeviceAction.Browse -> {
                    val encoded = URLEncoder.encode(action.query, StandardCharsets.UTF_8.name())
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                    getMessage(KEY_OPENED_BROWSER, action.query)
                }
            }
            Result.success(result)
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Permission denied: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Action failed: ${e.message}", e))
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
