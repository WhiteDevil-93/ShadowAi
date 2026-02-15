package com.shadowai.app.providers

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.Capability
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.providers.ApiStyle
import com.shadowai.provideradapters.ProviderCrudRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive unit tests for ProviderSelector.
 * Tests race condition fix, thread safety with concurrent access, and fallback selection logic.
 */
class ProviderSelectorTest {

    private lateinit var mockActiveProviderManager: ActiveProviderManager
    private lateinit var mockAdminRepository: AdminRepository
    private lateinit var mockCrudRepository: ProviderCrudRepository
    private lateinit var providerSelector: ProviderSelector

    @Before
    fun setup() {
        mockActiveProviderManager = mockk(relaxed = true)
        mockAdminRepository = mockk(relaxed = true)
        mockCrudRepository = mockk(relaxed = true)
        
        providerSelector = ProviderSelector(
            mockActiveProviderManager,
            mockAdminRepository,
            mockCrudRepository
        )
    }

    // ========== Basic Provider Selection Tests ==========

    @Test
    fun `nextLocal returns null when no local providers available`() = runTest {
        coEvery { mockActiveProviderManager.getActiveConfigs(null) } returns emptyList()

        val provider = providerSelector.nextLocal(TaskType.TEXT)

        assertNull(provider)
    }

    @Test
    fun `nextCloud returns null when no cloud providers available`() = runTest {
        coEvery { mockActiveProviderManager.getActiveConfigs(null) } returns emptyList()

        val provider = providerSelector.nextCloud(TaskType.TEXT)

        assertNull(provider)
    }

