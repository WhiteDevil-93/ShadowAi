package com.shadowai.core.security

import java.io.Closeable
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Secure byte array wrapper that prevents immutable strings in memory.
 * This pattern ensures sensitive data like API keys and secrets are properly
 * managed and zeroed after use to prevent memory leaks.
 */
class SecretBytes(private val size: Int) : Closeable {
    private val bytes = ByteArray(size)
    private var isDisposed = false

    override fun close() {
        dispose()
    }

    /**
     * Fill the secret bytes with random data.
     */
    fun fill(block: (ByteArray) -> Unit) {
        check(!isDisposed) { "SecretBytes has been disposed" }
        block(bytes)
    }

    /**
     * Use the secret bytes in a safe context and automatically zero them afterward.
     */
    fun <T> withSecretBytes(block: (ByteArray) -> T): T {
        check(!isDisposed) { "SecretBytes has been disposed" }
        return block(bytes)
    }

    /**
     * Use the secret bytes as a SecretKey in a safe context.
     */
    fun <T> withSecretKey(algorithm: String = "AES", block: (SecretKey) -> T): T {
        return withSecretBytes { secretBytes ->
            val keySpec = SecretKeySpec(secretBytes, algorithm)
            val secretKey = keySpec
            block(secretKey)
        }
    }

    /**
     * Get a copy of the secret bytes (use with caution).
     */
    fun copy(): ByteArray {
        check(!isDisposed) { "SecretBytes has been disposed" }
        return bytes.clone()
    }

    /**
     * Dispose of the secret bytes and zero the memory.
     */
    fun dispose() {
        if (!isDisposed) {
            Arrays.fill(bytes, 0.toByte())
            isDisposed = true
        }
    }

    /**
     * Check if the secret bytes have been disposed.
     */
    fun isDisposed(): Boolean = isDisposed

    /**
     * Create a SecretBytes instance filled with random data.
     */
    companion object {
        private val secureRandom = SecureRandom()

        fun random(size: Int): SecretBytes {
            val secretBytes = SecretBytes(size)
            secretBytes.fill { bytes ->
                secureRandom.nextBytes(bytes)
            }
            return secretBytes
        }

        fun fromByteArray(data: ByteArray): SecretBytes {
            val secretBytes = SecretBytes(data.size)
            secretBytes.fill { bytes ->
                System.arraycopy(data, 0, bytes, 0, data.size)
            }
            return secretBytes
        }
    }
}

/**
 * Extension function to create SecretBytes from a string.
 */
fun String.toSecretBytes(): SecretBytes {
    val utf8 = this.toByteArray(Charsets.UTF_8)
    return try {
        SecretBytes.fromByteArray(utf8)
    } finally {
        Arrays.fill(utf8, 0.toByte())
    }
}

/**
 * Converts a discovered API key string to [SecretBytes].
 *
 * Note: Kotlin/JVM strings are immutable and cannot be reliably zeroed in place.
 * This helper guarantees temporary UTF-8 buffers are wiped immediately.
 */
fun discoveredApiKey(key: String): SecretBytes = key.toSecretBytes()

/**
 * Extension function to create a string from SecretBytes (use with caution).
 */
fun SecretBytes.toStringValue(): String {
    return withSecretBytes { bytes ->
        String(bytes)
    }
}
