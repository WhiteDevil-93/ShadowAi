package com.shadowai.app.execution

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlinx.serialization.json.put
import kotlinx.serialization.json.put
import kotlinx.serialization.json.put

sealed class DeviceAction {
    data class Call(val number: String) : DeviceAction()
    data class Sms(val number: String, val message: String) : DeviceAction()
    object ReadSms : DeviceAction()
    object MediaToggle : DeviceAction()
    object MediaNext : DeviceAction()
    data class MediaVolume(val level: Int) : DeviceAction()
    data class AppLaunch(val packageName: String) : DeviceAction()
    data class Wifi(val enable: Boolean) : DeviceAction()
    object SystemStatus : DeviceAction()
    object AccessibilityInspect : DeviceAction()
    object GlobalBack : DeviceAction()
    object GlobalHome : DeviceAction()
    data class Browse(val query: String) : DeviceAction()
}

/**
 * Parses and serializes DeviceAction objects with STRICT schema versioning.
 * Implements Phase 0.1 of Agentic Roadmap.
 */
object DeviceActionParser {
    private const val REQUIRED_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_ACTION = "action"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_NUMBER = "number"
    private const val KEY_MESSAGE = "message"
    private const val KEY_LEVEL = "level"
    private const val KEY_PACKAGE = "package"
    private const val KEY_ENABLE = "enable"
    private const val KEY_QUERY = "query"

