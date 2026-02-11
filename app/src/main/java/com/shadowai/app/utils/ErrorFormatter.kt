package com.shadowai.app.utils

import java.io.IOException
import retrofit2.HttpException

/**
 * Utility for formatting errors consistently across the app.
 */
object ErrorFormatter {
    /**
     * Formats a Throwable into a user-friendly error message.
     */
    fun formatError(throwable: Throwable): String {
        return when (throwable) {
            is IOException -> "Network error: ${throwable.message}"
            is HttpException -> "Server error: ${throwable.code()}"
            else -> "Error: ${throwable.message ?: "Unknown error"}"
        }
    }
}
