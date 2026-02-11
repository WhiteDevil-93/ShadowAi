package com.shadowai.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Key Management (TEE/StrongBox).
 *
 * Enforces StrongBox usage where available for high-assurance key storage.
 */
class TeeKeyManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * Check if StrongBox is available on this device.
     */
    fun isStrongBoxBacked(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        }
        return false
    }

    /**
     * Get or create a hardware-backed AES key.
     * Tries to use StrongBox if available.
     */
    fun getOrCreateKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        return generateKey(alias)
    }

    private fun generateKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        // Require hardware backing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(isStrongBoxBacked())
            builder.setUnlockedDeviceRequired(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data directly using the hardware key.
     * Returns an EncryptedPackage with IV and ciphertext (Base64).
     */
    fun encrypt(keyId: String, plaintextBase64: String): EncryptedPackage {
        val key = getOrCreateKey(keyId)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        // Explicitly generate a random IV (12 bytes for GCM)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val plaintext = Base64.decode(plaintextBase64, Base64.DEFAULT)
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedPackage(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypt data directly using the hardware key.
     */
    fun decrypt(keyId: String, encryptedPackage: EncryptedPackage): String {
        val key = getOrCreateKey(keyId)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(encryptedPackage.iv, Base64.NO_WRAP)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val ciphertext = Base64.decode(encryptedPackage.ciphertext, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(ciphertext)
        return Base64.encodeToString(decrypted, Base64.DEFAULT)
    }
}

data class EncryptedPackage(
    val ciphertext: String,
    val iv: String
)
