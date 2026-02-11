package com.shadowai.app.diagnostics

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance monitoring for tracking model operations and system metrics.
 * Collects metrics for model load times, generation performance, memory usage.
 */
@Singleton
class PerformanceMonitor @Inject constructor() {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MAX_METRIC_HISTORY = 1000
    }

    /**
     * Metric types tracked by the monitor.
     */
    enum class MetricType {
        MODEL_LOAD_TIME,
        GENERATION_TOKENS_PER_SECOND,
        MEMORY_USAGE_BYTES,
        CACHE_HIT_RATE,
        API_LATENCY_MS,
        CONTEXT_WINDOW_UTILIZATION
    }

    /**
     * Single performance metric snapshot.
     */
    data class MetricSnapshot(
        val type: MetricType,
        val value: Double,
        val timestamp: Long = System.currentTimeMillis(),
        val labels: Map<String, String> = emptyMap()
    )

    /**
     * Aggregated statistics for a metric type.
     */
    data class MetricStats(
        val count: Long,
        val sum: Double,
        val avg: Double,
        val min: Double,
        val max: Double,
        val lastValue: Double
    )

    private val metrics = ConcurrentHashMap<MetricType, MutableList<MetricSnapshot>>()
    private val metricCounters = ConcurrentHashMap<MetricType, AtomicLong>()

    init {
        // Initialize metric storage
        MetricType.values().forEach { type ->
            metrics[type] = mutableListOf()
            metricCounters[type] = AtomicLong(0)
        }
    }

    /**
     * Record a performance metric.
     */
    fun recordMetric(
        type: MetricType,
        value: Double,
        labels: Map<String, String> = emptyMap()
    ) {
        val snapshot = MetricSnapshot(type, value, labels = labels)

        val metricList = metrics[type] ?: return
        synchronized(metricList) {
            metricList.add(snapshot)

            // Keep only recent metrics
            if (metricList.size > MAX_METRIC_HISTORY) {
                metricList.removeAt(0)
            }
        }
        metricCounters[type]?.incrementAndGet()

        Log.d(TAG, "Recorded $type: $value (labels: $labels)")
    }

    /**
     * Record model load time.
     */
    fun recordModelLoadTime(modelId: String, loadTimeMs: Long) {
        recordMetric(
            type = MetricType.MODEL_LOAD_TIME,
            value = loadTimeMs.toDouble(),
            labels = mapOf("model_id" to modelId)
        )
    }

    /**
     * Record generation performance.
     */
    fun recordGenerationPerformance(
        modelId: String,
        tokensGenerated: Int,
        generationTimeMs: Long
    ) {
        val tokensPerSecond = if (generationTimeMs > 0) {
            (tokensGenerated.toDouble() / generationTimeMs) * 1000.0
        } else {
            0.0
        }

        recordMetric(
            type = MetricType.GENERATION_TOKENS_PER_SECOND,
            value = tokensPerSecond,
            labels = mapOf("model_id" to modelId)
        )
    }

    /**
     * Record memory usage.
     */
    fun recordMemoryUsage(memoryBytes: Long) {
        recordMetric(
            type = MetricType.MEMORY_USAGE_BYTES,
            value = memoryBytes.toDouble()
        )
    }

    /**
     * Record cache hit.
     */
    fun recordCacheHit(cacheName: String) {
        recordMetric(
            type = MetricType.CACHE_HIT_RATE,
            value = 1.0,
            labels = mapOf("cache" to cacheName, "hit" to "true")
        )
    }

    /**
     * Record cache miss.
     */
    fun recordCacheMiss(cacheName: String) {
        recordMetric(
            type = MetricType.CACHE_HIT_RATE,
            value = 0.0,
            labels = mapOf("cache" to cacheName, "hit" to "false")
        )
    }

    /**
     * Record API latency.
     */
    fun recordApiLatency(endpoint: String, latencyMs: Long) {
        recordMetric(
            type = MetricType.API_LATENCY_MS,
            value = latencyMs.toDouble(),
            labels = mapOf("endpoint" to endpoint)
        )
    }

    /**
     * Record context window utilization.
     */
    fun recordContextUtilization(
        modelId: String,
        tokensUsed: Long,
        maxContext: Long
    ) {
        val utilization = if (maxContext > 0) {
            (tokensUsed.toDouble() / maxContext.toDouble()) * 100.0
        } else {
            0.0
        }

        recordMetric(
            type = MetricType.CONTEXT_WINDOW_UTILIZATION,
            value = utilization,
            labels = mapOf("model_id" to modelId)
        )
    }

    /**
     * Get statistics for a metric type.
     */
    fun getMetricStats(type: MetricType): MetricStats? {
        val metricList = metrics[type] ?: return null

        synchronized(metricList) {
            if (metricList.isEmpty()) return null

            val values = metricList.map { it.value }
            val sum = values.sum()
            val count = values.size

            return MetricStats(
                count = count.toLong(),
                sum = sum,
                avg = sum / count,
                min = values.minOrNull() ?: 0.0,
                max = values.maxOrNull() ?: 0.0,
                lastValue = values.lastOrNull() ?: 0.0
            )
        }
    }

    /**
     * Get metrics with specific labels.
     */
    fun getMetricsByLabels(type: MetricType, labels: Map<String, String>): List<MetricSnapshot> {
        val metricList = metrics[type] ?: return emptyList()

        synchronized(metricList) {
            return metricList.filter { snapshot ->
                labels.all { (key, value) ->
                    snapshot.labels[key] == value
                }
            }
        }
    }

    /**
     * Get the most recent metric for a type.
     */
    fun getLatestMetric(type: MetricType): MetricSnapshot? {
        val metricList = metrics[type] ?: return null

        synchronized(metricList) {
            return if (metricList.isNotEmpty()) {
                metricList.lastOrNull()
            } else {
                null
            }
        }
    }

    /**
     * Clear all metrics for a type.
     */
    fun clearMetrics(type: MetricType) {
        val metricList = metrics[type] ?: return

        synchronized(metricList) {
            metricList.clear()
        }

        metricCounters[type]?.set(0)
    }

    /**
     * Clear all metrics.
     */
    fun clearAllMetrics() {
        MetricType.values().forEach { type ->
            clearMetrics(type)
        }
    }

    /**
     * Generate a performance report.
     */
    fun generateReport(): String {
        val report = StringBuilder()
        report.appendLine("=== Performance Monitor Report ===")
        report.appendLine("Generated at: ${System.currentTimeMillis()}")

        MetricType.values().forEach { type ->
            val stats = getMetricStats(type)
            if (stats != null) {
                report.appendLine()
                report.appendLine("$type:")
                report.appendLine("  Count: ${stats.count}")
                report.appendLine("  Avg: ${String.format("%.2f", stats.avg)}")
                report.appendLine("  Min: ${String.format("%.2f", stats.min)}")
                report.appendLine("  Max: ${String.format("%.2f", stats.max)}")
                report.appendLine("  Last: ${String.format("%.2f", stats.lastValue)}")
            }
        }

        return report.toString()
    }

    /**
     * Log the current report.
     */
    fun logReport() {
        Log.i(TAG, generateReport())
    }
}
