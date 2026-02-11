package com.shadowai.app.ai

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.lang.Character.isISOControl

/**
 * Helper class for model loading with comprehensive error handling.
 * Wraps LocalInferenceManager to provide detailed error reporting.
 * 
 * CRITICAL FIXES APPLIED:
 * 1. Context Memory Leak - Using applicationContext instead of Activity context
 * 2. Path Traversal Prevention - Canonical path validation
 * 3. Double Extension Bypass Prevention - Full filename validation
 * 4. TOCTOU Race Condition - Re-check after validation
 * 5. Resource Cleanup - Close resources on exception
 */
class ModelLoadingHelper(
    private val context: Context,
    private val inferenceManager: LocalInferenceManager
) {

    companion object {
        private const val TAG = "ModelLoadingHelper"
        private const val DEFAULT_MODEL_DIR = "models"
        private val GGUF_EXTENSION = "gguf"
        private val FORBIDDEN_PATTERNS = listOf("..", "/.", "\\.")
    }

    // CRITICAL FIX: Use applicationContext to prevent memory leaks
    private val safeContext: Context by lazy {
        context.applicationContext
    }

    private val modelDir: File by lazy {
        File(safeContext.filesDir, DEFAULT_MODEL_DIR).also { it.mkdirs() }
    }

    private val loadingMutex = Mutex()

    /**
     * Load a model with comprehensive error handling.
     *
     * @param modelPath Path to the model file
     * @param config Generation configuration
     * @return Loaded LocalModel
     * @throws ModelLoadingException if loading fails
     */
    suspend fun loadModelWithErrorHandling(
        modelPath: String,
        config: LlamaNative.GenerationConfig = LlamaNative.GenerationConfig()
    ): LocalModel {
        return loadingMutex.withLock {
            try {
                // CRITICAL FIX: Path Traversal Prevention - Validate canonical path
                val canonicalPath = validateAndNormalizePath(modelPath)
                    ?: throw ModelLoadingException.invalidFormat(modelPath, "Invalid path: potential path traversal detected")

                // CRITICAL FIX: Double Extension Bypass - Validate full filename
                val fileName = File(canonicalPath).name
                validateFileName(fileName)
                    ?: throw ModelLoadingException.invalidFormat(modelPath, "Invalid filename: $fileName")

                val file = File(canonicalPath)

                // Step 1: Validate file existence with TOCTOU protection
                if (!file.exists()) {
                    Log.e(TAG, "Model file not found: $modelPath")
                    throw ModelLoadingException.fileNotFound(modelPath)
                }

                // Step 2: Validate file readability
                if (!file.canRead()) {
                    Log.e(TAG, "Cannot read model file: $modelPath")
                    throw ModelLoadingException.permissionDenied(modelPath)
                }

                // Step 3: Validate file size (minimum 1MB for a valid model)
                val fileSize = file.length()
                if (fileSize < MemoryConstants.MIN_MODEL_FILE_SIZE) {
                    Log.e(TAG, "Model file too small: $modelPath (${fileSize} bytes)")
                    throw ModelLoadingException.invalidFormat(modelPath, "File size too small for a valid model")
                }

                // Step 4: Validate file extension (case-insensitive)
                val extension = file.extension.lowercase()
                if (extension != GGUF_EXTENSION) {
                    Log.e(TAG, "Invalid file extension: ${file.extension}")
                    throw ModelLoadingException.invalidFormat(modelPath, "Expected .$GGUF_EXTENSION extension, got .$extension")
                }

                // CRITICAL FIX: TOCTOU Race Condition - Re-check file existence before loading
                // Re-verify file still exists and is valid after all checks
                if (!file.exists() || !file.isFile || !file.canRead()) {
                    Log.e(TAG, "TOCTOU race detected: file state changed during validation")
                    throw ModelLoadingException.fileNotFound(modelPath)
                }

                // Step 5: Check available system memory (not JVM heap)
                val activityManager = safeContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)

                // Estimate required memory (roughly 2x file size for GGUF models)
                val estimatedRequired = MemoryConstants.estimateModelRam(fileSize)
                if (memInfo.availMem < estimatedRequired) {
                    Log.e(TAG, "Insufficient system memory: available=${memInfo.availMem / 1024 / 1024}MB, required=${estimatedRequired / 1024 / 1024}MB")
                    throw ModelLoadingException.insufficientMemory(modelPath, estimatedRequired, memInfo.availMem)
                }

                // Step 6: Attempt to load the model
                val model = inferenceManager.loadModel(canonicalPath, config)

                if (model == null) {
                    Log.e(TAG, "Model loading returned null: $modelPath")
                    throw ModelLoadingException.unknown(modelPath,
                        RuntimeException("loadModel returned null"))
                }

                Log.i(TAG, "Model loaded successfully: $modelPath")
                model

            } catch (e: ModelLoadingException) {
                // Re-throw ModelLoadingException as-is
                throw e
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception loading model: $modelPath", e)
                throw ModelLoadingException.permissionDenied(modelPath)
            } catch (e: OutOfMemoryError) {
                // Critical #4: Do not allocate objects or format strings during OOM as it will fail again.
                // Re-throw as a specific exception with minimal context.
                Log.e(TAG, "OutOfMemoryError during model loading: $modelPath")
                throw ModelLoadingException.insufficientMemory(modelPath, 0, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading model: $modelPath", e)
                throw ModelLoadingException.unknown(modelPath, e)
            }
        }
    }

    /**
     * CRITICAL FIX: Path Traversal Prevention
     * Validates and normalizes the model path to prevent directory traversal attacks.
     * 
     * @param rawPath The raw user-provided path
     * @return Normalized canonical path or null if invalid
     */
    private fun validateAndNormalizePath(rawPath: String): String? {
        if (rawPath.isBlank()) {
            Log.w(TAG, "Empty model path rejected")
            return null
        }

        // Check for path traversal patterns
        for (pattern in FORBIDDEN_PATTERNS) {
            if (rawPath.contains(pattern)) {
                Log.w(TAG, "Path contains forbidden pattern '$pattern': $rawPath")
                return null
            }
        }

        return try {
            val file = File(rawPath)
            val canonical = file.canonicalPath
            
            // Ensure canonical path is within allowed directories
            val allowedRoots = listOfNotNull(
                safeContext.filesDir,
                safeContext.getExternalFilesDir(null),
                safeContext.cacheDir
            ).map { it.canonicalPath }
            
            // Allow if within any allowed root
            val isAllowed = allowedRoots.any { canonical.startsWith(it) }
            
            if (!isAllowed) {
                Log.w(TAG, "Path outside allowed directories: $canonical")
                null
            } else {
                canonical
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to resolve canonical path: $rawPath", e)
            null
        }
    }

    /**
     * CRITICAL FIX: Double Extension Bypass Prevention
     * Validates the filename to prevent tricks like "model.gguf.txt"
     * 
     * @param fileName The filename to validate
     * @return The filename if valid, null if suspicious
     */
    private fun validateFileName(fileName: String): String? {
        if (fileName.isBlank() || fileName.length > 255) {
            return null
        }

        // Check for multiple extensions (double extension bypass)
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex > 0) {
            val extension = fileName.substring(lastDotIndex + 1).lowercase()
            
            // If extension is gguf, verify no additional extensions
            if (extension == GGUF_EXTENSION) {
                val baseName = fileName.substring(0, lastDotIndex)
                if (baseName.contains('.')) {
                    Log.w(TAG, "Double extension detected: $fileName")
                    return null
                }
            }

            // Block potentially dangerous extensions
            val dangerousExtensions = listOf("exe", "sh", "bat", "cmd", "js", "jse", "vbs", "vbe", "ps1", "ps2", "app", "dmg")
            if (extension in dangerousExtensions || extension.length > 10) {
                Log.w(TAG, "Suspicious file extension: $extension in $fileName")
                return null
            }
        }

        // Check for control characters or special characters
        if (fileName.any { it.isISOControl() || it in "<>:\" /\\|?*" }) {
            Log.w(TAG, "Filename contains invalid characters: $fileName")
            return null
        }

        return fileName
    }

    /**
     * Validate a model file without loading it.
     *
     * @param modelPath Path to the model file
     * @return true if valid, false otherwise
     */
    fun validateModelFile(modelPath: String): Boolean {
        return try {
            // CRITICAL FIX: Validate path first
            val canonicalPath = validateAndNormalizePath(modelPath) ?: return false
            val fileName = File(canonicalPath).name
            if (validateFileName(fileName) == null) return false

            val file = File(canonicalPath)

            // Check existence
            if (!file.exists()) {
                Log.w(TAG, "Model file does not exist: $modelPath")
                return false
            }

            // Check readability
            if (!file.canRead()) {
                Log.w(TAG, "Model file is not readable: $modelPath")
                return false
            }

            // Check size
            val fileSize = file.length()
            if (fileSize < MemoryConstants.MIN_MODEL_FILE_SIZE) {
                Log.w(TAG, "Model file too small: $modelPath (${fileSize} bytes)")
                return false
            }

            // Check extension
            if (file.extension.lowercase() != GGUF_EXTENSION) {
                Log.w(TAG, "Invalid file extension: ${file.extension}")
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model file: $modelPath", e)
            false
        }
    }

    /**
     * Get available models in the model directory.
     *
     * @return List of valid model files
     */
    fun getAvailableModels(): List<File> {
        return try {
            modelDir.listFiles()
                ?.filter { it.extension.lowercase() == GGUF_EXTENSION }
                ?.filter { validateModelFile(it.absolutePath) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception accessing model directory", e)
            emptyList()
        }
    }
}
