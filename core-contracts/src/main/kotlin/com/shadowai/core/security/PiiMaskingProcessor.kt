package com.shadowai.core.security

import android.util.Patterns
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale
import java.util.regex.Pattern

/**
 * Detects and masks Personally Identifiable Information (PII) in text.
 */
@Singleton
class PiiMaskingProcessor @Inject constructor() {

    companion object {
        // Robust IP Address matching (IPv4)
        private val IP_ADDRESS_PATTERN = Regex(
            """\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b"""
        )

        // Credit card candidate pattern - validated by Luhn in code.
        private val CREDIT_CARD_CANDIDATE_PATTERN = Regex("""\b(?:\d[ -]?){13,19}\b""")
        private val CREDIT_CARD_FRAGMENT_PATTERN = Regex("""(?:\d{4}[ -]?){3,4}""")

        // SSN Pattern (US format)
        private val SSN_PATTERN = Regex(
            """\b(?!000|666|9\d{2})\d{3}[-\s]?(?!00)\d{2}[-\s]?(?!0000)\d{4}\b"""
        )

        // Generic API key pattern (at least 32 characters)
        private val API_KEY_PATTERN = Regex("""\b[A-Za-z0-9_-]{32,}\b""")

        // High entropy secret (hex or base64 patterns)
        private val HIGH_ENTROPY_PATTERN = Regex("""\b[a-fA-F0-9]{32,}\b|\b[A-Za-z0-9+/]{40,}={0,2}\b""")

        // Fallback patterns when android.util.Patterns cannot be used (local JVM tests).
        private val FALLBACK_PHONE_PATTERN = Regex(
            """(?<!\w)(?:\+?\d{1,3}[-.\s]?)?(?:\(\d{2,4}\)|\d{2,4})[-.\s]?\d{3,4}[-.\s]?\d{3,4}(?!\w)"""
        )
        private val FALLBACK_EMAIL_PATTERN = Regex(
            """(?i)\b[\p{L}\p{N}._%+\-]+@[\p{L}\p{N}\-]+(?:\.[\p{L}\p{N}\-]+)+\b"""
        )

        private const val MASK_CHAR = '•'

        // Type-specific placeholders
        private const val EMAIL_REDACTED = "[EMAIL_REDACTED]"
        private const val PHONE_REDACTED = "[PHONE_REDACTED]"
        private const val CC_REDACTED = "[CC_REDACTED]"
        private const val SSN_REDACTED = "[SSN_REDACTED]"
        private const val IP_REDACTED = "[IP_REDACTED]"
        private const val API_KEY_REDACTED = "[API_KEY_REDACTED]"
        private const val SECRET_REDACTED = "[SECRET_REDACTED]"
    }

    data class PiiDetectionResult(
        val containsPii: Boolean,
        val confidence: Double,
        val detectedTypes: List<PiiType>,
        val matchCount: Int
    )

    enum class PiiType {
        EMAIL,
        PHONE,
        CREDIT_CARD,
        SSN,
        API_KEY,
        IP_ADDRESS,
        HIGH_ENTROPY_SECRET
    }

    fun detectPii(text: String): PiiDetectionResult {
        if (text.isBlank()) return PiiDetectionResult(false, 0.0, emptyList(), 0)

        val detectedTypes = mutableListOf<PiiType>()
        var totalMatches = 0
        var confidenceScore = 0.0

        val emailMatchesCount = findEmailMatches(text).size
        if (emailMatchesCount > 0) {
            detectedTypes.add(PiiType.EMAIL)
            totalMatches += emailMatchesCount
            confidenceScore += emailMatchesCount * 0.9
        }

        val phoneMatchesCount = findPhoneMatches(text).size
        if (phoneMatchesCount > 0) {
            detectedTypes.add(PiiType.PHONE)
            totalMatches += phoneMatchesCount
            confidenceScore += phoneMatchesCount * 0.8
        }

        val creditCardMatches = findValidCreditCards(text).size
        if (creditCardMatches > 0) {
            detectedTypes.add(PiiType.CREDIT_CARD)
            totalMatches += creditCardMatches
            confidenceScore += creditCardMatches * 0.95
        }

        val ssnMatches = SSN_PATTERN.findAll(text).count()
        if (ssnMatches > 0) {
            detectedTypes.add(PiiType.SSN)
            totalMatches += ssnMatches
            confidenceScore += ssnMatches * 0.85
        }

        val ipMatches = IP_ADDRESS_PATTERN.findAll(text).count()
        if (ipMatches > 0) {
            detectedTypes.add(PiiType.IP_ADDRESS)
            totalMatches += ipMatches
            confidenceScore += ipMatches * 0.6
        }

        val apiKeyMatches = findContextualApiKeys(text).size
        if (apiKeyMatches > 0) {
            detectedTypes.add(PiiType.API_KEY)
            totalMatches += apiKeyMatches
            confidenceScore += apiKeyMatches * 0.5
        }

        val entropyMatches = HIGH_ENTROPY_PATTERN.findAll(text).count()
        if (entropyMatches > 0) {
            detectedTypes.add(PiiType.HIGH_ENTROPY_SECRET)
            totalMatches += entropyMatches
            confidenceScore += entropyMatches * 0.3
        }

        val normalizedConfidence = if (detectedTypes.isNotEmpty()) {
            (confidenceScore / detectedTypes.size).coerceIn(0.0, 1.0)
        } else 0.0

        return PiiDetectionResult(
            containsPii = detectedTypes.isNotEmpty(),
            confidence = normalizedConfidence,
            detectedTypes = detectedTypes,
            matchCount = totalMatches
        )
    }

