package com.shadowai.app.execution

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast-path rule-based parser for device actions.
 * Translates natural language directly to DeviceAction JSON without LLM.
 * Useful for low-latency execution and as a fallback.
 */
@Singleton
class HeuristicActionParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Attempts to parse input into a device command JSON.
     * @return JSON string if successful, null if no heuristic matched.
     */
    fun parseToCommand(input: String): String? {
        val lower = input.lowercase()
        val actionResult = parseDeviceAction(lower)

        return when (actionResult) {
            is DeviceActionResult.Success -> DeviceActionParser.toJson(actionResult.action)
            is DeviceActionResult.Error -> errorJson(actionResult.message)
            is DeviceActionResult.Unknown -> null
        }
    }

    private fun parseDeviceAction(input: String): DeviceActionResult {
        val lower = input.lowercase()

        return when {
            lower.startsWith("call") -> {
                val number = lower.removePrefix("call").trim()
                if (number.isBlank()) DeviceActionResult.Error("CALL missing number")
                else DeviceActionResult.Success(DeviceAction.Call(number))
            }

            lower.startsWith("sms") -> {
                val parts = lower.removePrefix("sms").trim().split(" ", limit = 2)
                if (parts.size == 2) DeviceActionResult.Success(DeviceAction.Sms(parts[0], parts[1]))
                else DeviceActionResult.Error("Invalid format. Use: sms <number> <message>")
            }

            lower.contains("read sms") || lower.contains("messages") -> DeviceActionResult.Success(DeviceAction.ReadSms)
            lower.contains("play") || lower.contains("pause") -> DeviceActionResult.Success(DeviceAction.MediaToggle)
            lower.contains("next") || lower.contains("skip") -> DeviceActionResult.Success(DeviceAction.MediaNext)

            lower.startsWith("volume") -> {
                val level = lower.removePrefix("volume").trim().toIntOrNull() ?: (maxMediaVolume() / 2)
                DeviceActionResult.Success(DeviceAction.MediaVolume(level.coerceIn(0, maxMediaVolume())))
            }

            lower.startsWith("launch") -> {
                val pkg = lower.removePrefix("launch").trim()
                if (pkg.isBlank()) DeviceActionResult.Error("APP_LAUNCH missing package")
                else DeviceActionResult.Success(DeviceAction.AppLaunch(pkg))
            }

            lower.startsWith("wifi") -> {
                val enable = lower.contains("on")
                DeviceActionResult.Success(DeviceAction.Wifi(enable))
            }

            lower.contains("inspect") -> DeviceActionResult.Success(DeviceAction.AccessibilityInspect)
            lower.contains("back") -> DeviceActionResult.Success(DeviceAction.GlobalBack)
            lower.contains("home") -> DeviceActionResult.Success(DeviceAction.GlobalHome)
            lower.contains("status") -> DeviceActionResult.Success(DeviceAction.SystemStatus)

            else -> DeviceActionResult.Unknown
        }
    }

    private fun maxMediaVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    private fun errorJson(message: String): String {
        return buildJsonObject {
            put("source", "heuristic-parser")
            put("error", message)
        }.toString()
    }

    private sealed class DeviceActionResult {
        data class Success(val action: DeviceAction) : DeviceActionResult()
        data class Error(val message: String) : DeviceActionResult()
        object Unknown : DeviceActionResult()
    }
}
