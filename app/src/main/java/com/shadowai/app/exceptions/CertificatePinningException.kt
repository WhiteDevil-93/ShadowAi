package com.shadowai.app.exceptions

import java.io.IOException

/**
 * Exception thrown when certificate pinning validation fails.
 *
 * This occurs when the SSL certificate presented by a server doesn't match
 * any of the pinned certificates configured for that hostname.
 *
 * Possible causes:
 * - Certificate rotation (provider updated their SSL certificate)
 * - MITM proxy intercepting the connection (Charles Proxy, Burp Suite, etc.)
 * - Malicious CA installed on the device
 * - Incorrect pin configuration
 *
 * M-9 FIXED: Extends IOException instead of SecurityException to comply with
 * OkHttp Interceptor interface (which declares 'throws IOException').
 *
 * @param hostname The hostname that failed pinning validation
 * @param message Detailed error message
 * @param cause The underlying SSL exception
 */
class CertificatePinningException(
    val hostname: String,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates a CertificatePinningException from an SSL peer unverified exception.
         *
         * @param hostname The hostname being connected to
         * @param cause The underlying SSLPeerUnverifiedException
         */
        fun fromSslException(hostname: String, cause: javax.net.ssl.SSLPeerUnverifiedException): CertificatePinningException {
            // Parse the cause to determine if it's a pinning failure
            val message = cause.message ?: "Certificate pinning validation failed for $hostname"
            val isPinningError = message.contains("Certificate pinning failure", ignoreCase = true) ||
                               message.contains("pinned", ignoreCase = true)

            val detailedMessage = if (isPinningError) {
                "Certificate pinning validation failed for $hostname. " +
                "This may indicate a MITM attack, certificate rotation, or incorrect pin configuration. " +
                "Original error: $message"
            } else {
                "Certificate validation failed for $hostname. Original error: $message"
            }

            return CertificatePinningException(hostname, detailedMessage, cause)
        }
    }
}