    fun containsPii(text: String): Boolean = detectPii(text).containsPii

    fun maskPii(text: String): String {
        if (text.isBlank()) return text
        var masked = text

        masked = maskMatches(masked, findEmailMatches(masked), EMAIL_REDACTED)
        masked = maskMatches(masked, findValidCreditCards(masked), CC_REDACTED)
        masked = maskMatches(masked, findPhoneMatches(masked), PHONE_REDACTED)
        masked = SSN_PATTERN.replace(masked, SSN_REDACTED)
        masked = IP_ADDRESS_PATTERN.replace(masked, IP_REDACTED)
        masked = maskMatches(masked, findContextualApiKeys(masked), API_KEY_REDACTED)

        masked = HIGH_ENTROPY_PATTERN.replace(masked, SECRET_REDACTED)

        return masked
    }

    /**
     * Redacts PII by replacing characters with [MASK_CHAR], preserving partial context
     * (e.g., email domains).
     */
    fun redactPii(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder(text)

        val emailMatches = findEmailMatches(sb.toString()).reversed()
        for (match in emailMatches) {
            val email = sb.substring(match.range)
            val parts = email.split("@")
            if (parts.size == 2) {
                val maskedLocal = MASK_CHAR.toString().repeat(parts[0].length)
                val redacted = "$maskedLocal@${parts[1]}"
                sb.replace(match.range.first, match.range.last + 1, redacted)
            }
        }

        redactPattern(sb, CREDIT_CARD_CANDIDATE_PATTERN, MASK_CHAR) { candidate ->
            isLikelyCreditCard(candidate)
        }
        redactPattern(sb, SSN_PATTERN, MASK_CHAR)
        redactPattern(sb, IP_ADDRESS_PATTERN, MASK_CHAR)
        findPhoneMatches(sb.toString()).reversed().forEach { match ->
            val masked = MASK_CHAR.toString().repeat(match.range.last - match.range.first + 1)
            sb.replace(match.range.first, match.range.last + 1, masked)
        }
        findContextualApiKeys(sb.toString()).reversed().forEach { match ->
            val masked = MASK_CHAR.toString().repeat(match.range.last - match.range.first + 1)
            sb.replace(match.range.first, match.range.last + 1, masked)
        }
        redactPattern(sb, HIGH_ENTROPY_PATTERN, MASK_CHAR)

        return sb.toString()
    }

    /**
     * Masks only the highest risk items (Emails, CC, SSN, Secrets) using fixed labels.
     */
    fun maskHighRiskPiiOnly(text: String): String {
        if (text.isBlank()) return text
        var masked = text
        masked = maskMatches(masked, findEmailMatches(masked), EMAIL_REDACTED)
        masked = maskMatches(masked, findValidCreditCards(masked), CC_REDACTED)
        masked = SSN_PATTERN.replace(masked, SSN_REDACTED)
        masked = maskMatches(masked, findContextualApiKeys(masked), API_KEY_REDACTED)
        masked = HIGH_ENTROPY_PATTERN.replace(masked, SECRET_REDACTED)
        return masked
    }

    private fun redactPattern(
        sb: StringBuilder,
        pattern: Regex,
        maskChar: Char,
        predicate: ((String) -> Boolean)? = null
    ) {
        val matches = pattern.findAll(sb.toString()).toList().reversed()
        for (match in matches) {
            if (predicate != null && !predicate(match.value)) continue
            val masked = maskChar.toString().repeat(match.value.length)
            sb.replace(match.range.first, match.range.last + 1, masked)
        }
    }

