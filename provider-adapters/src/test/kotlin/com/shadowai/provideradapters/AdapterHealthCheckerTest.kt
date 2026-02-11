package com.shadowai.provideradapters

import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AdapterHealthChecker.
 */
class AdapterHealthCheckerTest {

    private lateinit var healthChecker: AdapterHealthChecker
    private lateinit var metrics: AdapterMetrics

    @Before
    fun setup() {
        metrics = AdapterMetrics()
        healthChecker = AdapterHealthChecker(
            metrics = metrics,
            defaultTimeoutMs = 1000L,
            healthCheckIntervalMs = 5000L
        )
    }

    @Test
    fun `checkHealth returns false for unregistered adapter`() = runBlocking {
        val result = healthChecker.checkHealth(ProviderId.OPENAI)

        assertFalse(result.isHealthy)
        assertEquals("Adapter not registered", result.error)
    }

    @Test
    fun `registerAdapter initializes health status`() {
        val adapter = createMockAdapter(ProviderId.OPENAI, isAvailable = true)
        healthChecker.registerAdapter(adapter)

        val status = healthChecker.getHealthStatus(ProviderId.OPENAI)
        assertNotNull(status)
        assertTrue(status!!.isHealthy) // Initially assumed healthy
    }

    @Test
    fun `checkHealth returns true for available adapter`() = runBlocking {
        val adapter = createMockAdapter(ProviderId.ANTHROPIC, isAvailable = true)
        healthChecker.registerAdapter(adapter)

        val result = healthChecker.checkHealth(ProviderId.ANTHROPIC)

        assertTrue(result.isHealthy)
        assertNull(result.error)
        assertTrue(result.latencyMs >= 0)
    }

    @Test
    fun `checkHealth returns false for unavailable adapter`() = runBlocking {
        val adapter = createMockAdapter(ProviderId.GEMINI, isAvailable = false)
        healthChecker.registerAdapter(adapter)

        val result = healthChecker.checkHealth(ProviderId.GEMINI)

        assertFalse(result.isHealthy)
        assertEquals("Provider reported unavailable", result.error)
    }

    @Test
    fun `checkHealth tracks consecutive failures`() = runBlocking {
        val adapter = createMockAdapter(ProviderId.GROQ, isAvailable = false)
        healthChecker.registerAdapter(adapter)

        healthChecker.checkHealth(ProviderId.GROQ)
        healthChecker.checkHealth(ProviderId.GROQ)
        healthChecker.checkHealth(ProviderId.GROQ)

        val status = healthChecker.getHealthStatus(ProviderId.GROQ)
        assertEquals(3, status!!.consecutiveFailures)
    }

    @Test
    fun `checkHealth resets consecutive failures on success`() = runBlocking {
        // Start with failures
        val failingAdapter = createMockAdapter(ProviderId.MISTRAL, isAvailable = false)
        healthChecker.registerAdapter(failingAdapter)
        healthChecker.checkHealth(ProviderId.MISTRAL)
        healthChecker.checkHealth(ProviderId.MISTRAL)

        assertEquals(2, healthChecker.getHealthStatus(ProviderId.MISTRAL)!!.consecutiveFailures)

        // Replace with successful adapter
        healthChecker.unregisterAdapter(ProviderId.MISTRAL)
        val successAdapter = createMockAdapter(ProviderId.MISTRAL, isAvailable = true)
        healthChecker.registerAdapter(successAdapter)
        healthChecker.checkHealth(ProviderId.MISTRAL)

        assertEquals(0, healthChecker.getHealthStatus(ProviderId.MISTRAL)!!.consecutiveFailures)
    }

    @Test
    fun `isHealthy returns correct state`() = runBlocking {
        val adapter = createMockAdapter(ProviderId.DEEPSEEK, isAvailable = true)
        healthChecker.registerAdapter(adapter)
        healthChecker.checkHealth(ProviderId.DEEPSEEK)

        assertTrue(healthChecker.isHealthy(ProviderId.DEEPSEEK))
    }

    @Test
    fun `unregisterAdapter removes adapter`() {
        val adapter = createMockAdapter(ProviderId.XAI, isAvailable = true)
        healthChecker.registerAdapter(adapter)
        assertNotNull(healthChecker.getHealthStatus(ProviderId.XAI))

        healthChecker.unregisterAdapter(ProviderId.XAI)
        assertNull(healthChecker.getHealthStatus(ProviderId.XAI))
    }

    @Test
    fun `checkAllHealth checks all registered adapters`() = runBlocking {
        healthChecker.registerAdapter(createMockAdapter(ProviderId.OPENAI, isAvailable = true))
        healthChecker.registerAdapter(createMockAdapter(ProviderId.ANTHROPIC, isAvailable = true))
        healthChecker.registerAdapter(createMockAdapter(ProviderId.GEMINI, isAvailable = false))

        val results = healthChecker.checkAllHealth()

        assertEquals(3, results.size)
        assertEquals(2, results.count { it.isHealthy })
        assertEquals(1, results.count { !it.isHealthy })
    }

    @Test
    fun `getAllHealthStatus returns all statuses`() = runBlocking {
        healthChecker.registerAdapter(createMockAdapter(ProviderId.OPENAI, isAvailable = true))
        healthChecker.registerAdapter(createMockAdapter(ProviderId.ANTHROPIC, isAvailable = true))

        healthChecker.checkAllHealth()

        val allStatus = healthChecker.getAllHealthStatus()
        assertEquals(2, allStatus.size)
    }

    @Test
    fun `health check updates metrics`() = runBlocking {
        val adapter = createMockAdapter(ProviderId.COHERE, isAvailable = true)
        healthChecker.registerAdapter(adapter)

        healthChecker.checkHealth(ProviderId.COHERE)

        val metricsSnapshot = metrics.getMetrics(ProviderId.COHERE)
        assertNotNull(metricsSnapshot)
        assertTrue(metricsSnapshot!!.isHealthy)
    }

    @Test
    fun `HealthStatus isStale returns correct value`() {
        val freshStatus = HealthStatus(
            providerId = ProviderId.OPENAI,
            isHealthy = true,
            lastCheckTimestamp = System.currentTimeMillis()
        )
        assertFalse(freshStatus.isStale())

        val staleStatus = HealthStatus(
            providerId = ProviderId.OPENAI,
            isHealthy = true,
            lastCheckTimestamp = System.currentTimeMillis() - 200_000L
        )
        assertTrue(staleStatus.isStale())
    }

    // Helper to create mock adapters for testing
    private fun createMockAdapter(providerId: ProviderId, isAvailable: Boolean): ProviderAdapter {
        return object : ProviderAdapter {
            override val providerId: ProviderId = providerId
            override val config: ProviderAdapterConfig = ProviderAdapterConfig(providerId, apiKeySecret = null)
            override suspend fun initialize(): Boolean = true
            override suspend fun validateConfig(): Boolean = true
            override suspend fun isAvailable(): Boolean = isAvailable
            override suspend fun canExecute(transform: Transform): Boolean = false
            override suspend fun execute(
                transform: Transform,
                input: Any,
                parameters: Map<String, Any>
            ): Result<Any> = Result.failure(NotImplementedError())
        }
    }
}
