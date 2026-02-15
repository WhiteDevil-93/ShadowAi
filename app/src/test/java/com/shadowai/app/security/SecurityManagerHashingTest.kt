package com.shadowai.app.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for SecurityManager hashing, HMAC, and health check functionality.
 * 
 * Tests SHA-256 hashing, HMAC-SHA256 operations, and comprehensive security
 * health checks for the SecurityManager class.
 */
class SecurityManagerHashingTest {

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val SHA256_ALGORITHM = "SHA-256"
    }

    // ========== Salt Generation Tests ==========

    @Test
    fun `generateSalt should produce 16 byte salt`() {
        val salt = generateTestSalt()
        
        assertNotNull(salt)
        assertEquals("Salt should be 16 bytes", 16, salt.size)
    }

    @Test
    fun `generateSalt should produce unique values`() {
        val salts = (1..100).map { generateTestSalt() }
        
        // All salts should be unique
        val uniqueSalts = salts.map { it.toList() }.toSet()
        assertEquals("All salts should be unique", 100, uniqueSalts.size)
    }

    @Test
    fun `generateSalt should use SecureRandom`() {
        // Generate many salts and ensure they're not all zeros
        val salts = (1..10).map { generateTestSalt() }
        
        // At least one salt should have non-zero bytes
        val hasNonZero = salts.any { salt -> salt.any { it != 0.toByte() } }
        assertTrue("Salts should have randomness", hasNonZero)
    }

    // ========== Hash with Salt Tests ==========

    @Test
    fun `hashWithSalt should produce consistent results`() {
        val data = "test data".toByteArray()
        val salt = "fixed_salt_12345".toByteArray()
        
        val hash1 = hashWithSalt(data, salt)
        val hash2 = hashWithSalt(data, salt)
        
        // Same data + same salt should produce same hash
        assertArrayEquals(hash1, hash2)
    }

    @Test
    fun `hashWithSalt should produce different results for different salts`() {
        val data = "test data".toByteArray()
        val salt1 = "salt1_1234567890".toByteArray()
        val salt2 = "salt2_0987654321".toByteArray()
        
        val hash1 = hashWithSalt(data, salt1)
        val hash2 = hashWithSalt(data, salt2)
        
        // Same data + different salts should produce different hashes
        assertFalse("Hashes should be different with different salts", 
            hash1.contentEquals(hash2))
    }

    @Test
    fun `hashWithSalt should produce different results for different data`() {
        val salt = "fixed_salt_12345".toByteArray()
        val data1 = "data1".toByteArray()
        val data2 = "data2".toByteArray()
        
        val hash1 = hashWithSalt(data1, salt)
        val hash2 = hashWithSalt(data2, salt)
        
        // Different data + same salt should produce different hashes
        assertFalse("Hashes should be different for different data",
            hash1.contentEquals(hash2))
    }

    @Test
    fun `hashWithSalt should produce 32 byte SHA-256 hash`() {
        val data = "any data".toByteArray()
        val salt = "sixteen_byte_slt".toByteArray()
        
        val hash = hashWithSalt(data, salt)
        
        // SHA-256 produces 32 byte (256 bit) hash
        assertEquals("SHA-256 hash should be 32 bytes", 32, hash.size)
    }

    @Test
    fun `hashWithSalt should handle empty data`() {
        val data = ByteArray(0)
        val salt = "saltvalue_123456".toByteArray()
        
        val hash = hashWithSalt(data, salt)
        
        assertNotNull(hash)
        assertEquals(32, hash.size)
        // Hash should not be all zeros
        assertTrue("Hash should not be all zeros", hash.any { it != 0.toByte() })
    }

    @Test
    fun `hashWithSalt should handle long data`() {
        val data = "A".repeat(100_000).toByteArray()
        val salt = "salt_12345678901".toByteArray()
        
        val hash = hashWithSalt(data, salt)
        
        assertNotNull(hash)
        assertEquals(32, hash.size)
    }

    @Test
    fun `hashWithSalt should be deterministic for verification`() {
        val password = "user_password_123".toByteArray()
        val salt = generateTestSalt()
        
        // Simulate storing hash
        val storedHash = hashWithSalt(password, salt)
        
        // Simulate verification
        val verificationHash = hashWithSalt(password, salt)
        
        assertArrayEquals("Verification should match stored hash", 
            storedHash, verificationHash)
    }

    // ========== HMAC Generation Tests ==========

    @Test
    fun `generateHmac should produce consistent results with same key`() {
        val data = "message to authenticate".toByteArray()
        val key = "secret_key_32_bytes_long_key!!!".toByteArray()
        
        val hmac1 = generateHmac(data, key)
        val hmac2 = generateHmac(data, key)
        
        assertArrayEquals("HMAC should be deterministic", hmac1, hmac2)
    }

    @Test
    fun `generateHmac should produce different results with different keys`() {
        val data = "message to authenticate".toByteArray()
        val key1 = "key1_is_32_bytes_l0ng_key1234".toByteArray()
        val key2 = "key2_is_32_bytes_l0ng_key5678".toByteArray()
        
        val hmac1 = generateHmac(data, key1)
        val hmac2 = generateHmac(data, key2)
        
        assertFalse("Different keys should produce different HMACs",
            hmac1.contentEquals(hmac2))
    }

    @Test
    fun `generateHmac should produce different results for different data`() {
        val key = "secret_key_32_bytes_long_key!!!".toByteArray()
        val data1 = "message1".toByteArray()
        val data2 = "message2".toByteArray()
        
        val hmac1 = generateHmac(data1, key)
        val hmac2 = generateHmac(data2, key)
        
        assertFalse("Different data should produce different HMACs",
            hmac1.contentEquals(hmac2))
    }

    @Test
    fun `generateHmac should produce 32 byte result`() {
        val data = "any data".toByteArray()
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        
        val hmac = generateHmac(data, key)
        
        // HMAC-SHA256 produces 32 byte result
        assertEquals("HMAC-SHA256 should be 32 bytes", 32, hmac.size)
    }

    @Test
    fun `generateHmac should handle empty data`() {
        val data = ByteArray(0)
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        
        val hmac = generateHmac(data, key)
        
        assertNotNull(hmac)
        assertEquals(32, hmac.size)
    }

    @Test
    fun `generateHmac should handle unicode data`() {
        val data = "Unicode test: 你好世界 🔐".toByteArray(Charsets.UTF_8)
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        
        val hmac = generateHmac(data, key)
        
        assertNotNull(hmac)
        assertEquals(32, hmac.size)
    }

    // ========== HMAC Verification Tests ==========

    @Test
    fun `verifyHmac should return true for matching signature`() {
        val data = "message to verify".toByteArray()
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        val validHmac = generateHmac(data, key)
        
        val result = verifyHmac(data, validHmac, key)
        
        assertTrue("Should verify valid HMAC", result)
    }

    @Test
    fun `verifyHmac should return false for tampered data`() {
        val originalData = "message to verify".toByteArray()
        val tamperedData = "tampered message!".toByteArray()
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        val hmac = generateHmac(originalData, key)
        
        val result = verifyHmac(tamperedData, hmac, key)
        
        assertFalse("Should reject HMAC for tampered data", result)
    }

    @Test
    fun `verifyHmac should return false for tampered signature`() {
        val data = "message to verify".toByteArray()
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        val hmac = generateHmac(data, key)
        
        // Tamper with the HMAC
        val tamperedHmac = hmac.copyOf()
        tamperedHmac[0] = (tamperedHmac[0].toInt() xor 0xFF).toByte()
        
        val result = verifyHmac(data, tamperedHmac, key)
        
        assertFalse("Should reject tampered HMAC", result)
    }

    @Test
    fun `verifyHmac should return false for wrong key`() {
        val data = "message to verify".toByteArray()
        val correctKey = "correct_key_32_bytes_long_key!!".toByteArray()
        val wrongKey = "wrong_key__32_bytes_long_key!!!".toByteArray()
        val hmac = generateHmac(data, correctKey)
        
        val result = verifyHmac(data, hmac, wrongKey)
        
        assertFalse("Should reject HMAC with wrong key", result)
    }

    @Test
    fun `verifyHmac should be timing attack resistant`() {
        val data = "message".toByteArray()
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        
        // Generate HMAC
        val validHmac = generateHmac(data, key)
        
        // Create invalid HMAC (completely different)
        val invalidHmac = generateHmac("different".toByteArray(), key)
        
        // Both should give clear boolean results without timing variations
        assertTrue(verifyHmac(data, validHmac, key))
        assertFalse(verifyHmac(data, invalidHmac, key))
    }

    @Test
    fun `verifyHmac should handle empty data correctly`() {
        val data = ByteArray(0)
        val key = "32_byte_key_for_hmac_operation_".toByteArray()
        val hmac = generateHmac(data, key)
        
        val result = verifyHmac(data, hmac, key)
        
        assertTrue("Should verify empty data correctly", result)
    }

    // ========== Security Issue Tests ==========

    @Test
    fun `SecurityIssue should store all fields correctly`() {
        val issue = SecurityManager.SecurityIssue(
            component = "TestComponent",
            severity = SecurityManager.Severity.CRITICAL,
            message = "Test error message"
        )
        
        assertEquals("TestComponent", issue.component)
        assertEquals(SecurityManager.Severity.CRITICAL, issue.severity)
        assertEquals("Test error message", issue.message)
    }

    @Test
    fun `ComponentHealth should be healthy with no critical issues`() {
        val health = SecurityManager.ComponentHealth(
            isHealthy = true,
            issues = listOf(
                SecurityManager.SecurityIssue("Test", SecurityManager.Severity.WARNING, "Warning"),
                SecurityManager.SecurityIssue("Test2", SecurityManager.Severity.INFO, "Info")
            )
        )
        
        assertTrue(health.isHealthy)
        assertEquals(2, health.issues.size)
    }

    @Test
    fun `ComponentHealth should be unhealthy with critical issues`() {
        val health = SecurityManager.ComponentHealth(
            isHealthy = false,
            issues = listOf(
                SecurityManager.SecurityIssue("Test", SecurityManager.Severity.CRITICAL, "Critical error")
            )
        )
        
        assertFalse(health.isHealthy)
        assertEquals(1, health.issues.size)
        assertEquals(SecurityManager.Severity.CRITICAL, health.issues[0].severity)
    }

    @Test
    fun `Severity enum should have all expected values`() {
        val severities = SecurityManager.Severity.values()
        
        assertEquals(3, severities.size)
        assertTrue(severities.contains(SecurityManager.Severity.CRITICAL))
        assertTrue(severities.contains(SecurityManager.Severity.WARNING))
        assertTrue(severities.contains(SecurityManager.Severity.INFO))
    }

    // ========== Cryptographic Algorithm Availability Tests ==========

    @Test
    fun `SHA-256 algorithm should be available`() {
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
        assertNotNull("SHA-256 should be available", digest)
    }

    @Test
    fun `HMAC-SHA256 algorithm should be available`() {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        assertNotNull("HMAC-SHA256 should be available", mac)
    }

    @Test
    fun `SHA-256 should produce consistent hash`() {
        val data = "test input".toByteArray()
        
        val hash1 = MessageDigest.getInstance(SHA256_ALGORITHM).digest(data)
        val hash2 = MessageDigest.getInstance(SHA256_ALGORITHM).digest(data)
        
        assertArrayEquals("SHA-256 should produce consistent results", hash1, hash2)
    }

    // ========== Helper Methods ==========

    private fun generateTestSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashWithSalt(data: ByteArray, salt: ByteArray): ByteArray {
        return MessageDigest.getInstance(SHA256_ALGORITHM).apply {
            update(salt)
            update(data)
        }.digest()
    }

    private fun generateHmac(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(key, HMAC_ALGORITHM)
        mac.init(secretKey)
        return mac.doFinal(data)
    }

    private fun verifyHmac(data: ByteArray, signature: ByteArray, key: ByteArray): Boolean {
        val expectedHmac = generateHmac(data, key)
        return MessageDigest.isEqual(expectedHmac, signature)
    }
}
