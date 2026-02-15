package com.shadowai.app.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * M-16: Streaming backpressure with bounded buffer.
 *
 * This class provides a bounded buffer for streaming tokens from native code,
 * preventing memory overflows when the consumer is slower than the producer.
 *
 * Features:
 * - Bounded buffer size (default 100 tokens)
 * - Automatic backpressure handling via Channel
 * - Token dropping when buffer is full (old tokens dropped, new accepted)
 * - Thread-safe operation with atomic flags
 *
 * @param bufferSize Maximum number of tokens to buffer (default: 100)
 */
class BoundedStreamBuffer(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
) {
    companion object {
        private const val TAG = "BoundedStreamBuffer"
        private const val DEFAULT_BUFFER_SIZE = 100
    }

    // Channel with RENDEZVOUS capacity for natural backpressure
    // Overflow handled by dropping oldest tokens
    private val tokenChannel = Channel<String>(capacity = bufferSize)
    
    @Volatile
    private var isActive = AtomicBoolean(true)
    
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Add a token to the buffer.
     * If the buffer is full, this method returns immediately without blocking,
     * effectively implementing backpressure by dropping tokens when overwhelmed.
     *
     * @param token The token to add
     * @return true if token was added, false if buffer was full or closed
     */
    fun offer(token: String): Boolean {
        if (!isActive.get()) return false
        
        return try {
            val result = tokenChannel.trySend(token)
            if (!result.isSuccess && result.isClosed) {
                Log.w(TAG, "Buffer closed, dropping token")
                false
            } else if (!result.isSuccess) {
                Log.w(TAG, "Buffer full, dropping oldest tokens")
                // Try to make room by receiving one, then retry
                val drained = mutableListOf<String>()
                while (tokenChannel.tryReceive().isSuccess) {
                    // Drain old tokens
                }
                // Retry once after draining
                tokenChannel.trySend(token).isSuccess
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error offering token to buffer", e)
            false
        }
    }

    /**
     * Mark the stream as complete.
     */
    fun complete() {
        if (isActive.compareAndSet(true, false)) {
            tokenChannel.close()
            Log.d(TAG, "Stream buffer completed")
        }
    }

    /**
     * Mark the stream as failed with an error.
     *
     * @param error The error message
     */
    fun error(error: String) {
        if (isActive.compareAndSet(true, false)) {
            Log.e(TAG, "Stream buffer error: $error")
            tokenChannel.close(Exception(error))
        }
    }

    /**
     * Get the flow of tokens for consumption.
     * The flow can be collected in a coroutine.
     *
     * @return Flow of tokens
     */
    fun asFlow(): Flow<String> = tokenChannel.consumeAsFlow()
        .flowOn(Dispatchers.Default)

    /**
     * Get a native-compatible callback wrapper that feeds into this buffer.
     *
     * @param onToken Callback for each token (can be null for direct buffer access)
     * @param onComplete Callback when generation is complete
     * @param onError Callback for errors
     * @return Wrapped callback for LlamaNative.generateStream
     */
    fun wrapCallback(
        onToken: ((String) -> Unit)? = null,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): LlamaNative.GenerationCallback {
        return object : LlamaNative.GenerationCallback {
            override fun onToken(token: String) {
                // Always try to buffer first
                offer(token)
                
                // Then notify the callback on main thread if provided
                onToken?.let { callback ->
                    mainHandler.post { callback(token) }
                }
            }

            override fun onCompleted() {
                complete()
                mainHandler.post { onComplete() }
            }

            override fun onError(message: String) {
                error(message)
                mainHandler.post { onError(message) }
            }
        }
    }

    /**
     * Check if the buffer is still active and accepting tokens.
     */
    fun isActive(): Boolean = isActive.get()

    /**
     * Force shutdown and clear the buffer.
     */
    fun shutdown() {
        if (isActive.compareAndSet(true, false)) {
            tokenChannel.close()
            Log.d(TAG, "Stream buffer shut down")
        }
    }
}

/**
 * Extension function to create a bounded buffer callback wrapper for LlamaNative streaming.
 *
 * Usage:
 * ```
 * val buffer = BoundedStreamBuffer(bufferSize = 100)
 * 
 * // Start consuming
 * lifecycleScope.launch {
 *     buffer.asFlow().collect { token ->
 *         // Handle token
 *     }
 * }
 *
 * // Pass to native
 * llamaNative.generateStream(handle, prompt, config, buffer.wrapCallback(
 *     onToken = { token -> /* optional UI update */ },
 *     onComplete = { /* handle complete */ },
 *     onError = { error -> /* handle error */ }
 * ))
 * ```
 */
fun LlamaNative.createBoundedStreamBuffer(
    bufferSize: Int = 100,
    onToken: ((String) -> Unit)? = null,
    onComplete: () -> Unit = {},
    onError: (String) -> Unit = {}
): Pair<BoundedStreamBuffer, LlamaNative.GenerationCallback> {
    val buffer = BoundedStreamBuffer(bufferSize)
    val callback = buffer.wrapCallback(onToken, onComplete, onError)
    return buffer to callback
}
