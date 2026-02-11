package com.shadowai.app.tasks

import com.shadowai.app.execution.DeviceAction

/**
 * Phase 2.1: Explicit Planning Graph (DAGs)
 * Represents a structured sequence of actions delivered by the Agent.
 * 
 * @property version The plan version for schema compatibility.
 * @property nodes The list of plan nodes. Must be non-empty.
 */
data class Plan(
    val version: Int = 1,
    val nodes: List<PlanNode>
) {
    init {
        require(nodes.isNotEmpty()) { "Plan must contain at least one node" }
        require(version >= 1) { "Plan version must be at least 1" }
    }
}

/**
 * Represents a single action node in a plan.
 * Uses immutable properties with copy() for status updates.
 * 
 * @property id Unique identifier for this node.
 * @property action The device action to execute.
 * @property dependencies List of node IDs that must complete before this node.
 * @property status Current execution status of this node.
 * @property result The result of executing this node (if completed).
 */
data class PlanNode(
    val id: String,
    val action: DeviceAction,
    val dependencies: List<String> = emptyList(),
    val status: NodeStatus = NodeStatus.PENDING,
    val result: String? = null
) {
    init {
        require(id.isNotBlank()) { "PlanNode.id cannot be blank" }
        require(id.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "PlanNode.id contains invalid characters" }
        dependencies.forEach { dep ->
            require(dep.isNotBlank()) { "Dependency cannot be blank" }
        }
    }

    /**
     * Creates a copy with updated status.
     */
    fun withStatus(newStatus: NodeStatus): PlanNode = copy(status = newStatus)

    /**
     * Creates a copy with updated result.
     */
    fun withResult(newResult: String): PlanNode = copy(status = NodeStatus.COMPLETED, result = newResult)

    /**
     * Creates a copy with failed status and error message.
     */
    fun withFailure(errorMessage: String): PlanNode = copy(status = NodeStatus.FAILED, result = errorMessage)
}

enum class NodeStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
