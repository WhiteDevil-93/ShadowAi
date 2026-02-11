package com.shadowai.app.execution

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CircuitBreaker implementation.
 * 
 * Tests state transitions, failure thresholds, and recovery behavior.
 */
class CircuitBreakerTest {

    private lateinit var circuitBreaker: CircuitBreaker

    @Before
    fun setup() {
        circuitBreaker = CircuitBreaker(
            failureThreshold = 3,
            successThreshold = 2,
            timeoutMs = 1000
        )
    }

    @Test
    fun `initial state should be CLOSED`() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.currentState)
    }

    @Test
    fun `successful executions should keep circuit CLOSED`() = runTest {
        repeat(5) {
            circuitBreaker.execute { "success" }
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.currentState)
        assertEquals(0, circuitBreaker.currentFailureCount)
    }

    @Test
    fun `failures below threshold should keep circuit CLOSED`() = runTest {
        repeat(2) {
            try {
                circuitBreaker.execute { throw RuntimeException("error") }
            } catch (_: RuntimeException) {}
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.currentState)
        assertEquals(2, circuitBreaker.currentFailureCount)
    }

    @Test
    fun `reaching failure threshold should OPEN circuit`() = runTest {
        repeat(3) {
            try {
                circuitBreaker.execute { throw RuntimeException("error") }
            } catch (_: RuntimeException) {}
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.currentState)
    }

    @Test
    fun `OPEN circuit should throw CircuitOpenException`() = runTest {
        // Trigger circuit open
        repeat(3) {
            try {
                circuitBreaker.execute { throw RuntimeException("error") }
            } catch (_: RuntimeException) {}
        }

        try {
            circuitBreaker.execute { "should not run" }
            fail("Should have thrown CircuitOpenException")
        } catch (e: CircuitOpenException) {
            assertTrue(e.message?.contains("open") == true)
        }
    }

    @Test
    fun `success after failure should reset failure count`() = runTest {
        // 2 failures
        repeat(2) {
            try {
                circuitBreaker.execute { throw RuntimeException("error") }
            } catch (_: RuntimeException) {}
        }
        assertEquals(2, circuitBreaker.currentFailureCount)

        // 1 success should reset
        circuitBreaker.execute { "success" }
        assertEquals(0, circuitBreaker.currentFailureCount)
    }

    @Test
    fun `tryExecute should return Result instead of throwing`() = runTest {
        val successResult = circuitBreaker.tryExecute { "hello" }
        assertTrue(successResult.isSuccess)
        assertEquals("hello", successResult.getOrNull())

        val failureResult = circuitBreaker.tryExecute { throw RuntimeException("error") }
        assertTrue(failureResult.isFailure)
    }

    @Test
    fun `reset should restore to initial CLOSED state`() = runTest {
        // Trigger circuit open
        repeat(3) {
            try {
                circuitBreaker.execute { throw RuntimeException("error") }
            } catch (_: RuntimeException) {}
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.currentState)

        circuitBreaker.reset()

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.currentState)
        assertEquals(0, circuitBreaker.currentFailureCount)
    }

    @Test
    fun `configuration values should return correct values`() {
        assertEquals(3, circuitBreaker.failureThreshold)
        assertEquals(2, circuitBreaker.successThreshold)
        assertEquals(1000L, circuitBreaker.timeoutMs)
    }

    @Test
    fun `tryExecute on OPEN circuit should return failure`() = runTest {
        // Trigger circuit open
        repeat(3) {
            circuitBreaker.tryExecute { throw RuntimeException("error") }
        }

        val result = circuitBreaker.tryExecute { "should not run" }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CircuitOpenException)
    }
}