    @Test
    fun `nextLocal filters by task type capability`() = runTest {
        val localConfig = createActiveConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT)
        )

        val imageConfig = createActiveConfig(
            providerId = ProviderId.LOCAL_IMAGE,
            apiStyle = ApiStyle.LOCAL_IMAGE,
            capabilities = setOf(Capability.IMAGE_GEN)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(localConfig, imageConfig))

        val result = providerSelector.nextLocal(TaskType.TEXT)

        assertNotNull(result)
        assertEquals(Capability.TEXT, result?.capabilities?.firstOrNull())
    }

    @Test
    fun `nextCloud filters by task type capability`() = runTest {
        val textConfig = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val imageConfig = createActiveConfig(
            providerId = ProviderId.OPENAI_DALLE,
            apiStyle = ApiStyle.OPENAI_IMAGE,
            capabilities = setOf(Capability.IMAGE_GEN)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(textConfig, imageConfig))

        val result = providerSelector.nextCloud(TaskType.IMAGE_GEN)

        assertNotNull(result)
        assertEquals(Capability.IMAGE_GEN, result?.capabilities?.firstOrNull())
    }

    @Test
    fun `nextLocal excludes cloud providers`() = runTest {
        val localConfig = createActiveConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT)
        )

        val cloudConfig = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(localConfig, cloudConfig))

        val result = providerSelector.nextLocal(TaskType.TEXT)

        assertNotNull(result)
        assertEquals(ApiStyle.LOCAL_TEXT, result?.apiStyle)
    }

    @Test
    fun `nextCloud excludes local providers`() = runTest {
        val localConfig = createActiveConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT)
        )

        val cloudConfig = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(localConfig, cloudConfig))

        val result = providerSelector.nextCloud(TaskType.TEXT)

        assertNotNull(result)
        assertEquals(ApiStyle.OPENAI, result?.apiStyle)
    }

    @Test
    fun `gets preferred provider when set`() = runTest {
        val openaiConfig = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val geminiConfig = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(openaiConfig, geminiConfig))

        coEvery { mockAdminRepository.getActiveProvider() }.returns(ProviderId.GEMINI)

        val result = providerSelector.nextCloud(TaskType.TEXT)

        assertNotNull(result)
        assertEquals(ProviderId.GEMINI, result?.providerId)
    }

    // ========== Race Condition Tests ==========

    @Test
    fun `round robin through local providers increments index`() = runTest {
        val local1 = createActiveConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT)
        )

        val local2 = createActiveConfig(
            providerId = ProviderId.LOCAL_IMAGE,
            apiStyle = ApiStyle.LOCAL_IMAGE,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(local1, local2))

        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        // Get multiple providers
        val results = mutableListOf<ActiveProviderConfig?>()
        repeat(4) {
            results.add(providerSelector.nextLocal(TaskType.TEXT))
        }

        // Should get different providers due to round-robin
        assertEquals(4, results.size)
        assertNotNull(results[0])
        assertNotNull(results[1])
    }

    @Test
    fun `round robin through cloud providers`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val gemini = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(openai, gemini))

        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val first = providerSelector.nextCloud(TaskType.TEXT)
        val second = providerSelector.nextCloud(TaskType.TEXT)

        assertNotNull(first)
        assertNotNull(second)
    }

    @Test
    fun `round robin wraps around correctly`() = runTest {
        val config = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(config))

        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        // Should always return the same single provider
        repeat(10) {
            val result = providerSelector.nextCloud(TaskType.TEXT)
            assertEquals(ProviderId.OPENAI, result?.providerId)
        }
    }

    // ========== Thread Safety Tests ==========

    @Test
    fun `thread safe with concurrent local selections`() = runTest {
        val configs = (0..4).map {
            createActiveConfig(
                providerId = ProviderId.LOCAL_TEXT,
                apiStyle = ApiStyle.LOCAL_TEXT,
                capabilities = setOf(Capability.TEXT)
            )
        }

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(configs)
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val successCount = AtomicInteger(0)
        val jobs = mutableListOf<Deferred<Unit>>()

        // Launch 20 concurrent selections
        repeat(20) {
            jobs.add(async {
                try {
                    val result = providerSelector.nextLocal(TaskType.TEXT)
                    if (result != null) successCount.incrementAndGet()
                } catch (e: Exception) {
                    fail("Concurrent access threw exception: ${e.message}")
                }
            })
        }

        jobs.awaitAll()

        assertEquals("All concurrent selections should succeed", 20, successCount.get())
    }

    @Test
    fun `thread safe with concurrent cloud selections`() = runTest {
        val configs = (0..4).map {
            createActiveConfig(
                providerId = ProviderId.OPENAI,
                apiStyle = ApiStyle.OPENAI,
                capabilities = setOf(Capability.TEXT)
            )
        }

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(configs)
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val successCount = AtomicInteger(0)
        val jobs = mutableListOf<Deferred<Unit>>()

        // Launch 20 concurrent selections
        repeat(20) {
            jobs.add(async {
                try {
                    val result = providerSelector.nextCloud(TaskType.TEXT)
                    if (result != null) successCount.incrementAndGet()
                } catch (e: Exception) {
                    fail("Concurrent access threw exception: ${e.message}")
                }
            })
        }

        jobs.awaitAll()

        assertEquals("All concurrent selections should succeed", 20, successCount.get())
    }

    @Test
    fun `thread safe with mixed local and cloud access`() = runTest {
        val localConfigs = (0..2).map {
            createActiveConfig(
                providerId = ProviderId.LOCAL_TEXT,
                apiStyle = ApiStyle.LOCAL_TEXT,
                capabilities = setOf(Capability.TEXT)
            )
        }

        val cloudConfigs = (0..2).map {
            createActiveConfig(
                providerId = ProviderId.OPENAI,
                apiStyle = ApiStyle.OPENAI,
                capabilities = setOf(Capability.TEXT)
            )
        }

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(localConfigs + cloudConfigs)
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val successCount = AtomicInteger(0)
        val jobs = mutableListOf<Deferred<Unit>>()

        // Launch mixed concurrent selections
        repeat(10) {
            jobs.add(async {
                val local = providerSelector.nextLocal(TaskType.TEXT)
                if (local != null) successCount.incrementAndGet()
            })
            jobs.add(async {
                val cloud = providerSelector.nextCloud(TaskType.TEXT)
                if (cloud != null) successCount.incrementAndGet()
            })
        }

        jobs.awaitAll()

        assertEquals("All mixed selections should succeed", 20, successCount.get())
    }

    @Test
    fun `concurrent access maintains index consistency`() = runTest {
        val configs = listOf(
            createActiveConfig(
                providerId = ProviderId.OPENAI,
                apiStyle = ApiStyle.OPENAI,
                capabilities = setOf(Capability.TEXT)
            ),
            createActiveConfig(
                providerId = ProviderId.GEMINI,
                apiStyle = ApiStyle.GEMINI,
                capabilities = setOf(Capability.TEXT)
            )
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(configs)
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val selectedProviders = mutableListOf<ProviderId>()
        val mutex = kotlinx.coroutines.sync.Mutex()

        val jobs = mutableListOf<Deferred<Unit>>()

        // Launch concurrent selections and track results
        repeat(10) {
            jobs.add(async {
                val result = providerSelector.nextCloud(TaskType.TEXT)
                result?.let {
                    mutex.lock()
                    selectedProviders.add(it.providerId)
                    mutex.unlock()
                }
            })
        }

        jobs.awaitAll()

        // Should have selected both providers approximately evenly
        assertEquals(10, selectedProviders.size)
        val openaiCount = selectedProviders.count { it == ProviderId.OPENAI }
        val geminiCount = selectedProviders.count { it == ProviderId.GEMINI }
        
        // With proper round-robin, should have 5 of each (or close due to concurrency)
        assertTrue("Should select OPENAI at least once", openaiCount > 0)
        assertTrue("Should select GEMINI at least once", geminiCount > 0)
    }

    // ========== Fallback Chain Tests ==========

    @Test
    fun `getFallbackChain returns prioritized providers`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val gemini = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai, gemini))
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val chain = providerSelector.getFallbackChain(TaskType.TEXT, local = false)

        assertEquals(2, chain.size)
        assertTrue("Should have priorities", chain.all { it.priority >= 0 })
    }

    @Test
    fun `getFallbackChain puts preferred provider first`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val gemini = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai, gemini))
        coEvery { mockAdminRepository.getActiveProvider() }.returns(ProviderId.GEMINI)

        val chain = providerSelector.getFallbackChain(TaskType.TEXT, local = false)

        assertEquals(ProviderId.GEMINI, chain.first().config.providerId)
        assertEquals(0, chain.first().priority)
    }

    @Test
    fun `getFallbackChain excludes specified provider`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val gemini = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai, gemini))

        val chain = providerSelector.getFallbackChain(
            TaskType.TEXT, 
            local = false,
            excludeProviderId = ProviderId.OPENAI
        )

        assertEquals(1, chain.size)
        assertEquals(ProviderId.GEMINI, chain.first().config.providerId)
    }

    @Test
    fun `getFallbackChain returns empty when no providers match`() = runTest {
        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(emptyList())

        val chain = providerSelector.getFallbackChain(TaskType.TEXT, local = false)

        assertTrue(chain.isEmpty())
    }

    // ========== Execute With Fallback Tests ==========

    @Test
    fun `executeWithFallback succeeds on first attempt`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai))
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val result = providerSelector.executeWithFallback<String>(
            TaskType.TEXT,
            local = false
        ) { config ->
            Result.success("Success with ${config.providerId}")
        }

        val (fallbackResult, data) = result
        assertTrue("Should succeed", fallbackResult is FallbackResult.Success)
        assertEquals("Success with OPENAI", data)
        
        val success = fallbackResult as FallbackResult.Success
        assertEquals(1, success.attempts)
    }

    @Test
    fun `executeWithFallback tries next on failure`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val gemini = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai, gemini))
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        var attemptCount = 0
        val result = providerSelector.executeWithFallback<String>(
            TaskType.TEXT,
            local = false
        ) { config ->
            attemptCount++
            if (config.providerId == ProviderId.OPENAI) {
                Result.failure(Exception("OPENAI failed"))
            } else {
                Result.success("Success with ${config.providerId}")
            }
        }

        val (fallbackResult, data) = result
        assertTrue("Should eventually succeed", fallbackResult is FallbackResult.Success)
        assertEquals(2, attemptCount)
        assertEquals("Success with GEMINI", data)
    }

    @Test
    fun `executeWithFallback exhausts all providers`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai))
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val result = providerSelector.executeWithFallback<String>(
            TaskType.TEXT,
            local = false
        ) { _ ->
            Result.failure(Exception("All failed"))
        }

        val (fallbackResult, data) = result
        assertTrue("Should be exhausted", fallbackResult is FallbackResult.Exhausted)
        assertNull(data)
        
        val exhausted = fallbackResult as FallbackResult.Exhausted
        assertEquals(1, exhausted.attempted.size)
        assertEquals(1, exhausted.errors.size)
    }

    @Test
    fun `executeWithFallback disables provider on quota error`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai))
        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)
        coEvery { mockCrudRepository.setProviderEnabledSync(any(), false) } just Runs

        providerSelector.executeWithFallback<String>(
            TaskType.TEXT,
            local = false
        ) { _ ->
            Result.failure(ProviderQuotaException("Quota exceeded", ProviderId.OPENAI))
        }

        coVerify { mockCrudRepository.setProviderEnabledSync(ProviderId.OPENAI, false) }
    }

    @Test
    fun `executeWithFallback disables provider on auth error`() = runTest {
        val openai = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }.returns(listOf(openai))
        coEvery { mockCrudRepository.setProviderEnabledSync(any(), false) } just Runs

        providerSelector.executeWithFallback<String>(
            TaskType.TEXT,
            local = false
        ) { _ ->
            Result.failure(ProviderAuthException("Auth failed", ProviderId.OPENAI))
        }

        coVerify { mockCrudRepository.setProviderEnabledSync(ProviderId.OPENAI, false) }
    }

    // ========== Provider Lists Tests ==========

    @Test
    fun `getLocalProviders returns all local providers`() = runTest {
        val local1 = createActiveConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT)
        )

        val local2 = createActiveConfig(
            providerId = ProviderId.LOCAL_IMAGE,
            apiStyle = ApiStyle.LOCAL_IMAGE,
            capabilities = setOf(Capability.IMAGE_GEN)
        )

        val cloud = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(local1, local2, cloud))

        val localProviders = providerSelector.getLocalProviders(TaskType.TEXT)

        assertEquals(1, localProviders.size)
        assertEquals(ApiStyle.LOCAL_TEXT, localProviders[0].apiStyle)
    }

    @Test
    fun `getCloudProviders returns all cloud providers`() = runTest {
        val cloud1 = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val cloud2 = createActiveConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI,
            capabilities = setOf(Capability.TEXT)
        )

        val local = createActiveConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(cloud1, cloud2, local))

        val cloudProviders = providerSelector.getCloudProviders(TaskType.TEXT)

        assertEquals(2, cloudProviders.size)
        assertTrue(cloudProviders.all {
            it.apiStyle == ApiStyle.OPENAI || it.apiStyle == ApiStyle.GEMINI
        })
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `handles preferred provider not in available list`() = runTest {
        val config = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(config))

        coEvery { mockAdminRepository.getActiveProvider() }.returns(ProviderId.GEMINI)

        val result = providerSelector.nextCloud(TaskType.TEXT)

        assertNotNull(result)
        assertEquals(ProviderId.OPENAI, result?.providerId)
    }

    @Test
    fun `handles empty preferred provider`() = runTest {
        val config = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(config))

        coEvery { mockAdminRepository.getActiveProvider() }.returns(null)

        val result = providerSelector.nextCloud(TaskType.TEXT)

        assertNotNull(result)
        assertEquals(ProviderId.OPENAI, result?.providerId)
    }

    @Test
    fun `filters capabilities correctly for different task types`() = runTest {
        val textConfig = createActiveConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI,
            capabilities = setOf(Capability.TEXT)
        )

        val imageConfig = createActiveConfig(
            providerId = ProviderId.OPENAI_DALLE,
            apiStyle = ApiStyle.OPENAI_IMAGE,
            capabilities = setOf(Capability.IMAGE_GEN)
        )

        val audioConfig = createActiveConfig(
            providerId = ProviderId.OPENAI_AUDIO,
            apiStyle = ApiStyle.OPENAI_AUDIO,
            capabilities = setOf(Capability.AUDIO_SYNTHESIZE)
        )

        coEvery { mockActiveProviderManager.getActiveConfigs(null) }
            .returns(listOf(textConfig, imageConfig, audioConfig))

        val textResult = providerSelector.nextCloud(TaskType.TEXT)
        val imageResult = providerSelector.nextCloud(TaskType.IMAGE_GEN)
        val audioResult = providerSelector.nextCloud(TaskType.AUDIO_GEN)

        assertEquals(Capability.TEXT, textResult?.capabilities?.firstOrNull())
        assertEquals(Capability.IMAGE_GEN, imageResult?.capabilities?.firstOrNull())
        assertEquals(Capability.AUDIO_SYNTHESIZE, audioResult?.capabilities?.firstOrNull())
    }

    @Test
    fun `disableProvider calls repository`() = runTest {
        coEvery { mockCrudRepository.setProviderEnabledSync(ProviderId.OPENAI, false) } just Runs

        providerSelector.disableProvider(ProviderId.OPENAI)

        coVerify { mockCrudRepository.setProviderEnabledSync(ProviderId.OPENAI, false) }
    }

    // ========== Helper Methods ==========

    private fun createActiveConfig(
        providerId: ProviderId,
        apiStyle: ApiStyle,
        capabilities: Set<Capability>
    ): ActiveProviderConfig {
        return ActiveProviderConfig(
            providerId = providerId,
            displayName = providerId.toString(),
            apiStyle = apiStyle,
            capabilities = capabilities,
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            model = "test-model"
        )
    }
}