package com.shadowai.hotswapping

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.shadowai.core.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Loads and saves provider config snapshots from disk or raw text.
 */
class ProviderConfigLoader(
    private val gson: Gson,
    private val yaml: Yaml = Yaml()
) {
    /**
     * Loads a snapshot from disk.
     */
    suspend fun load(file: File): Result<ProviderConfigSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val content = if (file.exists()) file.readText() else ""
            if (content.isBlank()) {
                ProviderConfigSnapshot(version = 1, providers = emptyList())
            } else {
                parse(content, detectFormat(file, content))
            }
        }
    }

    /**
     * Saves a snapshot to disk.
     */
    suspend fun save(file: File, snapshot: ProviderConfigSnapshot, format: ConfigFormat): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        parent.mkdirs()
                    }
                }
                file.writeText(serialize(snapshot, format))
            }
        }

    /**
     * Parses a snapshot from raw text.
     */
    fun parse(raw: String, format: ConfigFormat): ProviderConfigSnapshot {
        return when (format) {
            ConfigFormat.JSON -> parseJson(raw)
            ConfigFormat.YAML -> parseYaml(raw)
        }
    }

    /**
     * Serializes a snapshot to text.
     */
    fun serialize(snapshot: ProviderConfigSnapshot, format: ConfigFormat): String {
        return when (format) {
            ConfigFormat.JSON -> gson.toJson(snapshot)
            ConfigFormat.YAML -> yaml.dump(snapshot.toYamlMap())
        }
    }

    private fun parseJson(raw: String): ProviderConfigSnapshot {
        return try {
            if (raw.trimStart().startsWith("[")) {
                val listType = object : TypeToken<List<ProviderConfig>>() {}.type
                val providers: List<ProviderConfig> = gson.fromJson(raw, listType)
                ProviderConfigSnapshot(version = 1, providers = providers)
            } else {
                gson.fromJson(raw, ProviderConfigSnapshot::class.java)
            }
        } catch (e: JsonSyntaxException) {
            ProviderConfigSnapshot(version = 1, providers = emptyList())
        }
    }

    private fun parseYaml(raw: String): ProviderConfigSnapshot {
        val data = yaml.load<Any>(raw)
        return when (data) {
            is Map<*, *> -> mapToSnapshot(data)
            is List<*> -> ProviderConfigSnapshot(version = 1, providers = data.mapNotNull { mapToProvider(it) })
            else -> ProviderConfigSnapshot(version = 1, providers = emptyList())
        }
    }

    private fun mapToSnapshot(map: Map<*, *>): ProviderConfigSnapshot {
        val version = (map["version"] as? Number)?.toInt() ?: 1
        val updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val providersList = map["providers"] as? List<*> ?: emptyList<Any>()
        val providers = providersList.mapNotNull { mapToProvider(it) }
        return ProviderConfigSnapshot(version = version, providers = providers, updatedAt = updatedAt)
    }

    private fun mapToProvider(entry: Any?): ProviderConfig? {
        val map = entry as? Map<*, *> ?: return null
        val providerId = (map["providerId"] as? String)
            ?.let { toProviderId(it) }
            ?: ProviderId.UNKNOWN
        val name = map["name"] as? String ?: providerId.getDisplayName()
        val baseUrl = map["baseUrl"] as? String ?: ""
        val apiKeySecret = map["apiKeySecret"] as? String
            ?: map["apiKey"] as? String  // Fallback for backward compatibility
        val modelId = map["modelId"] as? String
        val isEnabled = map["isEnabled"] as? Boolean ?: true
        val isLocal = map["isLocal"] as? Boolean ?: providerId.isLocal()
        val capabilities = (map["capabilities"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        return ProviderConfig(
            providerId = providerId,
            name = name,
            baseUrl = baseUrl,
            apiKeySecret = apiKeySecret,
            modelId = modelId,
            isEnabled = isEnabled,
            isLocal = isLocal,
            capabilities = capabilities
        )
    }

    private fun detectFormat(file: File, raw: String): ConfigFormat {
        val extension = file.extension.lowercase()
        return when {
            extension == "yaml" || extension == "yml" -> ConfigFormat.YAML
            raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[") -> ConfigFormat.JSON
            else -> ConfigFormat.YAML
        }
    }

    private fun toProviderId(value: String): ProviderId {
        return runCatching { ProviderId.valueOf(value.uppercase()) }.getOrDefault(ProviderId.UNKNOWN)
    }

    private fun ProviderConfigSnapshot.toYamlMap(): Map<String, Any> {
        return mapOf(
            "version" to version,
            "updatedAt" to updatedAt,
            "providers" to providers.map { provider ->
                mapOf(
                    "providerId" to provider.providerId.name,
                    "name" to provider.name,
                    "baseUrl" to provider.baseUrl,
                    "apiKeySecret" to (provider.apiKeySecret ?: ""),
                    "modelId" to (provider.modelId ?: ""),
                    "isEnabled" to provider.isEnabled,
                    "isLocal" to provider.isLocal,
                    "capabilities" to provider.capabilities
                )
            }
        )
    }
}
