package com.shadowai.app.security

import java.io.Closeable
import java.util.Arrays

/**
 * A wrapper for sensitive string data that allows for clearing memory.
 * Stores data as a CharArray and wipes it upon closing.
 */
class SecureString(data: CharArray) : Closeable {
    private val data: CharArray = data.clone()
    @Volatile
    private var isDestroyed = false

    val length: Int
        get() = if (isDestroyed) 0 else data.size

    /**
     * @deprecated Creates an immutable JVM String that cannot be wiped.
     * Use [useChars] instead for secure operations.
     */
    @Deprecated(
        message = "Creates immutable String defeating secure wipe. Use useChars() instead.",
        replaceWith = ReplaceWith("useChars { String(it) }")
    )
    fun asString(): String {
        check(!isDestroyed) { "SecureString has been destroyed" }
        return String(data)
    }
    
    fun <T> useChars(block: (CharArray) -> T): T {
        check(!isDestroyed)
        val copy = data.copyOf()
        try {
            return block(copy)
        } finally {
            Arrays.fill(copy, '\u0000')
        }
    }

    override fun close() {
        if (!isDestroyed) {
            Arrays.fill(data, '\u0000')
            isDestroyed = true
        }
    }

    protected fun finalize() {
        close()
    }

    companion object {
        fun from(s: String): SecureString {
            return SecureString(s.toCharArray())
        }
    }
}
