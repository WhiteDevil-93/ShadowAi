package com.shadowai.app.exceptions

import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * M-2: Exception mapper with enhanced context preservation.
 *
 * Maps platform exceptions to domain-specific [AppExceptions] types
 * while preserving full context including:
 * - Original exception with stack trace
 * - Operation context (what was being attempted)
 * - HTTP status codes and response details
 * - Request/URL information (sanitized)
 */
object ExceptionMapper {

    /**
     * Context information for exception mapping.
     */
    data class ExceptionContext(
        val operation: String? = null,
        val endpoint: String? = null,
        val requestId: String? = null,
        val providerId: String? = null,
        val extra: Map<String, Any?> = emptyMap()
    ) {
        fun toSummary(): String {
            val parts = mutableListOf<String>()
            operation?.let { parts.add("op=$it") }
            endpoint?.let { parts.add("endpoint=$it") }
            requestId?.let { parts.add("req=$it") }
            providerId?.let { parts.add("provider=$it") }
            return if (parts.isEmpty()) "no context" else parts.joinToString(", ")
        }
    }

    /**
     * Maps an exception to the most appropriate [NetworkException] subtype.
     */
    fun toNetworkException(e: Throwable, context: ExceptionContext? = null): NetworkException = when (e) {
        is NetworkException -> e.withContext(context)
        is SocketTimeoutException -> NetworkException.Timeout(
            cause = e,
            context = context?.toSummary()
        )
        is ConnectException -> NetworkException.NoConnection(
            cause = e,
            context = context?.toSummary()
        )
        is UnknownHostException -> NetworkException.NoConnection(
            cause = e,
            context = context?.toSummary()
        )
        is SSLException -> NetworkException.InvalidResponse(
            message = "SSL error during ${context?.operation ?: "request"}: ${e.message}",
            cause = e,
            context = context?.toSummary()
        )
        is IOException -> NetworkException.InvalidResponse(
            message = "I/O error during ${context?.operation ?: "request"}: ${e.message}",
            cause = e,
            httpCode = extractHttpCode(e),
            context = context?.toSummary()
        )
        else -> NetworkException.InvalidResponse(
            message = "Network error during ${context?.operation ?: "request"}: ${e.message}",
            cause = e,
            context = context?.toSummary()
        )
    }

    /**
     * Maps an exception to a [ParseException] subtype with context.
     */
    fun toParseException(
        e: Throwable,
        context: ExceptionContext? = null,
        contentSample: String? = null
    ): ParseException = when (e) {
        is ParseException -> e.withContext(context)
        is JsonSyntaxException -> ParseException.InvalidJson(
            message = "Malformed JSON in ${context?.operation ?: "parsing"}: ${e.message}",
            cause = e,
            contentSample = contentSample?.take(200),
            context = context?.toSummary()
        )
        is org.json.JSONException -> ParseException.InvalidJson(
            message = "JSON parse error in ${context?.operation ?: "parsing"}: ${e.message}",
            cause = e,
            contentSample = contentSample?.take(200),
            context = context?.toSummary()
        )
        else -> ParseException.InvalidFormat(
            expected = context?.endpoint ?: "valid data",
            actual = e.message ?: "unknown",
            cause = e,
            context = context?.toSummary()
        )
    }

    /**
     * Maps an exception to a [StorageException] subtype with context.
     */
    fun toStorageException(
        e: Throwable,
        path: String = "unknown",
        context: ExceptionContext? = null
    ): StorageException = when (e) {
        is StorageException -> e.withContext(context)
        is FileNotFoundException -> StorageException.FileNotFound(
            path = path,
            cause = e,
            operation = context?.operation,
            context = context?.toSummary()
        )
        is IOException -> StorageException.ReadFailed(
            path = path,
            cause = e,
            operation = context?.operation,
            context = context?.toSummary()
        )
        is SecurityException -> StorageException.PermissionDenied(
            path = path,
            cause = e,
            operation = context?.operation,
            context = context?.toSummary()
        )
        else -> StorageException.ReadFailed(
            path = path,
            cause = e,
            operation = context?.operation,
            context = context?.toSummary()
        )
    }

    /**
     * Maps an I/O-context exception to either [NetworkException] or [StorageException].
     */
    fun toIOException(
        e: Throwable,
        storagePath: String? = null,
        context: ExceptionContext? = null
    ): IOException = when {
        e is NetworkException -> (e as AppException).withContext(context) as IOException
        e is StorageException -> (e as AppException).withContext(context) as IOException
        e is SocketTimeoutException || e is ConnectException || e is UnknownHostException ->
            toNetworkException(e, context)
        e is FileNotFoundException && storagePath != null ->
            StorageException.FileNotFound(storagePath, e, context = context?.toSummary())
        storagePath != null -> toStorageException(e, storagePath, context)
        else -> toNetworkException(e, context)
    }

    /**
     * Extract HTTP status code from IOException if present in message.
     */
    private fun extractHttpCode(e: Throwable): Int? {
        val message = e.message ?: return null
        val regex = Regex("""(?:HTTP\s*)?(\d{3})""")
        return regex.find(message)?.groupValues?.get(1)?.toIntOrNull()
    }
}
