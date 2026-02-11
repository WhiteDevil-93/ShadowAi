package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AdapterCircuitBreaker.
 */
class AdapterCircuitBreakerTest {

    private lateinit var breaker: AdapterCircuitBreaker

    @Before
    fun setup() {
        breaker = AdapterCircuitBreaker(
            failureThreshold = 3,
            successThreshold = 2,
            timeoutMs = 100L // Short timeout for testing
        )
    }

    @Test
    fun `starts in CLOSED state`() {
        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `successful execution stays CLOSED`() = runBlocking {
        val result = breaker.execute { "success" }

        assertEquals("success", result)
        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `failures below threshold stay CLOSED`() = runBlocking {
        repeat(2) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `failures at threshold opens circuit`() = runBlocking {
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        assertEquals(AdapterCircuitBreaker.State.OPEN, breaker.currentState)
    }

    @Test
    fun `OPEN state throws CircuitOpenException`() = runBlocking {
        // Open the circuit
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        try {
            breaker.execute { "should not run" }
            fail("Should have thrown AdapterCircuitOpenException")
        } catch (e: AdapterCircuitOpenException) {
            assertTrue(e.message!!.contains("open"))
        }
    }

    @Test
    fun `circuit transitions to HALF_OPEN after timeout`() = runBlocking {
        // Open the circuit
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }
        assertEquals(AdapterCircuitBreaker.State.OPEN, breaker.currentState)

        // Wait for timeout
        Thread.sleep(150)

        // Next execution should transition to HALF_OPEN and succeed
        val result = breaker.execute { "recovered" }
        assertEquals("recovered", result)
    }

    @Test
    fun `HALF_OPEN transitions to CLOSED after successes`() = runBlocking {
        // Open the circuit
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        // Wait for timeout
        Thread.sleep(150)

        // First success in HALF_OPEN
        breaker.execute { "success1" }
        // Second success closes the circuit
        breaker.execute { "success2" }

        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `HALF_OPEN failure returns to OPEN`() = runBlocking {
        // Open the circuit
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        // Wait for timeout
        Thread.sleep(150)

        // Fail in HALF_OPEN
        try { breaker.execute { throw Exception("still failing") } } catch (_: Exception) {}

        assertEquals(AdapterCircuitBreaker.State.OPEN, breaker.currentState)
    }

    @Test
    fun `tryExecute returns Result instead of throwing`() = runBlocking {
        // Open the circuit
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        val result = breaker.tryExecute { "test" }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AdapterCircuitOpenException)
    }

    @Test
    fun `tryExecute returns success Result on success`() = runBlocking {
        val result = breaker.tryExecute { "success" }

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
    }

    @Test
    fun `reset returns to CLOSED state`() = runBlocking {
        // Open the circuit
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }
        assertEquals(AdapterCircuitBreaker.State.OPEN, breaker.currentState)

        breaker.reset()

        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `success resets failure count`() = runBlocking {
        // Some failures
        repeat(2) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        // Success
        breaker.execute { "success" }

        // More failures - should not trip since count was reset
        repeat(2) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }
}

/**
 * Unit tests for AdapterCircuitBreakerManager.
 */
class AdapterCircuitBreakerManagerTest {

    private lateinit var manager: AdapterCircuitBreakerManager

    @Before
    fun setup() {
        manager = AdapterCircuitBreakerManager(
            failureThreshold = 3,
            successThreshold = 2,
            timeoutMs = 100L
        )
    }

    @Test
    fun `getBreaker creates new breaker for provider`() {
        val breaker = manager.getBreaker(ProviderId.OPENAI)

        assertNotNull(breaker)
        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `getBreaker returns same breaker for same provider`() {
        val breaker1 = manager.getBreaker(ProviderId.OPENAI)
        val breaker2 = manager.getBreaker(ProviderId.OPENAI)

        assertSame(breaker1, breaker2)
    }

    @Test
    fun `getBreaker returns different breakers for different providers`() {
        val breaker1 = manager.getBreaker(ProviderId.OPENAI)
        val breaker2 = manager.getBreaker(ProviderId.ANTHROPIC)

        assertNotSame(breaker1, breaker2)
    }

    @Test
    fun `getState returns null for unknown provider`() {
        assertNull(manager.getState(ProviderId.OPENAI))
    }

    @Test
    fun `getState returns state for known provider`() {
        manager.getBreaker(ProviderId.OPENAI)

        assertEquals(AdapterCircuitBreaker.State.CLOSED, manager.getState(ProviderId.OPENAI))
    }

    @Test
    fun `getAllStates returns all provider states`() {
        manager.getBreaker(ProviderId.OPENAI)
        manager.getBreaker(ProviderId.ANTHROPIC)
        manager.getBreaker(ProviderId.GEMINI)

        val states = manager.getAllStates()

        assertEquals(3, states.size)
        assertTrue(states.containsKey(ProviderId.OPENAI))
        assertTrue(states.containsKey(ProviderId.ANTHROPIC))
        assertTrue(states.containsKey(ProviderId.GEMINI))
    }

    @Test
    fun `reset resets specific provider`() = runBlocking {
        val breaker = manager.getBreaker(ProviderId.OPENAI)
        repeat(3) {
            try { breaker.execute { throw Exception("fail") } } catch (_: Exception) {}
        }
        assertEquals(AdapterCircuitBreaker.State.OPEN, breaker.currentState)

        manager.reset(ProviderId.OPENAI)

        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker.currentState)
    }

    @Test
    fun `resetAll resets all providers`() = runBlocking {
        val breaker1 = manager.getBreaker(ProviderId.OPENAI)
        val breaker2 = manager.getBreaker(ProviderId.ANTHROPIC)

        repeat(3) {
            try { breaker1.execute { throw Exception("fail") } } catch (_: Exception) {}
            try { breaker2.execute { throw Exception("fail") } } catch (_: Exception) {}
        }

        manager.resetAll()

        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker1.currentState)
        assertEquals(AdapterCircuitBreaker.State.CLOSED, breaker2.currentState)
    }

    @Test
    fun `remove removes provider breaker`() {
        manager.getBreaker(ProviderId.OPENAI)
        assertNotNull(manager.getState(ProviderId.OPENAI))

        manager.remove(ProviderId.OPENAI)

        assertNull(manager.getState(ProviderId.OPENAI))
    }
}
