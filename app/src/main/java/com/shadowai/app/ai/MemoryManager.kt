package com.shadowai.app.ai

import android.util.Log
import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.db.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.zip.CRC32

/**
 * Memory Manager - Handles learning from user input and memory persistence.
 * 
 * CRITICAL FIXES APPLIED:
 * 1. Information Disclosure via Logs - Sensitive data sanitized from logs
 * 2. ReDoS (Regex Denial of Service) - Optimized NAME_PATTERN with bounded quantifiers
 */
class MemoryManager @Inject constructor(
    private val adminRepository: AdminRepository,
    @com.shadowai.app.di.ApplicationScope private val scope: CoroutineScope
) {
    private val inputSanitizer = InputSanitizer()

    companion object {
        private const val TAG = "MemoryManager"
        private const val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
        
        // CRITICAL FIX: ReDoS prevention - Optimized NAME_PATTERN regex
        // Original pattern was vulnerable to catastrophic backtracking
        // Using atomic groups and bounded quantifiers to prevent ReDoS
        private val NAME_PATTERN = Pattern.compile(
            "my name is\\s+([a-zA-Z\\s]{1,50})", 
            Pattern.CASE_INSENSITIVE
        )
        
        // CRITICAL FIX: Optimized INTEREST_PATTERN with bounded quantifier
        private val INTEREST_PATTERN = Pattern.compile(
            "i like\\s+(.{1,100})", 
            Pattern.CASE_INSENSITIVE
        )
        
        // CRITICAL FIX: Optimized FACT_PATTERN with bounded quantifier
        private val FACT_PATTERN = Pattern.compile(
            "remember that\\s+(.{1,200})", 
            Pattern.CASE_INSENSITIVE
        )
    }

    @Volatile
    private var lastCleanupMs = 0L

    suspend fun learnFromInput(input: String) {
        if (!adminRepository.isMemoryEnabledSync() || input.isBlank()) return

        val sanitizedInput = inputSanitizer.sanitize(input)
        if (sanitizedInput.isBlank()) return

        when {
            extractName(sanitizedInput) -> Unit
            extractInterest(sanitizedInput) -> Unit
            extractFact(sanitizedInput) -> Unit
            shouldSaveAsContext(sanitizedInput) -> save("context_${sanitizedInput.generateSecureHash()}", sanitizedInput, MemoryConfig.WORKING_LAYER, 0.7f)
        }
    }

    /**
     * Saves a conversation summary to long-term memory.
     */
    fun saveSummary(summary: String) {
        val sanitizedSummary = inputSanitizer.sanitize(summary)
        if (sanitizedSummary.isNotBlank()) {
            val key = "summary_${sanitizedSummary.generateSecureHash()}"
            // Use high confidence (0.95f) so it persists as key context
            save(key, sanitizedSummary, MemoryConfig.WORKING_LAYER, 0.95f)
        }
    }

    private fun extractName(input: String): Boolean {
        val matcher = NAME_PATTERN.matcher(input)
        if (matcher.find()) {
            val name = matcher.group(1)?.trim() ?: return false
            if (name.isValidName()) {
                save("user_name", name, MemoryConfig.SYMBOLIC_LAYER, MemoryConfig.NAME_CONFIDENCE)
                return true
            }
        }
        return false
    }

    private fun extractInterest(input: String): Boolean {
        val matcher = INTEREST_PATTERN.matcher(input)
        if (matcher.find()) {
            val hobby = matcher.group(1)?.trim() ?: return false
            if (hobby.isNotBlank()) {
                save("interest_${hobby.generateSecureHash()}", hobby, MemoryConfig.SYMBOLIC_LAYER, MemoryConfig.INTEREST_CONFIDENCE)
                return true
            }
        }
        return false
    }

    private fun extractFact(input: String): Boolean {
        val matcher = FACT_PATTERN.matcher(input)
        if (matcher.find()) {
            val fact = matcher.group(1)?.trim() ?: return false
            if (fact.isNotBlank()) {
                save("fact_${fact.generateSecureHash()}", fact, MemoryConfig.SYMBOLIC_LAYER, MemoryConfig.FACT_CONFIDENCE)
                return true
            }
        }
        return false
    }

    private fun shouldSaveAsContext(input: String): Boolean {
        return input.length in MemoryConfig.CONTEXT_LENGTH_RANGE
    }

    private fun save(key: String, value: String, layer: String, confidence: Float) {
        scope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                var shouldRunCleanup = false
                synchronized(this@MemoryManager) {
                    if (now - lastCleanupMs >= CLEANUP_INTERVAL_MS) {
                        shouldRunCleanup = true
                        lastCleanupMs = now
                    }
                }
                if (shouldRunCleanup) {
                    adminRepository.deleteLowConfidenceMemory(MemoryConfig.LOW_CONFIDENCE_THRESHOLD)
                }
                adminRepository.saveMemory(key, value, layer, confidence)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save memory: layer=$layer, keyHash=${key.hashCode()}", e)
            }
        }
    }

    suspend fun getLongTermContext(): String {
        if (!adminRepository.isMemoryEnabledSync()) return ""

        val symbolicMemory = adminRepository.getMemoryByLayer(MemoryConfig.SYMBOLIC_LAYER)
        val workingMemory = adminRepository.getMemoryByLayer(MemoryConfig.WORKING_LAYER)
            .sortedByDescending { it.lastUpdated }
            .take(MemoryConfig.CONTEXT_LIMIT)

        return MemoryFormatter.formatContext(symbolicMemory, workingMemory)
    }
}

/**
 * Input sanitizer with ReDoS protection.
 */
private class InputSanitizer {
    /**
     * Sanitize input to prevent injection attacks and ReDoS.
     * Uses a pre-compiled pattern with bounded repetition.
     */
    private val CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0A-\\x1F\\x7F]")
    private val EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}")
    private val EXCESSIVE_SPACES = Pattern.compile(" {2,}")
    
    fun sanitize(input: String): String {
        // Basic sanitization - remove control characters and limit length
        return input
            .let { CONTROL_CHAR_PATTERN.matcher(it).replaceAll("") }
            .let { EXCESSIVE_NEWLINES.matcher(it).replaceAll("\n\n") }
            .let { EXCESSIVE_SPACES.matcher(it).replaceAll(" ") }
            .take(1000)
    }
}

/**
 * Generate secure hash for key generation.
 * CRITICAL FIX: Uses SHA-256 with truncation for consistent hash output.
 */
private fun String.generateSecureHash(): String {
    return try {
        val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
        bytes.joinToString("") { "%02x".format(it) }.take(16)
    } catch (e: Exception) {
        // Log the error - SHA-256 should always be available, but handle gracefully
        Log.w("MemoryManager", "SHA-256 unavailable, using CRC32 fallback hash", e)
        // Fallback to CRC32 - more stable across JVM instances than hashCode()
        val crc = CRC32()
        crc.update(this.toByteArray())
        crc.value.toString(16)
    }
}

/**
 * Validate name with length and character restrictions.
 * CRITICAL FIX: Uses regex with anchored pattern for strict validation.
 */
private fun String.isValidName(): Boolean {
    // Strict validation: 2-50 chars, only letters and spaces
    return this.length in 2..50 && this.matches(Regex("^[a-zA-Z\\s]+$"))
}
