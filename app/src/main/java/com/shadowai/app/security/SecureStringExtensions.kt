package com.shadowai.app.security

import java.nio.CharBuffer
import java.util.Arrays

/**
 * Executes [block] with UTF-8 bytes derived from this secure string.
 * The temporary byte array is wiped after use.
 */
fun <T> SecureString.useBytes(block: (ByteArray) -> T): T {
    return useChars { chars ->
        val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        try {
            block(bytes)
        } finally {
            Arrays.fill(bytes, 0)
        }
    }
}

