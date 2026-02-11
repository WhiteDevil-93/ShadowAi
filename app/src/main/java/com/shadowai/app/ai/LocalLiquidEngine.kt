package com.shadowai.app.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local AI engine for LiquidAI on-device model inference.
 *
 * Supports GGUF models stored on device storage.
 * Preferred models are listed for optimal mobile performance, but any GGUF is allowed.
 * 
 * CRITICAL FIXES APPLIED:
 * 1. Deprecated External Storage API - Using scoped storage APIs
 * 2. Turkish Locale Bug - Using Locale.US for hex formatting (already fixed)
 * 3. Hardcoded Model Whitelist - Made LIQUID_ALLOWED_MODELS configurable
 */
@Singleton
class LocalLiquidEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llamaNative: LlamaNative,
    private val resourceMonitor: com.shadowai.app.device.implementation.AndroidResourceMonitor
) {
    companion object {
        private const val TAG = "LocalLiquidEngine"
        
        // CRITICAL FIX: Made model whitelist configurable via system property
        // Default preferred LiquidAI models for mobile performance (GGUF)
        // Q4_K_M is preferred for mobile - good balance of size/quality
        private var allowedModelsOverride: List<String>? = null
        
        @JvmStatic
        fun setAllowedModelsOverride(models: List<String>?) {
            allowedModelsOverride = models
        }
        
        private val LIQUID_ALLOWED_MODELS_DEFAULT = listOf(
            "LFM2.5-1.2B-Thinking-Q4_K_M.gguf",  // Q4 quantized - best mobile compatibility
            "LFM2.5-1.2B-Thinking-Q8_0.gguf",    // Q8 quantized - best quality
            "LFM2.5-1.2B-Instruct-BF16.gguf"     // BF16 - high precision (may fail on low-RAM devices)
        )
        
        private val LIQUID_ALLOWED_MODELS: List<String>
            get() = allowedModelsOverride ?: LIQUID_ALLOWED_MODELS_DEFAULT

        private fun getStandardDirectories(context: Context): List<String> {
            val paths = mutableListOf<String>()
            try {
                // CRITICAL FIX: Using scoped storage APIs instead of deprecated external storage
                paths.add(context.filesDir.absolutePath)
                context.getExternalFilesDir(null)?.let { paths.add(it.absolutePath) }
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { paths.add(it.absolutePath) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve standard directories: ${e.message}")
            }
            return paths.distinct().filterNotNull()
        }
    }

    private val initMutex = Mutex()
    private val stateLock = Any()

    data class ModelInfo(
        val path: String,
        val format: String,
        val size: Long
    )

    private var currentHandle: LlamaNative.ModelHandle? = null
    private var currentModelInfo: ModelInfo? = null
    private var customModelDir: File? = null

    /**
     * Set a custom directory to search for models.
     */
    fun setCustomModelDirectory(path: String?) {
        val nextDir = if (path.isNullOrBlank()) {
            null
        } else {
            val candidate = File(path).canonicalFile
            if (!isAllowedDirectory(candidate)) {
                throw IllegalArgumentException("Custom model directory is outside allowed app storage.")
            }
            candidate
        }
        synchronized(stateLock) {
            customModelDir = nextDir
        }
        Log.i(TAG, "Custom model directory set to: ${nextDir?.absolutePath ?: "none (using default)"}")
    }

    private val searchDirectories: List<String>
        get() {
            val paths = mutableListOf<String>()
            // 0. Custom path (HIGHEST PRIORITY)
            synchronized(stateLock) { customModelDir }?.let { dir ->
                if (isAllowedDirectory(dir)) {
                    paths.add(dir.absolutePath)
                } else {
                    Log.w(TAG, "Rejected custom model directory (unsafe path): ${dir.absolutePath}")
                }
            }
            // 1. App-private storage
            context.getExternalFilesDir("models")?.absolutePath?.let { paths.add(it) }
            // 2. Standard paths
            paths.addAll(getStandardDirectories(context))
            return paths.distinct()
        }

    private fun isAllowedDirectory(file: File): Boolean {
        return try {
            val canonical = file.canonicalFile
            // CRITICAL FIX: Using scoped storage APIs - only allow subdirectories of app-specific storage
            val allowedRoots = listOfNotNull(
                context.filesDir,
                context.getExternalFilesDir(null),
                context.cacheDir,
                context.externalCacheDir
            ).map { it.canonicalFile }
            
            allowedRoots.any { canonical.startsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Preferred model selection (mobile-safe order)
     * Q4_K_M is preferred for mobile - good balance of size/quality
     * 
     * CRITICAL FIX: Uses configurable model list instead of hardcoded whitelist
     */
    private fun selectLiquidModel(foundFiles: List<File>): File {
        val map = foundFiles.associateBy { it.name }
        
        // CRITICAL FIX: Using configurable LIQUID_ALLOWED_MODELS instead of hardcoded list
        for (preferred in LIQUID_ALLOWED_MODELS) {
            map[preferred]?.let {
                Log.i(TAG, "Selected model: ${it.name}")
                return it
            }
        }

        return foundFiles.maxByOrNull { it.length() }
            ?: error("No GGUF models found. Place a .gguf file in one of: $searchDirectories")
    }

    /**
     * Scan for GGUF models
     */
    private fun scanForLiquidModels(): List<File> {
        val foundFiles = mutableListOf<File>()

        for (dirPath in searchDirectories) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }?.let {
                    foundFiles.addAll(it)
                }
            }
        }

        Log.i(TAG, "Total GGUF models found: ${foundFiles.size}")
        return foundFiles
    }

    /**
     * Scan for allowed LiquidAI models and return metadata.
     */
    fun scanForModels(): List<ModelInfo> {
        return scanForLiquidModels()
            .map { file ->
                ModelInfo(
                    path = file.absolutePath,
                    format = file.extension.uppercase().ifBlank { "UNKNOWN" },
                    size = file.length()
                )
            }
            .sortedBy { it.path }
    }

    /**
     * Initialize with the best available LiquidAI model
     */
    suspend fun initialize(): Result<Unit> = initMutex.withLock {
        withContext(Dispatchers.IO) {
        if (!llamaNative.isLoaded()) {
            return@withContext Result.failure(IllegalStateException("llama_jni native library is unavailable"))
        }
        ensureActive()  // Check cancellation before starting
        val ggufFiles = scanForLiquidModels()

        if (ggufFiles.isEmpty()) {
            return@withContext Result.failure(
                Exception(
                    "No GGUF models found. This usually means either:\n" +
                    "1. No .gguf files are present in the search paths.\n" +
                    "2. Storage permission is not granted.\n\n" +
                    "Please place a .gguf file in your storage under:\n" +
                    searchDirectories.joinToString("\n") { "  - $it" }
                )
            )
        }

        val modelFile = selectLiquidModel(ggufFiles)

        ensureActive()  // Check cancellation before loading
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val requiredMem = MemoryConstants.estimateModelRam(modelFile.length())

        if (memInfo.availMem < requiredMem) {
            val error = "Insufficient RAM to load model. Available: ${memInfo.availMem / 1024 / 1024}MB, Required: ${requiredMem / 1024 / 1024}MB (${MemoryConstants.MODEL_MEMORY_MULTIPLIER}x model size)"
            Log.e(TAG, error)
            return@withContext Result.failure(Exception(error))
        }

        val preflightError = validateModelFile(modelFile)
        if (preflightError != null) {
            return@withContext Result.failure(Exception(preflightError))
        }

        Log.i(TAG, "=== Loading LiquidAI Model===")
        Log.i(TAG, "Path: ${modelFile.absolutePath}")

        // Load using the native library
        val handle = try {
            withContext(Dispatchers.Default) {
                llamaNative.loadModel(
                    modelPath = modelFile.absolutePath,
                    config = LlamaNative.GenerationConfig.FAST
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during model loading", e)
            return@withContext Result.failure(
                Exception("Failed to load model: ${e.message}", e)
            )
        }

        if (handle == null) {
            return@withContext Result.failure(
                Exception("Failed to load LiquidAI model: ${modelFile.name}")
            )
        }

        activityManager.getMemoryInfo(memInfo)
        if (memInfo.availMem < requiredMem / 2) {
            handle.free()
            return@withContext Result.failure(
                IllegalStateException("Insufficient RAM after model load. Available: ${memInfo.availMem / 1024 / 1024}MB")
            )
        }

        synchronized(stateLock) {
            currentHandle?.free()
            currentHandle = handle
            currentModelInfo = ModelInfo(
                path = modelFile.absolutePath,
                format = modelFile.extension.uppercase().ifBlank { "UNKNOWN" },
                size = modelFile.length()
            )
        }
        Log.i(TAG, "Successfully loaded: ${modelFile.name}")
            Result.success(Unit)
        }
    }

    /**
     * Initialize with a specific model path.
     */
    suspend fun initialize(modelPath: String): Result<Unit> = initMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!llamaNative.isLoaded()) {
                return@withContext Result.failure(IllegalStateException("llama_jni native library is unavailable"))
            }
            // Validate path traversal
            val modelFile = File(modelPath).canonicalFile
            val allowedRoots = listOfNotNull(
                context.filesDir,
                context.getExternalFilesDir(null),
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            )
            if (!allowedRoots.any { modelFile.startsWith(it) }) {
                return@withContext Result.failure(Exception("Path traversal detected: $modelPath"))
            }
            if (!modelFile.exists() || !modelFile.isFile) {
            return@withContext Result.failure(
                Exception("Model file not found or inaccessible: $modelPath\n" +
                          "Check if the path is correct and storage permission is granted.")
            )
        }
        val preflightError = validateModelFile(modelFile)
        if (preflightError != null) {
            return@withContext Result.failure(Exception(preflightError))
        }

        Log.i(TAG, "Loading LiquidAI model: ${modelFile.absolutePath}")

        val handle = withContext(Dispatchers.Default) {
            llamaNative.loadModel(
                modelPath = modelFile.absolutePath,
                config = LlamaNative.GenerationConfig.FAST
            )
        }

        if (handle == null) {
            return@withContext Result.failure(
                Exception("Failed to load LiquidAI model: ${modelFile.name}")
            )
        }

        synchronized(stateLock) {
            currentHandle?.free()
            currentHandle = handle
            currentModelInfo = ModelInfo(
                path = modelFile.absolutePath,
                format = modelFile.extension.uppercase().ifBlank { "UNKNOWN" },
                size = modelFile.length()
            )
        }
        Log.i(TAG, "Successfully loaded: ${modelFile.name}")
            Result.success(Unit)
        }
    }

    /**
     * Generate response using the loaded model
     */
    suspend fun generate(prompt: String, maxTokens: Int = 512): Result<String> = withContext(Dispatchers.Default) {
        val handle = synchronized(stateLock) { currentHandle }
            ?: return@withContext Result.failure(IllegalStateException("Model is not initialized"))

        ensureActive()

        try {
            // THERMAL-AWARE THREAD COUNT
            val nThreads = if (resourceMonitor.isThermalRestricted()) {
                Log.w(TAG, "Thermal throttling detected. Using reduced thread count (2).")
                2
            } else {
                Runtime.getRuntime().availableProcessors().coerceAtMost(4)
            }

            val response = llamaNative.generate(
                handle = handle,
                prompt = prompt,
                config = LlamaNative.GenerationConfig(
                    maxTokens = maxTokens,
                    nCtx = 2048,
                    nThreads = nThreads
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Check if model is currently loaded
     */
    fun isModelLoaded(): Boolean = synchronized(stateLock) { currentHandle != null }

    /**
     * Get info about the currently loaded model.
     */
    fun getModelInfo(): ModelInfo? = synchronized(stateLock) { currentModelInfo }

    /**
     * Unload the model and free native resources.
     */
    fun shutdown() = synchronized(stateLock) {
        currentHandle?.free()
        currentHandle = null
        currentModelInfo = null
    }

    /**
     * CRITICAL FIX: Turkish Locale Bug - Using Locale.US for hex formatting
     * GGUF magic check was failing in Turkish locale due to locale-sensitive string formatting
     */
    private fun validateModelFile(modelFile: File): String? {
        if (!modelFile.exists() || !modelFile.isFile) {
            return "Model file not found: ${modelFile.absolutePath}"
        }
        if (!modelFile.canRead()) {
            return "Model file is not readable: ${modelFile.absolutePath}"
        }
        val size = modelFile.length()
        if (size < MemoryConstants.MIN_MODEL_FILE_SIZE) {
            return "Model file is too small (${size} bytes). File may be incomplete."
        }
        val header = ByteArray(4)
        try {
            FileInputStream(modelFile).use { stream ->
                val read = stream.read(header)
                if (read < 4) return "Model file header is too short."
            }
        } catch (e: Exception) {
            return "Failed to read model file header: ${e.message}"
        }
        
        // CRITICAL FIX: Turkish Locale Bug - Using Locale.US for hex formatting
        // String.format uses locale-sensitive uppercase, which breaks GGUF magic check
        val magic = header.joinToString("") { String.format(Locale.US, "%02X", it) }
        if (magic != MemoryConstants.GGUF_MAGIC_HEX) return "Invalid GGUF header: $magic"

        return null
    }

    /**
     * Get current model status
     */
    fun getStatus(): Map<String, Any> = mapOf(
        "isLoaded" to isModelLoaded(),
        "preferredModels" to LIQUID_ALLOWED_MODELS,
        "searchPaths" to searchDirectories
    )
}
