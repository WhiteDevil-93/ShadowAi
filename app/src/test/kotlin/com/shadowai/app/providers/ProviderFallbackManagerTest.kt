package com.shadowai.app.providers

import com.shadowai.app.admin.implementation.AdminRepository
import com.shadowai.app.tasks.TaskType
import com.shadowai.core.Capability
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.ProviderId
import com.shadowai.core.providers.ActiveProviderConfig
import com.shadowai.core.providers.ApiStyle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderFallbackManagerTest {

    private lateinit var manager: ProviderFallbackManager
    private lateinit var localInferenceEngine: LocalInferenceEngine
    private lateinit var providerSelector: ProviderSelector
    private lateinit var adminRepository: AdminRepository

    @Before
    fun setUp() {
        localInferenceEngine = mockk()
        providerSelector = mockk()
        adminRepository = mockk(relaxed = true)

        every { localInferenceEngine.isNativeAvailable } returns true
        every { localInferenceEngine.getAvailableModels() } returns listOf("model.gguf")

        manager = ProviderFallbackManager(
            localInferenceEngine = localInferenceEngine,
            providerSelector = providerSelector,
            adminRepository = adminRepository
        )
    }

    @Test
    fun `selectProvider prefers local when local is available`() = runTest {
        val localConfig = mockConfig(
            providerId = ProviderId.LOCAL_TEXT,
            apiStyle = ApiStyle.LOCAL_TEXT
        )
        coEvery { providerSelector.nextLocal(TaskType.TEXT) } returns localConfig

        val result = manager.selectProvider(TaskType.TEXT, preferLocal = true)

        assertNotNull(result)
        assertFalse(result.isFallback)
        assertEquals(ProviderId.LOCAL_TEXT, result.config.providerId)
    }

    @Test
    fun `selectProvider falls back to cloud when local inference unavailable`() = runTest {
        every { localInferenceEngine.isNativeAvailable } returns false
        val cloudConfig = mockConfig(
            providerId = ProviderId.OPENAI,
            apiStyle = ApiStyle.OPENAI_COMPAT
        )
        coEvery {
            providerSelector.getFallbackChain(TaskType.TEXT, false, null)
        } returns listOf(FallbackProvider(cloudConfig, priority = 0))

        val result = manager.selectProvider(TaskType.TEXT, preferLocal = true)

        assertNotNull(result)
        assertTrue(result.isFallback)
        assertEquals(ProviderId.OPENAI, result.config.providerId)
        assertTrue(result.fallbackReason?.contains("falling back") == true)
    }

    @Test
    fun `selectProvider cloud-first uses cloud candidate when available`() = runTest {
        val cloudConfig = mockConfig(
            providerId = ProviderId.GEMINI,
            apiStyle = ApiStyle.GEMINI
        )
        coEvery {
            providerSelector.getFallbackChain(TaskType.TEXT, false, null)
        } returns listOf(FallbackProvider(cloudConfig, priority = 0))

        val result = manager.selectProvider(TaskType.TEXT, preferLocal = false)

        assertNotNull(result)
        assertFalse(result.isFallback)
        assertEquals(ProviderId.GEMINI, result.config.providerId)
    }

    @Test
    fun `selectProvider returns null when no local or cloud provider is available`() = runTest {
        every { localInferenceEngine.isNativeAvailable } returns false
        coEvery {
            providerSelector.getFallbackChain(TaskType.TEXT, false, null)
        } returns emptyList()

        val result = manager.selectProvider(TaskType.TEXT, preferLocal = true)

        assertNull(result)
    }

    @Test
    fun `getCloudFallbackChain preserves selector ordering`() = runTest {
        val first = mockConfig(ProviderId.OPENAI, ApiStyle.OPENAI_COMPAT)
        val second = mockConfig(ProviderId.GEMINI, ApiStyle.GEMINI)

        coEvery {
            providerSelector.getFallbackChain(TaskType.TEXT, false, null)
        } returns listOf(
            FallbackProvider(first, priority = 0),
            FallbackProvider(second, priority = 1)
        )

        val chain = manager.getCloudFallbackChain(TaskType.TEXT)

        assertEquals(listOf(first, second), chain)
    }

    @Test
    fun `wouldFallback is true for local provider when local engine is unavailable`() = runTest {
        every { localInferenceEngine.isNativeAvailable } returns false

        val localFallback = manager.wouldFallback(TaskType.TEXT, ProviderId.LOCAL_TEXT)
        val cloudFallback = manager.wouldFallback(TaskType.TEXT, ProviderId.OPENAI)

        assertTrue(localFallback)
        assertFalse(cloudFallback)
    }

    private fun mockConfig(
        providerId: ProviderId,
        apiStyle: ApiStyle,
        capabilities: Set<Capability> = setOf(Capability.TEXT)
    ): ActiveProviderConfig {
        val config = mockk<ActiveProviderConfig>()
        every { config.providerId } returns providerId
        every { config.apiStyle } returns apiStyle
        every { config.capabilities } returns capabilities
        return config
    }
}
