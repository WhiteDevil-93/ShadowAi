package com.shadowai.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aggregates diagnostics analytics.
 */
class ErrorAnalytics {
    private val _snapshot = MutableStateFlow(ErrorAnalyticsSnapshot())
    val snapshot: StateFlow<ErrorAnalyticsSnapshot> = _snapshot.asStateFlow()

    fun record(error: PipelineError) {
        val current = _snapshot.value
        val typeKey = error::class.simpleName ?: "Unknown"
        val providerKey = error.context.providerId ?: "unknown"

        val updatedTypeCounts = current.errorTypeCounts.toMutableMap().apply {
            put(typeKey, (this[typeKey] ?: 0) + 1)
        }
        val updatedProviderCounts = current.providerCounts.toMutableMap().apply {
            put(providerKey, (this[providerKey] ?: 0) + 1)
        }

        _snapshot.value = current.copy(
            totalErrors = current.totalErrors + 1,
            errorTypeCounts = updatedTypeCounts,
            providerCounts = updatedProviderCounts,
            lastErrorTimestamp = error.timestamp
        )
    }

    fun clear() {
        _snapshot.value = ErrorAnalyticsSnapshot()
    }
}

data class ErrorAnalyticsSnapshot(
    val totalErrors: Int = 0,
    val errorTypeCounts: Map<String, Int> = emptyMap(),
    val providerCounts: Map<String, Int> = emptyMap(),
    val lastErrorTimestamp: Long? = null
) {
    fun topErrorTypes(limit: Int = 3): List<Pair<String, Int>> {
        return errorTypeCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.toPair() }
    }

    fun topProviders(limit: Int = 3): List<Pair<String, Int>> {
        return providerCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.toPair() }
    }
}
