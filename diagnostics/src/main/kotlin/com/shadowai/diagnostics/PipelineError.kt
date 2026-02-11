package com.shadowai.diagnostics

import com.shadowai.core.Transform

/**
 * Structured error types for pipeline execution and diagnostics.
 */
sealed class PipelineError(
    open val message: String,
    open val severity: ErrorSeverity = ErrorSeverity.ERROR,
    open val timestamp: Long = System.currentTimeMillis(),
    open val context: ErrorContext = ErrorContext(),
    open val cause: Throwable? = null
) {
    data class ProviderError(
        override val message: String,
        val providerId: String,
        override val severity: ErrorSeverity = ErrorSeverity.ERROR,
        override val context: ErrorContext = ErrorContext(providerId = providerId),
        override val cause: Throwable? = null
    ) : PipelineError(message, severity, context = context, cause = cause)

    data class ModelLoadError(
        override val message: String,
        val modelId: String,
        override val severity: ErrorSeverity = ErrorSeverity.ERROR,
        override val context: ErrorContext = ErrorContext(modelId = modelId),
        override val cause: Throwable? = null
    ) : PipelineError(message, severity, context = context, cause = cause)

    data class TransformError(
        override val message: String,
        val transform: Transform,
        override val severity: ErrorSeverity = ErrorSeverity.ERROR,
        override val context: ErrorContext = ErrorContext(transform = transform),
        override val cause: Throwable? = null
    ) : PipelineError(message, severity, context = context, cause = cause)

    data class ArtifactError(
        override val message: String,
        val artifactId: String,
        override val severity: ErrorSeverity = ErrorSeverity.ERROR,
        override val context: ErrorContext = ErrorContext(metadata = mapOf("artifactId" to artifactId)),
        override val cause: Throwable? = null
    ) : PipelineError(message, severity, context = context, cause = cause)

    data class ValidationError(
        override val message: String,
        val field: String,
        override val severity: ErrorSeverity = ErrorSeverity.WARNING,
        override val context: ErrorContext = ErrorContext(metadata = mapOf("field" to field)),
        override val cause: Throwable? = null
    ) : PipelineError(message, severity, context = context, cause = cause)

    data class UnknownError(
        override val message: String,
        override val severity: ErrorSeverity = ErrorSeverity.ERROR,
        override val context: ErrorContext = ErrorContext(),
        override val cause: Throwable? = null
    ) : PipelineError(message, severity, context = context, cause = cause)

    fun toLogMessage(): String {
        val contextInfo = if (context.asMap().isEmpty()) "" else " context=${context.asMap()}"
        val causeInfo = cause?.let { " cause=${it::class.simpleName}: ${it.message}" } ?: ""
        return "${this::class.simpleName}: $message$contextInfo$causeInfo"
    }
}
