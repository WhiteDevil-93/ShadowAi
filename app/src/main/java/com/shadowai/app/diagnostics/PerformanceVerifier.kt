package com.shadowai.app.diagnostics

import android.os.SystemClock
import android.util.Log
import com.shadowai.app.ai.LocalLiquidEngine
import com.shadowai.app.db.ChatMessageEntity
import com.shadowai.app.db.ShadowDatabase
import com.shadowai.app.device.implementation.AndroidResourceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

data class GgufBenchmarkResult(
    val latenciesMs: List<Long>,
    val successCount: Int,
    val failureCount: Int,
    val averageLatencyMs: Double,
    val fastestMs: Long,
    val slowestMs: Long
)

data class DatabaseLatencyReport(
    val iterationCount: Int,
    val averageLatencyMs: Double,
    val fastestMs: Long,
    val slowestMs: Long,
    val totalDurationMs: Long
)

data class ThermalThrottlingReport(
    val iterationCount: Int,
    val throttleCount: Int,
    val throttleTimestampsMs: List<Long>,
    val averageLoopMs: Double,
    val maxLoopMs: Long,
    val summaryStatus: String
)

class PerformanceVerifier(
    private val localLiquidEngine: LocalLiquidEngine,
    private val database: ShadowDatabase,
    private val resourceMonitor: AndroidResourceMonitor
) {

    companion object {
        private const val TAG = "PerformanceVerifier"
    }

    /**
     * Run a short GGUF inference benchmark and capture latency stats.
     */
    suspend fun runGgufInferenceBenchmark(
        prompt: String = "Performance verification prompt.",
        iterations: Int = 3,
        maxTokens: Int = 128
    ): GgufBenchmarkResult = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        var failures = 0
        repeat(iterations) { index ->
            val start = SystemClock.elapsedRealtime()
            val result = localLiquidEngine.generate(prompt, maxTokens)
            val duration = SystemClock.elapsedRealtime() - start
            if (result.isSuccess) {
                latencies.add(duration)
            } else {
                failures++
                val cause = result.exceptionOrNull()?.message ?: "unknown error"
                Log.w(TAG, "Benchmark iteration ${index + 1} failed: $cause")
            }
            Log.i(TAG, "Benchmark iteration ${index + 1} latency: ${duration}ms")
        }

        val average = if (latencies.isEmpty()) 0.0 else latencies.average()
        return@withContext GgufBenchmarkResult(
            latenciesMs = latencies,
            successCount = latencies.size,
            failureCount = failures,
            averageLatencyMs = average,
            fastestMs = latencies.minOrNull() ?: 0L,
            slowestMs = latencies.maxOrNull() ?: 0L
        )
    }

    /**
     * Stress test ShadowDatabase latency by inserting/deleting messages repeatedly.
     */
    suspend fun runDatabaseLatencyTest(
        iterations: Int = 50
    ): DatabaseLatencyReport = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        val dao = database.messageDao()
        repeat(iterations) { index ->
            val id = UUID.randomUUID().toString()
            val message = ChatMessageEntity(
                id = id,
                text = "Benchmark message #$index",
                isUser = true,
                isThinking = false,
                timestamp = System.currentTimeMillis()
            )
            val start = SystemClock.elapsedRealtime()
            dao.insertMessage(message)
            dao.deleteMessage(message)
            val duration = SystemClock.elapsedRealtime() - start
            latencies.add(duration)
        }
        val total = latencies.sum()
        Log.i(TAG, "Database latency test finished (avg=${latencies.average()}ms)")
        return@withContext DatabaseLatencyReport(
            iterationCount = iterations,
            averageLatencyMs = if (latencies.isEmpty()) 0.0 else latencies.average(),
            fastestMs = latencies.minOrNull() ?: 0L,
            slowestMs = latencies.maxOrNull() ?: 0L,
            totalDurationMs = total
        )
    }

    /**
     * Verify that thermal throttling detection and recovery behave as expected while generating text.
     */
    suspend fun runThermalThrottlingCheck(
        prompt: String = "Thermal safety test.",
        iterations: Int = 10,
        maxTokens: Int = 64,
        coolDownMs: Long = 200L
    ): ThermalThrottlingReport = withContext(Dispatchers.IO) {
        val durations = mutableListOf<Long>()
        val throttleTimes = mutableListOf<Long>()
        val summaryStatus = resourceMonitor.getResourceStatus()
        repeat(iterations) { index ->
            val restricted = resourceMonitor.isThermalRestricted()
            if (restricted) {
                throttleTimes.add(SystemClock.elapsedRealtime())
                Log.w(TAG, "Iteration ${index + 1}: thermal restriction detected")
            }
            val start = SystemClock.elapsedRealtime()
            localLiquidEngine.generate("$prompt iteration ${index + 1}", maxTokens)
            durations.add(SystemClock.elapsedRealtime() - start)
            delay(coolDownMs)
        }
        val average = if (durations.isEmpty()) 0.0 else durations.average()
        Log.i(
            TAG,
            "Thermal throttling check complete (throttleHits=${throttleTimes.size}, status=$summaryStatus)"
        )
        return@withContext ThermalThrottlingReport(
            iterationCount = iterations,
            throttleCount = throttleTimes.size,
            throttleTimestampsMs = throttleTimes,
            averageLoopMs = average,
            maxLoopMs = durations.maxOrNull() ?: 0L,
            summaryStatus = summaryStatus
        )
    }
}
