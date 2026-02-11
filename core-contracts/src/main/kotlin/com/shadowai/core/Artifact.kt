package com.shadowai.core

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Sealed class representing artifacts produced or consumed by AI transforms.
 *
 * Artifacts are the unified data types flowing through the AI pipeline. They include:
 * - Content outputs (text, images, audio, video)
 * - Structured data (JSON, embeddings)
 * - Error states with recovery information
 * - References to external resources
 *
 * Each artifact carries integrity metadata (hash, timestamp) for verification
 * across pipeline stages.
 *
 * @see Transform Transforms produce/consume artifacts
 * @see ProviderExecutor Executors return artifacts
 */
sealed class Artifact : Parcelable {

    /**
     * Unique identifier for this artifact instance.
     */
    abstract val id: String

    /**
     * Timestamp when the artifact was created (milliseconds since epoch).
     */
    abstract val createdAt: Long

    /**
     * Integrity hash for verification across pipeline stages.
     * Format: "algorithm:hexvalue" (e.g., "sha256:a1b2c3...")
     */
    abstract val integrityHash: String?

    /**
     * Metadata about the artifact's origin and processing.
     */
    abstract val metadata: @RawValue Map<String, Any>

    /**
     * Returns the modality of this artifact.
     */
    abstract fun getModality(): Modality

    /**
     * Text content artifact.
     */
    @Parcelize
    data class Text(
        override val id: String,
        val content: String,
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Text

        companion object {
            /**
             * Create a text artifact with auto-generated ID and hash.
             */
            fun create(
                content: String,
                metadata: Map<String, Any> = emptyMap(),
                generateHash: Boolean = true
            ): Text = Text(
                id = java.util.UUID.randomUUID().toString(),
                content = content,
                integrityHash = if (generateHash) generateHash(content) else null,
                metadata = metadata
            )

            private fun generateHash(content: String): String {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(content.toByteArray())
                val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
                return "sha256:$hashHex"
            }
        }
    }

    /**
     * Image content artifact with URI reference.
     */
    @Parcelize
    data class Image(
        override val id: String,
        val uri: Uri,
        val mimeType: String = "image/png",
        val width: Int? = null,
        val height: Int? = null,
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Image

        companion object {
            /**
             * Create an image artifact from a file path.
             */
            fun fromPath(
                path: String,
                mimeType: String = "image/png",
                metadata: Map<String, Any> = emptyMap()
            ): Image = Image(
                id = java.util.UUID.randomUUID().toString(),
                uri = Uri.parse("file://$path"),
                mimeType = mimeType,
                metadata = metadata
            )
        }
    }

    /**
     * Audio content artifact.
     */
    @Parcelize
    data class Audio(
        override val id: String,
        val uri: Uri,
        val mimeType: String = "audio/mp3",
        val durationMs: Long? = null,
        val transcript: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Audio
    }

    /**
     * Video content artifact.
     */
    @Parcelize
    data class Video(
        override val id: String,
        val uri: Uri,
        val mimeType: String = "video/mp4",
        val durationMs: Long? = null,
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Video
    }

    /**
     * Binary/blob data artifact for embeddings, model weights, etc.
     */
    @Parcelize
    data class Binary(
        override val id: String,
        val data: ByteArray,
        val mimeType: String = "application/octet-stream",
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Mixed

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return id == other.id && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * JSON/structured data artifact.
     */
    @Parcelize
    data class Json(
        override val id: String,
        val jsonString: String,
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Text

        /**
         * Parse the JSON string to a Map.
         */
        fun toMap(): Map<String, Any> {
            return try {
                val json = org.json.JSONObject(jsonString)
                json.keys().asSequence().associateWith { json.get(it) }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * Error artifact representing a failed transformation.
     */
    @Parcelize
    data class Error(
        override val id: String,
        val message: String,
        val recoverable: Boolean = false,
        val errorCode: String? = null,
        val cause: String? = null,
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Mixed

        companion object {
            fun create(
                message: String,
                recoverable: Boolean = false,
                errorCode: String? = null,
                cause: Throwable? = null
            ): Error = Error(
                id = java.util.UUID.randomUUID().toString(),
                message = message,
                recoverable = recoverable,
                errorCode = errorCode,
                cause = cause?.message,
                metadata = cause?.let { mapOf("exception_class" to it::class.simpleName.orEmpty()) } ?: emptyMap()
            )
        }
    }

    /**
     * Empty/null artifact representing a no-op or skipped transformation.
     */
    @Parcelize
    data class Empty(
        override val id: String = "empty",
        override val createdAt: Long = System.currentTimeMillis(),
        override val integrityHash: String? = null,
        override val metadata: @RawValue Map<String, Any> = emptyMap()
    ) : Artifact() {
        override fun getModality(): Modality = Modality.Mixed
    }
}

/**
 * Result wrapper for artifact-producing operations.
 */
sealed class ArtifactResult {
    abstract val artifact: Artifact

    data class Success(override val artifact: Artifact) : ArtifactResult()
    data class Failure(
        val error: Artifact.Error,
        val partialArtifact: Artifact? = null
    ) : ArtifactResult() {
        override val artifact: Artifact get() = error
    }

    /**
     * Returns true if this result represents a successful operation.
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns the artifact if successful, or throws if failure.
     */
    fun getOrThrow(): Artifact = when (this) {
        is Success -> artifact
        is Failure -> throw RuntimeException("Artifact creation failed: ${error.message}")
    }

    /**
     * Map the artifact to a different type if successful.
     */
    inline fun <reified T : Artifact> map(transform: (Artifact) -> T): ArtifactResult {
        return when (this) {
            is Success -> Success(transform(artifact))
            is Failure -> this
        }
    }
}
