package com.shadowai.app.exceptions

import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps platform exceptions to domain-specific [AppExceptions] types.
 *
 * Usage:
 * ```
 * try { ... }
 * catch (e: Exception) {
 *     throw ExceptionMapper.toNetworkException(e)
 * }
 * ```
 */
object ExceptionMapper {

    /**
     * Maps an exception to the most appropriate [NetworkException] subtype.
     */
    fun toNetworkException(e: Throwable): NetworkException = when (e) {
        is NetworkException -> e
        is SocketTimeoutException -> NetworkException.Timeout(e)
        is ConnectException -> NetworkException.NoConnection(e)
        is UnknownHostException -> NetworkException.NoConnection(e)
        is SSLException -> NetworkException.InvalidResponse("SSL error: ${e.message}", e)
        is IOException -> NetworkException.InvalidResponse(e.message ?: "I/O error", e)
        else -> NetworkException.InvalidResponse(e.message ?: "Unknown network error", e)
    }

    /**
     * Maps an exception to a [ParseException] subtype.
     */
    fun toParseException(e: Throwable): ParseException = when (e) {
        is ParseException -> e
        is JsonSyntaxException -> ParseException.InvalidJson(e.message ?: "Malformed JSON", e)
        is org.json.JSONException -> ParseException.InvalidJson(e.message ?: "JSON parse error", e)
        else -> ParseException.InvalidFormat("valid data", e.message ?: "unknown", e)
    }

    /**
     * Maps an exception to a [StorageException] subtype.
     */
    fun toStorageException(e: Throwable, path: String = "unknown"): StorageException = when (e) {
        is StorageException -> e
        is FileNotFoundException -> StorageException.FileNotFound(path, e)
        is IOException -> StorageException.ReadFailed(path, e)
        else -> StorageException.ReadFailed(path, e)
    }

    /**
     * Maps an I/O-context exception to either [NetworkException] or [StorageException]
     * depending on the cause type.
     */
    fun toIOException(e: Throwable, storagePath: String? = null): IOException = when {
        e is NetworkException || e is StorageException -> e as IOException
        e is SocketTimeoutException || e is ConnectException || e is UnknownHostException -> toNetworkException(e)
        e is FileNotFoundException -> StorageException.FileNotFound(storagePath ?: "unknown", e)
        storagePath != null -> toStorageException(e, storagePath)
        else -> toNetworkException(e)
    }
}
