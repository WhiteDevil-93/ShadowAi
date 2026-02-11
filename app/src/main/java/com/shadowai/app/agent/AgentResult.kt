package com.shadowai.app.agent

import com.shadowai.app.execution.DeviceAction

sealed class AgentResult {
    data class Success(
        val message: String,
        val modelName: String? = null,
        val source: String? = null,
        val plan: com.shadowai.app.tasks.Plan? = null
    ) : AgentResult() {
        val response: String get() = message
        val modelId: String? get() = modelName
    }
    
    data class Failure(val error: AgentError) : AgentResult()
    
    data class ConfirmationRequired(
        val action: DeviceAction,
        val description: String,
        val modelName: String? = null,
        val source: String? = null,
        val plan: com.shadowai.app.tasks.Plan? = null
    ) : AgentResult() {
        val modelId: String? get() = modelName
    }

    data class Conversation(
        val message: String,
        val modelName: String? = null,
        val source: String? = null,
        val plan: com.shadowai.app.tasks.Plan? = null
    ) : AgentResult() {
        val response: String get() = message
        val modelId: String? get() = modelName
    }

    data class ActionProposed(
        val action: DeviceAction,
        val modelName: String? = null,
        val source: String? = null,
        val plan: com.shadowai.app.tasks.Plan? = null
    ) : AgentResult() {
        val modelId: String? get() = modelName
    }
}
