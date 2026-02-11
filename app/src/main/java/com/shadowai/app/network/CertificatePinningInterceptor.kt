package com.shadowai.app.network

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
 * The interceptor logs errors and can trigger user notifications via a callback.
 */
class CertificatePinningInterceptor(
    private val onError: (CertificatePinningException) -> Unit = {}
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            val request = chain.request()
            val response = chain.proceed(request)

            return response

        } catch (e: SSLPeerUnverifiedException) {
            // Extract hostname from the request (or chain)
            val hostname = chain.request().url.host

            val pinningException = CertificatePinningException.fromSslException(hostname, e)

            // Notify the error callback
            onError(pinningException)

            // Re-throw to let the caller handle it
            throw pinningException

        } catch (e: IOException) {
            // DNS failures are connectivity issues, not pinning failures.
            if (e is UnknownHostException) {
                throw e
            }

            val isCertificateError =
                e is java.security.cert.CertificateException ||
                    e.cause is java.security.cert.CertificateException

            if (isCertificateError) {
                val hostname = chain.request().url.host
                val pinningException = CertificatePinningException(
                    hostname = hostname,
                    message = "Certificate validation failed for $hostname: ${e.message}",
                    cause = e
                )
                onError(pinningException)
                throw pinningException
            }

            throw e
        }
    }
}
