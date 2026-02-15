package com.shadowai.app.execution

import android.util.Log
import com.shadowai.app.ai.PromptManager
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.ai.MemoryManager
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.app.providers.ProviderSelector
import com.shadowai.app.security.ApiKeyRedaction
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.Transform
import com.shadowai.core.toDisplayString
import com.shadowai.core.mapFailure
import com.shadowai.pipelineplanner.PipelinePlanner
import com.google.gson.Gson
import com.shadowai.app.tasks.Task
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.security.PiiMaskingProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale
import com.shadowai.pipelineplanner.PipelineExecutor
import com.shadowai.core.ProviderExecutor

/**
 * Phase 7: Multi-Agent Coordinator (Planner / Critic / Executor)
 */
@Singleton
class HybridAiExecutor @Inject constructor(
    private val providerSelector: ProviderSelector,
    private val adminRepo: AdminRepository,
    private val memoryManager: MemoryManager,
    private val promptManager: PromptManager,
    private val adapterBridge: AdapterBridge,
    private val heuristicParser: HeuristicActionParser,
    private val pipelinePlanner: PipelinePlanner,
    private val safetySettingsManager: com.shadowai.app.ai.SafetySettingsManager,
    private val piiMaskingProcessor: PiiMaskingProcessor,
    private val gson: Gson,
    private val pipelineExecutor: PipelineExecutor,
    private val executorSource: AdapterProviderExecutor
) {

    private companion object {
        private const val TAG = "HybridAiExecutor"
    }

    suspend fun execute(task: Task): String {
        memoryManager.learnFromInput(task.input)
        val genSettings = adminRepo.getGenerationSettings()

        if (task.type == TaskType.DEVICE_CONTROL) {
            heuristicParser.parseToCommand(task.input)?.let {
                Log.d(TAG, "Heuristic match found for task: ${task.input}")
                return it
            }
        }

        val targetModality = when (task.type) {
            TaskType.IMAGE_GEN -> Modality.Image
            TaskType.VIDEO_GEN -> Modality.Video
            TaskType.AUDIO_GEN -> Modality.Audio
            else -> Modality.Text
        }

        if (targetModality != Modality.Text) {
            val output = executeMultimodalPipeline(task, targetModality)
            return piiMaskingProcessor.maskPii(output)
        }

        val isCreative = task.type == TaskType.WRITING || task.type == TaskType.VOCAL

        val plannerMessages = if (isCreative) {
            promptManager.buildCreativePlannerMessages(task, genSettings)
        } else {
            promptManager.buildPlannerMessages(task, genSettings)
        }
        var planAttempt = callAdapter(plannerMessages, genSettings, task.type)

        val criticMessages = promptManager.buildCriticMessages(planAttempt, genSettings)
        val critique = callAdapter(criticMessages, genSettings, task.type)

        val critiqueResult = parseCritiqueResult(critique)
        when (critiqueResult.status) {
            CritiqueStatus.APPROVED -> return planAttempt
            CritiqueStatus.REJECTED -> {
                val repairHint = "Your previous output was REJECTED by the Security/Quality Critic with these reasons:\n${critiqueResult.reasons}\n\nPlease re-generate with these corrections."
                val repairPlannerMessages = (if (isCreative) {
                    promptManager.buildCreativePlannerMessages(task, genSettings)
                } else {
                    promptManager.buildPlannerMessages(task, genSettings)
                }) + listOf(
                    com.shadowai.core.Message.assistant(planAttempt),
                    com.shadowai.core.Message.user(repairHint)
                )

                var repairedPlan = callAdapter(repairPlannerMessages, genSettings, task.type)

                val finalCriticMessages = promptManager.buildCriticMessages(repairedPlan, genSettings)
                val finalCritique = callAdapter(finalCriticMessages, genSettings, task.type)

                val finalCritiqueResult = parseCritiqueResult(finalCritique)
                if (finalCritiqueResult.status == CritiqueStatus.APPROVED) {
                    return repairedPlan
                } else {
                    Log.e(TAG, "SECURITY/QUALITY HALT: Governance rejected output after repair. Final critique: $finalCritique")
                    return if (isCreative) {
                        "Workflow blocked: The generative output failed quality/safety standards. Reason: ${finalCritiqueResult.reasons}"
                    } else {
                        "{\"error\": \"Action rejected by security governance system after repair attempt failed validation.\", \"status\": \"blocked\", \"reasons\": \"${finalCritiqueResult.reasons}\"}"
                    }
                }
            }
            CritiqueStatus.REVIEW_NEEDED -> {
                Log.w(TAG, "Unknown critique status, treating as review needed: $critique")
                return planAttempt
            }
        }
    }

    private suspend fun executeMultimodalPipeline(task: Task, targetModality: Modality): String {
        val plan = pipelinePlanner.planPipeline(Modality.Text, targetModality)
            ?: throw IllegalStateException(
                "No transformation path available from ${Modality.Text.getDisplayName()} to ${targetModality.getDisplayName()}"
            )

        val builder = com.shadowai.pipelineplanner.PipelineBuilder()
            .named("Task-${task.id}")
            .withDescription("Auto-generated pipeline for ${task.type}")

        plan.transforms.forEach { transform ->
            builder.transform(transform)
        }
        val config = builder.build()

        val inputArtifact = Artifact.Text.create(content = task.input)

        val result = pipelineExecutor.execute(
            config = config,
            input = inputArtifact,
            executor = executorSource,
            parameters = emptyMap()
        )

        return result.mapCatching { artifact: Artifact ->
            artifact.toDisplayString()
        }.mapFailure { error: Throwable ->
            val apiKeyRedaction = ApiKeyRedaction()
            apiKeyRedaction.redactThrowable(error)
        }.getOrThrow()
    }

    suspend fun executeDeviceControlRepair(task: Task, badOutput: String, errorHint: String): String {
        val genSettings = adminRepo.getGenerationSettings()
        val repairHint = "Invalid Output:\n$badOutput\n\nValidation Error:\n$errorHint"
        val messages = promptManager.buildDeviceControlMessages(task, genSettings, repairHint)
        return callAdapter(messages, genSettings, task.type)
    }

    private suspend fun callAdapter(
        messages: List<com.shadowai.core.Message>,
        genSettings: com.shadowai.app.admin.GenerationSettings,
        taskType: TaskType
    ): String {
        val activeConfig = providerSelector.getActiveConfig()
            ?: providerSelector.nextForTask(taskType)
            ?: throw IllegalStateException("No active AI provider configured for $taskType")

        val prompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val inputArtifact = Artifact.Text.create(content = prompt)

        val parameters = mutableMapOf<String, Any>(
            "maxTokens" to if (genSettings.maxTokens > 0) genSettings.maxTokens else 512,
            "temperature" to genSettings.temperature,
            "topP" to genSettings.topP,
            "topK" to genSettings.topK
        )

        if (activeConfig.providerId == com.shadowai.core.ProviderId.GEMINI) {
            parameters["safetySettings"] = safetySettingsManager.buildGeminiSafetySettings()
        }

        val result = adapterBridge.execute(
            appConfig = activeConfig,
            transform = Transform.TextToText(),
            input = inputArtifact,
            parameters = parameters
        )

        return if (result.isSuccess) {
            val responseArtifact = result.getOrThrow()
            val response = responseArtifact.toDisplayString()
            piiMaskingProcessor.maskPii(response)
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown adapter error")
            Log.e(TAG, "Adapter execution failed: ${error.message}", error)
            val apiKeyRedaction = ApiKeyRedaction()
            val redactedError = apiKeyRedaction.redactThrowable(error)
            throw redactedError
        }
    }

    private data class ParsedCritique(
        val status: CritiqueStatus,
        val reasons: String = ""
    )

    private enum class CritiqueStatus {
        APPROVED, REJECTED, REVIEW_NEEDED
    }

    private fun parseCritiqueResult(critique: String): ParsedCritique {
        val trimmed = critique.trim()
        val upperCase = trimmed.uppercase(Locale.US)

        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val status = json.optString("status", "").uppercase(Locale.US)
                val reasons = json.optString("reasons", "")

                when (status) {
                    "APPROVED" -> return ParsedCritique(CritiqueStatus.APPROVED)
                    "REJECTED" -> return ParsedCritique(CritiqueStatus.REJECTED,
                        reasons.ifBlank { json.optString("reason", "No specific reason provided") })
                    "REVIEW_NEEDED" -> return ParsedCritique(CritiqueStatus.REVIEW_NEEDED)
                }
            } catch (_: Exception) {}
        }

        when {
            upperCase.contains("\"STATUS\":\"APPROVED\"") ||
            upperCase.contains("\"STATUS\": \"APPROVED\"") ||
            upperCase.contains("STATUS: APPROVED") ||
            (upperCase.startsWith("APPROVED") && !upperCase.contains("REJECTED")) -> {
                return ParsedCritique(CritiqueStatus.APPROVED)
            }

            upperCase.contains("\"STATUS\":\"REJECTED\"") ||
            upperCase.contains("\"STATUS\": \"REJECTED\"") ||
            upperCase.contains("STATUS: REJECTED") ||
            upperCase.startsWith("REJECTED") -> {
                val reasons = extractReasonsFromCritique(critique)
                return ParsedCritique(CritiqueStatus.REJECTED, reasons)
            }

            upperCase.contains("REVIEW") || upperCase.contains("CONDITIONAL") -> {
                return ParsedCritique(CritiqueStatus.REVIEW_NEEDED)
            }
        }

        return ParsedCritique(CritiqueStatus.REVIEW_NEEDED)
    }

    private fun extractReasonsFromCritique(critique: String): String {
        val patterns = listOf(
            "reason[s]?:?\\s*(.+?)(?:\\n|\\.\\s|$)".toRegex(RegexOption.IGNORE_CASE),
            "because\\s+(.+?)(?:\\n|\\.\\s|$)".toRegex(RegexOption.IGNORE_CASE),
            "issues?:?\\s*(.+?)(?:\\n|\\.\\s|$)".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(critique)?.groupValues?.getOrNull(1)?.let { reason ->
                if (reason.length in 5..500) return reason.trim()
            }
        }

        val sentences = critique.split(Regex("[\\.!?]\\s*")).filter { it.length > 10 }
        return sentences.firstOrNull()?.trim() ?: "No specific reason provided"
    }
}
