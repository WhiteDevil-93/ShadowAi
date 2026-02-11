package com.shadowai.app.agent

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-Trust Input Sanitization: TemplateVerifier.
 *
 * Verifies that user prompts do not contain jailbreak or system override patterns.
 * Uses a lightweight local classifier or pattern matching approach.
 *
 * Configuration options allow tuning sensitivity and thresholds.
 */
@Singleton
class TemplateVerifier @Inject constructor() {

    /**
     * Configuration for the template verifier.
     */
    data class Config(
        val maxPromptLength: Int = DEFAULT_MAX_PROMPT_LENGTH,
        val systemIndicatorDensityThreshold: Double = DEFAULT_DENSITY_THRESHOLD,
        val enableUnicodeCheck: Boolean = true,
        val enableDelimiterCheck: Boolean = true
    ) {
        companion object {
            const val DEFAULT_MAX_PROMPT_LENGTH = 10_000
            const val DEFAULT_DENSITY_THRESHOLD = 0.3
        }
    }

    @Volatile
    private var config = Config()

    /**
     * Update the verifier configuration.
     */
    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    // Pre-compiled patterns for better performance
    // Using lazy initialization to avoid regex compilation on class load
    private val injectionPatterns: List<Pair<Regex, String>> by lazy {
        listOf(
            // Jailbreak attempts
            Regex("""(?i)\b(ignore|disregard|bypass|override)\s+(all|previous|above|system|security|instruction)""") to "jailbreak_override",
            Regex("""(?i)\b(system\s+instruction|developer\s+mode|admin\s+mode|super\s*user)\b""") to "privilege_escalation",
            Regex("""(?i)\b(act\s+as|pretend\s+to\s+be|roleplay\s+as)\b.{0,30}\b(system|developer|admin)\b""") to "role_hijack",
            Regex("""(?i)\b(new\s+system\s+prompt|override\s+system|change\s+rules)\b""") to "system_override",
            Regex("""(?i)\b(DAN|Jailbreak|STAN|SIMIE)\b""") to "known_jailbreak",

            // Prompt extraction attempts
            Regex("""(?i)\b(what|show|display|repeat)\b.{0,20}\b(system|initial)\s*(instruction|prompt)\b""") to "prompt_extraction",
            Regex("""(?i)\b(ignore\s+all|forget\s+previous|disregard\s+above)\b""") to "context_reset",

            // Base64/encoding attempts
            Regex("""(?i)\b(base64|decode|encode)\s*[:=]\s*[A-Za-z0-9+/=]{20,}""") to "encoded_payload"
        )
    }

    // Unicode manipulation pattern - separate for conditional checking
    private val unicodeManipulationPattern: Regex by lazy {
        Regex("""[\u200B-\u200F\u202A-\u202E\u2060-\u2064\uFEFF]""")
    }

    // Delimiter injection patterns
    private val delimiterPatterns: List<Regex> by lazy {
        listOf(
            Regex("""(?i)\b(system|user|assistant|human|AI)\s*:"""),
            Regex("""(?i)(\[INST]|\[/INST]|\[SYS]|\[/SYS]|<\|im_start\|>|<\|im_end\|>)""")
        )
    }

    /**
     * Verify a user prompt for injection attempts.
     * Returns a VerificationResult with status and details.
     */
    fun verifyPrompt(userInput: String): VerificationResult {
        val currentConfig = config

        // Length check
        if (userInput.length > currentConfig.maxPromptLength) {
            return VerificationResult(
                isSafe = false,
                reason = "Prompt exceeds maximum length of ${currentConfig.maxPromptLength} characters"
            )
        }

        // Check for main injection patterns
        for ((index, patternPair) in injectionPatterns.withIndex()) {
            val (pattern, category) = patternPair
            if (pattern.containsMatchIn(userInput)) {
                return VerificationResult(
                    isSafe = false,
                    reason = "Potential prompt injection detected: $category",
                    patternIndex = index
                )
            }
        }

        // Unicode manipulation check (optional)
        if (currentConfig.enableUnicodeCheck && unicodeManipulationPattern.containsMatchIn(userInput)) {
            return VerificationResult(
                isSafe = false,
                reason = "Unicode manipulation characters detected"
            )
        }

        // Delimiter check (optional)
        if (currentConfig.enableDelimiterCheck) {
            for (pattern in delimiterPatterns) {
                if (pattern.containsMatchIn(userInput)) {
                    return VerificationResult(
                        isSafe = false,
                        reason = "Delimiter-based injection pattern detected"
                    )
                }
            }
        }

        // Check for excessive system instruction embedding
        if (countSystemIndicatorDensity(userInput) > currentConfig.systemIndicatorDensityThreshold) {
            return VerificationResult(
                isSafe = false,
                reason = "Excessive system instruction indicators detected"
            )
        }

        return VerificationResult(isSafe = true)
    }

    /**
     * Verify a complete message (with system instructions and user input).
     */
    fun verifyMessage(
        systemInstructions: String,
        userInput: String
    ): VerificationResult {
        val combined = "$systemInstructions $userInput"
        return verifyPrompt(combined)
    }

    // Pre-compiled word split pattern for performance
    private val wordSplitPattern = Regex("""\s+""")

    // System indicators as a set for O(1) lookup
    private val systemIndicators = setOf(
        "system:", "user:", "assistant:", "human:", "ai:",
        "[system]", "[user]", "[assistant]",
        "you are", "you must", "you should", "always",
        "never", "prohibited", "forbidden", "required"
    )

    /**
     * Calculate the density of system indicators in the prompt.
     * Returns a value between 0.0 and 1.0.
     */
    private fun countSystemIndicatorDensity(text: String): Double {
        val words = text.split(wordSplitPattern)
        val totalWords = words.size
        if (totalWords == 0) return 0.0

        val lowerText = text.lowercase()
        val indicatorCount = systemIndicators.count { indicator ->
            lowerText.contains(indicator)
        }

        return (indicatorCount.toDouble() / totalWords).coerceIn(0.0, 1.0)
    }

    /**
     * Sanitize a prompt by removing potentially dangerous content.
     */
    fun sanitizePrompt(userInput: String): SanitizedResult {
        var sanitized = userInput

        // Remove zero-width characters
        sanitized = sanitized.replace(Regex("""[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]"""), "")

        // Normalize unicode homoglyphs (basic normalization)
        sanitized = Normalizer.normalize(sanitized, Normalizer.Form.NFKC)

        // Check if sanitized version passes verification
        val result = verifyPrompt(sanitized)

        return SanitizedResult(
            sanitizedText = sanitized,
            wasModified = sanitized != userInput,
            verificationResult = result
        )
    }

    /**
     * Create a sanitized version of the prompt that passes verification.
     * May throw if the prompt cannot be safely sanitized.
     */
    fun createSafePrompt(userInput: String): String {
        val sanitized = sanitizePrompt(userInput)
        if (!sanitized.verificationResult.isSafe) {
            throw PromptInjectionException(
                "Cannot sanitize prompt: ${sanitized.verificationResult.reason}"
            )
        }
        return sanitized.sanitizedText
    }
}

data class VerificationResult(
    val isSafe: Boolean,
    val reason: String? = null,
    val patternIndex: Int? = null
)

data class SanitizedResult(
    val sanitizedText: String,
    val wasModified: Boolean,
    val verificationResult: VerificationResult
)

class PromptInjectionException(message: String) : Exception(message)
