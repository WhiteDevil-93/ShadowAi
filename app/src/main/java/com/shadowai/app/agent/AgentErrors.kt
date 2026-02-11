package com.shadowai.app.agent

/**
 * Phase 0.3: Unified Error Taxonomy
 * Normalizes all system failures into actionable categories.
 */
enum class ErrorCategory {
    /** Network timeouts, DNS issues, API unreachable. */
    TRANSPORT,

    /** Malformed JSON, schema violations, invalid arguments. */
    SEMANTIC,

    /** Contradictory intents, hallucinated capabilities. */
    LOGIC,

    /** Policy violations, permission denials, safety filter triggers. */
    VIOLATION,

    /** Execution failures that do not match other buckets, e.g., runtime errors. */
    EXECUTION,

    /** Token limits, retry budgets, timeout budgets exceeded. */
    EXHAUSTION,
    
    /** Security violations: prompt injection, PII leakage attempts. */
    SECURITY,
    
    /** Unknown or unclassified errors. */
    UNKNOWN
}

/**
 * Standardized error object for the Agentic System.
 */
data class AgentError(
    val category: ErrorCategory,
    val message: String,
    val code: String? = null,
    val requiredPermission: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
