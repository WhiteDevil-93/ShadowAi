package com.shadowai.modelcatalog

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shadowai.core.Capability
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Discovers available AI models from local storage and JSON configurations.
 * Implementation for Phase 4: Capability-Based Selection.
 */
class ModelDiscovery(
    private val context: Context,
    private val gson: Gson
) {
    private val scanMutex = Mutex()
    // M-1 FIX: Removed AtomicLong increment - using hash-only ID generation for deterministic IDs

    suspend fun discoverFromAllSources(
        localModelDirs: List<String> = getDefaultLocalModelDirs(),
        jsonConfigFiles: List<String> = emptyList()
    ): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val allModels = mutableListOf<ModelDescriptor>()
            allModels.addAll(discoverFromLocalFilesystem(localModelDirs))
            allModels.addAll(discoverFromJsonConfigs(jsonConfigFiles))
            android.util.Log.d("ModelDiscovery", "Discovered ${allModels.size} models")
            val deduplicated = deduplicateModels(allModels)
            android.util.Log.d("ModelDiscovery", "After dedup: ${deduplicated.size} unique models")
            deduplicated
        }
    }

    suspend fun rescan(): List<ModelDescriptor> = discoverFromAllSources(
        localModelDirs = getDefaultLocalModelDirs(),
        jsonConfigFiles = emptyList()
    )

    private fun discoverFromLocalFilesystem(dirs: List<String>): List<ModelDescriptor> {
        val models = mutableListOf<ModelDescriptor>()
        dirs.forEach { dirPath ->
            try {
                val dir = File(dirPath)
                if (dir.exists() && dir.isDirectory) {
                    if (!dir.canRead()) {
                        android.util.Log.w("ModelDiscovery", "Cannot read: $dirPath")
                        return@forEach
                    }
                    dir.listFiles()?.forEach { file ->
                        when {
                            file.name == "model.json" -> {
                                parseLocalModelConfig(file)?.let { models.add(it) }
                            }
                            file.name.endsWith(".safetensors", ignoreCase = true) -> {
                                try {
                                    models.add(createModelDescriptorFromFile(file))
                                } catch (e: Exception) {
                                    android.util.Log.e("ModelDiscovery", "Failed: ${file.name}", e)
                                }
                            }
                            file.name.endsWith(".gguf", ignoreCase = true) -> {
                                try {
                                    models.add(createModelDescriptorFromFile(file))
                                } catch (e: Exception) {
                                    android.util.Log.e("ModelDiscovery", "Failed: ${file.name}", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("ModelDiscovery", "SecurityException: $dirPath: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.e("ModelDiscovery", "Error scanning $dirPath", e)
            }
        }
        return models
    }

    private fun parseLocalModelConfig(file: File): ModelDescriptor? {
        return try {
            val json = file.readText()
            val config = gson.fromJson(json, LocalModelConfig::class.java)
            ModelDescriptor(
                id = config.id,
                name = config.name,
                providerId = ProviderId.LOCAL_TEXT,
                capabilities = config.capabilities.map { Capability.valueOf(it) }.toSet(),
                supportedTransforms = config.supportedTransforms.mapNotNull { name ->
                    // M-5 FIX: Explicit when statement replacing reflection
                    when (name) {
                        "TextToText" -> Transform.TextToText()
                        "TextToImage" -> Transform.TextToImage()
                        "ImageToText" -> Transform.ImageToText()
                        "ImageToImage" -> Transform.ImageToImage()
                        "AudioToText" -> Transform.AudioToText()
                        "TextToAudio" -> Transform.TextToAudio()
                        "TextToVideo" -> Transform.TextToVideo()
                        "VideoToText" -> Transform.VideoToText()
                        "TextToEmbeddings" -> Transform.TextToEmbeddings()
                        "EmbeddingsToText" -> Transform.EmbeddingsToText()
                        else -> null
                    }
                }.toSet(),
                metadata = mapOf(
                    "modelPath" to (file.parentFile?.absolutePath ?: ""),
                    "quantization" to (config.quantization ?: "unknown")
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * M-1 FIX: Generate deterministic model ID using hash only.
     * Removed increment counter to ensure consistent IDs across rescans.
     */
    private fun generateModelId(path: String, name: String): String {
        val pathHash = path.hashCode().toLong()
        val nameHash = name.hashCode().toLong()
        // Combine hashes deterministically - no increment for consistent IDs
        val combinedHash = (pathHash * 31L) + nameHash
        return "model_${combinedHash.toULong().toString(16)}"
    }

    private fun createModelDescriptorFromFile(file: File): ModelDescriptor {
        val modelName = file.nameWithoutExtension
        val ext = file.extension.lowercase()

        val (providerId, capabilities) = when (ext) {
            "gguf" -> {
                ProviderId.LOCAL_TEXT to setOf(
                    Capability.TEXT,
                    Capability.LOCAL_INFERENCE,
                    Capability.COST_EFFICIENT,
                    Capability.TEXT_GEN_FAST
                )
            }
            "safetensors" -> {
                ProviderId.LOCAL_IMAGE to setOf(
                    Capability.IMAGE_GEN,
                    Capability.LOCAL_INFERENCE,
                    Capability.IMAGE_GEN_FAST
                )
            }
            else -> ProviderId.LOCAL_TEXT to setOf(Capability.TEXT)
        }

        val generatedId = generateModelId(file.absolutePath, modelName)

        return ModelDescriptor(
            id = generatedId,
            name = "Local $modelName",
            providerId = providerId,
            capabilities = capabilities,
            metadata = mapOf(
                "modelPath" to file.absolutePath,
                "modelSize" to file.length(),
                "extension" to ext
            )
        )
    }

    private fun discoverFromJsonConfigs(configFiles: List<String>): List<ModelDescriptor> {
        val models = mutableListOf<ModelDescriptor>()
        configFiles.forEach { configPath ->
            val file = File(configPath)
            if (file.exists()) {
                try {
                    val json = file.readText()
                    val type = object : TypeToken<List<ModelConfig>>() {}.type
                    val configs: List<ModelConfig> = gson.fromJson(json, type)
                    configs.forEach { cfg ->
                        models.add(cfg.toModelDescriptor())
                    }
                } catch (e: Exception) {
                    // Continue
                }
            }
        }
        return models
    }

    private fun deduplicateModels(models: List<ModelDescriptor>): List<ModelDescriptor> {
        val seenIds = mutableSetOf<String>()
        return models.filter { model ->
            if (model.id in seenIds) {
                false
            } else {
                seenIds.add(model.id)
                true
            }
        }
    }

    private fun getDefaultLocalModelDirs(): List<String> {
        return buildList {
            add("${context.filesDir}/models")
            context.getExternalFilesDir("models")?.let { add(it.absolutePath) }
        }
    }

    data class LocalModelConfig(
        val id: String,
        val name: String,
        val capabilities: List<String>,
        val supportedTransforms: List<String>,
        val quantization: String?
    )

    data class ModelConfig(
        val id: String,
        val name: String,
        val providerId: String,
        val capabilities: List<String>,
        val supportedTransforms: List<String>,
        val metadata: Map<String, Any>?
    ) {
        /**
         * M-5 FIX: Replace reflection-based discovery with explicit when statement.
         * This eliminates the use of Transform::class.sealedSubclasses reflection
         * and provides explicit, type-safe transform mapping.
         */
        fun toModelDescriptor(): ModelDescriptor {
            return ModelDescriptor(
                id = id,
                name = name,
                providerId = ProviderId.valueOf(providerId),
                capabilities = capabilities.map { Capability.valueOf(it) }.toSet(),
                supportedTransforms = supportedTransforms.mapNotNull { name ->
                    // Explicit when statement replacing reflection
                    when (name) {
                        "TextToText" -> Transform.TextToText()
                        "TextToImage" -> Transform.TextToImage()
                        "ImageToText" -> Transform.ImageToText()
                        "ImageToImage" -> Transform.ImageToImage()
                        "AudioToText" -> Transform.AudioToText()
                        "TextToAudio" -> Transform.TextToAudio()
                        "TextToVideo" -> Transform.TextToVideo()
                        "VideoToText" -> Transform.VideoToText()
                        "TextToEmbeddings" -> Transform.TextToEmbeddings()
                        "EmbeddingsToText" -> Transform.EmbeddingsToText()
                        else -> null
                    }
                }.toSet(),
                metadata = metadata ?: emptyMap()
            )
        }
    }
}
