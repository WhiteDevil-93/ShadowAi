package com.shadowai.app.ai

import com.shadowai.app.BuildConfig
import com.shadowai.app.auth.UserPreferences
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SwitchableLocalInferenceEngine.
 * Tests mode switching logic and service binding/unbinding behavior.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SwitchableLocalInferenceEngineTest {

    private lateinit var switchableEngine: SwitchableLocalInferenceEngine
    private lateinit var localManager: LocalInferenceManager
    private lateinit var isolatedManager: IsolatedInferenceManager
    private lateinit var userPreferences: UserPreferences
    private lateinit var inferenceIsolationFlow: MutableStateFlow<Boolean>
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        localManager = mock()
        isolatedManager = mock()
        userPreferences = mock()
        
        inferenceIsolationFlow = MutableStateFlow(false)
        whenever(userPreferences.inferenceIsolationEnabled).thenReturn(inferenceIsolationFlow)
        
        // Default mock behaviors
        whenever(localManager.isNativeAvailable).thenReturn(true)
        whenever(isolatedManager.isNativeAvailable).thenReturn(true)
    }

    @Test
    fun `mode switching - should return success when already in requested mode`() = testScope.runTest {
        // Given: already in non-isolated mode (default)
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        
        // When: switching to same mode (false = non-isolated)
        val result = switchableEngine.switchMode(isolated = false)
        
        // Then: should return success without any actual switch
        assertTrue(result.isSuccess)
        verify(isolatedManager, never()).bindService()
    }

    @Test
    fun `mode switching - should bind service when switching to isolated mode`() = testScope.runTest {
        // Given: in non-isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        whenever(isolatedManager.bindService()).thenReturn(Result.success(Unit))
        
        // When: switching to isolated mode
        val result = switchableEngine.switchMode(isolated = true)
        
        // Then: should bind service and return success
        assertTrue(result.isSuccess)
        verify(isolatedManager).bindService()
        assertTrue(switchableEngine.isIsolatedMode())
    }

    @Test
    fun `mode switching - should return failure when service bind fails`() = testScope.runTest {
        // Given: in non-isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        val bindError = Exception("Service bind failed")
        whenever(isolatedManager.bindService()).thenReturn(Result.failure(bindError))
        
        // When: switching to isolated mode
        val result = switchableEngine.switchMode(isolated = true)
        
        // Then: should return failure with proper error message
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to bind") == true)
        assertFalse(switchableEngine.isIsolatedMode())
    }

    @Test
    fun `active engine - should delegate to local manager when not isolated`() = testScope.runTest {
        // Given: non-isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        whenever(localManager.isNativeAvailable).thenReturn(true)
        
        // When: checking native availability
        val isAvailable = switchableEngine.isNativeAvailable
        
        // Then: should delegate to local manager
        assertTrue(isAvailable)
        verify(localManager).isNativeAvailable
    }

    @Test
    fun `active engine - should delegate to isolated manager when isolated`() = testScope.runTest {
        // Given: isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        whenever(isolatedManager.bindService()).thenReturn(Result.success(Unit))
        switchableEngine.switchMode(isolated = true)
        
        whenever(isolatedManager.isNativeAvailable).thenReturn(true)
        
        // When: checking native availability
        val isAvailable = switchableEngine.isNativeAvailable
        
        // Then: should delegate to isolated manager
        assertTrue(isAvailable)
        verify(isolatedManager).isNativeAvailable
    }

    @Test
    fun `load model - should track handle ownership for proper unloading`() = testScope.runTest {
        // Given: non-isolated mode with mock local manager
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        val mockHandle = mock<LocalModelHandle>()
        val config = mock<LocalGenerationConfig>()
        whenever(localManager.loadModel(any(), any())).thenReturn(mockHandle)
        
        // When: loading a model
        val handle = switchableEngine.loadModel("/path/to/model.gguf", config)
        
        // Then: should return handle and delegate to local manager
        assertNotNull(handle)
        assertEquals(mockHandle, handle)
        
        // When: unloading the model
        switchableEngine.unloadModel(mockHandle)
        
        // Then: should delegate unload to the owner (localManager)
        verify(localManager).unloadModel(mockHandle)
    }

    @Test
    fun `load model - should delegate to isolated manager when in isolated mode`() = testScope.runTest {
        // Given: isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        whenever(isolatedManager.bindService()).thenReturn(Result.success(Unit))
        switchableEngine.switchMode(isolated = true)
        
        val mockHandle = mock<LocalModelHandle>()
        val config = mock<LocalGenerationConfig>()
        whenever(isolatedManager.loadModel(any(), any())).thenReturn(mockHandle)
        
        // When: loading a model in isolated mode
        val handle = switchableEngine.loadModel("/path/to/model.gguf", config)
        
        // Then: should delegate to isolated manager
        assertNotNull(handle)
        verify(isolatedManager).loadModel(any(), any())
    }

    @Test
    fun `warmup - should bind service in isolated mode`() = testScope.runTest {
        // Given: isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        whenever(isolatedManager.bindService()).thenReturn(Result.success(Unit))
        switchableEngine.switchMode(isolated = true)
        
        // When: calling warmup
        val result = switchableEngine.warmup()
        
        // Then: should bind service
        assertTrue(result.isSuccess)
        verify(isolatedManager).bindService()
    }

    @Test
    fun `warmup - should return success immediately in non-isolated mode`() = testScope.runTest {
        // Given: non-isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        
        // When: calling warmup
        val result = switchableEngine.warmup()
        
        // Then: should return success without binding
        assertTrue(result.isSuccess)
        verify(isolatedManager, never()).bindService()
    }

    @Test
    fun `is isolated mode - should reflect current state`() = testScope.runTest {
        // Given: non-isolated mode initially
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        assertFalse(switchableEngine.isIsolatedMode())
        
        // When: switching to isolated mode
        whenever(isolatedManager.bindService()).thenReturn(Result.success(Unit))
        switchableEngine.switchMode(isolated = true)
        
        // Then: should reflect isolated mode
        assertTrue(switchableEngine.isIsolatedMode())
    }

    @Test
    fun `get available models - should delegate to active engine`() = testScope.runTest {
        // Given: non-isolated mode with list of models
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        val expectedModels = listOf("model1.gguf", "model2.gguf")
        whenever(localManager.getAvailableModels()).thenReturn(expectedModels)
        
        // When: getting available models
        val models = switchableEngine.getAvailableModels()
        
        // Then: should return models from local manager
        assertEquals(expectedModels, models)
    }

    @Test
    fun `unload model - should use active engine when owner not found`() = testScope.runTest {
        // Given: non-isolated mode
        switchableEngine = SwitchableLocalInferenceEngine(localManager, isolatedManager, userPreferences)
        val unknownHandle = mock<LocalModelHandle>()
        
        // When: trying to unload a handle that wasn't loaded through this engine
        switchableEngine.unloadModel(unknownHandle)
        
        // Then: should fall back to active engine (localManager)
        verify(localManager).unloadModel(unknownHandle)
    }
}
