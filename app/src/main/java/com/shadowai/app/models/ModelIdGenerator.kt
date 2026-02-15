package com.shadowai.app.models

import java.io.File
import java.security.MessageDigest

/**
 * M-1: Hash-based Model ID generator.
 *
 * Replaces increment-based IDs with consistent hash-based IDs derived from model content/path.
 * This ensures:
 * - Same model always gets same ID (deduplication)
 * - No global increment counter needed
 * - IDs are deterministic and portable
 */
object ModelIdGenerator {

    /**
     * Generate a model ID from file path and metadata.
     * Uses SHA-256 hash of normalized path and file metadata.
     *
     * @param path Absolute or relative path to model file
     * @param modelName Human-readable model name (optional)
     * @return Deterministic ModelId based on hash
     */
    fun generate(path: String, modelName: String? = null): ModelId {
        val normalizedPath = normalizePath(path)
        val content = buildString {
            append(normalizedPath)
            modelName?.let { append("|$it") }
        }
        val hash = sha256(content)
        // Use first 12 chars of hex hash (sufficient entropy, readable)
        return ModelId("model_$hash")
    }

    /**
     * Generate a model ID from file with metadata extraction.
     * Includes file size and last modified time for uniqueness.
     *
     * @param file The model file
     * @return Deterministic ModelId
     */
    fun generateFromFile(file: File): ModelId {
        val content = buildString {
            append(normalizePath(file.absolutePath))
            append("|${file.length()}")
            append("|${file.lastModified()}")
        }
        val hash = sha256(content)
        return ModelId("model_$hash")
    }

    /**
     * Generate a model ID from provider and model name.
     * For cloud/API models without local files.
     *
     * @param provider Provider identifier (e.g., "openai", "anthropic")
     * @param modelName Model name (e.g., "gpt-4", "claude-3-opus")
     * @return Deterministic ModelId
     */
    fun generateFromProvider(provider: String, modelName: String): ModelId {
        val content = "$provider|$modelName"
        val hash = sha256(content)
        return ModelId("${provider}_${modelName.replace("-", "_")}_$hash")
    }

    /**
     * Normalize path for consistent hashing across platforms.
     */
    private fun normalizePath(path: String): String {
        return path
            .replace("\\", "/")  // Windows backslash to forward slash
            .lowercase()           // Case-insensitive
            .trimEnd('/')          // Remove trailing slash
    }

    /**
     * Compute SHA-256 hash and return hex string.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.take(12)
    }
}
