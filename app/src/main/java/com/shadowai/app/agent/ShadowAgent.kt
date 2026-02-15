package com.shadowai.app.agent

import android.content.Context
import android.util.Log
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.execution.DeviceAction
import com.shadowai.app.execution.DeviceActionExecutor
import com.shadowai.app.execution.TaskExecutor
import com.shadowai.app.tasks.NodeStatus
import com.shadowai.app.tasks.PlanParser
import com.shadowai.app.tasks.Task
import com.shadowai.app.tasks.TaskBudget
import com.shadowai.app.tasks.TaskIdentifier
import com.shadowai.app.tasks.TaskState
import com.shadowai.app.tasks.TaskType
import com.shadowai.app.exceptions.NetworkException
import com.shadowai.app.exceptions.ResourceException
import com.shadowai.core.security.PromptInjectionDefense
import com.shadowai.core.security.PiiMaskingProcessor
import com.shadowai.app.routing.ExecutionSource
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adminRepo: AdminRepository,
    private val executor: TaskExecutor,
    private val verificationEngine: VerificationEngine,
    private val planParser: PlanParser,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val promptInjectionDefense: PromptInjectionDefense,
    private val supervisorAgent: Lazy<SupervisorAgent>,
    private val piiMaskingProcessor: PiiMaskingProcessor
) {
    companion object {
        private const val TAG = "ShadowAgent"

        private val TELEPHONY_KEYWORDS = setOf("call", "dial", "phone", "ring", "hangup")
        private val MESSAGING_KEYWORDS = setOf("message", "sms", "text", "send text", "mms")
        private val MEDIA_KEYWORDS = setOf("music", "play", "pause", "volume", "skip", "next track", "previous track", "stop music", "resume")
        private val SYSTEM_KEYWORDS = setOf("open", "launch", "start", "close", "exit", "switch to")
        private val IMAGE_GEN_KEYWORDS = setOf("generate image", "draw", "paint", "create image", "make a picture", "illustration", "artwork")
        private val WRITING_KEYWORDS = setOf("write a", "compose", "essay", "story", "poem", "creative", "draft", "letter")
        private val VOCAL_KEYWORDS = setOf("speak", "voice", "say", "vocal", "synthesize", "read aloud", "text to speech", "tts")
        private val DEVICE_CONTROL_KEYWORDS = setOf("turn on", "turn off", "enable", "disable", "set", "toggle", "switch", "brightness", "wifi", "bluetooth")
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
                lowerInput.contains("and then") ||
                lowerInput.contains("first") && lowerInput.contains("then")
            }
        }
    }

    private fun sanitizeOutput(output: String, source: String): String {
        return if (source == ExecutionSource.CLOUD.name) {
            val masked = piiMaskingProcessor.maskPii(output)
            if (piiMaskingProcessor.containsPii(output)) {
                Log.i(TAG, "PII masked in response from $source")
            }
            masked
        } else {
            output
        }
    }

    suspend fun processInput(input: String): AgentResult {
        // Phase 5.4: Detect task type FROM ORIGINAL INPUT before prompt injection filter
        val originalTaskType = determineTaskType(input)

        val scanResult = promptInjectionDefense.scan(input)
        if (!scanResult.isSafe) {
            return AgentResult.Failure(
                AgentError(ErrorCategory.SECURITY, scanResult.reason ?: "Prompt injection detected")
            )
        }
        val currentPrompt = scanResult.sanitizedPrompt

        // Use ORIGINAL task type for routing
        val taskType = originalTaskType

        if (isComplexTaskType(taskType, input)) {
            Log.d(TAG, "Delegating complex task to SupervisorAgent: $taskType")
            return when (val result = supervisorAgent.get().processInput(
                input = currentPrompt,
                forcedTaskType = taskType
            )) {
                is SupervisorResult.Success -> AgentResult.Conversation(
                    message = sanitizeOutput(result.output, result.source ?: "unknown"),
                    modelName = result.modelId,
                    source = result.source
                )
                is SupervisorResult.ActionRequired -> AgentResult.ActionProposed(
                    action = result.action,
                    modelName = result.modelId,
                    source = result.source,
                    plan = result.plan
                )
                is SupervisorResult.Error -> AgentResult.Failure(result.error)
                is SupervisorResult.BudgetExceeded -> AgentResult.Failure(
                    AgentError(ErrorCategory.EXHAUSTION, result.reason)
                )
            }
        }

        val sanitizedPrompt = piiMaskingProcessor.maskPii(currentPrompt)
        if (sanitizedPrompt != currentPrompt) {
            Log.i(TAG, "PII masked before sending to providers")
        }
        val taskId = TaskIdentifier(UUID.randomUUID().toString())
        val task = Task(
            id = taskId,
            type = taskType,
            input = sanitizedPrompt,
            budget = TaskBudget(
                timeoutMs = 120000,
                maxTokens = 2000
            ),
            currentState = TaskState.Queued
        )

        val result = try {
            executor.execute(task, adminRepo.routingPolicyFlow.value)
        } catch (e: NetworkException) {
            return AgentResult.Failure(AgentError(ErrorCategory.TRANSPORT, e.message ?: "Network error"))
        } catch (e: ResourceException) {
            return AgentResult.Failure(AgentError(ErrorCategory.EXHAUSTION, e.message ?: "Resource exhausted"))
        } catch (e: IllegalStateException) {
            return AgentResult.Failure(AgentError(ErrorCategory.EXECUTION, "Invalid state: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            return AgentResult.Failure(AgentError(ErrorCategory.SEMANTIC, "Invalid argument: ${e.message}"))
        } catch (e: java.util.concurrent.TimeoutException) {
            return AgentResult.Failure(AgentError(ErrorCategory.EXHAUSTION, "Request timeout: ${e.message}"))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Never swallow CancellationException — re-throw to preserve structured concurrency
            throw e
        } catch (e: Exception) {
            // L-2: Catch-all for truly unexpected errors - log and categorize
            Log.e(TAG, "Unexpected execution error", e)
            val cat = when {
                e.message?.contains("budget", ignoreCase = true) == true -> ErrorCategory.EXHAUSTION
                e.message?.contains("timeout", ignoreCase = true) == true -> ErrorCategory.EXHAUSTION
                e.message?.contains("permission", ignoreCase = true) == true -> ErrorCategory.VIOLATION
                else -> ErrorCategory.UNKNOWN
            }
            return AgentResult.Failure(AgentError(cat, e.message ?: "Execution Error"))
        }

        if (result.task.currentState is TaskState.Completed) {
            val rawOutput = result.task.currentState.output
            val modelId = result.modelId?.id
            val source = result.routingDecision.selectedSource.name

            val output = sanitizeOutput(rawOutput, source)

            if (taskType == TaskType.DEVICE_CONTROL ||
                taskType == TaskType.TELEPHONY ||
                taskType == TaskType.MESSAGING ||
                taskType == TaskType.MEDIA_CONTROL ||
                taskType == TaskType.SYSTEM_INTERACTION) {
                val planResult = try {
                    planParser.parse(output)
                } catch (e: org.json.JSONException) {
                    val errorMsg = "Failed to parse plan JSON from model output: ${e.localizedMessage}"
                    return AgentResult.Failure(AgentError(ErrorCategory.SEMANTIC, errorMsg))
                } catch (e: IllegalArgumentException) {
                    val errorMsg = "Invalid plan structure: ${e.localizedMessage}"
                    return AgentResult.Failure(AgentError(ErrorCategory.SEMANTIC, errorMsg))
                }

                if (planResult.isSuccess) {
                    val plan = planResult.getOrThrow()
                    if (plan.nodes.isNotEmpty()) {
                        val nextNode = plan.nodes.firstOrNull { it.status == NodeStatus.PENDING }
                        return if (nextNode != null) {
                            AgentResult.ActionProposed(nextNode.action, modelId, source, plan)
                        } else {
                            AgentResult.Conversation(output, modelId, source, plan)
                        }
                    }
                }

                if (!isValidJson(output)) {
                    return AgentResult.Conversation(output, modelId, source)
                }

                val errorMsg = planResult.exceptionOrNull()?.message ?: "Malformed JSON plan structure."
                return AgentResult.Failure(AgentError(ErrorCategory.SEMANTIC, "Invalid Plan: $errorMsg"))
            } else {
                return AgentResult.Conversation(output, modelId, source)
            }
        } else {
            val failureState = result.task.currentState as? TaskState.Failed
            if (failureState != null) {
                return AgentResult.Failure(AgentError(ErrorCategory.EXECUTION, failureState.reason))
            }
            return AgentResult.Failure(AgentError(ErrorCategory.UNKNOWN, "Task failed"))
        }
    }

    suspend fun executeActionConfirmed(action: DeviceAction, envHistory: String = ""): Result<String> {
        return when (val verificationResult = verificationEngine.verifyAction(action)) {
            is VerificationEngine.VerificationResult.Passed -> {
                when (val consistencyResult = verificationEngine.verifyConsistency(action, envHistory)) {
                    is VerificationEngine.VerificationResult.Passed -> {
                        deviceActionExecutor.execute(action)
                    }
                    is VerificationEngine.VerificationResult.PreconditionMissing -> {
                        Log.w(TAG, "Precondition missing: ${consistencyResult.message}")
                        Result.failure(ActionBlockedException(consistencyResult.message))
                    }
                    is VerificationEngine.VerificationResult.PolicyViolation -> {
                        Log.w(TAG, "Policy violation: ${consistencyResult.message}")
                        Result.failure(ActionBlockedException(consistencyResult.message))
                    }
                }
            }
            is VerificationEngine.VerificationResult.PreconditionMissing -> {
                Log.w(TAG, "Precondition missing: ${verificationResult.message}")
                Result.failure(ActionBlockedException(verificationResult.message))
            }
            is VerificationEngine.VerificationResult.PolicyViolation -> {
                Log.w(TAG, "Policy violation: ${verificationResult.message}")
                Result.failure(ActionBlockedException(verificationResult.message))
            }
        }
    }

    private fun determineTaskType(input: String): TaskType {
        val lowerInput = input.lowercase()
        val scores = mutableMapOf<TaskType, Int>()

        fun countMatches(keywords: Set<String>): Int {
            return keywords.count { keyword -> lowerInput.contains(keyword) }
        }

        scores[TaskType.TELEPHONY] = countMatches(TELEPHONY_KEYWORDS)
        scores[TaskType.MESSAGING] = countMatches(MESSAGING_KEYWORDS)
        scores[TaskType.MEDIA_CONTROL] = countMatches(MEDIA_KEYWORDS)
        scores[TaskType.SYSTEM_INTERACTION] = countMatches(SYSTEM_KEYWORDS)
        scores[TaskType.IMAGE_GEN] = countMatches(IMAGE_GEN_KEYWORDS)
        scores[TaskType.WRITING] = countMatches(WRITING_KEYWORDS)
        scores[TaskType.VOCAL] = countMatches(VOCAL_KEYWORDS)
        scores[TaskType.DEVICE_CONTROL] = countMatches(DEVICE_CONTROL_KEYWORDS)

        val maxScore = scores.values.maxOrNull() ?: 0
        if (maxScore == 0) {
            return TaskType.CONVERSATION
        }

        val priorityOrder = listOf(
            TaskType.DEVICE_CONTROL,
            TaskType.TELEPHONY,
            TaskType.MESSAGING,
            TaskType.MEDIA_CONTROL,
            TaskType.SYSTEM_INTERACTION,
            TaskType.IMAGE_GEN,
            TaskType.WRITING,
            TaskType.VOCAL
        )

        return priorityOrder.firstOrNull { scores[it] == maxScore } ?: TaskType.CONVERSATION
    }

    private fun isValidJson(str: String): Boolean {
        val trimmed = str.trim()
        if (trimmed.isEmpty()) return false
        val firstChar = trimmed.firstOrNull() ?: return false
        if (firstChar != '{' && firstChar != '[') return false

        return try {
            org.json.JSONObject(trimmed)
            true
        } catch (e: org.json.JSONException) {
            try {
                org.json.JSONArray(trimmed)
                true
            } catch (e: org.json.JSONException) {
                false
            }
        }
    }
}
