package com.shadowai.app.agent

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.shadowai.app.device.implementation.ShadowAccessibilityService
import com.shadowai.app.execution.DeviceAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.2: Client-Side Verification Gates
 * Enforces preconditions and policy checks before any agentic action is authorized.
 *
 * This engine supports extensibility through:
 * - Custom action verifiers via [registerVerifier]
 * - Configurable permission messages
 */
@Singleton
class VerificationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VerificationEngine"

        // Permission error message templates
        private const val MSG_PHONE_PERMISSION = "Action requires Phone permission (CALL_PHONE)."
        private const val MSG_SMS_SEND_PERMISSION = "Action requires SMS permission (SEND_SMS)."
        private const val MSG_SMS_READ_PERMISSION = "Action requires SMS Read permission (READ_SMS)."
        private const val MSG_ACCESSIBILITY_REQUIRED = "Accessibility Service is not enabled. Please enable it in Settings."
        private const val MSG_WIFI_ALREADY_ON = "Redundant Action: Agent tried to enable WiFi but history shows it is already ON."
        private const val MSG_WIFI_ALREADY_OFF = "Redundant Action: Agent tried to disable WiFi but history shows it is already OFF."
    }

    sealed class VerificationResult {
        object Passed : VerificationResult()
        data class PreconditionMissing(val permission: String?, val message: String) : VerificationResult()
        data class PolicyViolation(val message: String) : VerificationResult()
    }

    /**
     * Interface for custom action verifiers.
     */
    fun interface ActionVerifier {
        fun verify(action: DeviceAction): VerificationResult
    }

    // Registry for custom verifiers
    private val customVerifiers = mutableListOf<ActionVerifier>()

    /**
     * Register a custom action verifier.
     * Custom verifiers are checked after built-in verification.
     */
    fun registerVerifier(verifier: ActionVerifier) {
        customVerifiers.add(verifier)
    }

    /**
     * Unregister a custom action verifier.
     */
    fun unregisterVerifier(verifier: ActionVerifier) {
        customVerifiers.remove(verifier)
    }

    /**
     * Verifies that all system-level requirements for the action are met.
     * Runs built-in checks first, then any registered custom verifiers.
     */
    fun verifyAction(action: DeviceAction): VerificationResult {
        // Built-in verification
        val builtInResult = when (action) {
            is DeviceAction.Call -> checkPermission(
                android.Manifest.permission.CALL_PHONE,
                MSG_PHONE_PERMISSION
            )
            is DeviceAction.Sms -> checkPermission(
                android.Manifest.permission.SEND_SMS,
                MSG_SMS_SEND_PERMISSION
            )
            DeviceAction.ReadSms -> checkPermission(
                android.Manifest.permission.READ_SMS,
                MSG_SMS_READ_PERMISSION
            )
            DeviceAction.AccessibilityInspect -> {
                if (ShadowAccessibilityService.instance?.get() == null) {
                    VerificationResult.PreconditionMissing(null, MSG_ACCESSIBILITY_REQUIRED)
                } else VerificationResult.Passed
            }
            // Extensibility point: add more gates here (e.g. Battery level, Network type)
            else -> VerificationResult.Passed
        }

        // If built-in check failed, return immediately
        if (builtInResult !is VerificationResult.Passed) {
            return builtInResult
        }

        // Run custom verifiers
        for (verifier in customVerifiers) {
            val customResult = verifier.verify(action)
            if (customResult !is VerificationResult.Passed) {
                return customResult
            }
        }

        return VerificationResult.Passed
    }

    private fun checkPermission(permission: String, message: String): VerificationResult {
        return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            VerificationResult.Passed
        } else {
            VerificationResult.PreconditionMissing(permission, message)
        }
    }

    /**
     * Governance check for sensitive actions.
     * Returns true if we should halt for explicit user confirmation.
     */
    fun requiresUserConfirmation(action: DeviceAction): Boolean {
        // SAFETY: Explicit blocklist of actions that MUST be confirmed.
        // Benign/Read-only actions are whitelisted (return false).
        return when (action) {
            is DeviceAction.ReadSms,
            DeviceAction.SystemStatus,
            DeviceAction.AccessibilityInspect,
            is DeviceAction.Browse -> false
            else -> true
        }
    }

    /**
     * Human-readable description of the intent for verification UI.
     */
    fun describeAction(action: DeviceAction): String {
        return when (action) {
            is DeviceAction.Call -> "Call ${action.number}"
            is DeviceAction.Sms -> "Send SMS to ${action.number}: \"${action.message}\""
            DeviceAction.MediaToggle -> "Toggle Media Playback"
            DeviceAction.MediaNext -> "Skip to Next Track"
            is DeviceAction.MediaVolume -> "Set Volume to ${action.level}"
            is DeviceAction.AppLaunch -> "Launch App: ${action.packageName}"
            is DeviceAction.Wifi -> "Turn WiFi ${if (action.enable) "On" else "Off"}"
            DeviceAction.GlobalBack -> "Go Back"
            DeviceAction.GlobalHome -> "Go Home"
            is DeviceAction.Browse -> "Browse: ${action.query}"
            else -> "Execute system action"
        }
    }

    /**
     * Phase 3.3: Contradiction Detection
     * Checks if the proposal contradicts the known environmental state.
     */
    fun verifyConsistency(action: DeviceAction, envHistory: String): VerificationResult {
        val lowerHistory = envHistory.lowercase()
        return when (action) {
            is DeviceAction.Wifi -> {
                if (action.enable && lowerHistory.contains("wifi is on")) {
                    VerificationResult.PolicyViolation(MSG_WIFI_ALREADY_ON)
                } else if (!action.enable && lowerHistory.contains("wifi is off")) {
                    VerificationResult.PolicyViolation(MSG_WIFI_ALREADY_OFF)
                } else VerificationResult.Passed
            }
            else -> VerificationResult.Passed
        }
    }
}
