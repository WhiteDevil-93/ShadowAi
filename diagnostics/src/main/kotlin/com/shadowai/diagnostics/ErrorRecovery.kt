package com.shadowai.diagnostics

import kotlinx.coroutines.delay

/**
 * Recovery strategy for diagnostic errors.
 */
interface ErrorRecoveryStrategy {
    fun canHandle(error: PipelineError): Boolean
    suspend fun <T> recover(error: PipelineError, block: suspend () -> Result<T>): Result<T>
}

class RetryRecoveryStrategy(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 500
) : ErrorRecoveryStrategy {
    override fun canHandle(error: PipelineError): Boolean {
        return error.severity >= ErrorSeverity.ERROR
    }

    override suspend fun <T> recover(error: PipelineError, block: suspend () -> Result<T>): Result<T> {
        var attempt = 0
        var lastResult: Result<T>? = null

        while (attempt <= maxRetries) {
            attempt++
            lastResult = block()
            if (lastResult.isSuccess) return lastResult
            if (attempt <= maxRetries) {
                delay(baseDelayMs * attempt)
            }
        }
        return lastResult ?: Result.failure(IllegalStateException("Recovery failed"))
    }
}

class NoRecoveryStrategy : ErrorRecoveryStrategy {
    override fun canHandle(error: PipelineError): Boolean = true

    override suspend fun <T> recover(error: PipelineError, block: suspend () -> Result<T>): Result<T> {
        return block()
    }
}

class ErrorRecoveryManager(
    private val strategies: List<ErrorRecoveryStrategy>
) {
    suspend fun <T> executeWithRecovery(
        error: PipelineError,
        block: suspend () -> Result<T>
    ): Result<T> {
        val strategy = strategies.firstOrNull { it.canHandle(error) } ?: NoRecoveryStrategy()
        return strategy.recover(error, block)
    }
}
