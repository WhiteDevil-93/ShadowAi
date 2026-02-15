package com.shadowai.app.ai

import android.os.Build
import android.util.Log
import com.shadowai.app.admin.GenerationSettings
import com.shadowai.app.tasks.Task
import com.shadowai.core.Message // Use core-contracts Message
import com.shadowai.core.security.PiiMaskingProcessor // Import PII Masking Processor
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for building AI prompts with security protections.
 * Now uses [com.shadowai.core.Message] for modality-agnostic message representation.
 * Integrates [PiiMaskingProcessor] to mask sensitive information in prompts.
 */
@Singleton
class PromptManager @Inject constructor(
    private val memoryManager: MemoryManager,
    private val piiMaskingProcessor: PiiMaskingProcessor, // Inject PII Masking Processor
    private val tokenCounter: TokenCounter // M-12: Use centralized TokenCounter
) {
    companion object {
        private const val TAG = "PromptManager"

        // Maximum prompt lengths
        const val MAX_INPUT_LENGTH = 4000
        const val MAX_MEMORY_CONTEXT = 2000
        const val MAX_SYSTEM_PROMPT = 3000
        const val DEFAULT_MODEL_MAX_CONTEXT_TOKENS = 4096
        const val SLIDING_WINDOW_SIZE = 5
        const val CONTEXT_THRESHOLD = 0.9f

        // Patterns for detecting prompt injection attempts
        private val INJECTION_PATTERNS = listOf(
            Pattern.compile("(?i)ignore.*previous.*instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)disregard.*previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)override.*instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)act.*as.*(dan|developer|admin|system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)you.*are.*now.*(dan|developer|admin)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)new.*(personality|mode|rule)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)reveal.*(system|prompt|instruction)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)what.*are.*your.*(instruction|rule|system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\{[^{}]*\\{[^{}]*\\{.*", Pattern.DOTALL),
            Pattern.compile("(?i)(select|insert|update|delete|drop).*from", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\\\[\\s]*n", Pattern.DOTALL),
            Pattern.compile("\\\\u[0-9a-fA-F]{4}", Pattern.DOTALL),
        )
    }

    private val androidVersion = Build.VERSION.RELEASE
    private val androidApiLevel = Build.VERSION.SDK_INT

    private val deviceControlSchema = """
        Available Tools:
        1. CALL { "number" }
        2. SMS { "number", "message" }
        3. READ_SMS
        4. MEDIA_TOGGLE, MEDIA_NEXT, MEDIA_VOLUME { "level" }
        5. APP_LAUNCH { "package" }
        6. WIFI { "enable" }
        7. SYSTEM_STATUS, ACCESSIBILITY_INSPECT, GLOBAL_BACK, GLOBAL_HOME
        8. BROWSE { "query" }
        OUTPUT FORMAT (MANDATORY):
        You must return a JSON object with "version": 1.
        For single actions:
        { "version": 1, "action": "ACTION_NAME", "payload": { ... } }
        For multi-step complex tasks:
        {
          "version": 1,
          "plan": {
            "nodes": [
              { "id": "task_1", "action": { "version": 1, "action": "READ_SMS" } },
              { "id": "task_2", "action": { "version": 1, "action": "SMS", "payload": { "number": "123", "message": "Reply" } }, "dependencies": ["task_1"] }
            ]
          }
        }
    """.trimIndent()

    private fun validateAndSanitizeInput(input: String): Pair<Boolean, String?> {
        if (input.isBlank()) {
            return Pair(true, "")
        }

        if (input.length > MAX_INPUT_LENGTH) {
            Log.w(TAG, "Input too long: ${input.length} chars, max is $MAX_INPUT_LENGTH")
            return Pair(false, null)
        }

        for (pattern in INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                Log.w(TAG, "Potential prompt injection detected")
                return Pair(false, null)
            }
        }

        // Sanitize the input for special characters, then mask PII
        val sanitized = sanitizeInput(input)
        val masked = piiMaskingProcessor.maskPii(sanitized)
        return Pair(true, masked)
    }

    private fun sanitizeInput(input: String): String {
        return input
            .replace("{", "{{")
            .replace("}", "}}")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            .replace("`", "")
            .replace("$", "\\$")
            .replace(";", "")
            .replace("|", "")
            .replace("&", "")
            .replace(">", "")
            .replace("<", "")
            .trim()
            .take(MAX_INPUT_LENGTH)
    }

    private fun sanitizeSystemPrompt(prompt: String): String {
        val sanitized = prompt
            .replace("{", "{{")
            .replace("}", "}}")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            .replace("`", "")
            .replace("$", "\\$")
            .trim()
            .take(MAX_SYSTEM_PROMPT)
        return piiMaskingProcessor.maskPii(sanitized)
    }

    private fun limitMemoryContext(context: String, maxChars: Int = MAX_MEMORY_CONTEXT): String {
        val windowed = applySlidingWindow(
            context = context,
            maxContextTokens = DEFAULT_MODEL_MAX_CONTEXT_TOKENS,
            contextThreshold = CONTEXT_THRESHOLD,
            slidingWindowSize = SLIDING_WINDOW_SIZE
        )
        val limited = if (windowed.length > maxChars) {
            windowed.takeLast(maxChars)
        } else {
            windowed
        }
        return piiMaskingProcessor.maskPii(limited)
    }

    /**
     * Applies a sliding window to memory context once usage reaches threshold.
     * Keeps the last N exchanges (approximated as 2 lines per exchange).
     * M-12: Uses centralized TokenCounter instead of duplicate local implementation.
     */
    private fun applySlidingWindow(
        context: String,
        maxContextTokens: Int,
        contextThreshold: Float,
        slidingWindowSize: Int
    ): String {
        if (context.isBlank()) return context

        val tokenCount = tokenCounter.countTokens(context)
        val thresholdTokens = (maxContextTokens * contextThreshold).toInt().coerceAtLeast(1)
        if (tokenCount < thresholdTokens) return context

        val lines = context.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return context

        val linesPerExchange = 2
        val keepLineCount = (slidingWindowSize * linesPerExchange).coerceAtLeast(slidingWindowSize)
        return lines.takeLast(keepLineCount).joinToString(separator = "\n")
    }

    suspend fun buildChatMessages(task: Task, settings: GenerationSettings): List<Message> {
        val (isValid, sanitizedInput) = validateAndSanitizeInput(task.input)

        if (!isValid || sanitizedInput == null) {
            Log.w(TAG, "Rejecting potentially malicious input")
            return listOf(
                Message.system("Input rejected for security reasons."),
                Message.user("[REDACTED]")
            )
        }

        val rawMemory = memoryManager.getLongTermContext()
        val memoryContext = limitMemoryContext(rawMemory)
        val safetyNote = if (settings.contentFilterEnabled) "Enforce safety and privacy." else "Direct response mode."

        val systemContent = buildString {
            append("You are ShadowAi, an autonomous AI assistant running on Android $androidVersion (API $androidApiLevel).\n")
            append("Safety: $safetyNote\n")
            if (memoryContext.isNotEmpty()) append(memoryContext)
        }.let { sanitizeSystemPrompt(it) }

        return listOf(
            Message.system(systemContent),
            Message.user(sanitizedInput)
        )
    }

    suspend fun buildPlannerMessages(task: Task, settings: GenerationSettings): List<Message> {
        val (isValid, sanitizedInput) = validateAndSanitizeInput(task.input)

        if (!isValid || sanitizedInput == null) {
            return listOf(
                Message.system("Input rejected for security reasons."),
                Message.user("[REDACTED]")
            )
        }

        val rawMemory = memoryManager.getLongTermContext()
        val memoryContext = limitMemoryContext(rawMemory)
        val safetyNote = if (settings.contentFilterEnabled) "Plans must be safe." else "Direct planning."

        val systemContent = """
            ROLE: STRATEGIC PLANNER
            Your job is to break down the user request into a high-level DAG (Directed Acyclic Graph) of actions.
            DO NOT execute. Only PLAN.
            Safety: $safetyNote
            $deviceControlSchema
            Memory Context:
            $memoryContext
        """.trimIndent().let { sanitizeSystemPrompt(it) }

        return listOf(
            Message.system(systemContent),
            Message.user(sanitizedInput)
        )
    }

    suspend fun buildCriticMessages(proposedPlan: String, settings: GenerationSettings? = null): List<Message> {
        val (isValid, sanitizedPlan) = validateAndSanitizeInput(proposedPlan)

        if (!isValid || sanitizedPlan == null) {
            return listOf(
                Message.system("Plan rejected for security reasons."),
                Message.user("[REDACTED]")
            )
        }

        val safetyNote = if (settings?.contentFilterEnabled == true)
            "Enforce maximum safety and strictly reject dangerous plans."
        else
            "Standard security and logic critique."

        val systemContent = """
            ROLE: SECURITY CRITIC
            Your job to find hallucinations, security risks, or redundant steps in the proposed plan.
            Safety Guidelines: $safetyNote
            If the plan is safe and logical, respond with "APPROVED".
            If there are issues, list them clearly as "REJECTED: [REASONS]".
            Proposed Plan:
            $sanitizedPlan
        """.trimIndent().let { sanitizeSystemPrompt(it) }

        return listOf(
            Message.system(systemContent),
            Message.user("Critique the proposed plan.")
        )
    }

    suspend fun buildDeviceControlMessages(task: Task, settings: GenerationSettings, repairHint: String? = null): List<Message> {
        val (isValid, sanitizedInput) = validateAndSanitizeInput(task.input)

        if (!isValid || sanitizedInput == null) {
            return listOf(
                Message.system("Input rejected for security reasons."),
                Message.user("[REDACTED]")
            )
        }

        val memoryContext = limitMemoryContext(memoryManager.getLongTermContext())
        val safetyNote = if (settings.contentFilterEnabled) "Execute safely." else "Direct execution."

        val sanitizedHint = repairHint?.let {
            validateAndSanitizeInput(it).second
        } ?: ""

        val systemContent = buildString {
            append("You are ShadowAi Executor. Return a SINGLE JSON object only.\n")
            append("Safety: $safetyNote\n")
            append(deviceControlSchema)
            if (memoryContext.isNotEmpty()) append("\n$memoryContext")
            if (sanitizedHint.isNotBlank()) append("\nRepair Hint: $sanitizedHint")
        }.let { sanitizeSystemPrompt(it) }

        return listOf(
            Message.system(systemContent),
            Message.user(sanitizedInput)
        )
    }

    suspend fun buildCreativePlannerMessages(task: Task, settings: GenerationSettings): List<Message> {
        val (isValid, sanitizedInput) = validateAndSanitizeInput(task.input)

        if (!isValid || sanitizedInput == null) {
            return listOf(
                Message.system("Input rejected for security reasons."),
                Message.user("[REDACTED]")
            )
        }

        val rawMemory = memoryManager.getLongTermContext()
        val memoryContext = limitMemoryContext(rawMemory)

        val systemContent = """
            ROLE: CREATIVE ORCHESTRATOR
            Your job is to generate high-quality creative output (Image Prompts, Creative Writing, or Voice Scripts).
            Align the output with the user's stylistic preferences from memory.
            Task Type: ${task.type}
            Memory Context: $memoryContext
        """.trimIndent().let { sanitizeSystemPrompt(it) }

        return listOf(
            Message.system(systemContent),
            Message.user(sanitizedInput)
        )
    }

    suspend fun buildSummarizationMessages(contentToSummarize: String): List<Message> {
        // Allow slightly longer input for summarization, or truncate differently
        val sanitizedContent = sanitizeInput(contentToSummarize).take(MAX_INPUT_LENGTH * 2) 
        val maskedContent = piiMaskingProcessor.maskPii(sanitizedContent)

        val systemContent = """
            ROLE: MEMORY SUMMARIZER
            Your task is to condense the provided text into a concise summary.
            Retain key facts, user preferences, names, and important context.
            Discard redundant information.
            Output format: Plain text summary.
        """.trimIndent().let { sanitizeSystemPrompt(it) }

        return listOf(
            Message.system(systemContent),
            Message.user(maskedContent)
        )
    }
}