    private fun hasApiKeyContext(text: String): Boolean {
        val contextWords = listOf(
            "api", "key", "token", "secret", "password",
            "auth", "bearer", "credential", "access", "private"
        )
        val lowerText = text.lowercase()
        return contextWords.any { lowerText.contains(it) }
    }

    private data class MatchRange(val range: IntRange)

    private fun findEmailMatches(text: String): List<MatchRange> {
        val androidMatches = runCatching {
            findPatternMatches(Patterns.EMAIL_ADDRESS, text)
        }.getOrNull().orEmpty()
        if (androidMatches.isNotEmpty()) return androidMatches.map { MatchRange(it) }

        return FALLBACK_EMAIL_PATTERN.findAll(text)
            .map { MatchRange(it.range) }
            .toList()
    }

    private fun findPhoneMatches(text: String): List<MatchRange> {
        val matches = mutableListOf<MatchRange>()

        val androidMatches = runCatching {
            findPatternMatches(Patterns.PHONE, text)
        }.getOrNull().orEmpty()
        matches += androidMatches.map { MatchRange(it) }

        matches += FALLBACK_PHONE_PATTERN.findAll(text)
            .map { MatchRange(it.range) }
            .toList()

        return matches
            .distinctBy { it.range.first to it.range.last }
            .filter { match -> isLikelyPhoneCandidate(text, match.range) }
    }

    private fun findPatternMatches(pattern: Pattern, text: String): List<IntRange> {
        val matcher = pattern.matcher(text)
        val ranges = mutableListOf<IntRange>()
        while (matcher.find()) {
            val value = matcher.group()?.trim().orEmpty()
            if (value.isBlank()) continue
            ranges += (matcher.start() until matcher.end())
        }
        return ranges
    }

    private fun findValidCreditCards(text: String): List<MatchRange> {
        return CREDIT_CARD_CANDIDATE_PATTERN.findAll(text)
            .filter { match -> isLikelyCreditCard(match.value) }
            .map { match -> MatchRange(match.range) }
            .toList()
    }

    private fun isLikelyCreditCard(value: String): Boolean {
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 13..19) return false
        return isValidLuhn(digits)
    }

    private fun isValidLuhn(digits: String): Boolean {
        var sum = 0
        var shouldDouble = false
        for (i in digits.length - 1 downTo 0) {
            var digit = digits[i].digitToIntOrNull() ?: return false
            if (shouldDouble) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return sum % 10 == 0
    }

    private fun isLikelyPhoneCandidate(text: String, range: IntRange): Boolean {
        val value = text.substring(range.first, range.last + 1)
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 10..15) return false
        if (isLikelyCreditCard(value)) return false
        if (looksLikeCreditCardFragment(value)) return false
        if (isPartOfLongNumericSequence(text, range)) return false

        val hasFormatting = value.any { ch ->
            ch == '+' || ch == '-' || ch == ' ' || ch == '.' || ch == '(' || ch == ')'
        }
        return hasFormatting || digits.length == 10
    }

    private fun looksLikeCreditCardFragment(value: String): Boolean {
        return CREDIT_CARD_FRAGMENT_PATTERN.matches(value.trim())
    }

    private fun isPartOfLongNumericSequence(text: String, range: IntRange): Boolean {
        fun isNumericTokenChar(ch: Char): Boolean {
            return ch.isDigit() || ch == ' ' || ch == '-' || ch == '.'
        }

        var start = range.first
        while (start > 0 && isNumericTokenChar(text[start - 1])) {
            start--
        }

        var end = range.last
        while (end < text.lastIndex && isNumericTokenChar(text[end + 1])) {
            end++
        }

        val expanded = text.substring(start, end + 1)
        return expanded.filter(Char::isDigit).length > 15
    }

    private fun findContextualApiKeys(text: String): List<MatchRange> {
        val lowerText = text.lowercase(Locale.US)
        return API_KEY_PATTERN.findAll(text)
            .filter { match ->
                val start = (match.range.first - 48).coerceAtLeast(0)
                val endExclusive = (match.range.last + 49).coerceAtMost(lowerText.length)
                hasApiKeyContext(lowerText.substring(start, endExclusive))
            }
            .map { match -> MatchRange(match.range) }
            .toList()
    }

    private fun maskMatches(text: String, matches: List<MatchRange>, replacement: String): String {
        if (matches.isEmpty()) return text
        val sorted = matches.sortedByDescending { it.range.first }
        val sb = StringBuilder(text)
        for (match in sorted) {
            sb.replace(match.range.first, match.range.last + 1, replacement)
        }
        return sb.toString()
    }
}
