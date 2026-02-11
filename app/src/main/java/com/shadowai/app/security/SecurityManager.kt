package com.shadowai.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level security manager used by legacy app components.
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val KEYSTORE_TYPE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE_BYTES = 12
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
    }

    fun generateHardwareBackedKey(alias: String): SecretKey {
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_TYPE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encrypt(data: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(IV_SIZE_BYTES + encrypted.size)
        System.arraycopy(cipher.iv, 0, payload, 0, IV_SIZE_BYTES)
        System.arraycopy(encrypted, 0, payload, IV_SIZE_BYTES, encrypted.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(data: String, key: SecretKey): String {
        val payload = Base64.decode(data, Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES) { "Invalid encrypted payload" }
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(encrypted)
        return String(plaintext, Charsets.UTF_8)
    }
}

