package com.shadowai.app.agent

/**
 * Defines the communication protocol for inter-agent messaging.
 */
data class AgentMessage(
    val senderId: String,
    val recipientId: String,
    val protocol: Protocol,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val taskId: String? = null
) {
    /**
     * Defines the type of message being sent, guiding the recipient's action.
     */
    enum class Protocol {
        // Supervisor to Worker
        TASK_DECOMPOSE,      // Request a worker to break down a complex task
        TASK_EXECUTE,        // Assign a specific, atomic task to a worker
        TASK_VERIFY,         // Request a worker to verify a result
        TASK_CANCEL,         // Request a worker to stop processing

        // Worker to Supervisor
        RESULT_PARTIAL,      // Send a partial result or progress update
        RESULT_FINAL,        // Send the final result of an assigned task
        REQUEST_ASSISTANCE,  // Request help or resources from the supervisor
        REPORT_FAILURE,      // Report a failure or error
        REPORT_THOUGHT       // Report internal reasoning (for logging/UI)
    }
}

/**
 * Interface for all agents (Supervisor and Workers).
 */
interface Agent {
    val id: String
    suspend fun handleMessage(message: AgentMessage): List<AgentMessage>
}