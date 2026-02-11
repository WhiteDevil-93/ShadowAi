package com.shadowai.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collects pipeline errors with analytics and logging.
 */
class ErrorCollector(
    private val logger: DiagnosticsLogger,
    private val analytics: ErrorAnalytics,
    private val contextStore: ErrorContextStore,
    private val maxEntries: Int = 200
) {
    private val _errors = MutableStateFlow<List<PipelineError>>(emptyList())
    val errors: StateFlow<List<PipelineError>> = _errors.asStateFlow()

    val analyticsSnapshot: StateFlow<ErrorAnalyticsSnapshot> = analytics.snapshot

    fun record(error: PipelineError) {
        val enriched = when {
            error.context != contextStore.get() && error.context == ErrorContext() -> {
                error.copyWithContext(contextStore.get())
            }
            else -> error
        }

        logger.log(enriched)
        analytics.record(enriched)

        val updated = (_errors.value + enriched)
            .takeLast(maxEntries)
        _errors.value = updated
    }

    fun clear() {
        _errors.value = emptyList()
        analytics.clear()
    }

    fun getRecent(limit: Int = 10): List<PipelineError> = errors.value.takeLast(limit)

    fun buildReport(): String {
        val snapshot = analytics.snapshot.value
        val lines = mutableListOf<String>()
        lines.add("Diagnostics Report")
        lines.add("Total Errors: ${snapshot.totalErrors}")
        snapshot.lastErrorTimestamp?.let { lines.add("Last Error: $it") }
        lines.add("Top Error Types: ${snapshot.topErrorTypes().joinToString { "${it.first}=${it.second}" }}")
        lines.add("Top Providers: ${snapshot.topProviders().joinToString { "${it.first}=${it.second}" }}")
        lines.add("Recent Errors:")
        getRecent(20).forEach { error ->
            lines.add("- ${error.toLogMessage()}")
        }
        return lines.joinToString("\n")
    }

    private fun PipelineError.copyWithContext(context: ErrorContext): PipelineError {
        return when (this) {
            is PipelineError.ProviderError -> copy(context = context)
            is PipelineError.ModelLoadError -> copy(context = context)
            is PipelineError.TransformError -> copy(context = context)
            is PipelineError.ArtifactError -> copy(context = context)
            is PipelineError.ValidationError -> copy(context = context)
            is PipelineError.UnknownError -> copy(context = context)
        }
    }
}
