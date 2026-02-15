package com.shadowai.app.network

import android.util.Log
import com.shadowai.app.exceptions.CertificatePinningException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Interceptor for handling certificate pinning failures and other SSL errors.
 *
 * This interceptor catches SSL-related exceptions and provides user-friendly
 * error messages. It helps distinguish between:
 * - Certificate pinning failures (security concern)
 * - Network connectivity issues
 * - Other SSL errors
 *
 * M-11: Standardized logging across all network security events.
 * The interceptor logs errors and can trigger user notifications via a callback.
 */
class CertificatePinningInterceptor(
    private val onError: (CertificatePinningException) -> Unit = {}
) : Interceptor {
    // M-11: Standardized logging tag
    companion object {
        private const val TAG = "CertPinningInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val hostname = request.url.host
        
        try {
            Log.d(TAG, "Intercepting request to $hostname")
            val response = chain.proceed(request)
            Log.d(TAG, "Request to $hostname successful: ${response.code}")
            return response

        } catch (e: IOException) {
            // Check for certificate-related IOExceptions (SSLPeerUnverifiedException, CertificateException, etc.)
            Log.w(TAG, "IOException for $hostname: ${e.javaClass.simpleName}: ${e.message}")

            val pinningException = when {
                e is SSLPeerUnverifiedException -> {
                    Log.e(TAG, "SSL peer unverified for $hostname - possible pinning failure")
                    CertificatePinningException.fromSslException(hostname, e)
                }
                e is javax.net.ssl.SSLHandshakeException -> {
                    Log.e(TAG, "SSL handshake failed for $hostname")
                    CertificatePinningException(
                        hostname = hostname,
                        message = "SSL handshake failed for $hostname: ${e.message}",
                        cause = e
                    )
                }
                e is java.security.cert.CertificateException || 
                e.cause is java.security.cert.CertificateException ||
                e.cause is SSLPeerUnverifiedException -> {
                    Log.e(TAG, "Certificate validation failed for $hostname")
                    CertificatePinningException(
                        hostname = hostname,
                        message = "Certificate validation failed for $hostname: ${e.message}",
                        cause = e
                    )
                }
                else -> null
            }

            if (pinningException != null) {
                Log.e(TAG, "Certificate pinning exception for $hostname: ${pinningException.message}")
                onError(pinningException)
                throw pinningException
            }

            // Re-throw if it's a normal network issue
            Log.d(TAG, "Re-throwing non-certificate IOException for $hostname")
            throw e
        }
    }
}
