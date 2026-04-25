package com.shadowai.app.execution

import android.util.Log
import com.shadowai.core.ProviderId

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.models.ModelId
import com.shadowai.core.providers.ApiStyle
import com.shadowai.app.routing.RoutingPolicy
import com.shadowai.app.routing.RoutingDecision
import com.shadowai.app.routing.ExecutionSource
import com.shadowai.app.tasks.Task
import com.shadowai.app.tasks.TaskState
import com.shadowai.app.ai.MemoryConstants
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.Capability
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.app.providers.LocalRuntimeConfig
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
// M-3: HTTP 408 retry codes - import NetworkException for 408 handling
import com.shadowai.app.exceptions.NetworkException
import com.shadowai.app.security.ApiKeyRedaction

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
    private val hybridExecutor: HybridAiExecutor,
    private val adminRepo: AdminRepository,
    private val providerSelector: ProviderSelector,
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

        // Select active provider configuration
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

        // L-4: Removed brainManager.applyConfig() - ProviderSelector now handles provider configuration
        // The active config is passed directly to execution methods

        // HARD ASSERTION: Verify LIQUID provider is routed correctly
        require(activeConfig.providerId != ProviderId.LIQUID ||
                activeConfig.apiStyle == ApiStyle.LIQUID) {
            "CRITICAL: LIQUID provider routed to wrong ApiStyle: ${activeConfig.apiStyle}. Expected: ApiStyle.LIQUID"
        }

        // Check if this is a LOCAL provider - bypass retries for deterministic local failures
        val isLocalProvider = activeConfig.apiStyle == ApiStyle.LIQUID ||
                              activeConfig.apiStyle == ApiStyle.LOCAL_TEXT ||
                              activeConfig.apiStyle == ApiStyle.LOCAL_IMAGE

        // Mask PII for cloud/external providers before execution
        val processedTask = if (!isLocalProvider) {
            maskPiiInTask(task)
        } else {
            task
        }

        Log.d(TAG, "Executing task with provider: ${activeConfig.providerId}, apiStyle: ${activeConfig.apiStyle}, isLocal: $isLocalProvider")

        return if (isLocalProvider) {
            // LOCAL providers: Execute directly without retries - deterministic failures should NOT retry
            Log.d(TAG, "LOCAL provider detected - bypassing retry logic")
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

    /** M-3: HTTP 408 retry codes - added 408 as retriable
     *  HTTP 408 (Request Timeout) is now properly flagged as a retriable error,
     *  allowing automatic retry with the configured exponential backoff.
     *  This handles servers that close idle connections before the client times out.
     */
    private fun isRetriableError(error: Throwable?): Boolean {
        if (error == null) return true

        return when (error) {
            is SocketTimeoutException -> true
            is ConnectException -> true
            is java.io.IOException -> true // Network errors
            is TimeoutException -> true
            // M-3: 408 Request Timeout is retriable - server closed connection, can retry
            is NetworkException.ServerError -> error.code == 408
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

        // MULTI-PROVIDER FALLBACK LOOP - H-9 Implementation
        return tryMultiProviderFallback(
            task = task,
            originalRoutingDecision = routingDecision,
            failedProviderId = providerId,
            executionTimestamp = executionTimestamp,
            initialError = error
        )
    }

    /**
     * MULTI-PROVIDER FALLBACK LOOP - H-9 Implementation
     *
     * Implements a robust fallback mechanism that iterates through available providers
     * in a prioritized order until a successful execution or all providers are exhausted.
     *
     * Fallback Priority:
     * 1. Other cloud providers (excluding the failed one)
     * 2. Local providers (if applicable for task type)
     * 3. Return failure with aggregated error information
     */
    private suspend fun tryMultiProviderFallback(
        task: Task,
        originalRoutingDecision: RoutingDecision,
        failedProviderId: ProviderId?,
        executionTimestamp: Long,
        initialError: Throwable
    ): ExecutionResult {
        val attemptedProviders = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Track the failed provider
        failedProviderId?.let { attemptedProviders.add(it.name) }
        errors.add("Initial failure: ${initialError.message}")

        // PHASE 1: Try other cloud providers
        android.util.Log.d(TAG, "Fallback Phase 1: Trying alternate cloud providers")
        val cloudFallbacks = providerSelector.getCloudProviders(task.type)
            .filter { config ->
                failedProviderId == null || config.providerId != failedProviderId
            }
            .sortedBy { it.priority } // Prioritize by configured priority

        for (config in cloudFallbacks) {
            if (config.providerId.name in attemptedProviders) continue

            attemptedProviders.add(config.providerId.name)
            android.util.Log.d(TAG, "Trying cloud fallback: ${config.providerId.name}")

            try {
                // L-4: Removed brainManager.applyConfig() - config passed directly to execution
                val fallbackDecision = originalRoutingDecision.copy(
                    selectedSource = ExecutionSource.CLOUD,
                    reason = "Provider ${failedProviderId?.name ?: "unknown"} failed. Trying ${config.providerId.name}.",
                    overrideSource = "MultiProviderFallback"
                )

                val result = executeWithRetryForFallback(task, fallbackDecision, ModelId(config.modelId))
                if (result.isSuccess) {
                    android.util.Log.i(TAG, "Cloud fallback successful: ${config.providerId.name}")
                    return result.getOrThrow()
                }

                val error = result.exceptionOrNull()
                errors.add("${config.providerId.name}: ${error?.message}")

                if (error is ProviderQuotaException) {
                    disableProvider(config.providerId)
                }
            } catch (e: IllegalStateException) {
                errors.add("${config.providerId.name}: ${e.message}")
                android.util.Log.w(TAG, "Cloud fallback failed (illegal state): ${config.providerId.name} - ${e.message}")
            } catch (e: java.io.IOException) {
                errors.add("${config.providerId.name}: ${e.message}")
                android.util.Log.w(TAG, "Cloud fallback failed (IO): ${config.providerId.name} - ${e.message}")
            } catch (e: SecurityException) {
                errors.add("${config.providerId.name}: ${e.message}")
                android.util.Log.w(TAG, "Cloud fallback failed (security): ${config.providerId.name} - ${e.message}")
            }
        }

        // PHASE 2: Try local providers if task type supports local execution
        android.util.Log.d(TAG, "Fallback Phase 2: Trying local providers")
        val localProviders = providerSelector.getLocalProviders(task.type)
            .sortedBy { it.priority }

        for (config in localProviders) {
            if (config.providerId.name in attemptedProviders) continue

            attemptedProviders.add(config.providerId.name)
            android.util.Log.d(TAG, "Trying local fallback: ${config.providerId.name}")

            try {
                // L-4: Removed brainManager.applyConfig() - config passed directly to execution
                val localDecision = originalRoutingDecision.copy(
                    selectedSource = ExecutionSource.LOCAL,
                    reason = "All cloud providers failed. Using local execution.",
                    overrideSource = "MultiProviderFallback"
                )

                val result = executeLocalProvider(
                    task = task,
                    routingDecision = localDecision,
                    modelId = ModelId(config.modelId),
                    executionTimestamp = executionTimestamp,
                    activeConfig = config
                )

                if (result.task.currentState is TaskState.Completed) {
                    android.util.Log.i(TAG, "Local fallback successful: ${config.providerId.name}")
                    return result
                }

                errors.add("${config.providerId.name}: ${(result.task.currentState as? TaskState.Failed)?.reason}")
            } catch (e: IllegalStateException) {
                errors.add("${config.providerId.name}: ${e.message}")
                android.util.Log.w(TAG, "Local fallback failed (illegal state): ${config.providerId.name} - ${e.message}")
            } catch (e: java.io.IOException) {
                errors.add("${config.providerId.name}: ${e.message}")
                android.util.Log.w(TAG, "Local fallback failed (IO): ${config.providerId.name} - ${e.message}")
            } catch (e: SecurityException) {
                errors.add("${config.providerId.name}: ${e.message}")
                android.util.Log.w(TAG, "Local fallback failed (security): ${config.providerId.name} - ${e.message}")
            }
        }

        // PHASE 3: All providers exhausted
        android.util.Log.e(TAG, "All providers exhausted. Attempted: ${attemptedProviders.joinToString()}")

        val aggregatedError = buildString {
            appendLine("All available providers failed:")
            errors.forEach { appendLine("• $it") }
        }

        // M-17: Redact API keys from aggregated error message
        val apiKeyRedaction = ApiKeyRedaction()
        val redactedErrorMessage = apiKeyRedaction.redact(aggregatedError.trim())

        return createFailureResult(
            task = task,
            reason = redactedErrorMessage,
            timestamp = executionTimestamp,
            routingDecision = originalRoutingDecision.copy(
                reason = "Multi-provider fallback exhausted after ${attemptedProviders.size} attempts",
                overrideSource = "FallbackExhausted"
            ),
            modelId = ModelId("fallback-exhausted"),
            isRecoverable = false
        )
    }

    /**
     * Execute with retry specifically for fallback attempts.
     * Uses a more conservative retry policy to fail fast during fallback.
     */
    private suspend fun executeWithRetryForFallback(
        task: Task,
        routingDecision: RoutingDecision,
        modelId: ModelId
    ): Result<ExecutionResult> {
        // Conservative retry for fallback: max 1 retry to fail fast
        val maxRetries = 1
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt <= maxRetries) {
            attempt++
            val result = try {
                attemptExecution(task, routingDecision, modelId)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            if (result.isSuccess) {
                return result
            }

            val error = result.exceptionOrNull()
            lastError = error

            // Don't retry quota errors during fallback
            if (error is ProviderQuotaException) {
                return Result.failure(error)
            }

            // Only retry network errors
            val isRetriable = isRetriableError(error)
            if (!isRetriable || attempt > maxRetries) {
                return Result.failure(error ?: IllegalStateException("Fallback execution failed"))
            }

            // Short delay for fallback retries
            delay(500)
        }

        return Result.failure(lastError ?: IllegalStateException("Fallback execution failed"))
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
            Log.e(TAG, "LOCAL execution failed (no retry): ${e.message}", e)
            // M-17: Redact API keys from error messages
            val apiKeyRedaction = ApiKeyRedaction()
            val redactedMessage = apiKeyRedaction.redact(e.message ?: "Local execution failed")
            val redactedError = apiKeyRedaction.redactThrowable(e)

            recordProviderError(
                message = redactedMessage,
                providerId = activeConfig.providerId.name,
                taskId = task.id.id,
                modelId = modelId.id,
                routingDecision = routingDecision,
                cause = redactedError
            )
            createFailureResult(task, redactedMessage, executionTimestamp, routingDecision, modelId)
        }
    }

    private suspend fun disableProvider(providerId: ProviderId) {
        withContext(Dispatchers.IO) {
            // REPOSITORY ADAPTER CLEANUP: Using ProviderSelector to coordinate with split repositories
            providerSelector.disableProvider(providerId)
        }
    }

    private suspend fun attemptExecution(
        task: Task,
        routingDecision: RoutingDecision,
        modelId: ModelId
    ): Result<ExecutionResult> = try {
        Result.success(try {
            withTimeout(task.budget.timeoutMs.milliseconds) {
                val output = hybridExecutor.execute(task)
                createSuccessResult(task, output, routingDecision, modelId)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // Never swallow CancellationException
        } catch (e: Throwable) {
            Log.e(TAG, "Caught throwable during execution for task ${task.id}: ${e.javaClass.simpleName} - ${e.message}", e)
            // M-17: Redact API keys from error messages
            val apiKeyRedaction = ApiKeyRedaction()
            val redactedError = apiKeyRedaction.redactThrowable(e)
            throw redactedError
        })
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e  // Never swallow CancellationException
    } catch (e: Throwable) {
        Result.failure(e)
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
    private fun getCapabilitiesForTaskType(taskType: TaskType): Set<com.shadowai.app.providers.Capability> {
        return when (taskType) {
            TaskType.CONVERSATION, TaskType.WRITING, TaskType.VOCAL, TaskType.TEXT_GEN -> setOf(com.shadowai.app.providers.Capability.TEXT)
            TaskType.IMAGE_GEN -> setOf(com.shadowai.app.providers.Capability.IMAGE_GEN)
            TaskType.VIDEO_GEN -> setOf(com.shadowai.app.providers.Capability.VISION)
            TaskType.AUDIO_GEN -> setOf(com.shadowai.app.providers.Capability.VOICE)
            TaskType.DEVICE_CONTROL, TaskType.TELEPHONY, TaskType.MESSAGING, TaskType.SYSTEM_INTERACTION -> setOf(com.shadowai.app.providers.Capability.FUNCTION_CALLS)
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
        // M-17: Redact API keys from error messages to prevent credential exposure
        val apiKeyRedaction = ApiKeyRedaction()
        val redactedMessage = apiKeyRedaction.redact(message)
        val redactedCause = cause?.let { apiKeyRedaction.redactThrowable(it) }

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
                message = redactedMessage,
                providerId = providerId,
                context = context,
                cause = redactedCause
            )
        )
    }

    private fun maskPiiInTask(task: Task): Task {
        val maskedInput = piiMaskingProcessor.maskPii(task.input)
        return task.copy(input = maskedInput)
    }

    // LEGACY REMOVAL: executeTask method removed - TaskExecutionService no longer used
    // All execution now flows through HybridAiExecutor using ProviderAdapter architecture

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
        // M-17: Redact API keys from failure reason to prevent credential exposure
        val apiKeyRedaction = ApiKeyRedaction()
        val redactedReason = apiKeyRedaction.redact(reason)

        val failedTask = task.copy(
            currentState = TaskState.Failed(reason = redactedReason, timestamp = timestamp, isRecoverable = isRecoverable)
        )
        return ExecutionResult(task = failedTask, routingDecision = routingDecision, modelId = modelId)
    }
}
