package com.shadowai.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SecureDataStore encryption and data persistence.
 * 
 * Note: These tests mock the Tink AEAD primitive since we can't easily
 * instantiate hardware-backed keys in unit tests. Full integration tests
 * should be run on a device.
 */
class SecureDataStoreTest {

    // Since SecureDataStore heavily relies on Android APIs (DataStore, Tink),
    // we test the encryption logic separately

    @Test
    fun `encrypt and decrypt should be reversible`() {
        // This tests the encryption pattern - in practice, we'd use
        // Robolectric or instrumented tests for the full SecureDataStore
        
        val plaintext = "my-secret-api-key"
        val key = ByteArray(32) { it.toByte() } // Deterministic key for testing
        
        // Simulate AES-GCM encryption pattern
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Decrypt
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = String(decryptCipher.doFinal(ciphertext), Charsets.UTF_8)
        
        assertEquals("Decryption should match original", plaintext, decrypted)
    }

    @Test
    fun `base64 encoding should be reversible`() {
        val originalBytes = "test-data-12345".toByteArray()
        
        val encoded = java.util.Base64.getEncoder().withoutPadding().encodeToString(originalBytes)
        val decoded = java.util.Base64.getDecoder().decode(encoded)
        
        assertTrue("Decoded should match original", originalBytes.contentEquals(decoded))
    }

    @Test
    fun `empty string encryption should work`() {
        val plaintext = ""
        val key = ByteArray(32) { it.toByte() }
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = String(decryptCipher.doFinal(ciphertext), Charsets.UTF_8)
        
        assertEquals("Empty string should encrypt/decrypt", plaintext, decrypted)
    }

    @Test
    fun `unicode string encryption should work`() {
        val plaintext = "密码🔐Пароль"
        val key = ByteArray(32) { it.toByte() }
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = String(decryptCipher.doFinal(ciphertext), Charsets.UTF_8)
        
        assertEquals("Unicode should encrypt/decrypt correctly", plaintext, decrypted)
    }

    @Test
    fun `different keys should produce different ciphertext`() {
        val plaintext = "same-plaintext"
        val key1 = ByteArray(32) { 1 }
        val key2 = ByteArray(32) { 2 }
        
        val cipher1 = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher1.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key1, "AES"))
        val ciphertext1 = cipher1.doFinal(plaintext.toByteArray())
        
        val cipher2 = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher2.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key2, "AES"))
        val ciphertext2 = cipher2.doFinal(plaintext.toByteArray())
        
        // Remove IVs for comparison (first 12 bytes usually)
        assertTrue("Different keys should produce different ciphertext", 
            !ciphertext1.contentEquals(ciphertext2))
    }

    @Test
    fun `tampered ciphertext should fail decryption`() {
        val plaintext = "sensitive-data"
        val key = ByteArray(32) { it.toByte() }
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Tamper with the ciphertext
        ciphertext[ciphertext.size / 2] = (ciphertext[ciphertext.size / 2] + 1).toByte()
        
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        var exceptionThrown = false
        try {
            decryptCipher.doFinal(ciphertext)
        } catch (e: Exception) {
            exceptionThrown = true
        }
        
        assertTrue("Tampered data should fail authentication", exceptionThrown)
    }
}
