package com.shadowai.provideradapters

import java.io.IOException

sealed class NovitaException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class AuthenticationError : NovitaException(
        "Invalid API key. Please check your Novita API key in settings."
    )

    class RateLimitError : NovitaException(
        "Rate limit exceeded. Please wait a moment and try again."
    )

    class BadRequest(details: String?) : NovitaException(
        "Bad request: ${details ?: "Unknown error"}"
    )

    class ServerError(code: Int) : NovitaException(
        "Novita server error (HTTP $code). Please try again later."
    )

    class EmptyResponse : NovitaException(
        "Empty response from Novita API"
    )

    class EmptyContent : NovitaException(
        "Response contained no content"
    )

    class ParseError(cause: Throwable) : NovitaException(
        "Failed to parse response: ${cause.message}",
        cause
    )

    class Timeout : NovitaException(
        "Request timed out. The task may still be processing on the server."
    )

    class NetworkError(cause: IOException) : NovitaException(
        "Network error: ${cause.message}",
        cause
    )

    class UnknownError(code: Int, details: String?, cause: Throwable? = null) : NovitaException(
        "Unknown error (HTTP $code): ${details ?: "No details"}",
        cause
    )
}
