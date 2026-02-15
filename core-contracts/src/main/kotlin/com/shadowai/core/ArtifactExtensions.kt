package com.shadowai.core

/**
 * Extension functions for the Artifact system.
 */

/**
 * Converts an artifact to a user-friendly display string.
 */
fun Artifact.toDisplayString(): String = when (this) {
    is Artifact.Text -> content
    is Artifact.Image -> uri.toString()
    is Artifact.Audio -> uri.toString()
    is Artifact.Video -> uri.toString()
    is Artifact.Binary -> "Binary Data (${data.size} bytes)"
    is Artifact.Json -> jsonString
    is Artifact.Error -> "Error: $message"
    is Artifact.Empty -> ""
}

/**
 * Maps a Result failure to a different exception type while preserving the stack trace.
 */
inline fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> {
    return if (isFailure) {
        Result.failure(transform(exceptionOrNull()!!))
    } else {
        this
    }
}
