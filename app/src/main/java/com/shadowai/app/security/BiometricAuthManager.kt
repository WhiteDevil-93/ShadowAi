package com.shadowai.app.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages biometric authentication for sensitive operations.
 *
 * Use cases:
 * - Model downloads
 * - Context history access
 * - Sensitive settings access
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class AuthResult {
        SUCCESS,
        NOT_AVAILABLE,
        NOT_ENROLLED,
        NOT_SECURE,
        FAILED,
        CANCELLED
    }

    private val biometricManager = BiometricManager.from(context)

    /**
     * Check if biometric authentication is available and user has enrolled credentials.
     */
    fun canAuthenticate(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Check if device is secure (biometrics or device lock available).
     */
    fun isDeviceSecure(): Boolean {
        return when {
            canAuthenticate() -> true
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Get available authentication type for UI display.
     */
    fun getAuthType(): AuthType {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthType.BIOMETRIC
            else -> when (biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
                BiometricManager.BIOMETRIC_SUCCESS -> AuthType.DEVICE_CREDENTIAL
                else -> AuthType.NONE
            }
        }
    }

    /**
     * Prompt for biometric authentication.
     *
     * @param activity The fragment activity to show the prompt on
     * @param title Title to display on the authentication dialog
     * @param subtitle Optional subtitle
     * @param description Optional description
     * @param allowDeviceCredential Whether to allow device credential (PIN/pattern/password) as fallback
     * @return AuthResult indicating the outcome
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        description: String? = null,
        allowDeviceCredential: Boolean = true
    ): AuthResult = suspendCancellableCoroutine { continuation ->
        val authenticators = if (allowDeviceCredential) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (subtitle != null) {
                    setSubtitle(subtitle)
                }
                if (description != null) {
                    setDescription(description)
                }
            }
            .setAllowedAuthenticators(authenticators)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                continuation.resume(AuthResult.SUCCESS)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> {
                        continuation.resume(AuthResult.NOT_AVAILABLE)
                    }
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON, // User cancelled
                    BiometricPrompt.ERROR_USER_CANCELED -> {
                        continuation.resume(AuthResult.CANCELLED)
                    }
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        continuation.resume(AuthResult.NOT_SECURE)
                    }
                    else -> {
                        continuation.resumeWithException(
                            Exception("Authentication error: $errString")
                        )
                    }
                }
            }

            override fun onAuthenticationFailed() {
                // Don't resume here - auth prompt will retry
                // Only resume on explicit error or success
            }
        }

        try {
            val executor = activity.mainExecutor
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            
            continuation.invokeOnCancellation {
                // No explicit cancellation available for BiometricPrompt
                // The prompt will be cancelled when activity is destroyed
            }

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    enum class AuthType {
        BIOMETRIC,
        DEVICE_CREDENTIAL,
        NONE
    }
}