    private const val ACTION_CALL = "CALL"
    private const val ACTION_SMS = "SMS"
    private const val ACTION_READ_SMS = "READ_SMS"
    private const val ACTION_MEDIA_TOGGLE = "MEDIA_TOGGLE"
    private const val ACTION_MEDIA_NEXT = "MEDIA_NEXT"
    private const val ACTION_MEDIA_VOLUME = "MEDIA_VOLUME"
    private const val ACTION_APP_LAUNCH = "APP_LAUNCH"
    private const val ACTION_WIFI = "WIFI"
    private const val ACTION_SYSTEM_STATUS = "SYSTEM_STATUS"
    private const val ACTION_ACCESSIBILITY_INSPECT = "ACCESSIBILITY_INSPECT"
    private const val ACTION_GLOBAL_BACK = "GLOBAL_BACK"
    private const val ACTION_GLOBAL_HOME = "GLOBAL_HOME"
    private const val ACTION_BROWSE = "BROWSE"

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }
    private val schema = Schema(setOf(KEY_VERSION, KEY_ACTION, KEY_PAYLOAD))

    private data class Schema(val allowedRootKeys: Set<String>)

    fun parse(raw: String): Result<DeviceAction> {
        return runCatching {
            val root = json.parseToJsonElement(raw.trim()).jsonObject

            // Phase 0.1: Strict Versioning
            val version = root[KEY_VERSION]?.jsonPrimitive?.intOrNull
                ?: throw DeviceActionParseException("Missing required field '$KEY_VERSION'. Schema version $REQUIRED_VERSION is mandatory.")
            if (version != REQUIRED_VERSION) {
                throw DeviceActionParseException("Unsupported schema version: $version. Expected: $REQUIRED_VERSION.")
            }

            // Phase 0.1: Strict Key Check (No Unknown Fields)
            root.keys.forEach { key ->
                if (key !in schema.allowedRootKeys) {
                    throw DeviceActionParseException("Unknown field in root object: '$key'. Allowed: ${schema.allowedRootKeys}")
                }
            }

            // Generic action parsing with strict field requirements
            val action = root[KEY_ACTION]?.jsonPrimitive?.contentOrNull
                ?: throw DeviceActionParseException("Missing required field '$KEY_ACTION'.")
            val payload = root[KEY_PAYLOAD]?.jsonObject

            when (action) {
                ACTION_CALL -> {
                    val number = payload.requiredString(KEY_NUMBER, "$ACTION_CALL payload missing or empty '$KEY_NUMBER'")
                    DeviceAction.Call(number)
                }
                ACTION_SMS -> {
                    val number = payload.requiredString(KEY_NUMBER, "$ACTION_SMS payload missing or empty '$KEY_NUMBER'")
                    val message = payload.requiredString(KEY_MESSAGE, "$ACTION_SMS payload missing or empty '$KEY_MESSAGE'")
                    DeviceAction.Sms(number, message)
                }
                ACTION_READ_SMS -> DeviceAction.ReadSms
                ACTION_MEDIA_TOGGLE -> DeviceAction.MediaToggle
                ACTION_MEDIA_NEXT -> DeviceAction.MediaNext
                ACTION_MEDIA_VOLUME -> {
                    val level = payload?.get(KEY_LEVEL)?.jsonPrimitive?.intOrNull
                        ?: throw DeviceActionParseException("$ACTION_MEDIA_VOLUME missing '$KEY_LEVEL'")
                    if (level < 0) throw DeviceActionParseException("$ACTION_MEDIA_VOLUME level must be non-negative")
                    DeviceAction.MediaVolume(level)
                }
                ACTION_APP_LAUNCH -> {
                    val pkg = payload.requiredString(KEY_PACKAGE, "$ACTION_APP_LAUNCH payload missing or empty '$KEY_PACKAGE'")
                    DeviceAction.AppLaunch(pkg)
                }
                ACTION_WIFI -> {
                    val enable = payload?.get(KEY_ENABLE)?.jsonPrimitive?.booleanOrNull
                        ?: throw DeviceActionParseException("$ACTION_WIFI missing '$KEY_ENABLE'")
                    DeviceAction.Wifi(enable)
                }
                ACTION_SYSTEM_STATUS -> DeviceAction.SystemStatus
                ACTION_ACCESSIBILITY_INSPECT -> DeviceAction.AccessibilityInspect
                ACTION_GLOBAL_BACK -> DeviceAction.GlobalBack
                ACTION_GLOBAL_HOME -> DeviceAction.GlobalHome
                ACTION_BROWSE -> {
                    val query = payload.requiredString(KEY_QUERY, "$ACTION_BROWSE payload missing or empty '$KEY_QUERY'")
                    DeviceAction.Browse(query)
                }
                else -> throw DeviceActionParseException("Unsupported action: $action")
            }
        }
    }

    fun toJson(action: DeviceAction): String {
        val root = buildJsonObject {
            put(KEY_VERSION, REQUIRED_VERSION)
            put(KEY_ACTION, when (action) {
                is DeviceAction.Call -> ACTION_CALL
                is DeviceAction.Sms -> ACTION_SMS
                DeviceAction.ReadSms -> ACTION_READ_SMS
                DeviceAction.MediaToggle -> ACTION_MEDIA_TOGGLE
                DeviceAction.MediaNext -> ACTION_MEDIA_NEXT
                is DeviceAction.MediaVolume -> ACTION_MEDIA_VOLUME
                is DeviceAction.AppLaunch -> ACTION_APP_LAUNCH
                is DeviceAction.Wifi -> ACTION_WIFI
                DeviceAction.SystemStatus -> ACTION_SYSTEM_STATUS
                DeviceAction.AccessibilityInspect -> ACTION_ACCESSIBILITY_INSPECT
                DeviceAction.GlobalBack -> ACTION_GLOBAL_BACK
                DeviceAction.GlobalHome -> ACTION_GLOBAL_HOME
                is DeviceAction.Browse -> ACTION_BROWSE
            })
            val payload = when (action) {
                is DeviceAction.Call -> buildJsonObject { put(KEY_NUMBER, action.number) }
                is DeviceAction.Sms -> buildJsonObject { put(KEY_NUMBER, action.number); put(KEY_MESSAGE, action.message) }
                DeviceAction.ReadSms, DeviceAction.MediaToggle, DeviceAction.MediaNext,
                DeviceAction.SystemStatus, DeviceAction.AccessibilityInspect,
                DeviceAction.GlobalBack, DeviceAction.GlobalHome -> null
                is DeviceAction.MediaVolume -> buildJsonObject { put(KEY_LEVEL, action.level) }
                is DeviceAction.AppLaunch -> buildJsonObject { put(KEY_PACKAGE, action.packageName) }
                is DeviceAction.Wifi -> buildJsonObject { put(KEY_ENABLE, action.enable) }
                is DeviceAction.Browse -> buildJsonObject { put(KEY_QUERY, action.query) }
            }
            payload?.let { put(KEY_PAYLOAD, it) }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun JsonObject?.requiredString(key: String, error: String): String {
        val value = this?.get(key)?.jsonPrimitive?.contentOrNull?.trim()
        if (value.isNullOrEmpty()) throw DeviceActionParseException(error)
        return value
    }
}

class DeviceActionParseException(message: String) : IllegalArgumentException(message)
