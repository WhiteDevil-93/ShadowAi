package com.shadowai.app.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages streaming token visualization.
 */
class TokenVisualizationManager {

    companion object {
        private const val TAG = "TokenVisualizationManager"
        private const val MAX_HISTORY_SIZE = 100
    }

    data class InferenceMetrics(
        val messageId: String,
        val tokenCount: Int = 0,
        val ttftMs: Long = 0,
        val totalDurationMs: Long = 0,
        val startTime: Long = 0L,
        val firstTokenTime: Long = 0L,
        val endTime: Long = 0L,
        val tokensPerSecond: Float = 0f,
        val isComplete: Boolean = false
    ) {
        val durationSoFar: Long
            get() = System.currentTimeMillis() - startTime

        val ttftDisplay: String
            get() = if (ttftMs > 0) "${ttftMs}ms" else "Calculating..."

        val tpsDisplay: String
            get() = String.format("%.1f token/s", tokensPerSecond)
    }

    data class PerformanceHistory(
        val last50AvgTps: Float = 0f,
        val last100AvgTps: Float = 0f,
        val last50AvgTtft: Float = 0f,
        val last100AvgTtft: Float = 0f,
        val totalInferences: Int = 0,
        val fastestTpsRecord: Float = 0f,
        val slowestTpsRecord: Float = 0f
    )

    data class VisualizationState(
        val currentMetrics: InferenceMetrics? = null,
        val isStreaming: Boolean = false,
        val progress: Float = 0f,
        val performanceHistory: PerformanceHistory = PerformanceHistory()
    )

    private val _state = MutableStateFlow(VisualizationState())
    val state: StateFlow<VisualizationState> = _state

    private val metricsHistory = HashMap<String, InferenceMetrics>()
    private val completedMetrics = mutableListOf<InferenceMetrics>()
    private val tokenCounter = AtomicLong(0)

    private var currentMessageId: String? = null

    fun startInference(messageId: String) {
        val metrics = InferenceMetrics(
            messageId = messageId,
            startTime = System.currentTimeMillis()
        )

        metricsHistory[messageId] = metrics
        currentMessageId = messageId

        _state.value = VisualizationState(
            currentMetrics = metrics,
            isStreaming = true,
            progress = 0f,
            performanceHistory = calculatePerformanceHistory()
        )
    }

    fun onFirstToken(messageId: String) {
        val metrics = metricsHistory[messageId] ?: return

        if (metrics.firstTokenTime == 0L) {
            val updated = metrics.copy(
                firstTokenTime = System.currentTimeMillis(),
                ttftMs = System.currentTimeMillis() - metrics.startTime
            )
            metricsHistory[messageId] = updated
            _state.value = _state.value.copy(
                currentMetrics = updated,
                progress = 0.1f
            )
        }
    }

    fun onToken(messageId: String, partialToken: String = "") {
        val metrics = metricsHistory[messageId] ?: return

        if (metrics.firstTokenTime == 0L) {
            onFirstToken(messageId)
            return
        }

        val newTokenCount = metrics.tokenCount + 1
        val currentTime = System.currentTimeMillis()
        val elapsedSinceFirstToken = currentTime - metrics.firstTokenTime
        val tokensPerSecond = if (elapsedSinceFirstToken > 0) {
            (newTokenCount * 1000f) / elapsedSinceFirstToken
        } else {
            0f
        }

        val updatedMetrics = metrics.copy(
            tokenCount = newTokenCount,
            tokensPerSecond = tokensPerSecond
        )

        metricsHistory[messageId] = updatedMetrics

        _state.value = _state.value.copy(
            currentMetrics = updatedMetrics,
            progress = calculateProgress(updatedMetrics),
            performanceHistory = calculatePerformanceHistory()
        )
    }

    fun completeInference(messageId: String, totalTokens: Int = -1) {
        val metrics = metricsHistory[messageId] ?: return

        val endTime = System.currentTimeMillis()
        val finalTokenCount = if (totalTokens >= 0) totalTokens else metrics.tokenCount

        val completed = metrics.copy(
            isComplete = true,
            tokenCount = finalTokenCount,
            endTime = endTime,
            totalDurationMs = endTime - metrics.startTime
        )

        metricsHistory[messageId] = completed

        this.completedMetrics.add(completed)
        if (this.completedMetrics.size > MAX_HISTORY_SIZE) {
            this.completedMetrics.removeAt(0)
        }

        _state.value = VisualizationState(
            currentMetrics = completed,
            isStreaming = false,
            progress = 1f,
            performanceHistory = calculatePerformanceHistory()
        )
    }

