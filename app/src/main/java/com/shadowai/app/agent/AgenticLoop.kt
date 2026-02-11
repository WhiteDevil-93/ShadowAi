package com.shadowai.app.agent

import android.util.Log
import com.shadowai.app.ai.TokenCounter
import com.shadowai.app.execution.DeviceAction
import com.shadowai.app.execution.DeviceActionExecutor
import com.shadowai.app.execution.TaskExecutor
import com.shadowai.app.providers.ProviderRepository
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.routing.ExecutionSource
import com.shadowai.app.routing.RoutingDecision
import com.shadowai.app.routing.RoutingEngine
import com.shadowai.app.routing.RoutingPolicy
import com.shadowai.app.tasks.NodeStatus
import com.shadowai.app.tasks.Plan
import com.shadowai.app.tasks.PlanNode
import com.shadowai.app.tasks.PlanParser
import com.shadowai.app.tasks.TaskType
import com.shadowai.pipelineplanner.PipelineExecutor
import com.shadowai.pipelineplanner.PipelinePlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Agentic execution loop for iterative task processing with state management.
 *
 * The AgenticLoop manages the lifecycle of multi-step AI tasks including:
 * - Iterative execution with context preservation
 * - State tracking across execution stages
 * - Termination condition handling (max iterations, success criteria, failures)
 * - Dependency resolution between plan nodes
 * - Partial failure recovery (retry node vs abort plan)
 * - Budget enforcement (time, tokens, iterations)
 * - Coordination with TaskExecutor for each execution step
 *
 * ## State Machine
 * ```
 * INITIALIZING → PLANNING → EXECUTING → [ITERATING] → COMPLETED
 *                    ↓           ↓
 *              FAILED      [RETRY_NODE]
 *                    ↓           ↓
 *              RETRY_PLAN → ABORT
 * ```
 *
 * ## Termination Conditions
 * - **Success**: All plan nodes completed successfully
 * - **Max Iterations**: Iteration limit reached (default: 10)
 * - **Budget Exhausted**: Time or token budget exceeded
 * - **Unrecoverable Error**: Node failed with no retry path
 * - **Cancelled**: External cancellation signal
 *
 * @see SupervisorAgent High-level orchestration using this loop
 * @see TaskExecutor Delegates individual task execution
 */
