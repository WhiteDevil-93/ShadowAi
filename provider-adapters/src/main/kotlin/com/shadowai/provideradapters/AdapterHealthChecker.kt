package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Health checker for provider adapters.
 * Performs periodic health checks and tracks availability.
 */
class AdapterHealthChecker(
    private val metrics: AdapterMetrics = GlobalAdapterMetrics.get(),
    private val defaultTimeoutMs: Long = 5000L,
    private val healthCheckIntervalMs: Long = 60_000L
) {
    private val healthStatus = ConcurrentHashMap<ProviderId, HealthStatus>()
    private val adapters = ConcurrentHashMap<ProviderId, ProviderAdapter>()
    private var healthCheckJob: Job? = null

    /**
     * Registers an adapter for health monitoring.
     */
    fun registerAdapter(adapter: ProviderAdapter) {
        adapters[adapter.providerId] = adapter
        healthStatus[adapter.providerId] = HealthStatus(
            providerId = adapter.providerId,
            isHealthy = true, // Assume healthy until checked
            lastCheckTimestamp = 0,
            consecutiveFailures = 0
        )
    }

    /**
     * Unregisters an adapter from health monitoring.
     */
    fun unregisterAdapter(providerId: ProviderId) {
        adapters.remove(providerId)
        healthStatus.remove(providerId)
    }

    /**
     * Performs a health check on a specific adapter.
     */
    suspend fun checkHealth(providerId: ProviderId): HealthCheckResult {
        val adapter = adapters[providerId]
            ?: return HealthCheckResult(
                providerId = providerId,
                isHealthy = false,
                latencyMs = 0,
                error = "Adapter not registered"
            )

        return checkAdapterHealth(adapter)
    }

    /**
     * Performs health checks on all registered adapters.
     */
    suspend fun checkAllHealth(): List<HealthCheckResult> = coroutineScope {
        adapters.values.map { adapter ->
            async { checkAdapterHealth(adapter) }
        }.awaitAll()
    }

    /**
     * Gets the current health status for a provider.
     */
    fun getHealthStatus(providerId: ProviderId): HealthStatus? = healthStatus[providerId]

    /**
     * Gets health status for all providers.
     */
    fun getAllHealthStatus(): Map<ProviderId, HealthStatus> = healthStatus.toMap()

    /**
     * Checks if a provider is currently healthy.
     */
    fun isHealthy(providerId: ProviderId): Boolean {
        return healthStatus[providerId]?.isHealthy ?: false
    }

    /**
     * Starts periodic health checking.
     */
    fun startPeriodicHealthChecks(scope: CoroutineScope) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                try {
                    checkAllHealth()
                } catch (e: Exception) {
                    // Log but don't crash the health check loop
                }
                delay(healthCheckIntervalMs)
            }
        }
    }

    /**
     * Stops periodic health checking.
     */
    fun stopPeriodicHealthChecks() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private suspend fun checkAdapterHealth(adapter: ProviderAdapter): HealthCheckResult {
        val startTime = System.currentTimeMillis()

        return try {
            val isAvailable = withTimeout(defaultTimeoutMs) {
                adapter.isAvailable()
            }

            val latency = System.currentTimeMillis() - startTime

            updateHealthStatus(adapter.providerId, isAvailable, latency, null)
            metrics.recordHealthCheck(adapter.providerId, isAvailable, latency)

            HealthCheckResult(
                providerId = adapter.providerId,
                isHealthy = isAvailable,
                latencyMs = latency,
                error = if (!isAvailable) "Provider reported unavailable" else null
            )
        } catch (e: TimeoutCancellationException) {
            val latency = System.currentTimeMillis() - startTime
            updateHealthStatus(adapter.providerId, false, latency, "Timeout")
            metrics.recordHealthCheck(adapter.providerId, false, latency)

            HealthCheckResult(
                providerId = adapter.providerId,
                isHealthy = false,
                latencyMs = latency,
                error = "Health check timed out after ${defaultTimeoutMs}ms"
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            updateHealthStatus(adapter.providerId, false, latency, e.message)
            metrics.recordHealthCheck(adapter.providerId, false, latency)

            HealthCheckResult(
                providerId = adapter.providerId,
                isHealthy = false,
                latencyMs = latency,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun updateHealthStatus(
        providerId: ProviderId,
        isHealthy: Boolean,
        latencyMs: Long,
        error: String?
    ) {
        healthStatus.compute(providerId) { _, current ->
            val consecutiveFailures = if (isHealthy) 0 else (current?.consecutiveFailures ?: 0) + 1

            HealthStatus(
                providerId = providerId,
                isHealthy = isHealthy,
                lastCheckTimestamp = System.currentTimeMillis(),
                lastCheckLatencyMs = latencyMs,
                consecutiveFailures = consecutiveFailures,
                lastError = error
            )
        }
    }
}

/**
 * Result of a single health check.
 */
data class HealthCheckResult(
    val providerId: ProviderId,
    val isHealthy: Boolean,
    val latencyMs: Long,
    val error: String? = null
)

/**
 * Current health status of a provider.
 */
data class HealthStatus(
    val providerId: ProviderId,
    val isHealthy: Boolean,
    val lastCheckTimestamp: Long,
    val lastCheckLatencyMs: Long = 0,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null
) {
    /**
     * Returns true if this status is stale (last check > 2 minutes ago).
     */
    fun isStale(maxAgeMs: Long = 120_000L): Boolean {
        return System.currentTimeMillis() - lastCheckTimestamp > maxAgeMs
    }
}
