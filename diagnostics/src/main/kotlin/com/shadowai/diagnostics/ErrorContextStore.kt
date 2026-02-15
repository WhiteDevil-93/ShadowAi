package com.shadowai.diagnostics

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the current diagnostics context for structured reporting.
 */
@Singleton
class ErrorContextStore @Inject constructor() {
    @Volatile
    private var currentContext: ErrorContext = ErrorContext()

    fun get(): ErrorContext = currentContext

    fun update(context: ErrorContext) {
        currentContext = context
    }

    suspend fun <T> withContext(context: ErrorContext, block: suspend () -> T): T {
        val previous = currentContext
        currentContext = context
        return try {
            block()
        } finally {
            currentContext = previous
        }
    }
}
