package com.shadowai.core.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defense system against prompt injection attacks.
 */
@Singleton
class PromptInjectionDefense @Inject constructor() {

    enum class RiskLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    data class ScanResult(
        val isSafe: Boolean,
        val riskLevel: RiskLevel,
        val reason: String? = null,
        val sanitizedPrompt: String = ""
    )

    companion object {
        private val JAILBREAK_PATTERNS = listOf(
            Regex("ignore (all )?previous instructions", RegexOption.IGNORE_CASE),
            Regex("ignore (all )?directions", RegexOption.IGNORE_CASE),
            Regex("start(ing)? (your )?response with", RegexOption.IGNORE_CASE),
            Regex("you are now (a|an)", RegexOption.IGNORE_CASE),
            Regex("act as (a|an)", RegexOption.IGNORE_CASE),
            Regex("do anything now", RegexOption.IGNORE_CASE),
            Regex("AIM mode", RegexOption.IGNORE_CASE),
            Regex("developer mode", RegexOption.IGNORE_CASE),
            Regex("system prompt", RegexOption.IGNORE_CASE),
            Regex("bypass (all )?restrictions", RegexOption.IGNORE_CASE),
            Regex("remove (all )?safety", RegexOption.IGNORE_CASE),
            Regex("pretend (you are|you're)", RegexOption.IGNORE_CASE),
            Regex("forget everything", RegexOption.IGNORE_CASE),
            Regex("reset (your )?instructions", RegexOption.IGNORE_CASE),
            Regex("jailbreak", RegexOption.IGNORE_CASE),
            Regex("evil mode", RegexOption.IGNORE_CASE),
            Regex("unrestricted mode", RegexOption.IGNORE_CASE),
            Regex("unethical requests", RegexOption.IGNORE_CASE),
            Regex("without limitations", RegexOption.IGNORE_CASE),
            Regex("no safety restrictions", RegexOption.IGNORE_CASE)
        )

        private val LEAKAGE_PATTERNS = listOf(
            Regex("repeat (the )?system prompt", RegexOption.IGNORE_CASE),
            Regex("print (the )?instructions", RegexOption.IGNORE_CASE),
            Regex("what are your (rules|instructions)", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Scans a prompt for potential injection attacks.
     */
    fun scan(prompt: String): ScanResult {
        if (prompt.isBlank()) return ScanResult(true, RiskLevel.NONE, sanitizedPrompt = prompt)

        JAILBREAK_PATTERNS.firstOrNull { it.containsMatchIn(prompt) }?.let { matchedPattern ->
            return ScanResult(
                isSafe = false,
                riskLevel = RiskLevel.HIGH,
                reason = "Jailbreak pattern detected: ${matchedPattern.pattern}",
                sanitizedPrompt = ""
            )
        }

        LEAKAGE_PATTERNS.firstOrNull { it.containsMatchIn(prompt) }?.let { matchedPattern ->
            val sanitized = sanitizeLeakageAttempt(prompt)
            return ScanResult(
                isSafe = true,
                riskLevel = RiskLevel.MEDIUM,
                reason = "Potential system instruction leakage attempt detected and sanitized: ${matchedPattern.pattern}",
                sanitizedPrompt = sanitized
            )
        }

        return ScanResult(true, RiskLevel.NONE, sanitizedPrompt = prompt.trim())
    }

    /**
     * Compatibility helper for older call sites.
     */
    fun verifyPrompt(prompt: String): ScanResult = scan(prompt)

    private fun sanitizeLeakageAttempt(prompt: String): String {
        var sanitized = prompt
        LEAKAGE_PATTERNS.forEach { pattern ->
            sanitized = pattern.replace(sanitized, "[REDACTED]")
        }
        return sanitized.trim()
    }
}
