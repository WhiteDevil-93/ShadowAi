package com.shadowai.app.agent

import android.content.Context
import android.util.Log
import com.shadowai.app.execution.DeviceAction
import com.shadowai.app.execution.DeviceActionExecutor
import com.shadowai.app.execution.TaskExecutor
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.routing.RoutingDecision
import com.shadowai.app.routing.RoutingEngine
import com.shadowai.app.routing.RoutingPolicy
import com.shadowai.app.tasks.Plan
import com.shadowai.app.tasks.PlanParser
import com.shadowai.app.tasks.TaskType
// REPOSITORY ADAPTER CLEANUP: Removed ProviderRepository facade dependency
import com.shadowai.app.ai.TokenCounter
import com.shadowai.core.security.PromptInjectionDefense
import com.shadowai.pipelineplanner.PipelineExecutor
import com.shadowai.pipelineplanner.PipelinePlanner
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupervisorAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowAgent: Lazy<ShadowAgent>,
    private val taskExecutor: TaskExecutor,
    private val routingEngine: RoutingEngine,
    private val providerSelector: ProviderSelector,
    // REPOSITORY ADAPTER CLEANUP: Removed ProviderRepository facade - using split repositories via ProviderSelector
    private val planParser: PlanParser,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val verificationEngine: VerificationEngine,
    private val promptInjectionDefense: PromptInjectionDefense,
    private val pipelinePlanner: PipelinePlanner,
    private val pipelineExecutor: PipelineExecutor,
    private val memorySummarizer: com.shadowai.app.ai.MemorySummarizer,
    private val tokenCounter: TokenCounter
) {
    companion object {
        private const val TAG = "SupervisorAgent"
    }

    private val _activeTasks = MutableStateFlow<Map<String, SupervisedTask>>(emptyMap())
    val activeTasks: StateFlow<Map<String, SupervisedTask>> = _activeTasks.asStateFlow()

    private val _taskResults = MutableStateFlow<Map<String, SupervisorResult>>(emptyMap())
    val taskResults: StateFlow<Map<String, SupervisorResult>> = _taskResults.asStateFlow()

    suspend fun processInput(
        input: String,
        policy: RoutingPolicy = RoutingPolicy.AUTO,
        forcedTaskType: TaskType? = null
    ): SupervisorResult {
        // STEP 1: Detect task type from ORIGINAL input before any sanitization.
        // This ensures jailbreak prompts with TASK prefixes are correctly routed
        // even if the prompt content is flagged for sanitization.
        val taskType = forcedTaskType ?: determineTaskType(input)

        // STEP 2: Security scan of the original input.
        val scanResult = promptInjectionDefense.scan(input)
        if (!scanResult.isSafe) {
            return SupervisorResult.Error(
                error = AgentError(
                    ErrorCategory.SECURITY,
                    scanResult.reason ?: "Prompt injection detected"
                ),
                modelId = null,
                source = null
            )
        }

        // STEP 3: Use detected task type (from original input) for routing.
        val isComplexTask = isComplexTaskType(taskType, input)
        val sanitizedInput = scanResult.sanitizedPrompt

        return if (isComplexTask) {
            val result = executeComplexTask(sanitizedInput, taskType, policy)
            enforcePlanSafety(result)
        } else {
            when (val result = shadowAgent.get().processInput(sanitizedInput)) {
                is AgentResult.Success -> SupervisorResult.Success(
                    output = result.response,
                    modelId = result.modelId,
                    source = result.source,
                    plan = result.plan
                )
                is AgentResult.Conversation -> SupervisorResult.Success(
                    output = result.response,
                    modelId = result.modelId,
                    source = result.source
                )
                is AgentResult.ActionProposed -> SupervisorResult.ActionRequired(
                    action = result.action,
                    modelId = result.modelId,
                    source = result.source,
                    plan = result.plan
                )
                is AgentResult.ConfirmationRequired -> SupervisorResult.ActionRequired(
                    action = result.action,
                    modelId = result.modelId,
                    source = result.source,
                    plan = result.plan
                )
                is AgentResult.Failure -> SupervisorResult.Error(
                    error = result.error,
                    modelId = null,
                    source = null
                )
                else -> SupervisorResult.Error(
                    error = AgentError(ErrorCategory.UNKNOWN, "Unknown result type"),
                    modelId = null,
                    source = null
                )
            }
        }
    }

    /**
     * Final security check on generated plans before returning them to callers.
     */
    private fun enforcePlanSafety(result: SupervisorResult): SupervisorResult {
        val plan = when (result) {
            is SupervisorResult.Success -> result.plan
            is SupervisorResult.ActionRequired -> result.plan
            is SupervisorResult.Error -> null
            is SupervisorResult.BudgetExceeded -> null
        } ?: return result

        val serializedPlan = buildString {
            append("plan version=")
            append(plan.version)
            append("; nodes=")
            append(plan.nodes.size)
            append("; actions=")
            append(plan.nodes.joinToString(separator = " | ") { node ->
                "${node.id}:${node.action}"
            })
        }

        val scan = promptInjectionDefense.scan(serializedPlan)
        if (scan.isSafe) return result

        return SupervisorResult.Error(
            error = AgentError(
                ErrorCategory.SECURITY,
                scan.reason ?: "Generated plan failed security validation"
            ),
            modelId = null,
            source = null
        )
    }

    private suspend fun executeComplexTask(
        input: String,
        taskType: TaskType,
        policy: RoutingPolicy
    ): SupervisorResult {
        val taskId = generateTaskId()
        val agenticLoop = AgenticLoop(
            taskId = taskId,
            taskExecutor = taskExecutor,
            routingEngine = routingEngine,
            providerSelector = providerSelector,
            // REPOSITORY ADAPTER CLEANUP: Removed ProviderRepository facade dependency
            planParser = planParser,
            deviceActionExecutor = deviceActionExecutor,
            verificationEngine = verificationEngine,
            pipelinePlanner = pipelinePlanner,
            pipelineExecutor = pipelineExecutor,
            memorySummarizer = memorySummarizer,
            tokenCounter = tokenCounter
        )

        val supervisedTask = SupervisedTask(
            taskId = taskId,
            input = input,
            taskType = taskType,
            loop = agenticLoop,
            state = SupervisedTaskState.RUNNING
        )
        _activeTasks.update { it + (taskId to supervisedTask) }

        return try {
            val result = agenticLoop.execute(input, taskType, policy)
            _taskResults.update { it + (taskId to result) }
            _activeTasks.update { current ->
                current.toMutableMap().apply {
                    this[taskId] = this[taskId]?.copy(state = SupervisedTaskState.COMPLETED) ?: supervisedTask
                }
            }
            result
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Never swallow CancellationException — re-throw to preserve structured concurrency
            _activeTasks.update { current ->
                current.toMutableMap().apply {
                    this[taskId] = this[taskId]?.copy(
                        state = SupervisedTaskState.FAILED,
                        error = "Cancelled"
                    ) ?: supervisedTask
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Complex task execution failed", e)
            _activeTasks.update { current ->
                current.toMutableMap().apply {
                    this[taskId] = this[taskId]?.copy(
                        state = SupervisedTaskState.FAILED,
                        error = e.message
                    ) ?: supervisedTask
                }
            }
            SupervisorResult.Error(
                error = AgentError(ErrorCategory.EXECUTION, e.message ?: "Execution failed"),
                modelId = null,
                source = null
            )
        }
    }

    suspend fun executeConfirmedAction(
        taskId: String,
        action: DeviceAction
    ): Result<String> {
        return try {
            verificationEngine.verifyAction(action).let { verificationResult ->
                when (verificationResult) {
                    is VerificationEngine.VerificationResult.Passed -> {
                        deviceActionExecutor.execute(action)
                    }
                    is VerificationEngine.VerificationResult.PreconditionMissing -> {
                        Result.failure(ActionBlockedException(verificationResult.message))
                    }
                    is VerificationEngine.VerificationResult.PolicyViolation -> {
                        Result.failure(ActionBlockedException(verificationResult.message))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action execution failed", e)
            Result.failure(e)
        }
    }

    fun isTaskActive(taskId: String): Boolean {
        return _activeTasks.value[taskId]?.state == SupervisedTaskState.RUNNING
    }

    fun getTaskState(taskId: String): SupervisedTaskState {
        return _activeTasks.value[taskId]?.state ?: SupervisedTaskState.UNKNOWN
    }

    suspend fun cancelTask(taskId: String) {
        _activeTasks.value[taskId]?.let { task ->
            task.loop.cancel()
            _activeTasks.update { current ->
                current.toMutableMap().apply {
                    this[taskId] = task.copy(state = SupervisedTaskState.CANCELLED)
                }
            }
        }
    }

    fun clearCompletedTasks() {
        _activeTasks.update { current ->
            current.filterValues { it.state == SupervisedTaskState.RUNNING }
        }
    }

    private fun determineTaskType(input: String): TaskType {
        val lowerInput = input.lowercase()
        return when {
            lowerInput.contains("call") || lowerInput.contains("dial") -> TaskType.TELEPHONY
            lowerInput.contains("message") || lowerInput.contains("text") -> TaskType.MESSAGING
            lowerInput.contains("play") || lowerInput.contains("music") -> TaskType.MEDIA_CONTROL
            lowerInput.contains("open") || lowerInput.contains("launch") -> TaskType.SYSTEM_INTERACTION
            lowerInput.contains("generate image") || lowerInput.contains("draw") -> TaskType.IMAGE_GEN
            lowerInput.contains("turn on") || lowerInput.contains("turn off") -> TaskType.DEVICE_CONTROL
            else -> TaskType.CONVERSATION
        }
    }

    private fun isComplexTaskType(taskType: TaskType, input: String): Boolean {
        return when (taskType) {
            TaskType.DEVICE_CONTROL,
            TaskType.TELEPHONY,
            TaskType.MESSAGING,
            TaskType.MEDIA_CONTROL,
            TaskType.SYSTEM_INTERACTION -> true
            TaskType.IMAGE_GEN,
            TaskType.VIDEO_GEN,
            TaskType.AUDIO_GEN -> true
            TaskType.WRITING,
            TaskType.VOCAL,
            TaskType.ANALYSIS,
            TaskType.SYSTEM,
            TaskType.TEXT_GEN,
            TaskType.CODE_GEN,
            TaskType.SEARCH,
            TaskType.CUSTOM -> false
            TaskType.CONVERSATION -> {
                val lowerInput = input.lowercase()
                lowerInput.contains("plan") ||
                lowerInput.contains("schedule") ||
                lowerInput.contains("remind me to") ||
                lowerInput.contains("and then")
            }
        }
    }

    private fun generateTaskId(): String = "supervisor_${UUID.randomUUID()}"
}

enum class SupervisedTaskState {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN
}

data class SupervisedTask(
    val taskId: String,
    val input: String,
    val taskType: TaskType,
    val loop: AgenticLoop,
    val state: SupervisedTaskState,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

sealed class SupervisorResult {
    abstract val modelId: String?
    abstract val source: String?

    data class Success(
        val output: String,
        override val modelId: String?,
        override val source: String?,
        val plan: Plan? = null
    ) : SupervisorResult()

    data class ActionRequired(
        val action: DeviceAction,
        override val modelId: String?,
        override val source: String?,
        val plan: Plan? = null
    ) : SupervisorResult()

    data class Error(
        val error: AgentError,
        override val modelId: String?,
        override val source: String?
    ) : SupervisorResult()

    data class BudgetExceeded(
        val reason: String,
        val partialOutput: String?,
        override val modelId: String?,
        override val source: String?
    ) : SupervisorResult()
}

class ActionBlockedException(message: String) : Exception("Action blocked: $message")
