package com.shadowai.provideradapters

import android.net.Uri
import com.shadowai.core.Artifact
import com.shadowai.core.Modality
import com.shadowai.core.ModelDescriptor
import com.shadowai.core.ProviderExecutor
import com.shadowai.core.ProviderId
import com.shadowai.core.Transform
import com.shadowai.core.security.SecretBytes
import kotlinx.coroutines.flow.Flow

/**
 * Base interface for all provider adapters.
 * Extends ProviderExecutor with provider-specific configuration.
 */
interface ProviderAdapter : ProviderExecutor {
    /**
     * Configuration for the provider adapter.
     */
    val config: ProviderAdapterConfig

    /**
     * Initializes the adapter with the given configuration.
     */
    suspend fun initialize(): Boolean

    /**
     * Validates the configuration.
     */
    suspend fun validateConfig(): Boolean

    /**
     * Returns the health status of the provider.
     */
    override suspend fun isAvailable(): Boolean

    /**
     * Legacy execution signature used by existing adapters.
     *
     * New pipeline call sites should use [ProviderExecutor.execute] with [Artifact].
     */
    suspend fun execute(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Result<Any>

    /**
     * Artifact-aware execution bridge required by [ProviderExecutor].
     *
     * This keeps existing adapter implementations compile-safe while the codebase
     * migrates from raw payloads to artifact-first I/O.
     */
    override suspend fun execute(
        transform: Transform,
        input: Artifact,
        parameters: Map<String, Any>
    ): Result<Artifact> {
        val legacyInput = input.toLegacyInput()
        return execute(transform, legacyInput, parameters).mapCatching { output ->
            output.toArtifact(transform.targetModality)
        }
    }

    /**
     * Executes a streaming transformation with incremental responses.
     * 
     * This is an optional feature - adapters can override this to provide
     * real-time streaming output. The default implementation returns null
     * indicating streaming is not supported.
     *
     * @param transform The transformation to perform
     * @param input The input content (type depends on transform's source modality)
     * @param parameters Additional execution parameters
     * @return A Flow of streaming responses, or null if streaming is not supported
     */
    fun executeStreaming(
        transform: Transform,
        input: Any,
        parameters: Map<String, Any> = emptyMap()
    ): Flow<StreamingResponse>? = null

    private fun Artifact.toLegacyInput(): Any = when (this) {
        is Artifact.Text -> content
        is Artifact.Image -> uri
        is Artifact.Audio -> uri
        is Artifact.Video -> uri
        is Artifact.Binary -> data
        is Artifact.Json -> jsonString
        is Artifact.Error -> message
        is Artifact.Empty -> ""
    }

    private fun Any.toArtifact(targetModality: Modality): Artifact {
        if (this is Artifact) return this
        return when (targetModality) {
            is Modality.Text -> Artifact.Text.create(content = asText())
            is Modality.Image -> asMediaArtifact(
                imageBuilder = { uri -> Artifact.Image(id = newArtifactId(), uri = uri) },
                fallbackModality = "image"
            )
            is Modality.Audio -> asMediaArtifact(
                imageBuilder = { uri -> Artifact.Audio(id = newArtifactId(), uri = uri) },
                fallbackModality = "audio"
            )
            is Modality.Video -> asMediaArtifact(
                imageBuilder = { uri -> Artifact.Video(id = newArtifactId(), uri = uri) },
                fallbackModality = "video"
            )
            is Modality.Mixed -> Artifact.Json(
                id = newArtifactId(),
                jsonString = asText()
            )
        }
    }

    private fun Any.asMediaArtifact(
        imageBuilder: (Uri) -> Artifact,
        fallbackModality: String
    ): Artifact {
        val mediaUri = extractFirstUri()?.let(Uri::parse)
        return if (mediaUri != null) {
            imageBuilder(mediaUri)
        } else {
            Artifact.Text.create(
                content = asText(),
                metadata = mapOf("expected_modality" to fallbackModality)
            )
        }
    }

    private fun Any.asText(): String = when (this) {
        is String -> this
        is Number -> this.toString()
        is Boolean -> this.toString()
        is ByteArray -> String(this, Charsets.UTF_8)
        is Uri -> this.toString()
        else -> this.toString()
    }

    private fun Any.extractFirstUri(): String? {
        return when (this) {
            is Uri -> toString()
            is String -> takeIf { looksLikeUri(it) }
            is List<*> -> this.asSequence()
                .filterIsInstance<String>()
                .firstOrNull { looksLikeUri(it) }
            is Map<*, *> -> this.values.asSequence()
                .filterIsInstance<String>()
                .firstOrNull { looksLikeUri(it) }
            else -> extractUriFromJavaBean()
        }
    }

    private fun Any.extractUriFromJavaBean(): String? {
        val clazz = this::class.java

        val singleUri = listOf("getImageUrl", "getUrl", "getUri")
            .asSequence()
            .mapNotNull { methodName ->
                clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                    ?.invoke(this) as? String
            }
            .firstOrNull { looksLikeUri(it) }
        if (singleUri != null) return singleUri

        return listOf("getImageUrls", "getImages", "getMediaUrls")
            .asSequence()
            .mapNotNull { methodName ->
                @Suppress("UNCHECKED_CAST")
                clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                    ?.invoke(this) as? List<String>
            }
            .flatMap { it.asSequence() }
            .firstOrNull { looksLikeUri(it) }
    }

    private fun looksLikeUri(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            normalized.startsWith("content://") ||
            normalized.startsWith("file://")
    }

    private fun newArtifactId(): String = java.util.UUID.randomUUID().toString()
}

/**
 * Configuration for a provider adapter.
 */
data class ProviderAdapterConfig(
    val providerId: ProviderId,
    val baseUrl: String = "",
    val apiKey: String? = null,
    val apiKeySecret: SecretBytes? = null,
    val modelId: String? = null,
    val timeoutSeconds: Int = 60,
    val maxRetries: Int = 3
) {
    /**
     * Returns the API key as plaintext for legacy adapter call sites.
     * Prefer [resolveApiKeySecret] for new code.
     */
    fun resolveApiKey(): String? {
        apiKey?.takeIf { it.isNotBlank() }?.let { return it }
        val secret = apiKeySecret ?: return null
        val bytes = secret.copy()
        return try {
            String(bytes, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } finally {
            java.util.Arrays.fill(bytes, 0)
        }
    }

    /**
     * Returns the API key as SecretBytes for secure operations.
     */
    fun resolveApiKeySecret(): SecretBytes? {
        apiKeySecret?.let { return it }
        val plain = apiKey?.takeIf { it.isNotBlank() } ?: return null
        return SecretBytes.fromByteArray(plain.toByteArray(Charsets.UTF_8))
    }
}

/**
 * Result of an inference operation.
 */
sealed class InferenceResult {
    data class Success(
        val output: Any,
        val modelId: String,
        val latencyMs: Long
    ) : InferenceResult()

    data class Error(
        val message: String,
        val exception: Throwable? = null,
        val isRetryable: Boolean = false
    ) : InferenceResult()
}
