package com.shadowai.app.security

import android.content.Context
import android.util.Log
import com.shadowai.app.exceptions.CertificatePinningException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles certificate pinning errors and provides user notifications.
 *
 * This class is responsible for:
 * - Logging certificate errors for debugging
 * - Notifying users when certificate pinning fails
 * - Providing guidance on what to do (e.g., update pins, disable proxy)
 *
 * Usage:
 * ```kotlin
 * val errorHandler = CertificateErrorHandler(context)
 * errorHandler.handleCertificatePinningError(exception)
 * ```
 *
 * @param context Application context
 */
class CertificateErrorHandler(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "CertificateErrorHandler"

        /**
         * Channel ID for certificate error notifications
         */
        const val NOTIFICATION_CHANNEL_ID = "certificate_errors"

        /**
         * Notification ID for certificate pinning errors
         */
        const val NOTIFICATION_ID_PIN_FAILURE = 1001
    }

    /**
     * Handles a certificate pinning exception.
     *
     * This method:
     * 1. Logs the error with details
     * 2. Shows a user notification
     * 3. Stores the error for later analysis
     *
     * @param exception The certificate pinning exception to handle
     */
    fun handleCertificatePinningError(exception: CertificatePinningException) {
        Log.e(TAG, "Certificate pinning failure detected", exception)

        scope.launch {
            try {
                logError(exception)
                showUserNotification(exception)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle certificate error: ${e.message}", e)
            }
        }
    }

    /**
     * Logs the certificate error details.
     *
     * @param exception The certificate pinning exception
     */
    private suspend fun logError(exception: CertificatePinningException) = withContext(Dispatchers.IO) {
        val logEntry = buildString {
            appendLine("=== Certificate Pinning Failure ===")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Hostname: ${exception.hostname}")
            appendLine("Message: ${exception.message}")
            exception.cause?.let { appendLine("Cause: ${it.message}") }
            appendLine("=================================")
        }

        // Log to Android LogCat
        Log.w(TAG, logEntry)

        // TODO: Store to error log database for later analysis
        // Consider adding to FailureDao or a new CertificateErrorDao
    }

    /**
     * Shows a user notification about the certificate failure.
     *
     * @param exception The certificate pinning exception
     */
    private suspend fun showUserNotification(exception: CertificatePinningException) {
        // TODO: Implement actual notification showing
        // This requires integration with Android's NotificationManager

        // For now, log the intent to show notification
        val message = buildString {
            appendLine("SECURITY ALERT: Certificate pinning failed for ${exception.hostname}")
            appendLine("Possible causes:")
            appendLine("- MITM proxy (Charles, Burp Suite) intercepting connections")
            appendLine("- Provider rotated their SSL certificate")
            appendLine("- Malicious certificate authority on device")
            appendLine("\nPlease disable any proxy or contact support if this persists.")
        }

        Log.w(TAG, "Notification to user: $message")

        // Example notification code (requires NotificationManager setup):
        // val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        //     .setSmallIcon(R.drawable.ic_security_warning)
        //     .setContentTitle("Security Warning")
        //     .setContentText("Certificate pinning failed for ${exception.hostname}")
        //     .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        //     .setPriority(NotificationCompat.PRIORITY_HIGH)
        //     .setAutoCancel(true)
        //     .build()
        //
        // val notificationManager = NotificationManagerCompat.from(context)
        // if (ActivityCompat.checkSelfPermission(
        //         context, Manifest.permission.POST_NOTIFICATIONS) ==
        //         PackageManager.PERMISSION_GRANTED) {
        //     notificationManager.notify(NOTIFICATION_ID_PIN_FAILURE, notification)
        // }
    }

    /**
     * Checks if a certificate error might be due to a proxy or network tool.
     *
     * @return True if the error pattern suggests a MITM proxy
     */
    fun isLikelyProxyError(exception: CertificatePinningException): Boolean {
        val message = exception.message ?: return false
        return message.contains("proxy", ignoreCase = true) ||
               message.contains("charles", ignoreCase = true) ||
               message.contains("burp", ignoreCase = true) ||
               message.contains("fiddler", ignoreCase = true) ||
               message.contains("mitm", ignoreCase = true)
    }
}