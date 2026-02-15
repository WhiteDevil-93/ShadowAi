package com.shadowai.backend

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookVerifier {
    private const val HMAC_SHA256 = "HmacSHA256"
    
    // Replay protection settings
    private const val TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L // 5 minutes tolerance
    private const val MAX_NONCE_CACHE_SIZE = 10000
    private const val NONCE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
    
    // Thread-safe nonce cache with timestamps for cleanup
    private val usedNonces = ConcurrentHashMap<String, Long>()

    /**
     * Verify webhook signature with replay protection.
     *
     * @param body The request body
     * @param signature The HMAC signature (hex encoded)
     * @param secret The shared secret
     * @param timestamp Optional timestamp header (milliseconds since epoch or ISO8601)
     * @param nonce Optional unique nonce to prevent replay attacks
     * @return true if verification succeeds, false otherwise
     */
    fun verify(
        body: String,
        signature: String?,
        secret: String?,
        timestamp: String? = null,
        nonce: String? = null
    ): Boolean {
        // Fail closed: If secret is not configured, reject the webhook.
        if (secret.isNullOrBlank()) return false
        if (signature.isNullOrBlank()) return false

        // Replay protection: Validate timestamp if provided
        if (timestamp != null) {
            val timestampMs = parseTimestamp(timestamp)
            if (timestampMs == null || !isTimestampValid(timestampMs)) {
                return false
            }
        }
        
        // Replay protection: Check and record nonce if provided
        if (nonce != null) {
            if (!recordNonce(nonce)) {
                return false // Nonce was already used (replay attack)
            }
        }

        val computed = hmac(body, secret)
        val providedSignatureBytes = decodeSignature(signature) ?: return false

        // Constant-time comparison
        return MessageDigest.isEqual(computed, providedSignatureBytes)
    }
    
    /**
     * Parse timestamp from string (supports milliseconds or ISO8601 format)
     */
    private fun parseTimestamp(timestamp: String): Long? {
        return try {
            // Try parsing as milliseconds
            timestamp.toLongOrNull()
                ?: java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if timestamp is within acceptable tolerance window
     */
    private fun isTimestampValid(timestampMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val diff = kotlin.math.abs(now - timestampMs)
        return diff <= TIMESTAMP_TOLERANCE_MS
    }
    
    /**
     * Record a nonce and return false if it was already used (replay attack)
     * Includes periodic cleanup of expired nonces
     */
    private fun recordNonce(nonce: String): Boolean {
        val now = System.currentTimeMillis()
        
        // Periodic cleanup: remove expired nonces when cache is getting large
        if (usedNonces.size > MAX_NONCE_CACHE_SIZE / 2) {
            cleanupExpiredNonces(now)
        }
        
        // Check if nonce already exists (atomic putIfAbsent)
        val existing = usedNonces.putIfAbsent(nonce, now)
        return existing == null // Returns true if nonce was newly added
    }
    
    /**
     * Remove expired nonces from the cache
     */
    private fun cleanupExpiredNonces(now: Long) {
        usedNonces.entries.removeIf { (_, timestamp) ->
            now - timestamp > NONCE_EXPIRY_MS
        }
    }

    private fun hmac(data: String, secret: String): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        mac.init(key)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun decodeSignature(signature: String): ByteArray? {
        val normalized = signature.trim()
            .removePrefix("sha256=")
            .removePrefix("SHA256=")
            .removePrefix("SHA-256=")

        val hexDecoded = runCatching { hexStringToByteArray(normalized) }.getOrNull()
        if (hexDecoded != null) return hexDecoded

        return runCatching { java.util.Base64.getDecoder().decode(normalized) }.getOrNull()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        require(s.length % 2 == 0) { "Hex string must have an even length" }
        return s.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
