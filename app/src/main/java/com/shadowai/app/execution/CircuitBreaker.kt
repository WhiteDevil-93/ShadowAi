package com.shadowai.app.execution

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Circuit Breaker implementation for robust failure handling.
 * 
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Circuit is open, requests fail fast
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 * 
 * @param failureThreshold Number of failures before opening the circuit (default: 5)
 * @param successThreshold Number of successes in HALF_OPEN state before closing (default: 2)
 * @param timeoutMs Time in milliseconds before attempting to transition from OPEN to HALF_OPEN (default: 30000)
 */
class CircuitBreaker(
    val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    val successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    companion object {
        private const val TAG = "CircuitBreaker"
        private const val DEFAULT_FAILURE_THRESHOLD = 5
        private const val DEFAULT_SUCCESS_THRESHOLD = 2
        private const val DEFAULT_TIMEOUT_MS = 30_000L
    }

    enum class State {
        CLOSED, OPEN, HALF_OPEN
    }

    @Volatile
    private var state: State = State.CLOSED
    @Volatile
    private var failureCount: Int = 0
    @Volatile
    private var successCount: Int = 0
    @Volatile
    private var lastFailureTime: Long = 0
    private val mutex = Mutex()
    @Volatile
    private var halfOpenInFlight: Boolean = false

    val currentState: State
        get() = state
    val currentFailureCount: Int
        get() = failureCount

    /**
     * Execute a block of code with circuit breaker protection.
     * Throws CircuitOpenException if the circuit is open.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        val snapshot = currentState
        return when (snapshot) {
            State.CLOSED -> executeClosed(block)
            State.OPEN -> executeOpen(block)
            State.HALF_OPEN -> executeHalfOpen(block)
        }
    }

    /**
     * Execute a block of code with circuit breaker protection, returning a Result.
     * This is a non-throwing alternative to execute().
     */
    suspend fun <T> tryExecute(block: suspend () -> T): Result<T> {
        return try {
            Result.success(execute(block))
        } catch (e: CancellationException) {
            throw e
        } catch (e: CircuitOpenException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeClosed(block: suspend () -> T): T {
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: CancellationException) {
            // FATAL fix: Never swallow CancellationException — preserves structured concurrency
            throw e
        } catch (e: Throwable) {
            onFailure()
            throw e
        }
    }

    private suspend fun <T> executeOpen(block: suspend () -> T): T {
        val transition = mutex.withLock {
            if (state != State.OPEN) {
                OpenTransition.RETRY_DISPATCH
            } else {
                val currentTime = System.currentTimeMillis()
                val lastFailure = lastFailureTime
                if ((currentTime - lastFailure) > timeoutMs) {
                    state = State.HALF_OPEN
                    successCount = 0
                    OpenTransition.EXECUTE_HALF_OPEN
                } else {
                    OpenTransition.FAIL_FAST
                }
            }
        }
        return when (transition) {
            OpenTransition.RETRY_DISPATCH -> execute(block)
            OpenTransition.EXECUTE_HALF_OPEN -> executeHalfOpen(block)
            OpenTransition.FAIL_FAST -> throw CircuitOpenException("Circuit breaker is open. Service temporarily unavailable.")
        }
    }

    private suspend fun <T> executeHalfOpen(block: suspend () -> T): T {
        val permitAcquired = mutex.withLock {
            if (state != State.HALF_OPEN) {
                false
            } else if (halfOpenInFlight) {
                false
            } else {
                halfOpenInFlight = true
                true
            }
        }
        if (!permitAcquired) {
            if (currentState == State.HALF_OPEN) {
                throw CircuitOpenException("Circuit breaker is testing recovery - try again shortly")
            }
            return execute(block)
        }
        return try {
            val result = block()
            onHalfOpenSuccess()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onHalfOpenFailure()
            throw e
        } finally {
            mutex.withLock {
                halfOpenInFlight = false
            }
        }
    }

    private suspend fun onSuccess() {
        mutex.withLock {
            failureCount = 0
        }
    }

    private suspend fun onFailure() {
        mutex.withLock {
            failureCount += 1
            val currentTime = System.currentTimeMillis()
            lastFailureTime = currentTime
            if (failureCount >= failureThreshold && state != State.OPEN) {
                state = State.OPEN
                Log.w(TAG, "Circuit breaker tripped! Moving to OPEN.")
            }
        }
    }

    private suspend fun onHalfOpenSuccess() {
        mutex.withLock {
            successCount += 1
            if (successCount >= successThreshold) {
                state = State.CLOSED
                failureCount = 0
                successCount = 0
                Log.i(TAG, "Circuit breaker recovered! Moving from HALF_OPEN to CLOSED.")
            }
        }
    }

    private suspend fun onHalfOpenFailure() {
        mutex.withLock {
            state = State.OPEN
            lastFailureTime = System.currentTimeMillis()
            Log.w(TAG, "Circuit breaker testing failed. Re-opening circuit.")
        }
    }

    /**
     * Reset the circuit breaker to initial CLOSED state.
     */
    suspend fun reset() {
        mutex.withLock {
            state = State.CLOSED
            failureCount = 0
            successCount = 0
            lastFailureTime = 0
            halfOpenInFlight = false
        }
    }

    private enum class OpenTransition {
        RETRY_DISPATCH,
        EXECUTE_HALF_OPEN,
        FAIL_FAST
    }
}

/**
 * Exception thrown when the circuit breaker is open.
 */
class CircuitOpenException(message: String) : Exception(message)
