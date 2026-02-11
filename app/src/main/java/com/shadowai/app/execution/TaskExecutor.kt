package com.shadowai.app.execution

import com.shadowai.core.ProviderId

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.models.ModelId
import com.shadowai.app.providers.ApiStyle
import com.shadowai.app.routing.RoutingPolicy
import com.shadowai.app.routing.RoutingDecision
import com.shadowai.app.routing.ExecutionSource
import com.shadowai.app.tasks.Task
import com.shadowai.app.tasks.TaskState
import com.shadowai.app.ai.MemoryConstants
import com.shadowai.app.ai.LocalBrainManager
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.providers.ActiveProviderConfig
import com.shadowai.app.providers.Capability
import com.shadowai.core.providers.ProviderRepository
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.app.providers.LocalRuntimeConfig
import com.shadowai.app.execution.TaskExecutionService
import com.shadowai.app.routing.RoutingEngine
import com.shadowai.diagnostics.ErrorCollector
import com.shadowai.diagnostics.ErrorContext
import com.shadowai.diagnostics.ErrorContextStore
import com.shadowai.diagnostics.PipelineError
import kotlinx.coroutines.*
import android.content.Context
import android.app.ActivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import com.shadowai.app.tasks.TaskType

interface TaskExecutor {
    suspend fun execute(task: Task, policy: RoutingPolicy): ExecutionResult
}

/**
 * Configuration for retry behavior
 */
data class RetryPolicy(
    val maxRetries: Int = 2,
    val initialBackoffMs: Long = 100,
    val maxBackoffMs: Long = 5000,
    val backoffMultiplier: Double = 2.0
)

