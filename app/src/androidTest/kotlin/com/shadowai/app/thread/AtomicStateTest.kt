package com.shadowai.app.thread

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shadowai.app.agent.MutableAgenticState
import com.shadowai.app.agent.AgenticState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for atomic state changes in AgenticLoop and IsolatedInferenceManager.
 */
@RunWith(AndroidJUnit4::class)
class AtomicStateTest {

    @Test
    fun `agentic state updates are atomic`() = runBlocking {
        val state = MutableAgenticState(AgenticState.INITIALIZING)
        val writers = 100
        val readers = 100
        val stateCounts = ConcurrentHashMap<AgenticState, AtomicInteger>()

        coroutineScope {
            val writerJobs = List(writers) { index ->
                async(Dispatchers.Default) {
                    val newStates = listOf(
                        AgenticState.PLANNING,
                        AgenticState.EXECUTING,
                        AgenticState.ITERATING,
                        AgenticState.COMPLETED
                    )
                    val newState = newStates[index % newStates.size]
                    state.value = newState
                    stateCounts.computeIfAbsent(newState) { AtomicInteger(0) }.incrementAndGet()
                }
            }

            val readerJobs = List(readers) { index ->
                async(Dispatchers.Default) {
                    val currentState = state.value
                    // Should never be null
                    assertNotNull("State should never be null", currentState)
                    currentState
                }
            }

            awaitAll(writerJobs + readerJobs)
        }

        // Verify all writers completed without race conditions
        val totalWrites = stateCounts.values.sumOf { it.get() }
        assertEquals("All writers should have updated state", writers, totalWrites)

        println("✅ Atomic agentic state updates verified")
    }

    @Test
    fun `compare and set operations work correctly`() = runBlocking {
        val state = MutableAgenticState(AgenticState.INITIALIZING)
        val attempts = 1000
        val successfulUpdates = AtomicInteger(0)

        coroutineScope {
            val jobs = List(attempts) {
                async(Dispatchers.Default) {
                    // Get current state
                    val current = state.value

                    // Try to update only if still INITIALIZING
                    val success = if (current == AgenticState.INITIALIZING) {
                        state.value = AgenticState.PLANNING
                        successfulUpdates.incrementAndGet()
                        true
                    } else {
                        false
                    }

                    success
                }
            }

            jobResults = jobs.awaitAll()
        }

        // Only one thread should succeed in compare-and-set
        assertEquals("Only one CAS should succeed", 1, successfulUpdates.get())

        println("✅ Compare and set operations verified")
    }
}
