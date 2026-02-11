package com.shadowai.app.admin.implementation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.SharedPreferences
import androidx.core.content.edit
import com.shadowai.app.BuildConfig
import com.shadowai.app.admin.AdminContract
import com.shadowai.app.admin.GenerationSettings
import com.shadowai.app.db.ChatMessageEntity
import android.util.Log
import com.shadowai.app.db.ShadowDatabase
import com.shadowai.app.db.toModel
import com.shadowai.app.functions.PixAiSettings
import com.shadowai.app.models.ModelId
import com.shadowai.core.ProviderId
import com.shadowai.app.routing.RoutingPolicy
import com.shadowai.app.security.SecureDataStore
import com.shadowai.app.tasks.Task
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

typealias PreferenceEditorAction = SharedPreferences.Editor.() -> Unit

@Singleton
class AdminRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val secureDataStore: SecureDataStore
) : AdminContract {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val executionHistory = CopyOnWriteArrayList<Task>()
    private val database = ShadowDatabase.getDatabase(appContext)
    private val messageDao = database.messageDao()
    private val memoryDao = database.memoryDao()
    
    private val _routingPolicyFlow = MutableStateFlow(loadRoutingPolicy())
    val routingPolicyFlow: StateFlow<RoutingPolicy> get() = _routingPolicyFlow.asStateFlow()

    private val _cloudProviderFlow = MutableStateFlow(loadCloudProvider())
    val cloudProviderFlow: StateFlow<String> get() = _cloudProviderFlow.asStateFlow()
    
    private val _voiceEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ENABLED, false))
    val voiceEnabledFlow: StateFlow<Boolean> get() = _voiceEnabledFlow.asStateFlow()
    
    private val _memoryEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_MEMORY_ENABLED, true))
    val memoryEnabledFlow: StateFlow<Boolean> get() = _memoryEnabledFlow.asStateFlow()
    
    private val _activeProviderFlow = MutableStateFlow(loadActiveProvider())
    val activeProviderFlow: StateFlow<ProviderId?> get() = _activeProviderFlow.asStateFlow()

    private val disabledModels = Collections.newSetFromMap(ConcurrentHashMap<ModelId, Boolean>())
        .apply { addAll(loadDisabledModels()) }
    private val disabledModelsMutex = Mutex()

    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val routingShard = PreferenceShard(
        name = "routing",
        mutex = Mutex(),
        batcher = PreferenceBatcher(prefs, preferenceScope, windowMs = 250L, domain = "routing")
    )
    private val toggleShard = PreferenceShard(
        name = "toggles",
        mutex = Mutex(),
        batcher = PreferenceBatcher(prefs, preferenceScope, windowMs = 200L, domain = "toggles")
    )
    private val modelShard = PreferenceShard(
        name = "model",
        mutex = Mutex(),
        batcher = PreferenceBatcher(prefs, preferenceScope, windowMs = 300L, domain = "model")
    )
    private val settingsShard = PreferenceShard(
        name = "generation_settings",
        mutex = Mutex(),
        batcher = PreferenceBatcher(prefs, preferenceScope, windowMs = 400L, domain = "generation_settings")
    )
    private val pixAiShard = PreferenceShard(
        name = "pixai",
        mutex = Mutex(),
        batcher = PreferenceBatcher(prefs, preferenceScope, windowMs = 250L, domain = "pixai")
    )
    private val trustShard = PreferenceShard(
        name = "trust",
        mutex = Mutex(),
        batcher = PreferenceBatcher(prefs, preferenceScope, windowMs = 150L, domain = "trust")
    )

    // Caching for heavy objects
    private var _cachedGenerationSettings: GenerationSettings? = null
    private var _cachedPixAiSettings: PixAiSettings? = null
    private val settingsMutex = Mutex()

    // In-memory cache for secure strings to avoid ESP overhead on UI thread
    private var _cachedCloudApiKey: String? = null
    private var _cachedActiveProviderApiKey: String? = null
    private var _secureCacheInitialized = false
    private val secureCacheMutex = Mutex()

    override suspend fun enableModel(modelId: ModelId) = withContext(Dispatchers.IO) {
        disabledModelsMutex.withLock {
            if (disabledModels.remove(modelId)) {
                persistDisabledModels()
            }
        }
    }

    override suspend fun disableModel(modelId: ModelId) = withContext(Dispatchers.IO) {
        disabledModelsMutex.withLock {
            if (disabledModels.add(modelId)) {
                persistDisabledModels()
            }
        }
    }

    override suspend fun setRoutingPolicy(policy: RoutingPolicy) = withContext(Dispatchers.IO) {
        routingShard.mutex.withLock {
            _routingPolicyFlow.value = policy
            routingShard.batcher.schedule {
                putString(KEY_ROUTING_POLICY, policy.name)
            }
        }
    }

    override fun getExecutionHistory(): List<Task> {
        return executionHistory.toList()
    }

    override fun isModelEnabled(modelId: ModelId): Boolean {
        return !disabledModels.contains(modelId)
    }

    override suspend fun setCloudApiKey(key: String) = withContext(Dispatchers.IO) {
        secureCacheMutex.withLock {
            _cachedCloudApiKey = key
            secureDataStore.putString("admin_$KEY_CLOUD_API_KEY", key)
        }
    }

    override suspend fun getCloudApiKey(): String? {
        if (!_secureCacheInitialized) {
            initializeSecureCache()
        }
        return _cachedCloudApiKey
    }

    override suspend fun setCustomBaseUrl(url: String) = withContext(Dispatchers.IO) {
        modelShard.mutex.withLock {
            modelShard.batcher.schedule {
                putString(KEY_CUSTOM_BASE_URL, url)
            }
        }
    }

    override fun getCustomBaseUrl(): String? {
        return prefs.getString(KEY_CUSTOM_BASE_URL, null)
    }

    override suspend fun setCloudProvider(provider: String) = withContext(Dispatchers.IO) {
        routingShard.mutex.withLock {
            _cloudProviderFlow.value = provider
            routingShard.batcher.schedule {
                putString(KEY_CLOUD_PROVIDER, provider)
            }
        }
    }

    override suspend fun getCloudProvider(): String {
        return _cloudProviderFlow.value
    }

    override suspend fun isVoiceEnabled(): Boolean {
        return _voiceEnabledFlow.value
    }

    override suspend fun setVoiceEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        toggleShard.mutex.withLock {
            _voiceEnabledFlow.value = enabled
            toggleShard.batcher.schedule {
                putBoolean(KEY_VOICE_ENABLED, enabled)
            }
        }
    }

    override suspend fun isMemoryEnabled(): Boolean {
        return isMemoryEnabledSync()
    }

    /** Synchronous version for non-suspend callers */
    fun isMemoryEnabledSync(): Boolean {
        return _memoryEnabledFlow.value
    }

    override suspend fun setMemoryEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        toggleShard.mutex.withLock {
            _memoryEnabledFlow.value = enabled
            toggleShard.batcher.schedule {
                putBoolean(KEY_MEMORY_ENABLED, enabled)
            }
        }
    }

    override suspend fun setModelName(model: String) = withContext(Dispatchers.IO) {
        modelShard.mutex.withLock {
            modelShard.batcher.schedule {
                putString(KEY_MODEL_NAME, model)
            }
        }
    }

    override suspend fun getModelName(): String {
        return getModelNameSync()
    }

    /** Synchronous version for non-suspend callers */
    fun getModelNameSync(): String {
        return prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME) ?: DEFAULT_MODEL_NAME
    }

    override suspend fun getGenerationSettings(): GenerationSettings {
        _cachedGenerationSettings?.let { return it }
        val settings = GenerationSettings(
            temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE).toDouble(),
            topP = prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P).toDouble(),
            topK = prefs.getInt(KEY_TOP_K, DEFAULT_TOP_K),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS),
            contentFilterEnabled = prefs.getBoolean(KEY_CONTENT_FILTER_ENABLED, false),
            repetitionPenalty = prefs.getFloat(KEY_REPETITION_PENALTY, DEFAULT_REPETITION_PENALTY).toDouble(),
            presencePenalty = prefs.getFloat(KEY_PRESENCE_PENALTY, DEFAULT_PRESENCE_PENALTY).toDouble(),
            mirostatEnabled = prefs.getBoolean(KEY_MIROSTAT_ENABLED, false),
            mirostatTau = prefs.getFloat(KEY_MIROSTAT_TAU, DEFAULT_MIROSTAT_TAU).toDouble(),
            mirostatEta = prefs.getFloat(KEY_MIROSTAT_ETA, DEFAULT_MIROSTAT_ETA).toDouble(),
            filterHate = prefs.getBoolean(KEY_FILTER_HATE, false),
            filterViolence = prefs.getBoolean(KEY_FILTER_VIOLENCE, false),
            filterAdult = prefs.getBoolean(KEY_FILTER_ADULT, false),
            filterSelfHarm = prefs.getBoolean(KEY_FILTER_SELF_HARM, false),
            blocklist = readStringList(KEY_BLOCKLIST),
            allowlist = readStringList(KEY_ALLOWLIST)
        )
        _cachedGenerationSettings = settings
        return settings
    }

    override suspend fun saveGenerationSettings(settings: GenerationSettings) = withContext(Dispatchers.IO) {
        settingsMutex.withLock {
            _cachedGenerationSettings = settings
            settingsShard.batcher.schedule {
                putFloat(KEY_TEMPERATURE, settings.temperature.toFloat())
                putFloat(KEY_TOP_P, settings.topP.toFloat())
                putInt(KEY_TOP_K, settings.topK)
                putInt(KEY_MAX_TOKENS, settings.maxTokens)
                putBoolean(KEY_CONTENT_FILTER_ENABLED, settings.contentFilterEnabled)
                putFloat(KEY_REPETITION_PENALTY, settings.repetitionPenalty.toFloat())
                putFloat(KEY_PRESENCE_PENALTY, settings.presencePenalty.toFloat())
                putBoolean(KEY_MIROSTAT_ENABLED, settings.mirostatEnabled)
                putFloat(KEY_MIROSTAT_TAU, settings.mirostatTau.toFloat())
                putFloat(KEY_MIROSTAT_ETA, settings.mirostatEta.toFloat())
                putBoolean(KEY_FILTER_HATE, settings.filterHate)
                putBoolean(KEY_FILTER_VIOLENCE, settings.filterViolence)
                putBoolean(KEY_FILTER_ADULT, settings.filterAdult)
                putBoolean(KEY_FILTER_SELF_HARM, settings.filterSelfHarm)
                putStringSet(KEY_BLOCKLIST, settings.blocklist.toSet())
                putStringSet(KEY_ALLOWLIST, settings.allowlist.toSet())
            }
        }
    }

    override suspend fun setActiveProvider(id: ProviderId) = withContext(Dispatchers.IO) {
        routingShard.mutex.withLock {
            _activeProviderFlow.value = id
            routingShard.batcher.schedule {
                putString(KEY_ACTIVE_PROVIDER, id.name)
            }
        }
    }

    override suspend fun setActiveProviderBaseUrl(url: String) = withContext(Dispatchers.IO) {
        routingShard.mutex.withLock {
            routingShard.batcher.schedule {
                putString(KEY_ACTIVE_PROVIDER_BASE_URL, url)
            }
        }
    }

    override suspend fun getActiveProviderBaseUrl(): String? {
        return prefs.getString(KEY_ACTIVE_PROVIDER_BASE_URL, null)
    }

    override suspend fun setActiveProviderApiKey(key: String?) = withContext(Dispatchers.IO) {
        secureCacheMutex.withLock {
            _cachedActiveProviderApiKey = key
            if (key == null) {
                secureDataStore.remove("admin_$KEY_ACTIVE_PROVIDER_API_KEY")
            } else {
                secureDataStore.putString("admin_$KEY_ACTIVE_PROVIDER_API_KEY", key)
            }
        }
    }

    override suspend fun getActiveProviderApiKey(): String? {
        if (!_secureCacheInitialized) {
            initializeSecureCache()
        }
        return _cachedActiveProviderApiKey
    }

    private suspend fun initializeSecureCache() {
        secureCacheMutex.withLock {
            if (_secureCacheInitialized) return
            _cachedCloudApiKey = secureDataStore.getString("admin_$KEY_CLOUD_API_KEY")
            _cachedActiveProviderApiKey = secureDataStore.getString("admin_$KEY_ACTIVE_PROVIDER_API_KEY")
            _secureCacheInitialized = true
        }
    }

    override suspend fun recordTask(task: Task) {
        if (executionHistory.size >= 100) {
            executionHistory.removeAt(0)
        }
        executionHistory.add(task)
    }

    override suspend fun isBackendTrusted(): Boolean {
        return isBackendTrustedSync()
    }

    /** Synchronous version for non-suspend callers like RoutingEngine */
    fun isBackendTrustedSync(): Boolean {
        return prefs.getBoolean(KEY_BACKEND_TRUSTED, true)
    }

    override suspend fun updateTrustScore(delta: Float) = withContext(Dispatchers.IO) {
        val currentScore = prefs.getFloat(KEY_TRUST_SCORE, 1.0f)
        val newScore = (currentScore + delta).coerceIn(0f, 1f)
        trustShard.mutex.withLock {
            // Use apply() for async write - runs on Dispatcher.IO, no UI blocking concerns
            trustShard.batcher.flushNow(commit = false) {
                putFloat(KEY_TRUST_SCORE, newScore)
            }
        }
    }
    
    override suspend fun getAllMessages(): List<com.shadowai.app.ui.ChatMessage> = withContext(Dispatchers.IO) {
        return@withContext messageDao.getAllMessages().map { it.toModel() }
    }

    override suspend fun deleteMessagesFrom(timestamp: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesFrom(timestamp)
    }

    override suspend fun saveMemory(key: String, value: String, layer: String, confidence: Float) = withContext(Dispatchers.IO) {
        memoryDao.saveMemory(
            com.shadowai.app.db.MemoryEntity(
                key = key,
                value = value,
                layer = layer,
                confidence = confidence.coerceIn(0f, 1f)
            )
        )
    }

    override suspend fun getMemoryByLayer(layer: String): List<com.shadowai.app.db.MemoryEntity> = withContext(Dispatchers.IO) {
        return@withContext memoryDao.getMemoryByLayer(layer)
    }

    override suspend fun deleteLowConfidenceMemory(threshold: Float) = withContext(Dispatchers.IO) {
        memoryDao.deleteLowConfidence(threshold)
    }

    override suspend fun getSdModelPath(): String {
        return prefs.getString(KEY_SD_MODEL_PATH, "") ?: ""
    }

    override suspend fun setSdModelPath(path: String) = withContext(Dispatchers.IO) {
        modelShard.mutex.withLock {
            modelShard.batcher.schedule {
                putString(KEY_SD_MODEL_PATH, path)
            }
        }
    }

    override suspend fun getPreferredLlamaModel(): String? {
        return prefs.getString(KEY_PREFERRED_LLAMA_MODEL, null)
    }

    override suspend fun setPreferredLlamaModel(modelName: String) = withContext(Dispatchers.IO) {
        modelShard.mutex.withLock {
            modelShard.batcher.schedule {
                putString(KEY_PREFERRED_LLAMA_MODEL, modelName)
            }
        }
    }

    override suspend fun getLocalTextModelPath(): String {
        return prefs.getString(KEY_LOCAL_TEXT_MODEL_PATH, "") ?: ""
    }

    fun getLocalTextModelPathSync(): String {
        return prefs.getString(KEY_LOCAL_TEXT_MODEL_PATH, "") ?: ""
    }

    override suspend fun setLocalTextModelPath(path: String) = withContext(Dispatchers.IO) {
        modelShard.mutex.withLock {
            modelShard.batcher.schedule {
                putString(KEY_LOCAL_TEXT_MODEL_PATH, path)
            }
        }
    }

    override suspend fun getPixAiSettings(): PixAiSettings {
        _cachedPixAiSettings?.let { return it }
        val settings = PixAiSettings(
            prompt = prefs.getString(KEY_PIXAI_PROMPT, "") ?: "",
            negativePrompt = prefs.getString(KEY_PIXAI_NEGATIVE_PROMPT, "") ?: "",
            modelId = prefs.getString(KEY_PIXAI_MODEL_ID, "") ?: "",
            loraIds = prefs.getString(KEY_PIXAI_LORA_IDS, "") ?: "",
            vaeModelId = prefs.getString(KEY_PIXAI_VAE_MODEL_ID, "") ?: "",
            samplingMethod = prefs.getString(KEY_PIXAI_SAMPLING_METHOD, DEFAULT_PIXAI_SAMPLING_METHOD)
                ?: DEFAULT_PIXAI_SAMPLING_METHOD,
            samplingSteps = prefs.getInt(KEY_PIXAI_SAMPLING_STEPS, DEFAULT_PIXAI_SAMPLING_STEPS),
            cfgScale = prefs.getFloat(KEY_PIXAI_CFG_SCALE, DEFAULT_PIXAI_CFG_SCALE).toDouble(),
            seed = prefs.getLong(KEY_PIXAI_SEED, DEFAULT_PIXAI_SEED).takeIf { it != DEFAULT_PIXAI_SEED },
            width = prefs.getInt(KEY_PIXAI_WIDTH, DEFAULT_PIXAI_WIDTH),
            height = prefs.getInt(KEY_PIXAI_HEIGHT, DEFAULT_PIXAI_HEIGHT),
            batchSize = prefs.getInt(KEY_PIXAI_BATCH_SIZE, DEFAULT_PIXAI_BATCH_SIZE)
        )
        _cachedPixAiSettings = settings
        return settings
    }

    override suspend fun savePixAiSettings(settings: PixAiSettings) = withContext(Dispatchers.IO) {
        settingsMutex.withLock {
            _cachedPixAiSettings = settings
            pixAiShard.mutex.withLock {
                pixAiShard.batcher.schedule {
                    putString(KEY_PIXAI_PROMPT, settings.prompt)
                    putString(KEY_PIXAI_NEGATIVE_PROMPT, settings.negativePrompt)
                    putString(KEY_PIXAI_MODEL_ID, settings.modelId)
                    putString(KEY_PIXAI_LORA_IDS, settings.loraIds)
                    putString(KEY_PIXAI_VAE_MODEL_ID, settings.vaeModelId)
                    putString(KEY_PIXAI_SAMPLING_METHOD, settings.samplingMethod)
                    putInt(KEY_PIXAI_SAMPLING_STEPS, settings.samplingSteps)
                    putFloat(KEY_PIXAI_CFG_SCALE, settings.cfgScale.toFloat())
                    putLong(KEY_PIXAI_SEED, settings.seed ?: DEFAULT_PIXAI_SEED)
                    putInt(KEY_PIXAI_WIDTH, settings.width)
                    putInt(KEY_PIXAI_HEIGHT, settings.height)
                    putInt(KEY_PIXAI_BATCH_SIZE, settings.batchSize)
                }
            }
        }
    }

    private fun loadCloudProvider(): String {
        return prefs.getString(KEY_CLOUD_PROVIDER, DEFAULT_CLOUD_PROVIDER) ?: DEFAULT_CLOUD_PROVIDER
    }

    private fun loadActiveProvider(): ProviderId? {
        val stored = prefs.getString(KEY_ACTIVE_PROVIDER, null) ?: return null
        return try {
            ProviderId.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun getActiveProvider(): ProviderId? {
        return getActiveProviderSync()
    }

    /** Synchronous version for non-suspend callers */
    fun getActiveProviderSync(): ProviderId? {
        return _activeProviderFlow.value
    }

    private fun loadDisabledModels(): Set<ModelId> {
        return prefs.getStringSet(KEY_DISABLED_MODELS, emptySet())
            ?.mapNotNull { raw -> raw.takeIf { it.isNotBlank() }?.let(::ModelId) }
            ?.toSet()
            ?: emptySet()
    }

    private fun persistDisabledModels() {
        modelShard.batcher.schedule {
            putStringSet(KEY_DISABLED_MODELS, disabledModels.map { it.id }.toSet())
        }
    }

    private fun loadRoutingPolicy(): RoutingPolicy {
        val storedName = prefs.getString(KEY_ROUTING_POLICY, DEFAULT_ROUTING_POLICY.name)
        return try {
            RoutingPolicy.valueOf(storedName ?: DEFAULT_ROUTING_POLICY.name)
        } catch (e: IllegalArgumentException) {
            DEFAULT_ROUTING_POLICY
        }
    }

    private fun readStringList(key: String): List<String> {
        return prefs.getStringSet(key, emptySet())?.toList() ?: emptyList()
    }

    private data class PreferenceShard(
        val name: String,
        val mutex: Mutex,
        val batcher: PreferenceBatcher
    )

    private inner class PreferenceBatcher(
        private val sharedPrefs: SharedPreferences,
        private val scope: CoroutineScope,
        private val windowMs: Long,
        private val domain: String
    ) {
        private val lock = Any()
        private val pending = mutableListOf<PreferenceEditorAction>()
        private var flushJob: Job? = null
        private val metrics = PreferenceBatchMetrics(domain)

        fun schedule(edit: PreferenceEditorAction) {
            metrics.registerPending()
            val shouldSchedule = synchronized(lock) {
                pending.add(edit)
                if (flushJob == null) {
                    flushJob = scope.launch {
                        delay(windowMs)
                        flushPending()
                    }
                    true
                } else {
                    false
                }
            }

            if (!shouldSchedule) {
                // Another flush job is already scheduled.
            }
        }

        fun flushNow(commit: Boolean = false, edit: PreferenceEditorAction) {
            val snapshot = drainPending()
            // Use commit = true ONLY when synchronous confirmation is absolutely required (e.g., before app exit)
            // Default is apply() for all async operations to avoid blocking UI thread
            applyEdits(snapshot, commit, accountPending = true)
            applyEdits(listOf(edit), commit, accountPending = false)
        }

        private fun flushPending() {
            val snapshot = drainPending()
            applyEdits(snapshot, commit = false, accountPending = true)
        }

        private fun drainPending(): List<PreferenceEditorAction> {
            return synchronized(lock) {
                val snapshot = pending.toList()
                pending.clear()
                flushJob = null
                snapshot
            }
        }

        private fun applyEdits(
            actions: List<PreferenceEditorAction>,
            commit: Boolean,
            accountPending: Boolean
        ) {
            if (actions.isEmpty()) return
            val editor = sharedPrefs.edit()
            actions.forEach { it(editor) }
            val start = System.nanoTime()
            // commit() = synchronous, blocks caller - ONLY use if immediate persistence required
            // apply() = asynchronous, preferred default - doesn't block UI thread
            if (commit) {
                editor.commit()
            } else {
                editor.apply()
            }
            val duration = System.nanoTime() - start
            metrics.recordFlush(actions.size, duration, accountPending)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "[$domain] Flushed ${actions.size} edits in ${duration / 1_000_000}ms (pending=${metrics.pendingOperations.get()})"
                )
            }
        }
    }

    private class PreferenceBatchMetrics(private val domain: String) {
        val pendingOperations = AtomicInteger()
        private val flushCount = AtomicInteger()
        private val totalFlushedOps = AtomicInteger()
        private val lastFlushDurationNs = AtomicLong()
        private val lastFlushTimestampMs = AtomicLong()

        fun registerPending() {
            pendingOperations.incrementAndGet()
        }

        fun recordFlush(actionCount: Int, durationNs: Long, accountPending: Boolean) {
            if (actionCount <= 0) return
            flushCount.incrementAndGet()
            totalFlushedOps.addAndGet(actionCount)
            lastFlushDurationNs.set(durationNs)
            lastFlushTimestampMs.set(System.currentTimeMillis())
            if (accountPending) {
                pendingOperations.addAndGet(-actionCount)
                if (pendingOperations.get() < 0) {
                    pendingOperations.set(0)
                }
            }
        }

        fun snapshot(): PreferenceBatchMetricsSnapshot {
            val flushes = flushCount.get()
            val averageDurationMs = if (flushes == 0) 0.0 else lastFlushDurationNs.get() / 1_000_000.0
            return PreferenceBatchMetricsSnapshot(
                domain = domain,
                pendingOperations = pendingOperations.get(),
                flushCount = flushes,
                averageFlushDurationMs = averageDurationMs,
                totalFlushedOperations = totalFlushedOps.get(),
                lastFlushTimestampMs = lastFlushTimestampMs.get()
            )
        }
    }

    private data class PreferenceBatchMetricsSnapshot(
        val domain: String,
        val pendingOperations: Int,
        val flushCount: Int,
        val averageFlushDurationMs: Double,
        val totalFlushedOperations: Int,
        val lastFlushTimestampMs: Long
    )

    /**
     * Cleans up resources when the repository is no longer needed.
     * Should be called during application shutdown or when replacing the singleton.
     */
    fun shutdown() {
        preferenceScope.cancel()
        Log.d(TAG, "AdminRepository shut down, preferenceScope cancelled")
    }

    private companion object {
        private const val TAG = "AdminRepository"
        const val PREFS_NAME = "admin_prefs"
        const val SECURE_PREFS_NAME = "admin_prefs_secure"

        const val KEY_ROUTING_POLICY = "routing_policy"
        const val KEY_CLOUD_API_KEY = "cloud_api_key"
        const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        const val KEY_CLOUD_PROVIDER = "cloud_provider"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_DISABLED_MODELS = "disabled_models"

        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_P = "top_p"
        const val KEY_TOP_K = "top_k"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_CONTENT_FILTER_ENABLED = "content_filter_enabled"
        const val KEY_REPETITION_PENALTY = "repetition_penalty"
        const val KEY_PRESENCE_PENALTY = "presence_penalty"
        const val KEY_MIROSTAT_ENABLED = "mirostat_enabled"
        const val KEY_MIROSTAT_TAU = "mirostat_tau"
        const val KEY_MIROSTAT_ETA = "mirostat_eta"
        const val KEY_FILTER_HATE = "filter_hate"
        const val KEY_FILTER_VIOLENCE = "filter_violence"
        const val KEY_FILTER_ADULT = "filter_adult"
        const val KEY_FILTER_SELF_HARM = "filter_self_harm"
        const val KEY_BLOCKLIST = "blocklist"
        const val KEY_ALLOWLIST = "allowlist"

        const val KEY_ACTIVE_PROVIDER = "active_provider"
        const val KEY_ACTIVE_PROVIDER_BASE_URL = "active_provider_base_url"
        const val KEY_ACTIVE_PROVIDER_API_KEY = "active_provider_api_key"

        const val KEY_BACKEND_TRUSTED = "backend_trusted"
        const val KEY_TRUST_SCORE = "trust_score"

        const val KEY_LOCAL_TEXT_MODEL_PATH = "local_text_model_path"
        const val KEY_SD_MODEL_PATH = "sd_model_path"
        const val KEY_PREFERRED_LLAMA_MODEL = "preferred_llama_model"

        const val KEY_PIXAI_PROMPT = "pixai_prompt"
        const val KEY_PIXAI_NEGATIVE_PROMPT = "pixai_negative_prompt"
        const val KEY_PIXAI_MODEL_ID = "pixai_model_id"
        const val KEY_PIXAI_LORA_IDS = "pixai_lora_ids"
        const val KEY_PIXAI_VAE_MODEL_ID = "pixai_vae_model_id"
        const val KEY_PIXAI_SAMPLING_METHOD = "pixai_sampling_method"
        const val KEY_PIXAI_SAMPLING_STEPS = "pixai_sampling_steps"
        const val KEY_PIXAI_CFG_SCALE = "pixai_cfg_scale"
        const val KEY_PIXAI_SEED = "pixai_seed"
        const val KEY_PIXAI_WIDTH = "pixai_width"
        const val KEY_PIXAI_HEIGHT = "pixai_height"
        const val KEY_PIXAI_BATCH_SIZE = "pixai_batch_size"

        const val DEFAULT_CLOUD_PROVIDER = "OPENROUTER"
        const val DEFAULT_MODEL_NAME = "gpt-4"

        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_REPETITION_PENALTY = 1.0f
        const val DEFAULT_PRESENCE_PENALTY = 0.0f
        const val DEFAULT_MIROSTAT_TAU = 5.0f
        const val DEFAULT_MIROSTAT_ETA = 0.1f

        const val DEFAULT_PIXAI_SAMPLING_METHOD = "Euler a"
        const val DEFAULT_PIXAI_SAMPLING_STEPS = 28
        const val DEFAULT_PIXAI_CFG_SCALE = 7.5f
        const val DEFAULT_PIXAI_SEED = -1L
        const val DEFAULT_PIXAI_WIDTH = 512
        const val DEFAULT_PIXAI_HEIGHT = 768
        const val DEFAULT_PIXAI_BATCH_SIZE = 1

        val DEFAULT_ROUTING_POLICY = RoutingPolicy.AUTO
    }
}
