package com.shadowai.provideradapters

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.shadowai.core.ProviderId

/**
 * Unit tests for AdapterMetrics.
 */
class AdapterMetricsTest {

    private lateinit var metrics: AdapterMetrics

    @Before
    fun setup() {
        metrics = AdapterMetrics()
    }

    @Test
    fun `recordSuccess increments success count`() {
        val providerId = ProviderId.OPENAI

        metrics.recordSuccess(providerId, 100L, 50)
        metrics.recordSuccess(providerId, 150L, 75)

        val snapshot = metrics.getMetrics(providerId)
        assertNotNull(snapshot)
        assertEquals(2L, snapshot!!.successCount)
        assertEquals(0L, snapshot.failureCount)
        assertEquals(2L, snapshot.totalRequests)
        assertEquals(1.0, snapshot.successRate, 0.01)
    }

    @Test
    fun `recordFailure increments failure count`() {
        val providerId = ProviderId.ANTHROPIC

        metrics.recordFailure(providerId, 500L, "timeout")
        metrics.recordFailure(providerId, 600L, "rate_limit")
        metrics.recordFailure(providerId, 400L, "timeout")

        val snapshot = metrics.getMetrics(providerId)
        assertNotNull(snapshot)
        assertEquals(0L, snapshot!!.successCount)
        assertEquals(3L, snapshot.failureCount)
        assertEquals(0.0, snapshot.successRate, 0.01)
        assertEquals(2L, snapshot.errorBreakdown["timeout"])
        assertEquals(1L, snapshot.errorBreakdown["rate_limit"])
    }

    @Test
    fun `calculates correct success rate`() {
        val providerId = ProviderId.GEMINI

        metrics.recordSuccess(providerId, 100L)
        metrics.recordSuccess(providerId, 100L)
        metrics.recordSuccess(providerId, 100L)
        metrics.recordFailure(providerId, 100L, "error")

        val snapshot = metrics.getMetrics(providerId)
        assertEquals(0.75, snapshot!!.successRate, 0.01)
    }

    @Test
    fun `tracks average latency correctly`() {
        val providerId = ProviderId.OPENAI

        metrics.recordSuccess(providerId, 100L)
        metrics.recordSuccess(providerId, 200L)
        metrics.recordSuccess(providerId, 300L)

        val snapshot = metrics.getMetrics(providerId)
        assertEquals(200L, snapshot!!.avgLatencyMs)
    }

    @Test
    fun `tracks min and max latency`() {
        val providerId = ProviderId.GROQ

        metrics.recordSuccess(providerId, 150L)
        metrics.recordSuccess(providerId, 50L)
        metrics.recordSuccess(providerId, 300L)

        val snapshot = metrics.getMetrics(providerId)
        assertEquals(50L, snapshot!!.minLatencyMs)
        assertEquals(300L, snapshot.maxLatencyMs)
    }

    @Test
    fun `recordHealthCheck updates health state`() {
        val providerId = ProviderId.MISTRAL

        metrics.recordHealthCheck(providerId, true, 45L)

        val snapshot = metrics.getMetrics(providerId)
        assertTrue(snapshot!!.isHealthy)
        assertEquals(45L, snapshot.lastHealthCheckLatencyMs)
    }

    @Test
    fun `tracks tokens used`() {
        val providerId = ProviderId.OPENAI

        metrics.recordSuccess(providerId, 100L, 100)
        metrics.recordSuccess(providerId, 100L, 250)
        metrics.recordSuccess(providerId, 100L, 150)

        val snapshot = metrics.getMetrics(providerId)
        assertEquals(500L, snapshot!!.totalTokensUsed)
    }

    @Test
    fun `getMetrics returns null for unknown provider`() {
        val snapshot = metrics.getMetrics(ProviderId.NOVELAI)
        assertNull(snapshot)
    }

    @Test
    fun `resetMetrics clears provider data`() {
        val providerId = ProviderId.DEEPSEEK

        metrics.recordSuccess(providerId, 100L)
        assertNotNull(metrics.getMetrics(providerId))

        metrics.resetMetrics(providerId)
        assertNull(metrics.getMetrics(providerId))
    }

    @Test
    fun `resetAllMetrics clears all data`() {
        metrics.recordSuccess(ProviderId.OPENAI, 100L)
        metrics.recordSuccess(ProviderId.ANTHROPIC, 100L)

        metrics.resetAllMetrics()

        assertTrue(metrics.getAllMetrics().isEmpty())
    }

    @Test
    fun `getAllMetrics returns all providers`() {
        metrics.recordSuccess(ProviderId.OPENAI, 100L)
        metrics.recordSuccess(ProviderId.ANTHROPIC, 200L)
        metrics.recordSuccess(ProviderId.GEMINI, 300L)

        val all = metrics.getAllMetrics()
        assertEquals(3, all.size)
        assertTrue(all.containsKey(ProviderId.OPENAI))
        assertTrue(all.containsKey(ProviderId.ANTHROPIC))
        assertTrue(all.containsKey(ProviderId.GEMINI))
    }

    @Test
    fun `metricsSnapshot toSummary produces readable output`() {
        val providerId = ProviderId.OPENAI

        metrics.recordSuccess(providerId, 100L, 50)
        metrics.recordHealthCheck(providerId, true, 30L)

        val summary = metrics.getMetrics(providerId)!!.toSummary()
        assertTrue(summary.contains("Requests: 1"))
        assertTrue(summary.contains("100% success"))
        assertTrue(summary.contains("Tokens: 50"))
        assertTrue(summary.contains("Healthy: true"))
    }
}
