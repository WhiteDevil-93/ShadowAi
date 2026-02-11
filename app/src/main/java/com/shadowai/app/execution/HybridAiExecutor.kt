package com.shadowai.app.execution

import com.shadowai.app.ai.LocalBrainManager
import com.shadowai.app.ai.PromptManager
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.ai.MemoryManager
import com.shadowai.app.providers.AdapterBridge
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.Transform
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
import java.util.Locale
import com.shadowai.pipelineplanner.PipelineExecutor
import com.shadowai.core.ProviderExecutor

/**
 * Phase 7: Multi-Agent Coordinator (Planner / Critic / Executor)
 * Refactored to use:
 * - Unified Provider Adapter architecture (Phase 2)
 * - Normalized Artifact System (Phase 3)
 * - Pipeline Planner for multimodal transformations (Phase 4)
 * - PII Masking for incoming/outgoing data (Phase 1 Security)
 */
class HybridAiExecutor @Inject constructor(
    private val brainManager: LocalBrainManager,
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

        // 1. FAST PATH: Rule-based heuristic parsing
        if (task.type == TaskType.DEVICE_CONTROL) {
            heuristicParser.parseToCommand(task.input)?.let {
                android.util.Log.d(TAG, "Heuristic match found for task: ${task.input}")
                return it
            }
        }

        // 2. MODALITY PATH: Multi-step pipeline planning (for non-text generative tasks)
        val targetModality = when (task.type) {
            TaskType.IMAGE_GEN -> Modality.Image
            TaskType.VIDEO_GEN -> Modality.Video
            TaskType.AUDIO_GEN -> Modality.Audio
            else -> Modality.Text
        }

        if (targetModality != Modality.Text) {
            val output = executeMultimodalPipeline(task, targetModality)
            // Mask PII in the output URI if any (unlikely for paths, but safe to check)
            return piiMaskingProcessor.maskPii(output)
        }

        // 3. AGENTIC PATH: Planner/Critic loop for complex text tasks
        val isCreative = task.type == TaskType.WRITING || task.type == TaskType.VOCAL

        // TURN 1: STRATEGIC PLANNING
        val plannerMessages = if (isCreative) {
            promptManager.buildCreativePlannerMessages(task, genSettings)
        } else {
            promptManager.buildPlannerMessages(task, genSettings)
        }
        var planAttempt = callAdapter(plannerMessages, genSettings)

        // TURN 2: CRITICAL REVIEW (Agentic Governance)
        val criticMessages = promptManager.buildCriticMessages(planAttempt, genSettings)
        val critique = callAdapter(criticMessages, genSettings)

        // Structured parsing for governance logic
        val critiqueResult = parseCritiqueResult(critique)
        when (critiqueResult.status) {
            CritiqueStatus.APPROVED -> return planAttempt
            CritiqueStatus.REJECTED -> {
                // TURN 3: ADVERSARIAL REPAIR
                val repairHint = "Your previous output was REJECTED by the Security/Quality Critic with these reasons:\n${critiqueResult.reasons}\n\nPlease re-generate with these corrections."
                val repairPlannerMessages = (if (isCreative) {
                    promptManager.buildCreativePlannerMessages(task, genSettings)
                } else {
                    promptManager.buildPlannerMessages(task, genSettings)
                }) + listOf(
                    com.shadowai.core.Message.assistant(planAttempt),
                    com.shadowai.core.Message.user(repairHint)
                )

                var repairedPlan = callAdapter(repairPlannerMessages, genSettings)

                // TURN 4: FINAL SAFETY VALIDATION
                val finalCriticMessages = promptManager.buildCriticMessages(repairedPlan, genSettings)
                val finalCritique = callAdapter(finalCriticMessages, genSettings)

                val finalCritiqueResult = parseCritiqueResult(finalCritique)
                if (finalCritiqueResult.status == CritiqueStatus.APPROVED) {
                    return repairedPlan
                } else {
                    android.util.Log.e(TAG, "SECURITY/QUALITY HALT: Governance rejected output after repair. Final critique: $finalCritique")
                    return if (isCreative) {
                        "Workflow blocked: The generative output failed quality/safety standards. Reason: ${finalCritiqueResult.reasons}"
                    } else {
                        "{\"error\": \"Action rejected by security governance system after repair attempt failed validation.\", \"status\": \"blocked\", \"reasons\": \"${finalCritiqueResult.reasons}\"}"
                    }
                }
            }
            CritiqueStatus.REVIEW_NEEDED -> {
                android.util.Log.w(TAG, "Unknown critique status, treating as review needed: $critique")
                return planAttempt
            }
        }
    }

    /**
     * Executes a multimodal request by finding and executing an optimal pipeline.
     */
    /**
     * Executes a multimodal request by finding and executing an optimal pipeline.
     */
    private suspend fun executeMultimodalPipeline(task: Task, targetModality: Modality): String {
        val plan = pipelinePlanner.planPipeline(Modality.Text, targetModality)
            ?: throw IllegalStateException(
                "No transformation path available from ${Modality.Text.getDisplayName()} to ${targetModality.getDisplayName()}"
            )

        // Convert Plan to Config
        val builder = com.shadowai.pipelineplanner.PipelineBuilder()
            .named("Task-${task.id}")
            .withDescription("Auto-generated pipeline for ${task.type}")
        
        plan.transforms.forEach { transform ->
            builder.transform(transform)
        }
        val config = builder.build()

        // Create Executor Source that bridges to our Adapter system (using injected source)
        // No anonymous class needed anymore


        // Prepare Input Artifact
        val inputArtifact = Artifact.Text.create(content = task.input)

        // Execute Pipeline
        val result = pipelineExecutor.execute(
            config = config,
            input = inputArtifact,
            executor = executorSource,
            parameters = emptyMap() // Pass global params if needed
        )

        return result.mapCatching { artifact ->
            // Convert final artifact to string representation
            when (artifact) {
                is Artifact.Text -> artifact.content
                is Artifact.Image -> artifact.uri.toString()
                is Artifact.Audio -> artifact.uri.toString()
                is Artifact.Video -> artifact.uri.toString()
                is Artifact.Json -> artifact.jsonString
                is Artifact.Binary -> java.util.Base64.getEncoder().encodeToString(artifact.data)
                is Artifact.Empty -> ""
                is Artifact.Error -> throw IllegalStateException(artifact.message)
            }
        }.getOrThrow()
    }

    suspend fun executeDeviceControlRepair(task: Task, badOutput: String, errorHint: String): String {
        val genSettings = adminRepo.getGenerationSettings()
        val repairHint = "Invalid Output:\n$badOutput\n\nValidation Error:\n$errorHint"
        val messages = promptManager.buildDeviceControlMessages(task, genSettings, repairHint)
        return callAdapter(messages, genSettings)
    }

    /**
     * Unified API call through the Provider Adapter architecture using Normalized Artifacts.
     */
    private suspend fun callAdapter(
        messages: List<com.shadowai.core.Message>,
        genSettings: com.shadowai.app.admin.GenerationSettings
    ): String {
        val activeConfig = brainManager.getActiveConfig()
            ?: throw IllegalStateException("No active AI provider configured")

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
            val response = result.getOrThrow() as String
            // CRITICAL: Mask PII in incoming AI response
            piiMaskingProcessor.maskPii(response)
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown adapter error")
            android.util.Log.e(TAG, "Adapter execution failed: ${error.message}", error)
            throw error
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