class AgenticLoop(
    private val taskId: String,
    private val taskExecutor: TaskExecutor,
    private val routingEngine: RoutingEngine,
    private val providerSelector: ProviderSelector,
    private val providerRepository: ProviderRepository,
    private val planParser: PlanParser,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val verificationEngine: VerificationEngine,
    private val pipelinePlanner: PipelinePlanner,
    private val pipelineExecutor: PipelineExecutor,
    private val memorySummarizer: com.shadowai.app.ai.MemorySummarizer,
    private val tokenCounter: TokenCounter
) {
    companion object {
        private const val TAG = "AgenticLoop"
        private const val DEFAULT_MAX_ITERATIONS = 10
        private const val DEFAULT_TIMEOUT_MS = 120000L
        private const val MAX_NODE_RETRIES = 2
    }

    private val _state = MutableAgenticState(AgenticState.INITIALIZING)
    val state: AgenticState get() = _state.value

    private val _currentPlan = MutableAgenticState<Plan?>(null)
    val currentPlan: Plan? get() = _currentPlan.value

    private val _executedNodes = MutableAgenticState<List<ExecutedNode>>(emptyList())
    val executedNodes: List<ExecutedNode> get() = _executedNodes.value

    private val _currentIteration = AtomicInteger(0)
    val currentIteration: Int get() = _currentIteration.get()

    private val _isCancelled = AtomicBoolean(false)
    val isCancelled: Boolean get() = _isCancelled.get()

    private val _accumulatedContext = StringBuilder()
    private val completionSignal = CompletableDeferred<SupervisorResult>()

    /**
     * Configuration for loop execution.
     */
    data class LoopConfig(
        val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        val maxNodeRetries: Int = MAX_NODE_RETRIES,
        val enablePipelinePlanner: Boolean = true,
        val autoConfirmSafeActions: Boolean = false,
        val slidingWindowSize: Int = 5,
        val contextThreshold: Float = 0.9f
    )

    /**
     * Executes a task through the full agentic loop.
     *
     * @param input The user input to process
     * @param taskType The detected task type
     * @param policy Routing policy for execution
     * @param config Loop configuration options
     * @return Supervisor result with execution outcome
     */
    suspend fun execute(
        input: String,
        taskType: TaskType,
        policy: RoutingPolicy = RoutingPolicy.AUTO,
        config: LoopConfig = LoopConfig()
    ): SupervisorResult {
        val startTime = System.currentTimeMillis()
        _state.value = AgenticState.INITIALIZING
        _currentIteration.set(0)

        return try {
            // Phase 1: Planning
            _state.value = AgenticState.PLANNING
            val plan = createOrParsePlan(input, taskType)
            _currentPlan.value = plan

            if (plan.nodes.isEmpty()) {
                return SupervisorResult.Success(
                    output = "No actions required for this request.",
                    modelId = null,
                    source = null
                )
            }

            // Phase 2: Execution
            _state.value = AgenticState.EXECUTING
            val result = executePlan(plan, input, taskType, policy, config, startTime)

            _state.value = when (result) {
                is SupervisorResult.Success -> AgenticState.COMPLETED
                is SupervisorResult.ActionRequired -> AgenticState.WAITING_CONFIRMATION
                is SupervisorResult.Error -> AgenticState.FAILED
                is SupervisorResult.BudgetExceeded -> AgenticState.BUDGET_EXHAUSTED
            }

            result

        } catch (e: CancellationException) {
            _state.value = AgenticState.CANCELLED
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agentic loop failed", e)
            _state.value = AgenticState.FAILED
            SupervisorResult.Error(
                error = AgentError(ErrorCategory.EXECUTION, e.message ?: "Unknown error"),
                modelId = null,
                source = null
            )
        }
    }

    /**
     * Cancels the running agentic loop.
     */
    fun cancel() {
        _isCancelled.set(true)
        completionSignal.cancel()
    }

    /**
     * Creates a plan from input or parses an existing plan structure.
     */
    private suspend fun createOrParsePlan(
        input: String,
        taskType: TaskType
    ): Plan {
        val planResult = planParser.parse(input)

        return if (planResult.isSuccess) {
            planResult.getOrThrow()
        } else {
            // Fallback: Create a simple single-step plan
            Plan(
                nodes = listOf(
                    PlanNode(
                        id = "single_step",
                        action = createSimpleAction(input, taskType),
                        status = NodeStatus.PENDING,
                        dependencies = emptyList()
                    )
                )
            )
        }
    }

    /**
     * Executes a plan with dependency resolution and iteration management.
     */
    private suspend fun executePlan(
        plan: Plan,
        originalInput: String,
        taskType: TaskType,
        policy: RoutingPolicy,
        config: LoopConfig,
        startTime: Long
    ): SupervisorResult {
        val remainingNodes = plan.nodes.filter { it.status == NodeStatus.PENDING }.toMutableList()
        val completedNodes = mutableListOf<PlanNode>()
        val failedNodes = mutableListOf<FailedNode>()
        val executionContext = StringBuilder(originalInput)

        while (remainingNodes.isNotEmpty()) {
            // Check termination conditions
            if (_isCancelled.get()) {
                throw CancellationException("Agentic loop cancelled")
            }

            if (_currentIteration.incrementAndGet() > config.maxIterations) {
                return SupervisorResult.BudgetExceeded(
                    reason = "Maximum iterations (${config.maxIterations}) reached",
                    partialOutput = executionContext.toString(),
                    modelId = null,
                    source = null
                )
            }

            if (System.currentTimeMillis() - startTime > config.timeoutMs) {
                return SupervisorResult.BudgetExceeded(
                    reason = "Timeout exceeded (${config.timeoutMs}ms)",
                    partialOutput = executionContext.toString(),
                    modelId = null,
                    source = null
                )
            }

            // Find next executable node (dependencies satisfied)
            val nextNode = remainingNodes.firstOrNull { node ->
                node.dependencies.all { depId ->
                    completedNodes.any { it.id == depId }
                }
            } ?: run {
                // No executable nodes - check for dependency failure
                val blockedNodes = remainingNodes.filter { node ->
                    node.dependencies.any { depId ->
                        failedNodes.any { it.node.id == depId }
                    }
                }
                return if (blockedNodes.isNotEmpty()) {
                    SupervisorResult.Error(
                        error = AgentError(
                            ErrorCategory.EXECUTION,
                            "Plan execution blocked: ${blockedNodes.size} nodes have failed dependencies"
                        ),
                        modelId = null,
                        source = null
                    )
                } else {
                    SupervisorResult.Error(
                        error = AgentError(
                            ErrorCategory.EXECUTION,
                            "Dependency resolution failed: circular or unsatisfied dependencies"
                        ),
                        modelId = null,
                        source = null
                    )
                }
            }

            // Execute the node
            val nodeResult = executeNode(nextNode, executionContext.toString(), policy, config)

            when (nodeResult) {
                is NodeExecutionResult.Success -> {
                    completedNodes.add(nextNode.copy(status = NodeStatus.COMPLETED))
                    remainingNodes.remove(nextNode)
                    executionContext.append("\n[Step ${nextNode.id}]: ${nodeResult.output}")
                    _accumulatedContext.append(nodeResult.output)

                    // Phase 5.3: Sliding Window Context Management
                    // Truncate context if it gets too large to fit in model window
                    val currentContextString = executionContext.toString()
                    val currentTokenCount = tokenCounter.countTokens(currentContextString)

                    // Default context window - can be enhanced to use model-specific limits
                    val maxContext = 4096
                    val thresholdTokens = (maxContext * config.contextThreshold).toInt()

                    if (currentTokenCount > thresholdTokens) {
                        // Summarize discarded nodes before removing them from context
                        val discardedNodes = completedNodes.dropLast(config.slidingWindowSize)
                        if (discardedNodes.isNotEmpty()) {
                            val discardedContext = discardedNodes.joinToString("\n") { node ->
                                val en = _executedNodes.value.find { it.nodeId == node.id }
                                if (en != null) "[Step ${node.id}]: ${en.output}" else ""
                            }
                            if (discardedContext.isNotBlank()) {
                                memorySummarizer.summarizeAndStore(discardedContext, "task_execution_${taskId}")
                            }
                        }

                        // Rebuild context from original input + last N steps
                        val newContext = StringBuilder(originalInput)
                        val windowNodes = completedNodes.takeLast(config.slidingWindowSize)
                        windowNodes.forEach { node ->
                            val executedNode = _executedNodes.value.find { it.nodeId == node.id }
                            if (executedNode != null) {
                                newContext.append("\n[Step ${node.id}]: ${executedNode.output}")
                            }
                        }

                        // Replace executionContext content
                        executionContext.clear()
                        executionContext.append(newContext)

                        // Log truncation for debugging
                        val newTokenCount = tokenCounter.countTokens(executionContext.toString())
                        Log.d(TAG, "Context truncated from $currentTokenCount to $newTokenCount tokens " +
                                 "(threshold: ${config.contextThreshold * 100}%, maxContext: $maxContext)")
                    }

                    _executedNodes.value += ExecutedNode(
                        nodeId = nextNode.id,
                        success = true,
                        output = nodeResult.output,
                        timestamp = System.currentTimeMillis()
                    )
                }
                is NodeExecutionResult.Failure -> {
                    // Check retry count
                    val retryCount = failedNodes.count { it.node.id == nextNode.id }
                    if (retryCount < config.maxNodeRetries) {
                        Log.w(TAG, "Node ${nextNode.id} failed, scheduling retry (${retryCount + 1}/${config.maxNodeRetries})")
                        failedNodes.add(FailedNode(nextNode, nodeResult.error, retryCount + 1))
                        // Keep node in remaining for retry
                    } else {
                        // Max retries exceeded
                        return SupervisorResult.Error(
                            error = AgentError(
                                ErrorCategory.EXECUTION,
                                "Node ${nextNode.id} failed after ${config.maxNodeRetries} retries: ${nodeResult.error}"
                            ),
                            modelId = null,
                            source = null
                        )
                    }
                }
                is NodeExecutionResult.ActionRequired -> {
                    // Return to user for confirmation
                    return SupervisorResult.ActionRequired(
                        action = nodeResult.action,
                        modelId = nodeResult.modelId,
                        source = nodeResult.source,
                        plan = plan
                    )
                }
            }
        }

        // All nodes completed successfully
        return SupervisorResult.Success(
            output = executionContext.toString(),
            modelId = null,
            source = null,
            plan = plan.copy(nodes = completedNodes)
        )
    }

    /**
     * Executes a single plan node.
     */
    private suspend fun executeNode(
        node: PlanNode,
        context: String,
        policy: RoutingPolicy,
        config: LoopConfig
    ): NodeExecutionResult {
        return try {
            // For device actions, verify before executing
            val action = node.action
            val verificationResult = verificationEngine.verifyAction(action)

            when (verificationResult) {
                is VerificationEngine.VerificationResult.Passed -> {
                    if (config.autoConfirmSafeActions && isActionSafe(action)) {
                        // Auto-execute safe actions
                        val result = deviceActionExecutor.execute(action)
                        if (result.isSuccess) {
                            NodeExecutionResult.Success(result.getOrThrow())
                        } else {
                            NodeExecutionResult.Failure(result.exceptionOrNull()?.message ?: "Action failed")
                        }
                    } else {
                        // Return for user confirmation
                        NodeExecutionResult.ActionRequired(
                            action = action,
                            modelId = null,
                            source = null
                        )
                    }
                }
                is VerificationEngine.VerificationResult.PreconditionMissing -> {
                    NodeExecutionResult.Failure("Precondition missing: ${verificationResult.message}")
                }
                is VerificationEngine.VerificationResult.PolicyViolation -> {
                    NodeExecutionResult.Failure("Policy violation: ${verificationResult.message}")
                }
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failure(e.message ?: "Node execution failed")
        }
    }

    private fun isActionSafe(action: DeviceAction): Boolean {
        // Define criteria for "safe" actions that can be auto-confirmed
        // This is a simplified check - real implementation would be more comprehensive
        return when (action) {
            is DeviceAction.AppLaunch -> true  // Opening apps is generally safe
            is DeviceAction.Browse -> !action.query.contains("://") || action.query.startsWith("https://")
            else -> false  // Other actions require explicit confirmation
        }
    }

    private fun createSimpleAction(input: String, taskType: TaskType): DeviceAction {
        // Create a simple device action from input and task type
        return when (taskType) {
            TaskType.SYSTEM_INTERACTION -> DeviceAction.AppLaunch(input)
            else -> DeviceAction.Browse(input)
        }
    }

    /**
     * Sealed class representing results of node execution.
     */
    private sealed class NodeExecutionResult {
        data class Success(val output: String) : NodeExecutionResult()
        data class Failure(val error: String) : NodeExecutionResult()
        data class ActionRequired(
            val action: DeviceAction,
            val modelId: String?,
            val source: String?
        ) : NodeExecutionResult()
    }
}

/**
 * States in the agentic execution lifecycle.
 */
enum class AgenticState {
    INITIALIZING,
    PLANNING,
    EXECUTING,
    ITERATING,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED,
    BUDGET_EXHAUSTED,
    RETRYING_NODE,
    ABORTED
}

/**
 * Thread-safe mutable state holder using AtomicReference.
 */
private class MutableAgenticState<T>(initialValue: T) {
    private val atomic = AtomicReference(initialValue)

    var value: T
        get() = atomic.get()
        set(newValue) { atomic.set(newValue) }

    fun update(newValue: T) {
        atomic.set(newValue)
    }
}

/**
 * Data class for executed node tracking.
 */
data class ExecutedNode(
    val nodeId: String,
    val success: Boolean,
    val output: String,
    val timestamp: Long
)

/**
 * Data class for failed node tracking.
 */
private data class FailedNode(
    val node: PlanNode,
    val error: String,
    val retryCount: Int
)
