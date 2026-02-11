package com.shadowai.app.routing

/**
 * Defines the authoritative policy that governs execution routing.
 */
enum class RoutingPolicy {
    /**
     * Strictly enforce local execution.
     * If local execution is impossible, the task fails.
     */
    FORCE_LOCAL,

    /**
     * Strictly enforce cloud execution.
     * If cloud execution is impossible, the task fails.
     */
    FORCE_CLOUD,

    /**
     * The system determines the best execution path based on capabilities,
     * network state, and model availability.
     */
    AUTO
}

/**
 * Represents a finalized decision on where to execute a task.
 * This structure captures the "why" behind a routing choice.
 *
 * @property policy The high-level policy that governed this decision.
 * @property selectedSource The actual execution source chosen.
 * @property reason A human-readable explanation for the decision.
 * @property overrideSource If overridden, the source of the override (User, Admin, System).
 */
data class RoutingDecision(
    val policy: RoutingPolicy,
    val selectedSource: ExecutionSource,
    val reason: String,
    val overrideSource: String? = null
)
