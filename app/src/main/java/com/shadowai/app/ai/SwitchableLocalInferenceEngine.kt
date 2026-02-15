package com.shadowai.app.ai

import android.util.Log
import com.shadowai.app.BuildConfig
import com.shadowai.app.auth.UserPreferences
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime-switchable LocalInferenceEngine wrapper.
 *
 * H-24: Implements complete inference engine switching with:
 * - Proper model unload/reload when switching modes
 * - Native library cleanup and re-initialization
 * - Automatic fallback when isolated process crashes
 * - State preservation across mode switches
 *
 * Delegates to LocalInferenceManager or IsolatedInferenceManager based on
 * user preference (with BuildConfig fallback default).
 */
@Singleton
class SwitchableLocalInferenceEngine @Inject constructor(
    private val localManager: LocalInferenceManager,
    private val isolatedManager: IsolatedInferenceManager,
    private val userPreferences: UserPreferences
) : LocalInferenceEngine, InferenceEngineControl, ModelTreeUriConfigurable {

    companion object {
        private const val TAG = "SwitchableInference"
        private const val MODEL_SWITCH_TIMEOUT_MS = 30000L // 30 seconds max for model switch
        private const val MAX_CRASH_RECOVERY_ATTEMPTS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handleOwners = ConcurrentHashMap<LocalModelHandle, LocalInferenceEngine>()
    private val loadedModels = ConcurrentHashMap<String, ModelLoadInfo>() // path -> load info
    private val switchMutex = Mutex()

    @Volatile
    private var useIsolatedEngine: Boolean = BuildConfig.USE_ISOLATED_INFERENCE_ENGINE

    @Volatile
    private var isSwitching = false

    private var crashRecoveryAttempts = 0

    init {
        scope.launch {
            userPreferences.inferenceIsolationEnabled.collect { enabled ->
                if (enabled != useIsolatedEngine && !isSwitching) {
                    Log.i(TAG, "Preference changed: switching to ${if (enabled) "isolated" else "in-process"} mode")
                    switchMode(enabled)
                }
            }
        }

        // Monitor isolated process connection status for crash recovery
        scope.launch {
            isolatedManager.connectionStatus.collect { status ->
                when (status) {
                    IsolatedInferenceManager.ConnectionStatus.FAILED -> {
                        if (useIsolatedEngine && crashRecoveryAttempts < MAX_CRASH_RECOVERY_ATTEMPTS) {
                            crashRecoveryAttempts++
                            Log.w(TAG, "Isolated process failed, attempt $crashRecoveryAttempts/$MAX_CRASH_RECOVERY_ATTEMPTS")
                            handleIsolatedProcessCrash()
                        } else if (crashRecoveryAttempts >= MAX_CRASH_RECOVERY_ATTEMPTS) {
                            Log.e(TAG, "Max crash recovery attempts reached, falling back to in-process mode")
                            fallbackToLocalMode()
                        }
                    }
                    IsolatedInferenceManager.ConnectionStatus.OPERATIONAL -> {
                        crashRecoveryAttempts = 0 // Reset on successful connection
                    }
                    else -> { /* No action needed */ }
                }
            }
        }
    }

    data class ModelLoadInfo(
        val path: String,
        val config: LocalGenerationConfig,
        val owner: LocalInferenceEngine
    )

    override val isNativeAvailable: Boolean
        get() = activeEngine().isNativeAvailable

    override suspend fun loadModel(
        modelPath: String,
        config: LocalGenerationConfig
    ): LocalModelHandle? = switchMutex.withLock {
        if (isSwitching) {
            Log.w(TAG, "Cannot load model while switching modes")
            return@withLock null
        }

        val engine = activeEngine()
        val handle = engine.loadModel(modelPath, config)
        if (handle != null) {
            handleOwners[handle] = engine
            loadedModels[modelPath] = ModelLoadInfo(modelPath, config, engine)
        }
        return@withLock handle
    }

    override suspend fun unloadModel(model: LocalModelHandle) {
        switchMutex.withLock {
            val owner = handleOwners.remove(model)
            owner?.unloadModel(model)

            // Also remove from loadedModels tracking
            loadedModels.entries.removeIf { (_, info) ->
                handleOwners.none { (_, engine) -> engine == info.owner }
            }
        }
    }

    override suspend fun isModelLoaded(modelPath: String): Boolean {
        return loadedModels.containsKey(modelPath)
    }

    override suspend fun getAvailableModels(): List<String> {
        // Combine models from both engines for maximum availability
        val localModels = try {
            localManager.getAvailableModels()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local models", e)
            emptyList()
        }

        val isolatedModels = try {
            if (isolatedManager.connectionStatus.value == IsolatedInferenceManager.ConnectionStatus.OPERATIONAL) {
                isolatedManager.getAvailableModels()
            } else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get isolated models", e)
            emptyList()
        }

        return (localModels + isolatedModels).distinct()
    }

    override suspend fun warmup(): Result<Unit> {
        return if (useIsolatedEngine) {
            isolatedManager.bindService()
        } else {
            Result.success(Unit)
        }
    }

    override fun setCustomModelTreeUri(treeUri: android.net.Uri?) {
        localManager.setCustomModelTreeUri(treeUri)
        isolatedManager.setCustomModelTreeUri(treeUri)
    }

    /**
     * H-24: Switch between isolated and in-process inference modes.
     *
     * This implementation:
     * 1. Acquires lock to prevent concurrent operations
     * 2. Unloads all models from current engine
     * 3. Cleans up native resources
     * 4. Switches mode
     * 5. Reloads all previously loaded models
     * 6. Handles failures gracefully
     *
     * @param isolated If true, use isolated process inference; if false, use in-process inference
     * @return Result indicating success or failure of the switch
     */
    suspend fun switchMode(isolated: Boolean): Result<Unit> = switchMutex.withLock {
        if (isolated == useIsolatedEngine) {
            Log.d(TAG, "Already in ${if (isolated) "isolated" else "in-process"} mode")
            return@withLock Result.success(Unit)
        }

        isSwitching = true
        Log.i(TAG, "Switching to ${if (isolated) "isolated" else "in-process"} mode...")

        return@withLock try {
            // Step 1: Save current model states
            val modelStates = loadedModels.values.map { it.copy() }
            Log.d(TAG, "Preserving ${modelStates.size} model load states")

            // Step 2: Unload all models from current engine
            val unloadResults = unloadAllModels()
            if (unloadResults.isFailure) {
                Log.w(TAG, "Some models failed to unload: ${unloadResults.exceptionOrNull()?.message}")
                // Continue anyway - force cleanup ahead
            }

            // Step 3: Clean up native resources if leaving isolated mode
            if (!isolated) {
                // We're switching TO local mode, so unbind isolated service
                withTimeoutOrNull(MODEL_SWITCH_TIMEOUT_MS) {
                    isolatedManager.unbindService()
                }
            }

            // Step 4: Prepare the target engine
            if (isolated) {
                // Switching to isolated - ensure service is bound
                val bindResult = withTimeoutOrNull(MODEL_SWITCH_TIMEOUT_MS) {
                    isolatedManager.bindService()
                }
                if (bindResult == null || bindResult.isFailure) {
                    throw IllegalStateException(
                        "Failed to bind isolated inference service: ${bindResult?.exceptionOrNull()?.message ?: "timeout"}"
                    )
                }
            }

            // Step 5: Update the mode
            useIsolatedEngine = isolated
            Log.i(TAG, "Mode switched to ${if (isolated) "isolated" else "in-process"}")

            // Step 6: Reload models in new mode
            val reloadResults = reloadModels(modelStates)
            if (reloadResults.isFailure) {
                Log.w(TAG, "Some models failed to reload: ${reloadResults.exceptionOrNull()?.message}")
                // Don't fail the switch - models can be reloaded on demand
            }

            // Update preference if switch was successful
            if (isolated != userPreferences.inferenceIsolationEnabled.first()) {
                scope.launch {
                    userPreferences.saveInferenceIsolationEnabled(isolated)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Mode switch failed", e)
            Result.failure(e)
        } finally {
            isSwitching = false
        }
    }

    /**
     * H-24: Get current inference mode.
     *
     * @return true if using isolated process inference, false if using in-process
     */
    fun isIsolatedMode(): Boolean = useIsolatedEngine

    /**
     * H-24: Check if currently switching modes.
     */
    fun isSwitching(): Boolean = isSwitching

    /**
     * H-24: Force immediate fallback to local mode.
     * Used when isolated process crashes repeatedly.
     */
    suspend fun forceFallbackToLocal(): Result<Unit> {
        Log.w(TAG, "Force fallback to local mode requested")
        return switchMode(isolated = false)
    }

    private fun activeEngine(): LocalInferenceEngine {
        return if (useIsolatedEngine) isolatedManager else localManager
    }

    private suspend fun unloadAllModels(): Result<Unit> = withContext(Dispatchers.IO) {
        val handles = handleOwners.keys.toList()
        var failures = 0

        handles.forEach { handle ->
            val owner = handleOwners[handle]
            try {
                owner?.unloadModel(handle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unload model", e)
                failures++
            }
        }

        handleOwners.clear()
        loadedModels.clear()

        if (failures > 0) {
            Result.failure(IllegalStateException("Failed to unload $failures models"))
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun reloadModels(states: List<ModelLoadInfo>): Result<Unit> = withContext(Dispatchers.IO) {
        var successes = 0
        var failures = 0

        states.forEach { state ->
            try {
                val engine = activeEngine()
                val handle = engine.loadModel(state.path, state.config)
                if (handle != null) {
                    handleOwners[handle] = engine
                    loadedModels[state.path] = ModelLoadInfo(state.path, state.config, engine)
                    successes++
                    Log.d(TAG, "Reloaded model: ${state.path}")
                } else {
                    failures++
                    Log.w(TAG, "Failed to reload model: ${state.path}")
                }
            } catch (e: Exception) {
                failures++
                Log.w(TAG, "Exception reloading model: ${state.path}", e)
            }
        }

        Log.i(TAG, "Model reload complete: $successes succeeded, $failures failed")

        if (failures > 0 && successes == 0) {
            Result.failure(IllegalStateException("All models failed to reload"))
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun handleIsolatedProcessCrash() {
        Log.w(TAG, "Handling isolated process crash")

        // Save current models
        val modelStates = loadedModels.values.toList()

        // Clear ownership (the isolated process died, so handles are invalid)
        handleOwners.clear()
        loadedModels.clear()

        // Attempt to rebind and reload
        withContext(Dispatchers.IO) {
            try {
                isolatedManager.bindService()
                reloadModels(modelStates)
            } catch (e: Exception) {
                Log.e(TAG, "Crash recovery failed", e)
            }
        }
    }

    private suspend fun fallbackToLocalMode() {
        Log.w(TAG, "Falling back to local mode due to isolated process instability")

        // Save current state
        val modelStates = loadedModels.values.toList()

        // Unbind isolated service
        isolatedManager.unbindService()

        // Switch to local mode
        useIsolatedEngine = false

        // Update preference to prevent auto-switch back
        scope.launch {
            userPreferences.saveInferenceIsolationEnabled(false)
        }

        // Reload models in local mode
        withContext(Dispatchers.IO) {
            reloadModels(modelStates)
        }

        Log.i(TAG, "Fallback to local mode complete")
    }
}
