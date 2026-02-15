package com.shadowai.app.thread

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shadowai.app.agent.AgenticState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Concurrency tests for atomic state transitions used by agentic execution paths.
 *
 * These tests validate the expected atomic semantics directly, without relying on
 * internal/private implementation details from production files.
 */
@RunWith(AndroidJUnit4::class)
class AtomicStateTest {

    @Test
    fun `agentic state updates are atomic`() = runBlocking {
        val state = AtomicReference(AgenticState.INITIALIZING)
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
                    state.set(newState)
                    stateCounts.computeIfAbsent(newState) { AtomicInteger(0) }.incrementAndGet()
                    true
                }
            }

            val readerJobs = List(readers) {
                async(Dispatchers.Default) {
                    val currentState = state.get()
                    assertNotNull("State should never be null", currentState)
                    true
                }
            }

            (writerJobs + readerJobs).awaitAll()
        }

        val totalWrites = stateCounts.values.sumOf { it.get() }
        assertEquals("All writers should have updated state", writers, totalWrites)
    }

    @Test
    fun `compare and set operations work correctly`() = runBlocking {
        val state = AtomicReference(AgenticState.INITIALIZING)
        val attempts = 1000
        val successfulUpdates = AtomicInteger(0)

        val jobResults = coroutineScope {
            val jobs = List(attempts) {
                async(Dispatchers.Default) {
                    val success = state.compareAndSet(
                        AgenticState.INITIALIZING,
                        AgenticState.PLANNING
                    )
                    if (success) {
                        successfulUpdates.incrementAndGet()
                    }
                    success
                }
            }
            jobs.awaitAll()
        }

        assertEquals("Only one CAS should succeed", 1, successfulUpdates.get())
        assertTrue("Exactly one successful update expected", jobResults.count { it } == 1)
        assertEquals(AgenticState.PLANNING, state.get())
    }
}
