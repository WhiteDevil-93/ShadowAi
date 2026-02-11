package com.shadowai.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Unit tests for SecurityManager encryption functionality.
 * 
 * Tests AES-GCM encryption/decryption patterns without requiring
 * Android Keystore (which isn't available in unit tests).
 */
class SecurityManagerTest {

    companion object {
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    @Test
    fun `AES-GCM encryption should produce different ciphertext for same plaintext`() {
        val key = generateTestKey()
        val plaintext = "Sensitive data"
        
        val ciphertext1 = encryptAesGcm(plaintext.toByteArray(), key)
        val ciphertext2 = encryptAesGcm(plaintext.toByteArray(), key)
        
        // Due to random IV, same plaintext should produce different ciphertext
        assertNotEquals(ciphertext1.toList(), ciphertext2.toList())
    }

    @Test
    fun `AES-GCM decryption should recover original plaintext`() {
        val key = generateTestKey()
        val plaintext = "Secret message to encrypt"
        
        val ciphertext = encryptAesGcm(plaintext.toByteArray(), key)
        val decrypted = decryptAesGcm(ciphertext, key)
        
        assertEquals(plaintext, String(decrypted))
    }

    @Test
    fun `AES-GCM should handle empty string`() {
        val key = generateTestKey()
        val plaintext = ""
        
        val ciphertext = encryptAesGcm(plaintext.toByteArray(), key)
        val decrypted = decryptAesGcm(ciphertext, key)
        
        assertEquals(plaintext, String(decrypted))
    }

    @Test
    fun `AES-GCM should handle unicode characters`() {
        val key = generateTestKey()
        val plaintext = "你好世界 🔐 مرحبا"
        
        val ciphertext = encryptAesGcm(plaintext.toByteArray(Charsets.UTF_8), key)
        val decrypted = decryptAesGcm(ciphertext, key)
        
        assertEquals(plaintext, String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `AES-GCM should handle large data`() {
        val key = generateTestKey()
        val plaintext = "A".repeat(100_000) // 100KB
        
        val ciphertext = encryptAesGcm(plaintext.toByteArray(), key)
        val decrypted = decryptAesGcm(ciphertext, key)
        
        assertEquals(plaintext, String(decrypted))
    }

    @Test
    fun `different keys should produce different ciphertext`() {
        val key1 = generateTestKey()
        val key2 = generateTestKey()
        val plaintext = "Same plaintext"
        
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        
        val ciphertext1 = encryptAesGcmWithIv(plaintext.toByteArray(), key1, iv)
        val ciphertext2 = encryptAesGcmWithIv(plaintext.toByteArray(), key2, iv)
        
        // Same IV but different keys should produce different ciphertext
        assertNotEquals(ciphertext1.toList(), ciphertext2.toList())
    }

    @Test
    fun `tampering with ciphertext should fail decryption`() {
        val key = generateTestKey()
        val plaintext = "Original message"
        
        val ciphertext = encryptAesGcm(plaintext.toByteArray(), key)
        
        // Tamper with the ciphertext (not the IV)
        if (ciphertext.size > GCM_IV_LENGTH) {
            ciphertext[GCM_IV_LENGTH] = (ciphertext[GCM_IV_LENGTH].toInt() xor 0xFF).toByte()
        }
        
        try {
            decryptAesGcm(ciphertext, key)
            // If we get here, authentication failed to detect tampering
            assertTrue("Should have thrown exception", false)
        } catch (e: Exception) {
            // Expected - AES-GCM detects tampering
            assertTrue(e is javax.crypto.AEADBadTagException || 
                      e.message?.contains("mac check") == true ||
                      e.message?.contains("tag") == true)
        }
    }

    @Test
    fun `IV should be prepended to ciphertext`() {
        val key = generateTestKey()
        val plaintext = "Test"
        
        val combined = encryptAesGcm(plaintext.toByteArray(), key)
        
        // Combined should be at least IV length + some ciphertext
        assertTrue(combined.size > GCM_IV_LENGTH)
        
        // Extract IV from first 12 bytes
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        assertNotNull(iv)
        assertEquals(GCM_IV_LENGTH, iv.size)
    }

    @Test
    fun `SecureRandom should generate unique IVs`() {
        val random = SecureRandom()
        val ivs = (1..100).map { 
            ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        }
        
        // All IVs should be unique
        val uniqueIvs = ivs.map { it.toList() }.toSet()
        assertEquals("All IVs should be unique", 100, uniqueIvs.size)
    }

    // Helper methods that mirror SecurityManager implementation

    private fun generateTestKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        return keyGen.generateKey()
    }

    private fun encryptAesGcm(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return encryptAesGcmWithIv(plaintext, key, iv)
    }

    private fun encryptAesGcmWithIv(plaintext: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        // Prepend IV to ciphertext
        return iv + ciphertext
    }

    private fun decryptAesGcm(combined: ByteArray, key: SecretKey): ByteArray {
        require(combined.size > GCM_IV_LENGTH) { "Ciphertext too short" }
        
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return cipher.doFinal(ciphertext)
    }
}
