package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit breaker implementation for provider adapters.
 * Prevents cascading failures by failing fast when a provider is unhealthy.
 */
class AdapterCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val successThreshold: Int = 2,
    private val timeoutMs: Long = 30_000L
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val mutex = Mutex()
    private val halfOpenInFlight = AtomicBoolean(false)

    val currentState: State get() = state.get()

    /**
     * Executes a block with circuit breaker protection.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        return when (currentState) {
            State.CLOSED -> executeClosed(block)
            State.OPEN -> executeOpen(block)
            State.HALF_OPEN -> executeHalfOpen(block)
        }
    }

    /**
     * Executes a block with circuit breaker protection, returning a Result.
     */
    suspend fun <T> tryExecute(block: suspend () -> T): Result<T> {
        return try {
            Result.success(execute(block))
        } catch (e: AdapterCircuitOpenException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeClosed(block: suspend () -> T): T {
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private suspend fun <T> executeOpen(block: suspend () -> T): T {
        if (isTimeoutElapsed()) {
            mutex.withLock {
                if (state.get() == State.OPEN && isTimeoutElapsed()) {
                    state.set(State.HALF_OPEN)
                    successCount.set(0)
                }
            }
            return executeHalfOpen(block)
        }
        throw AdapterCircuitOpenException("Circuit breaker is open. Provider temporarily unavailable.")
    }

    private suspend fun <T> executeHalfOpen(block: suspend () -> T): T {
        if (!halfOpenInFlight.compareAndSet(false, true)) {
            throw AdapterCircuitOpenException("Circuit breaker is testing recovery - try again shortly")
        }

        return try {
            val result = block()
            onHalfOpenSuccess()
            result
        } catch (e: Exception) {
            onHalfOpenFailure()
            throw e
        } finally {
            halfOpenInFlight.set(false)
        }
    }

    private suspend fun onSuccess() {
        failureCount.set(0)
    }

    private suspend fun onFailure() {
        lastFailureTime.set(System.currentTimeMillis())
        if (failureCount.incrementAndGet() >= failureThreshold) {
            mutex.withLock {
                if (state.get() == State.CLOSED) {
                    state.set(State.OPEN)
                }
            }
        }
    }

    private suspend fun onHalfOpenSuccess() {
        if (successCount.incrementAndGet() >= successThreshold) {
            mutex.withLock {
                if (state.get() == State.HALF_OPEN) {
                    state.set(State.CLOSED)
                    failureCount.set(0)
                    successCount.set(0)
                }
            }
        }
    }

    private suspend fun onHalfOpenFailure() {
        mutex.withLock {
            state.set(State.OPEN)
            lastFailureTime.set(System.currentTimeMillis())
        }
    }

    private fun isTimeoutElapsed(): Boolean {
        return System.currentTimeMillis() - lastFailureTime.get() >= timeoutMs
    }

    /**
     * Manually resets the circuit breaker to CLOSED state.
     */
    fun reset() {
        state.set(State.CLOSED)
        failureCount.set(0)
        successCount.set(0)
        lastFailureTime.set(0)
    }
}

/**
 * Exception thrown when the circuit is open.
 */
class AdapterCircuitOpenException(message: String) : Exception(message)

/**
 * Manages circuit breakers for multiple providers.
 */
class AdapterCircuitBreakerManager(
    private val failureThreshold: Int = 5,
    private val successThreshold: Int = 2,
    private val timeoutMs: Long = 30_000L
) {
    private val breakers = ConcurrentHashMap<ProviderId, AdapterCircuitBreaker>()

    /**
     * Gets or creates a circuit breaker for a provider.
     */
    fun getBreaker(providerId: ProviderId): AdapterCircuitBreaker {
        return breakers.getOrPut(providerId) {
            AdapterCircuitBreaker(failureThreshold, successThreshold, timeoutMs)
        }
    }

    /**
     * Gets the state of a provider's circuit breaker.
     */
    fun getState(providerId: ProviderId): AdapterCircuitBreaker.State? {
        return breakers[providerId]?.currentState
    }

    /**
     * Gets states for all providers.
     */
    fun getAllStates(): Map<ProviderId, AdapterCircuitBreaker.State> {
        return breakers.mapValues { it.value.currentState }
    }

    /**
     * Resets a specific provider's circuit breaker.
     */
    fun reset(providerId: ProviderId) {
        breakers[providerId]?.reset()
    }

    /**
     * Resets all circuit breakers.
     */
    fun resetAll() {
        breakers.values.forEach { it.reset() }
    }

    /**
     * Removes a provider's circuit breaker.
     */
    fun remove(providerId: ProviderId) {
        breakers.remove(providerId)
    }
}

/**
 * Wraps a ProviderAdapter with circuit breaker protection.
 */
class CircuitProtectedAdapter(
    private val delegate: ProviderAdapter,
    private val circuitBreaker: AdapterCircuitBreaker,
    private val metrics: AdapterMetrics = GlobalAdapterMetrics.get()
) : ProviderAdapter by delegate {

    override suspend fun execute(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any>
    ): Result<Any> {
        val startTime = System.currentTimeMillis()

        return try {
            circuitBreaker.execute {
                val result = delegate.execute(transform, input, parameters)
                val latency = System.currentTimeMillis() - startTime

                if (result.isSuccess) {
                    metrics.recordSuccess(delegate.providerId, latency)
                } else {
                    metrics.recordFailure(delegate.providerId, latency, "execution_failure")
                }

                result.getOrThrow()
            }.let { Result.success(it) }
        } catch (e: AdapterCircuitOpenException) {
            val latency = System.currentTimeMillis() - startTime
            metrics.recordFailure(delegate.providerId, latency, "circuit_open")
            Result.failure(e)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            metrics.recordFailure(delegate.providerId, latency, e::class.simpleName ?: "unknown")
            Result.failure(e)
        }
    }

    override suspend fun isAvailable(): Boolean {
        // If circuit is open, treat as unavailable
        if (circuitBreaker.currentState == AdapterCircuitBreaker.State.OPEN) {
            return false
        }
        return delegate.isAvailable()
    }
}
