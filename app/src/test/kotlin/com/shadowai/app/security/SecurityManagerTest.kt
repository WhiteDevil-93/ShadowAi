package com.shadowai.app.security

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SecurityManager.
 * Tests encryption/decryption, hashing, and HMAC operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mock()
        securityManager = SecurityManager(mockContext)
    }

    // ========== AES-256-GCM Encryption Roundtrip Tests ==========

    @Test
    fun `encryption roundtrip - AES-256-GCM should encrypt and decrypt successfully`() {
        // Given: test data and key
        val testData = "Hello, World! This is sensitive data."
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_1")

        // When: encrypting the data
        val encrypted = securityManager.encrypt(testData, key)

        // Then: encrypted data should be different and Base64 encoded
        assertNotNull(encrypted)
        assertNotEquals(testData, encrypted)

        // When: decrypting the data
        val decrypted = securityManager.decrypt(encrypted, key)

        // Then: decrypted data should match original
        assertEquals(testData, decrypted)
    }

    @Test
    fun `encryption roundtrip - should handle empty string`() {
        // Given: empty string
        val testData = ""
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_2")

        // When: encrypting and decrypting
        val encrypted = securityManager.encrypt(testData, key)
        val decrypted = securityManager.decrypt(encrypted, key)

        // Then: should work with empty string
        assertEquals(testData, decrypted)
    }

    @Test
    fun `encryption roundtrip - should handle large data`() {
        // Given: large data (10KB)
        val testData = "A".repeat(10_000)
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_3")

        // When: encrypting and decrypting
        val encrypted = securityManager.encrypt(testData, key)
        val decrypted = securityManager.decrypt(encrypted, key)

        // Then: large data should roundtrip correctly
        assertEquals(testData, decrypted)
    }

    @Test
    fun `encryption roundtrip - should handle unicode and special characters`() {
        // Given: data with unicode and special characters
        val testData = "Hello 世界 🌍 ñ é ü \n\t\"'"
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_4")

        // When: encrypting and decrypting
        val encrypted = securityManager.encrypt(testData, key)
        val decrypted = securityManager.decrypt(encrypted, key)

        // Then: unicode/special chars should roundtrip correctly
        assertEquals(testData, decrypted)
    }

    @Test
    fun `encryption roundtrip - same data produces different ciphertext due to random IV`() {
        // Given: same data and key
        val testData = "Test data"
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_5")

        // When: encrypting twice
        val encrypted1 = securityManager.encrypt(testData, key)
        val encrypted2 = securityManager.encrypt(testData, key)

        // Then: ciphertexts should be different (due to random IV)
        assertNotEquals(encrypted1, encrypted2)

        // But both should decrypt to same plaintext
        assertEquals(testData, securityManager.decrypt(encrypted1, key))
        assertEquals(testData, securityManager.decrypt(encrypted2, key))
    }

    @Test(expected = Exception::class)
    fun `encryption roundtrip - decryption with wrong key should fail`() {
        // Given: encrypted data with one key
        val testData = "Secret message"
        val key1 = securityManager.generateHardwareBackedKey("test_encrypt_key_6")
        val key2 = securityManager.generateHardwareBackedKey("test_encrypt_key_7")
        val encrypted = securityManager.encrypt(testData, key1)

        // When: trying to decrypt with different key
        // Then: should throw exception
        securityManager.decrypt(encrypted, key2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encryption roundtrip - invalid payload should throw`() {
        // Given: invalid Base64 data (too short for IV)
        val invalidPayload = "abc123"
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_8")

        // When: trying to decrypt invalid payload
        // Then: should throw IllegalArgumentException
        securityManager.decrypt(invalidPayload, key)
    }

    @Test
    fun `encryption roundtrip - tampered ciphertext should fail`() {
        // Given: encrypted data
        val testData = "Secret message"
        val key = securityManager.generateHardwareBackedKey("test_encrypt_key_9")
        val encrypted = securityManager.encrypt(testData, key)

        // When: tampering with ciphertext (change a character)
        val tampered = encrypted.substring(0, 20) + "X" + encrypted.substring(21)

        // Then: decryption should fail with AEADBadTagException
        try {
            securityManager.decrypt(tampered, key)
            assertFalse(true, "Should have thrown exception")
        } catch (e: Exception) {
            // Expected - tampering detected by GCM authentication tag
            assertTrue(true)
        }
    }

    // ========== SHA-256 Hashing Tests ==========

    @Test
    fun `hashing with salt - SHA-256 should produce consistent results`() {
        // Given: data and salt
        val data = "password123".toByteArray()
        val salt = securityManager.generateSalt()

        // When: hashing twice with same salt
        val hash1 = securityManager.hashWithSalt(data, salt)
        val hash2 = securityManager.hashWithSalt(data, salt)

        // Then: hashes should be identical
        assertTrue(MessageDigest.isEqual(hash1, hash2))
    }

    @Test
    fun `hashing with salt - different salts produce different hashes`() {
        // Given: same data with different salts
        val data = "password123".toByteArray()
        val salt1 = securityManager.generateSalt()
        val salt2 = securityManager.generateSalt()

        // When: hashing
        val hash1 = securityManager.hashWithSalt(data, salt1)
        val hash2 = securityManager.hashWithSalt(data, salt2)

        // Then: hashes should be different
        assertFalse(MessageDigest.isEqual(hash1, hash2))
    }

    @Test
    fun `hashing with salt - same salt with different data produces different hashes`() {
        // Given: same salt with different data
        val data1 = "password123".toByteArray()
        val data2 = "password124".toByteArray()
        val salt = securityManager.generateSalt()

        // When: hashing
        val hash1 = securityManager.hashWithSalt(data1, salt)
        val hash2 = securityManager.hashWithSalt(data2, salt)

        // Then: hashes should be different
        assertFalse(MessageDigest.isEqual(hash1, hash2))
    }

    @Test
    fun `hashing with salt - hash length should be 32 bytes (SHA-256)`() {
        // Given: any data
        val data = "test".toByteArray()
        val salt = securityManager.generateSalt()

        // When: hashing
        val hash = securityManager.hashWithSalt(data, salt)

        // Then: should be 256 bits = 32 bytes
        assertEquals(32, hash.size)
    }

    @Test
    fun `hashing with salt - should handle empty data`() {
        // Given: empty data
        val data = ByteArray(0)
        val salt = securityManager.generateSalt()

        // When: hashing
        val hash = securityManager.hashWithSalt(data, salt)

        // Then: should produce valid hash
        assertEquals(32, hash.size)
    }

    @Test
    fun `hashing with salt - should handle large data`() {
        // Given: large data
        val data = ByteArray(1_000_000) { it.toByte() }
        val salt = securityManager.generateSalt()

        // When: hashing
        val hash = securityManager.hashWithSalt(data, salt)

        // Then: should produce valid hash
        assertEquals(32, hash.size)
    }

    // ========== HMAC Tests ==========

    @Test
    fun `HMAC generation - should generate consistent HMAC for same data and key`() {
        // Given: data and key
        val data = "Important data".toByteArray()
        val key = ByteArray(32) { it.toByte() }

        // When: generating HMAC twice
        val hmac1 = securityManager.generateHmac(data, key)
        val hmac2 = securityManager.generateHmac(data, key)

        // Then: HMACs should be identical
        assertTrue(MessageDigest.isEqual(hmac1, hmac2))
    }

    @Test
    fun `HMAC generation - different keys produce different HMACs`() {
        // Given: same data with different keys
        val data = "Important data".toByteArray()
        val key1 = ByteArray(32) { it.toByte() }
        val key2 = ByteArray(32) { (it + 1).toByte() }

        // When: generating HMACs
        val hmac1 = securityManager.generateHmac(data, key1)
        val hmac2 = securityManager.generateHmac(data, key2)

        // Then: HMACs should be different
        assertFalse(MessageDigest.isEqual(hmac1, hmac2))
    }

    @Test
    fun `HMAC generation - different data produces different HMACs`() {
        // Given: different data with same key
        val data1 = "Data1".toByteArray()
        val data2 = "Data2".toByteArray()
        val key = ByteArray(32) { it.toByte() }

        // When: generating HMACs
        val hmac1 = securityManager.generateHmac(data1, key)
        val hmac2 = securityManager.generateHmac(data2, key)

        // Then: HMACs should be different
        assertFalse(MessageDigest.isEqual(hmac1, hmac2))
    }

    @Test
    fun `HMAC generation - output should be 32 bytes`() {
        // Given: data and key
        val data = "test".toByteArray()
        val key = ByteArray(32) { it.toByte() }

        // When: generating HMAC
        val hmac = securityManager.generateHmac(data, key)

        // Then: should be 256 bits = 32 bytes
        assertEquals(32, hmac.size)
    }

    @Test
    fun `HMAC verification - should verify valid signature`() {
        // Given: data, key, and valid signature
        val data = "Verification test".toByteArray()
        val key = ByteArray(32) { it.toByte() }
        val signature = securityManager.generateHmac(data, key)

        // When: verifying signature
        val isValid = securityManager.verifyHmac(data, signature, key)

        // Then: should be valid
        assertTrue(isValid)
    }

    @Test
    fun `HMAC verification - should reject invalid signature`() {
        // Given: data and key
        val data = "Verification test".toByteArray()
        val key = ByteArray(32) { it.toByte() }
        val validSignature = securityManager.generateHmac(data, key)

        // When: verifying with tampered signature
        val tamperedSignature = validSignature.copyOf()
        tamperedSignature[0] = (tamperedSignature[0] + 1).toByte()
        val isValid = securityManager.verifyHmac(data, tamperedSignature, key)

        // Then: should be invalid
        assertFalse(isValid)
    }

    @Test
    fun `HMAC verification - should reject signature with wrong key`() {
        // Given: data and signatures with different keys
        val data = "Verification test".toByteArray()
        val key1 = ByteArray(32) { it.toByte() }
        val key2 = ByteArray(32) { (it + 1).toByte() }
        val signatureWithKey1 = securityManager.generateHmac(data, key1)

        // When: verifying with wrong key
        val isValid = securityManager.verifyHmac(data, signatureWithKey1, key2)

        // Then: should be invalid
        assertFalse(isValid)
    }

    @Test
    fun `HMAC verification - timing attack resistant comparison`() {
        // Given: data and varying signatures
        val data = "Test".toByteArray()
        val key = ByteArray(32) { it.toByte() }
        val validSignature = securityManager.generateHmac(data, key)
        val wrongSignature = ByteArray(32) { 0xFF.toByte() }

        // When: verifying valid and invalid (should be constant time)
        val validResult = securityManager.verifyHmac(data, validSignature, key)
        val invalidResult = securityManager.verifyHmac(data, wrongSignature, key)

        // Then: results should be correct
        assertTrue(validResult)
        assertFalse(invalidResult)
    }

    // ========== Salt Generation Tests ==========

    @Test
    fun `generateSalt - should produce 16 byte salts`() {
        // When: generating salt
        val salt = securityManager.generateSalt()

        // Then: should be 128 bits = 16 bytes
        assertEquals(16, salt.size)
    }

    @Test
    fun `generateSalt - should produce random salts`() {
        // When: generating multiple salts
        val salt1 = securityManager.generateSalt()
        val salt2 = securityManager.generateSalt()
        val salt3 = securityManager.generateSalt()

        // Then: all should be different
        assertFalse(MessageDigest.isEqual(salt1, salt2))
        assertFalse(MessageDigest.isEqual(salt2, salt3))
        assertFalse(MessageDigest.isEqual(salt1, salt3))
    }

    @Test
    fun `generateSalt - should not produce all zeros`() {
        // When: generating multiple salts
        val salts = List(10) { securityManager.generateSalt() }

        // Then: none should be all zeros
        salts.forEach { salt ->
            assertTrue(salt.any { it != 0.toByte() }, "Salt should not be all zeros")
        }
    }

    // ========== Hardware-Backed Key Tests ==========

    @Test
    fun `generateHardwareBackedKey - should return existing key if alias exists`() {
        // Given: key with alias
        val alias = "persistent_key_alias"
        val key1 = securityManager.generateHardwareBackedKey(alias)

        // When: generating key with same alias
        val key2 = securityManager.generateHardwareBackedKey(alias)

        // Then: should return same key
        assertEquals(key1, key2)
    }

    @Test
    fun `generateHardwareBackedKey - different aliases produce different keys`() {
        // Given: different aliases
        val alias1 = "key_alias_1"
        val alias2 = "key_alias_2"

        // When: generating keys
        val key1 = securityManager.generateHardwareBackedKey(alias1)
        val key2 = securityManager.generateHardwareBackedKey(alias2)

        // Then: should be different keys
        assertNotEquals(key1, key2)
    }

    @Test
    fun `generateHardwareBackedKey - should produce AES key`() {
        // When: generating key
        val alias = "aes_key_test"
        val key = securityManager.generateHardwareBackedKey(alias)

        // Then: should have AES algorithm
        assertEquals("AES", key.algorithm)
    }

    // ========== Security Health Check Tests ==========

    @Test
    fun `securityHealthCheck - should report healthy when all checks pass`() {
        // When: performing health check
        val health = securityManager.performSecurityHealthCheck()

        // Then: should be healthy (no critical issues)
        assertTrue(health.isHealthy, "Security health check should pass: ${health.issues}")
    }

    @Test
    fun `securityHealthCheck - should report encryption status`() {
        // When: performing health check
        val health = securityManager.performSecurityHealthCheck()

        // Then: should not have encryption errors
        val encryptionIssues = health.issues.filter { it.component == "Encryption" }
        assertTrue(encryptionIssues.isEmpty(), "Encryption should be working: $encryptionIssues")
    }

    @Test
    fun `securityHealthCheck - should report keystore accessibility`() {
        // When: performing health check
        val health = securityManager.performSecurityHealthCheck()

        // Then: should not have keystore errors
        val keystoreIssues = health.issues.filter { it.component == "Keystore" }
        assertTrue(keystoreIssues.isEmpty(), "Keystore should be accessible: $keystoreIssues")
    }

    @Test
    fun `securityHealthCheck - should report hashing availability`() {
        // When: performing health check
        val health = securityManager.performSecurityHealthCheck()

        // Then: should not have hashing errors
        val hashingIssues = health.issues.filter { it.component == "Hashing" }
        assertTrue(hashingIssues.isEmpty(), "SHA-256 should be available: $hashingIssues")
    }

    @Test
    fun `securityHealthCheck - should report HMAC availability`() {
        // When: performing health check
        val health = securityManager.performSecurityHealthCheck()

        // Then: should not have HMAC errors
        val hmacIssues = health.issues.filter { it.component == "HMAC" }
        assertTrue(hmacIssues.isEmpty(), "HMAC-SHA256 should be available: $hmacIssues")
    }
}