    fun cancelInference(messageId: String) {
        val metrics = metricsHistory[messageId] ?: return

        val cancelledMetrics = metrics.copy(
            isComplete = true,
            endTime = System.currentTimeMillis(),
            totalDurationMs = System.currentTimeMillis() - metrics.startTime
        )

        metricsHistory[messageId] = cancelledMetrics

        _state.value = VisualizationState(
            currentMetrics = cancelledMetrics,
            isStreaming = false,
            progress = _state.value.progress,
            performanceHistory = calculatePerformanceHistory()
        )
    }

    fun reset() {
        currentMessageId = null
        _state.value = _state.value.copy(
            currentMetrics = null,
            isStreaming = false,
            progress = 0f
        )
    }

    fun getMetrics(messageId: String): InferenceMetrics? {
        return metricsHistory[messageId]
    }

    fun getHistory(): List<InferenceMetrics> {
        return completedMetrics.toList()
    }

    private fun calculateProgress(metrics: InferenceMetrics): Float {
        if (metrics.firstTokenTime == 0L) {
            val elapsed = System.currentTimeMillis() - metrics.startTime
            return (elapsed % 1000) / 1000f * 0.1f
        }
        return (metrics.tokenCount / 1000f).coerceIn(0.1f, 1f)
    }

    private fun calculatePerformanceHistory(): PerformanceHistory {
        if (completedMetrics.isEmpty()) {
            return PerformanceHistory()
        }

        val recent50 = completedMetrics.takeLast(50)
        val recent100 = completedMetrics.takeLast(100)

        val avgTps50 = if (recent50.isNotEmpty()) {
            recent50.map { it.tokensPerSecond }.average().toFloat()
        } else 0f

        val avgTps100 = if (recent100.isNotEmpty()) {
            recent100.map { it.tokensPerSecond }.average().toFloat()
        } else 0f

        val avgTtft50 = if (recent50.isNotEmpty()) {
            recent50.map { it.ttftMs }.average().toFloat()
        } else 0f

        val avgTtft100 = if (recent100.isNotEmpty()) {
            recent100.map { it.ttftMs }.average().toFloat()
        } else 0f

        val fastestTps = completedMetrics.maxOfOrNull { it.tokensPerSecond } ?: 0f
        val slowestTps = completedMetrics.filter { it.tokensPerSecond > 0 }.minOfOrNull { it.tokensPerSecond } ?: 0f

        return PerformanceHistory(
            last50AvgTps = avgTps50,
            last100AvgTps = avgTps100,
            last50AvgTtft = avgTtft50,
            last100AvgTtft = avgTtft100,
            totalInferences = completedMetrics.size,
            fastestTpsRecord = fastestTps,
            slowestTpsRecord = slowestTps
        )
    }

    fun getPerformanceSummary(): String {
        val history = _state.value.performanceHistory
        return buildString {
            if (history.totalInferences == 0) {
                append("No performance data available")
            } else {
                append("Recent Performance:\n")
                append("• Avg speed: ${String.format("%.1f", history.last50AvgTps)} token/s\n")
                append("• Avg TTFT: ${String.format("%.0f", history.last50AvgTtft)}ms\n")
                append("• Total inferences: ${history.totalInferences}")
                if (history.fastestTpsRecord > 0) {
                    append("\n• Fastest: ${String.format("%.1f", history.fastestTpsRecord)} token/s")
                }
            }
        }
    }

    fun clearHistory() {
        completedMetrics.clear()
        metricsHistory.clear()
        _state.value = _state.value.copy(
            currentMetrics = null,
            isStreaming = false,
            progress = 0f,
            performanceHistory = PerformanceHistory()
        )
    }
}

data class TokenVisualizationUIState(
    val isStreaming: Boolean = false,
    val tokensPerSecond: Float = 0f,
    val ttft: String = "Calculating...",
    val tokenCount: Int = 0,
    val progress: Float = 0f,
    val avg50Tps: Float = 0f,
    val avg100Tps: Float = 0f,
    val totalInferences: Int = 0
) {
    val tpsDisplay: String
        get() = String.format("%.1f token/s", tokensPerSecond)

    val progressDisplay: String
        get() = "${(progress * 100).toInt()}%"
}