@Singleton
class DefaultTaskExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routingEngine: RoutingEngine,
    private val brainManager: LocalBrainManager,
    private val hybridExecutor: HybridAiExecutor,
    private val adminRepo: AdminRepository,
    private val providerSelector: ProviderSelector,
    private val providerRepository: ProviderRepository,
    private val taskExecutionService: TaskExecutionService,
    private val errorCollector: ErrorCollector,
    private val errorContextStore: ErrorContextStore,
    internal val piiMaskingProcessor: PiiMaskingProcessor
) : TaskExecutor {
    companion object {
        private const val TAG = "TaskExecutor"
        // Default model size: 1.2GB (configurable via LocalRuntimeConfig)
        private const val DEFAULT_LIQUID_MODEL_SIZE_GB = 1.2
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
        private const val LIQUID_MIN_FREE_RAM_BYTES = 500L * 1024L * 1024L // 500MB minimum

        fun getLiquidModelSizeBytes(config: LocalRuntimeConfig? = null): Long {
            val sizeGb = config?.modelSizeGb ?: DEFAULT_LIQUID_MODEL_SIZE_GB
            return (sizeGb * BYTES_PER_GB).toLong()
        }
    }

    // Configurable retry policy
    private val retryPolicyRef = AtomicReference(RetryPolicy())
    var retryPolicy: RetryPolicy
        get() = retryPolicyRef.get()
        set(value) {
            retryPolicyRef.set(value)
        }

    // Thread-safe random for jitter generation
    private val random = Random(System.nanoTime())

    // Circuit breaker for task execution with exponential backoff
    private val circuitBreaker = CircuitBreaker(
        failureThreshold = 5,  // Open after 5 failures
        successThreshold = 2,   // Close after 2 successes in half-open state
        timeoutMs = 30000       // Try again after 30 seconds
    )

    override suspend fun execute(task: Task, policy: RoutingPolicy): ExecutionResult {
        val executionTimestamp = System.currentTimeMillis()
        val routingDecision = routingEngine.determineRouting(task, policy)

        // Configure brain manager with active provider before execution
        val activeConfig = selectActiveProviderConfig(task, routingDecision)

        // Determine model ID from actual provider config
        val modelId = if (activeConfig != null) {
            ModelId(activeConfig.modelId)
        } else {
            // Placeholder for error reporting when no provider available
            when (routingDecision.selectedSource) {
                ExecutionSource.LOCAL -> ModelId("local-unavailable")
                ExecutionSource.CLOUD -> ModelId("cloud-unavailable")
                else -> ModelId("unavailable")
            }
        }

        if (activeConfig == null) {
            val errorMsg = when (routingDecision.selectedSource) {
                ExecutionSource.LOCAL -> {
                    if (task.type == com.shadowai.app.tasks.TaskType.IMAGE_GEN || task.type == com.shadowai.app.tasks.TaskType.VIDEO_GEN || task.type == com.shadowai.app.tasks.TaskType.AUDIO_GEN) {
                        "No local image/media provider is configured. Enable a local media provider or use a cloud image provider."
                    } else {
                        "No local AI provider is configured. Place GGUF models in Download folder or enable a local text provider."
                    }
                }
                ExecutionSource.CLOUD -> "No cloud AI provider is configured. Add an API key in Settings → Providers."
                else -> "No AI provider (Local or Cloud) is configured or enabled. Please check settings."
            }
            recordProviderError(
                message = errorMsg,
                providerId = routingDecision.selectedSource.name.lowercase(),
                taskId = task.id.id,
                modelId = modelId.id,
                routingDecision = routingDecision
            )
            return createFailureResult(
                task,
                errorMsg,
                executionTimestamp,
                routingDecision,
                modelId
            )
        }

        brainManager.applyConfig(activeConfig)

        // HARD ASSERTION: Verify LIQUID provider is routed correctly
        require(activeConfig.providerId != ProviderId.LIQUID ||
                activeConfig.apiStyle == ApiStyle.LIQUID) {
            "CRITICAL: LIQUID provider routed to wrong ApiStyle: ${activeConfig.apiStyle}. Expected: ApiStyle.LIQUID"
        }

        // Check if this is a LOCAL provider - bypass retries for deterministic local failures
        val isLocalProvider = activeConfig?.apiStyle == ApiStyle.LIQUID ||
                              activeConfig?.apiStyle == ApiStyle.LOCAL_TEXT ||
                              activeConfig?.apiStyle == ApiStyle.LOCAL_IMAGE

        // Mask PII for cloud/external providers before execution
        val processedTask = if (!isLocalProvider) {
            maskPiiInTask(task)
        } else {
            task
        }

        android.util.Log.d(TAG, "Executing task with provider: ${activeConfig.providerId}, apiStyle: ${activeConfig.apiStyle}, isLocal: $isLocalProvider")

        return if (isLocalProvider) {
            // LOCAL providers: Execute directly without retries - deterministic failures should NOT retry
            android.util.Log.d(TAG, "LOCAL provider detected - bypassing retry logic")
            executeLocalProvider(processedTask, routingDecision, modelId, executionTimestamp, activeConfig)
        } else {
            // CLOUD providers: Use circuit breaker and retry logic
            circuitBreaker.execute {
                executeWithRetry(processedTask, routingDecision, modelId, executionTimestamp)
            }
        }
    }

    private suspend fun executeWithRetry(
        task: Task,
        routingDecision: RoutingDecision,
        modelId: ModelId,
        executionTimestamp: Long
    ): ExecutionResult {
        val policy = retryPolicyRef.get()
        val maxRetries = policy.maxRetries
        var attempt = 0

        while (attempt <= maxRetries) {
            attempt++
            val result = attemptExecution(task, routingDecision, modelId)

            if (result.isSuccess) {
                return result.getOrThrow()
            }

            val error = result.exceptionOrNull()
            if (error is ProviderQuotaException) {
                return handleQuotaFailure(error, task, routingDecision, modelId, executionTimestamp)
            }

            // Check if error is retriable before retrying
            val isRetriable = isRetriableError(error)
            if (!isRetriable) {
                // Non-retriable error - fail immediately
                recordProviderError(
                    message = "Non-retriable error: ${error?.message}",
                    providerId = routingDecision.selectedSource.name,
                    taskId = task.id.id,
                    modelId = modelId.id,
                    routingDecision = routingDecision,
                    cause = error
                )
                return createFailureResult(task, error?.message ?: "Execution failed", executionTimestamp, routingDecision, modelId, isRecoverable = false)
            }

            if (attempt > maxRetries) {
                recordProviderError(
                    message = "Execution failed after $maxRetries retries",
                    providerId = routingDecision.selectedSource.name,
                    taskId = task.id.id,
                    modelId = modelId.id,
                    routingDecision = routingDecision
                )
                return createFailureResult(task, "Execution failed after $maxRetries retries", executionTimestamp, routingDecision, modelId)
            }

            delay(calculateBackoff(attempt, policy))
        }

        recordProviderError(
            message = "Execution failed",
            providerId = routingDecision.selectedSource.name,
            taskId = task.id.id,
            modelId = modelId.id,
            routingDecision = routingDecision
        )
        return createFailureResult(task, "Execution failed", executionTimestamp, routingDecision, modelId)
    }

    private fun isRetriableError(error: Throwable?): Boolean {
        if (error == null) return true

        return when (error) {
            is SocketTimeoutException -> true
            is ConnectException -> true
            is java.io.IOException -> true // Network errors
            is TimeoutException -> true
            else -> false
        }
    }

    private suspend fun handleQuotaFailure(
        error: ProviderQuotaException,
        task: Task,
        routingDecision: RoutingDecision,
        modelId: ModelId,
        executionTimestamp: Long
    ): ExecutionResult {
        val providerId = error.providerId
        if (providerId != null) {
            disableProvider(providerId)
        }

        recordProviderError(
            message = "Provider quota/billing failure: ${error.message}",
            providerId = providerId?.name ?: "unknown",
            taskId = task.id.id,
            modelId = modelId.id,
            routingDecision = routingDecision,
            cause = error
        )

        val cloudFallbacks = providerSelector.getCloudProviders(task.type)
            .filter { providerId == null || it.providerId != providerId }

        if (cloudFallbacks.isNotEmpty()) {
            val fallbackDecision = routingDecision.copy(
                selectedSource = ExecutionSource.CLOUD,
                reason = "Cloud provider out of credits. Switching to alternate cloud provider.",
                overrideSource = "QuotaFallback"
            )
            for (config in cloudFallbacks) {
                brainManager.applyConfig(config)
                val attempt = attemptExecution(task, fallbackDecision, ModelId(config.modelId))
                if (attempt.isSuccess) return attempt.getOrThrow()
                val attemptError = attempt.exceptionOrNull()
                if (attemptError is ProviderQuotaException) {
                    attemptError.providerId?.let { disableProvider(it) }
                    continue
                }
                break
            }
        }

        val localConfig = providerSelector.nextLocal(task.type)
        if (localConfig != null) {
            brainManager.applyConfig(localConfig)
            val localDecision = routingDecision.copy(
                selectedSource = ExecutionSource.LOCAL,
                reason = "Cloud provider out of credits. Falling back to local execution.",
                overrideSource = "QuotaFallback"
            )
            return executeLocalProvider(task, localDecision, ModelId(localConfig.modelId), executionTimestamp, localConfig)
        }

        return createFailureResult(
            task,
            error.message ?: "Provider out of credits and no fallback provider is available.",
            executionTimestamp,
            routingDecision,
            modelId
        )
    }

    private suspend fun executeLocalProvider(
        task: Task,
        routingDecision: RoutingDecision,
        modelId: ModelId,
        executionTimestamp: Long,
        activeConfig: ActiveProviderConfig
    ): ExecutionResult {
        if (activeConfig.apiStyle == ApiStyle.LIQUID) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val minRequired = LIQUID_MIN_FREE_RAM_BYTES
            if (memInfo.availMem < minRequired) {
                return createFailureResult(
                    task,
                    "Insufficient RAM to launch Liquid AI. Available: ${memInfo.availMem / 1024 / 1024}MB, Required: ${minRequired / 1024 / 1024}MB+",
                    executionTimestamp,
                    routingDecision,
                    modelId
                )
            }
        }

        return try {
            val output = hybridExecutor.execute(task)
            createSuccessResult(task, output, routingDecision, modelId)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "LOCAL execution failed (no retry): ${e.message}", e)
            recordProviderError(
                message = e.message ?: "Local execution failed",
                providerId = activeConfig.providerId.name,
                taskId = task.id.id,
                modelId = modelId.id,
                routingDecision = routingDecision,
                cause = e
            )
            createFailureResult(task, e.message ?: "Local execution failed", executionTimestamp, routingDecision, modelId)
        }
    }

    private suspend fun disableProvider(providerId: ProviderId) {
        withContext(Dispatchers.IO) {
            providerRepository.setProviderEnabled(providerId, false)
        }
    }

    private suspend fun attemptExecution(
        task: Task,
        routingDecision: RoutingDecision,
        modelId: ModelId
    ): Result<ExecutionResult> = runCatching {
        try {
            withTimeout(task.budget.timeoutMs.milliseconds) {
                val output = hybridExecutor.execute(task)
                createSuccessResult(task, output, routingDecision, modelId)
            }
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Caught throwable during execution for task ${task.id}: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }

    /**
     * Calculate exponential backoff delay with jitter.
     * Base delay: 1 second, doubles each retry attempt.
     * Uses kotlin.random.Random for thread-safe jitter generation.
     */
    private fun calculateBackoff(attempt: Int, policy: RetryPolicy): Long {
        val baseDelay = policy.initialBackoffMs
        val maxDelay = policy.maxBackoffMs
        val exponentialDelay = (baseDelay * Math.pow(policy.backoffMultiplier, (attempt - 1).toDouble())).toLong()
        val jitter = (random.nextDouble() * exponentialDelay * 0.3).toLong() // 30% jitter
        return minOf(exponentialDelay + jitter, maxDelay)
    }

    private suspend fun selectActiveProviderConfig(
        task: Task,
        routingDecision: RoutingDecision
    ): ActiveProviderConfig? {
        return when (routingDecision.selectedSource) {
            ExecutionSource.LOCAL -> providerSelector.nextLocal(task.type) ?: providerSelector.nextCloud(task.type)
            ExecutionSource.CLOUD -> providerSelector.nextCloud(task.type) ?: providerSelector.nextLocal(task.type)
        }
    }

    /**
     * Determines the set of capabilities required for a given task type.
     */
    private fun getCapabilitiesForTaskType(taskType: TaskType): Set<Capability> {
        return when (taskType) {
            TaskType.CONVERSATION, TaskType.WRITING, TaskType.VOCAL, TaskType.TEXT_GEN -> setOf(Capability.TEXT)
            TaskType.IMAGE_GEN -> setOf(Capability.IMAGE_GEN)
            TaskType.VIDEO_GEN -> setOf(Capability.VISION)
            TaskType.AUDIO_GEN -> setOf(Capability.VOICE)
            TaskType.DEVICE_CONTROL, TaskType.TELEPHONY, TaskType.MESSAGING, TaskType.SYSTEM_INTERACTION -> setOf(Capability.FUNCTION_CALLS)
            else -> emptySet()
        }
    }

    private fun recordProviderError(
        message: String,
        providerId: String,
        taskId: String,
        modelId: String,
        routingDecision: RoutingDecision,
        cause: Throwable? = null
    ) {
        val context = ErrorContext(
            taskId = taskId,
            providerId = providerId,
            modelId = modelId,
            routingSource = routingDecision.selectedSource.name,
            metadata = mapOf(
                "policy" to routingDecision.policy.name,
                "reason" to routingDecision.reason,
                "overrideSource" to (routingDecision.overrideSource ?: "")
            )
        )
        errorContextStore.update(context)
        errorCollector.record(
            PipelineError.ProviderError(
                message = message,
                providerId = providerId,
                context = context,
                cause = cause
            )
        )
    }

    private fun maskPiiInTask(task: Task): Task {
        val maskedInput = piiMaskingProcessor.maskPii(task.input)
        return task.copy(input = maskedInput)
    }

    /**
     * Executes a task using the TaskExecutionService for AI model selection and execution.
     * This method provides a streamlined execution path with proper model selection.
     */
    suspend fun executeTask(
        task: Task,
        routingDecision: RoutingDecision,
        providerConfig: ActiveProviderConfig
    ): String {
        return taskExecutionService.executeTask(task, routingDecision, providerConfig)
    }

    private fun createSuccessResult(
        task: Task,
        output: String,
        routingDecision: RoutingDecision,
        modelId: ModelId
    ): ExecutionResult {
        // Mask PII in AI response before completing the task
        val maskedOutput = piiMaskingProcessor.maskPii(output)
        val completedTask = task.copy(
            currentState = TaskState.Completed(output = maskedOutput, timestamp = System.currentTimeMillis())
        )
        return ExecutionResult(task = completedTask, routingDecision = routingDecision, modelId = modelId)
    }

    private fun createFailureResult(
        task: Task,
        reason: String,
        timestamp: Long,
        routingDecision: RoutingDecision,
        modelId: ModelId,
        isRecoverable: Boolean = true
    ): ExecutionResult {
        val failedTask = task.copy(
            currentState = TaskState.Failed(reason = reason, timestamp = timestamp, isRecoverable = isRecoverable)
        )
        return ExecutionResult(task = failedTask, routingDecision = routingDecision, modelId = modelId)
    }
}
