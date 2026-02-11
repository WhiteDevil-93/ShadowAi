package com.shadowai.diagnostics

import com.shadowai.core.Modality
import com.shadowai.core.Transform

/**
 * Captures contextual information for diagnostics events.
 */
data class ErrorContext(
    val taskId: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val routingSource: String? = null,
    val transform: Transform? = null,
    val modality: Modality? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun asMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>()
        taskId?.let { base["taskId"] = it }
        providerId?.let { base["providerId"] = it }
        modelId?.let { base["modelId"] = it }
        routingSource?.let { base["routingSource"] = it }
        transform?.let { base["transform"] = it::class.simpleName.orEmpty() }
        modality?.let { base["modality"] = it.getDisplayName() }
        if (metadata.isNotEmpty()) {
            base.putAll(metadata)
        }
        return base
    }
}

/**
 * Stores the current diagnostics context for structured reporting.
 */
class ErrorContextStore {
    @Volatile
    private var currentContext: ErrorContext = ErrorContext()

    fun get(): ErrorContext = currentContext

    fun update(context: ErrorContext) {
        currentContext = context
    }

    suspend fun <T> withContext(context: ErrorContext, block: suspend () -> T): T {
        val previous = currentContext
        currentContext = context
        return try {
            block()
        } finally {
            currentContext = previous
        }
    }
}
