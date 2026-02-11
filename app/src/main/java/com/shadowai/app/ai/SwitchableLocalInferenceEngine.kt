package com.shadowai.app.ai

import com.shadowai.app.BuildConfig
import com.shadowai.app.auth.UserPreferences
import com.shadowai.core.LocalGenerationConfig
import com.shadowai.core.LocalInferenceEngine
import com.shadowai.core.LocalModelHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime-switchable LocalInferenceEngine wrapper.
 *
 * Delegates to LocalInferenceManager or IsolatedInferenceManager based on
 * user preference (with BuildConfig fallback default).
 */
@Singleton
class SwitchableLocalInferenceEngine @Inject constructor(
    private val localManager: LocalInferenceManager,
    private val isolatedManager: IsolatedInferenceManager,
    userPreferences: UserPreferences
) : LocalInferenceEngine, InferenceEngineControl, ModelTreeUriConfigurable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handleOwners = ConcurrentHashMap<LocalModelHandle, LocalInferenceEngine>()

    @Volatile
    private var useIsolatedEngine: Boolean = BuildConfig.USE_ISOLATED_INFERENCE_ENGINE

    init {
        scope.launch {
            userPreferences.inferenceIsolationEnabled.collect { enabled ->
                useIsolatedEngine = enabled
            }
        }
    }

    override val isNativeAvailable: Boolean
        get() = activeEngine().isNativeAvailable

    override suspend fun loadModel(
        modelPath: String,
        config: LocalGenerationConfig
    ): LocalModelHandle? {
        val engine = activeEngine()
        val handle = engine.loadModel(modelPath, config)
        if (handle != null) {
            handleOwners[handle] = engine
        }
        return handle
    }

    override suspend fun unloadModel(model: LocalModelHandle) {
        val owner = handleOwners.remove(model) ?: activeEngine()
        owner.unloadModel(model)
    }

    override suspend fun isModelLoaded(modelPath: String): Boolean {
        return activeEngine().isModelLoaded(modelPath)
    }

    override suspend fun getAvailableModels(): List<String> {
        return activeEngine().getAvailableModels()
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

    private fun activeEngine(): LocalInferenceEngine {
        return if (useIsolatedEngine) isolatedManager else localManager
    }
}
