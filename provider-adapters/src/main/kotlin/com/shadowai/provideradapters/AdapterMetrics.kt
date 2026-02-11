package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Metrics collection for provider adapters.
 * Thread-safe implementation for tracking adapter performance.
 */
class AdapterMetrics {
    private val metricsMap = ConcurrentHashMap<ProviderId, ProviderMetrics>()

    /**
     * Records a successful request.
     */
    fun recordSuccess(providerId: ProviderId, latencyMs: Long, tokensUsed: Int = 0) {
        getOrCreate(providerId).apply {
            successCount.incrementAndGet()
            totalLatencyMs.addAndGet(latencyMs)
            totalTokensUsed.addAndGet(tokensUsed.toLong())
            lastSuccessTimestamp.set(System.currentTimeMillis())
            updateLatencyStats(latencyMs)
        }
    }

    /**
     * Records a failed request.
     */
    fun recordFailure(providerId: ProviderId, latencyMs: Long, errorType: String) {
        getOrCreate(providerId).apply {
            failureCount.incrementAndGet()
            totalLatencyMs.addAndGet(latencyMs)
            lastFailureTimestamp.set(System.currentTimeMillis())
            errorCounts.merge(errorType, 1L) { old, new -> old + new }
        }
    }

    /**
     * Records a health check result.
     */
    fun recordHealthCheck(providerId: ProviderId, isHealthy: Boolean, latencyMs: Long) {
        getOrCreate(providerId).apply {
            healthCheckCount.incrementAndGet()
            lastHealthCheckTimestamp.set(System.currentTimeMillis())
            lastHealthCheckLatencyMs.set(latencyMs)
            isCurrentlyHealthy.set(isHealthy)
        }
    }

    /**
     * Gets metrics snapshot for a provider.
     */
    fun getMetrics(providerId: ProviderId): MetricsSnapshot? {
        return metricsMap[providerId]?.toSnapshot()
    }

    /**
     * Gets metrics for all providers.
     */
    fun getAllMetrics(): Map<ProviderId, MetricsSnapshot> {
        return metricsMap.mapValues { it.value.toSnapshot() }
    }

    /**
     * Resets metrics for a provider.
     */
    fun resetMetrics(providerId: ProviderId) {
        metricsMap.remove(providerId)
    }

    /**
     * Resets all metrics.
     */
    fun resetAllMetrics() {
        metricsMap.clear()
    }

    private fun getOrCreate(providerId: ProviderId): ProviderMetrics {
        return metricsMap.getOrPut(providerId) { ProviderMetrics() }
    }

    private class ProviderMetrics {
        val successCount = AtomicLong(0)
        val failureCount = AtomicLong(0)
        val totalLatencyMs = AtomicLong(0)
        val totalTokensUsed = AtomicLong(0)
        val healthCheckCount = AtomicLong(0)

        val lastSuccessTimestamp = AtomicLong(0)
        val lastFailureTimestamp = AtomicLong(0)
        val lastHealthCheckTimestamp = AtomicLong(0)
        val lastHealthCheckLatencyMs = AtomicLong(0)
        val isCurrentlyHealthy = java.util.concurrent.atomic.AtomicBoolean(true)

        val minLatencyMs = AtomicLong(Long.MAX_VALUE)
        val maxLatencyMs = AtomicLong(0)

        val errorCounts = ConcurrentHashMap<String, Long>()

        fun updateLatencyStats(latencyMs: Long) {
            minLatencyMs.updateAndGet { current -> minOf(current, latencyMs) }
            maxLatencyMs.updateAndGet { current -> maxOf(current, latencyMs) }
        }

        fun toSnapshot(): MetricsSnapshot {
            val total = successCount.get() + failureCount.get()
            val avgLatency = if (total > 0) totalLatencyMs.get() / total else 0L

            return MetricsSnapshot(
                successCount = successCount.get(),
                failureCount = failureCount.get(),
                totalRequests = total,
                successRate = if (total > 0) successCount.get().toDouble() / total else 1.0,
                avgLatencyMs = avgLatency,
                minLatencyMs = if (minLatencyMs.get() == Long.MAX_VALUE) 0 else minLatencyMs.get(),
                maxLatencyMs = maxLatencyMs.get(),
                totalTokensUsed = totalTokensUsed.get(),
                lastSuccessTimestamp = lastSuccessTimestamp.get(),
                lastFailureTimestamp = lastFailureTimestamp.get(),
                isHealthy = isCurrentlyHealthy.get(),
                lastHealthCheckLatencyMs = lastHealthCheckLatencyMs.get(),
                errorBreakdown = errorCounts.toMap()
            )
        }
    }
}

/**
 * Immutable snapshot of provider metrics.
 */
data class MetricsSnapshot(
    val successCount: Long,
    val failureCount: Long,
    val totalRequests: Long,
    val successRate: Double,
    val avgLatencyMs: Long,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val totalTokensUsed: Long,
    val lastSuccessTimestamp: Long,
    val lastFailureTimestamp: Long,
    val isHealthy: Boolean,
    val lastHealthCheckLatencyMs: Long,
    val errorBreakdown: Map<String, Long>
) {
    /**
     * Returns a human-readable summary.
     */
    fun toSummary(): String = buildString {
        append("Requests: $totalRequests (${(successRate * 100).toInt()}% success)")
        append(", Avg latency: ${avgLatencyMs}ms")
        if (totalTokensUsed > 0) {
            append(", Tokens: $totalTokensUsed")
        }
        append(", Healthy: $isHealthy")
    }
}

/**
 * Singleton metrics instance for global access.
 */
object GlobalAdapterMetrics {
    private val instance = AdapterMetrics()

    fun get(): AdapterMetrics = instance
}
