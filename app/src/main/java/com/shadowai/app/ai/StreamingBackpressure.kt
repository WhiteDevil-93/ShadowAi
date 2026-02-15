package com.shadowai.app.ai

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow

/**
 * M-16: Streaming backpressure utilities.
 *
 * Provides bounded buffering for streaming operations.
 */
object StreamingBackpressure {

    /**
     * Default buffer capacity for streaming operations.
     */
    private const val DEFAULT_BUFFER_CAPACITY = 16

    /**
     * Small buffer for mobile devices with limited memory.
     */
    private const val SMALL_BUFFER_CAPACITY = 8

    /**
     * Large buffer for high-throughput operations.
     */
    private const val LARGE_BUFFER_CAPACITY = 32

    /**
     * Backpressure strategy for handling buffer overflow.
     */
    enum class BackpressureStrategy {
        DROP_OLDEST,
        DROP_NEWEST,
        SUSPEND,
        FAIL
    }

    /**
     * Creates a bounded streaming channel with backpressure.
     *
     * @param capacity Buffer capacity
     * @param strategy Strategy for handling overflow
     * @return Channel with applied backpressure
     */
    fun <T> createBoundedChannel(
        capacity: Int = DEFAULT_BUFFER_CAPACITY,
        strategy: BackpressureStrategy = BackpressureStrategy.SUSPEND
    ): Channel<T> {
        val bufferOverflow = when (strategy) {
            BackpressureStrategy.DROP_OLDEST -> BufferOverflow.DROP_OLDEST
            BackpressureStrategy.DROP_NEWEST -> BufferOverflow.DROP_LATEST
            BackpressureStrategy.SUSPEND -> BufferOverflow.SUSPEND
            BackpressureStrategy.FAIL -> BufferOverflow.SUSPEND // FAIL semantics: suspend (do not silently drop data)
        }
        return Channel(capacity, bufferOverflow)
    }

    /**
     * Applies bounded buffering to a Flow.
     *
     * @param flow The source flow
     * @param capacity Buffer capacity
     * @param strategy Strategy for handling overflow
     * @return Buffered flow
     */
    fun <T> bufferFlow(
        flow: Flow<T>,
        capacity: Int = DEFAULT_BUFFER_CAPACITY,
        strategy: BackpressureStrategy = BackpressureStrategy.SUSPEND
    ): Flow<T> {
        val bufferOverflow = when (strategy) {
            BackpressureStrategy.DROP_OLDEST -> BufferOverflow.DROP_OLDEST
            BackpressureStrategy.DROP_NEWEST -> BufferOverflow.DROP_LATEST
            BackpressureStrategy.SUSPEND -> BufferOverflow.SUSPEND
            BackpressureStrategy.FAIL -> BufferOverflow.SUSPEND // FAIL semantics: suspend (do not silently drop data)
        }
        return flow.buffer(capacity, bufferOverflow)
    }

    /**
     * Creates a bounded token stream for AI inference streaming.
     */
    fun createTokenStream(
        strategy: BackpressureStrategy = BackpressureStrategy.SUSPEND
    ): Channel<String> {
        return createBoundedChannel(DEFAULT_BUFFER_CAPACITY, strategy)
    }

    /**
     * Creates a small bounded stream for resource-constrained devices.
     */
    fun createSmallBoundedStream(
        strategy: BackpressureStrategy = BackpressureStrategy.SUSPEND
    ): Channel<String> {
        return createBoundedChannel(SMALL_BUFFER_CAPACITY, strategy)
    }

    /**
     * Creates a flow with backpressure monitoring.
     */
    fun <T> monitoredBufferFlow(
        flow: Flow<T>,
        capacity: Int = DEFAULT_BUFFER_CAPACITY,
        tag: String = "Streaming"
    ): Flow<T> {
        return flow.buffer(capacity, BufferOverflow.SUSPEND)
            .also {
                android.util.Log.d(tag, "Created monitored buffered flow with capacity=$capacity")
            }
    }
}
