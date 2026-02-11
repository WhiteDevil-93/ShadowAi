package com.shadowai.app.execution

import com.shadowai.app.models.ModelId
import com.shadowai.app.routing.RoutingDecision
import com.shadowai.app.tasks.Task

/**
 * Encapsulates the final result of a task execution, including the updated task state
 * and full attribution metadata.
 *
 * CRITICAL FIX: Removed redundant failureReason field
 * The failure information is already available in task.currentState.reason
 *
 * @property task The final state of the task (Completed or Failed).
 * @property routingDecision The routing decision that governed this execution.
 * @property modelId The identifier of the model used, if applicable.
 */
data class ExecutionResult(
    val task: Task,
    val routingDecision: RoutingDecision,
    val modelId: ModelId? = null
) {
    /**
     * Get failure reason from task state for backward compatibility.
     */
    val failureReason: String?
        get() = (task.currentState as? com.shadowai.app.tasks.TaskState.Failed)?.reason
}
