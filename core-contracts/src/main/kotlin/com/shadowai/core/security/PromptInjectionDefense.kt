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
        val sanitizedPrompt: String = "",
        val riskScore: Double = 0.0
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

        private val SUSPICIOUS_KEYWORDS = setOf(
            "ignore",
            "override",
            "bypass",
            "jailbreak",
            "system",
            "developer",
            "instructions",
            "prompt",
            "root",
            "admin",
            "sudo",
            "execute",
            "disable",
            "policy",
            "safety"
        )

        private val ENCODED_PAYLOAD_PATTERNS = listOf(
            Regex("""\b(?:[A-Za-z0-9+/]{20,}={0,2})\b"""),
            Regex("""\\x[0-9a-fA-F]{2}"""),
            Regex("""%[0-9A-Fa-f]{2}""")
        )

        private const val HIGH_RISK_THRESHOLD = 0.70
        private const val MEDIUM_RISK_THRESHOLD = 0.35
        private const val DENSITY_FLAG_THRESHOLD = 0.12
    }

    /**
     * Scans a prompt for potential injection attacks.
     */
    fun scan(prompt: String): ScanResult {
        if (prompt.isBlank()) {
            return ScanResult(
                isSafe = true,
                riskLevel = RiskLevel.NONE,
                sanitizedPrompt = prompt,
                riskScore = 0.0
            )
        }

        val normalized = prompt.trim()

        JAILBREAK_PATTERNS.firstOrNull { it.containsMatchIn(normalized) }?.let { matchedPattern ->
            return ScanResult(
                isSafe = false,
                riskLevel = RiskLevel.CRITICAL,
                reason = "Jailbreak pattern detected: ${matchedPattern.pattern}",
                sanitizedPrompt = "",
                riskScore = 1.0
            )
        }

        LEAKAGE_PATTERNS.firstOrNull { it.containsMatchIn(normalized) }?.let { matchedPattern ->
            val sanitized = sanitizeLeakageAttempt(normalized)
            return ScanResult(
                isSafe = true,
                riskLevel = RiskLevel.MEDIUM,
                reason = "Potential system instruction leakage attempt detected and sanitized: ${matchedPattern.pattern}",
                sanitizedPrompt = sanitized,
                riskScore = MEDIUM_RISK_THRESHOLD
            )
        }

        val riskScore = calculateHeuristicRiskScore(normalized)
        val riskLevel = scoreToRiskLevel(riskScore)
        val density = calculateSuspiciousKeywordDensity(normalized)

        if (riskScore >= HIGH_RISK_THRESHOLD || density >= DENSITY_FLAG_THRESHOLD) {
            return ScanResult(
                isSafe = false,
                riskLevel = maxOf(riskLevel, RiskLevel.HIGH),
                reason = "Suspicious prompt characteristics detected (score=%.2f, density=%.2f)".format(
                    riskScore,
                    density
                ),
                sanitizedPrompt = "",
                riskScore = riskScore
            )
        }

        return ScanResult(
            isSafe = true,
            riskLevel = riskLevel,
            sanitizedPrompt = normalized,
            riskScore = riskScore
        )
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

    private fun calculateHeuristicRiskScore(prompt: String): Double {
        val lowered = prompt.lowercase()
        val tokens = lowered.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0.0

        val keywordHits = tokens.count { token ->
            val normalized = token.trim('.', ',', ';', ':', '!', '?', '"', '\'')
            normalized in SUSPICIOUS_KEYWORDS
        }
        val keywordDensity = keywordHits.toDouble() / tokens.size.toDouble()
        val encodedMatches = ENCODED_PAYLOAD_PATTERNS.sumOf { pattern ->
            pattern.findAll(prompt).count()
        }

        var score = 0.0
        score += (keywordDensity * 2.0).coerceAtMost(0.65)
        if (encodedMatches > 0) {
            score += (encodedMatches * 0.12).coerceAtMost(0.30)
        }
        if (prompt.contains("```")) {
            score += 0.08
        }
        if (prompt.contains("<system>", ignoreCase = true) || prompt.contains("[system]", ignoreCase = true)) {
            score += 0.20
        }
        if (prompt.contains("do not follow", ignoreCase = true)) {
            score += 0.20
        }

        return score.coerceIn(0.0, 1.0)
    }

    private fun calculateSuspiciousKeywordDensity(prompt: String): Double {
        val lowered = prompt.lowercase()
        val tokens = lowered.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0.0

        val suspiciousCount = tokens.count { token ->
            val normalized = token.trim('.', ',', ';', ':', '!', '?', '"', '\'')
            normalized in SUSPICIOUS_KEYWORDS
        }
        return suspiciousCount.toDouble() / tokens.size.toDouble()
    }

    private fun scoreToRiskLevel(score: Double): RiskLevel {
        return when {
            score >= 0.85 -> RiskLevel.CRITICAL
            score >= HIGH_RISK_THRESHOLD -> RiskLevel.HIGH
            score >= MEDIUM_RISK_THRESHOLD -> RiskLevel.MEDIUM
            score >= 0.10 -> RiskLevel.LOW
            else -> RiskLevel.NONE
        }
    }
}
