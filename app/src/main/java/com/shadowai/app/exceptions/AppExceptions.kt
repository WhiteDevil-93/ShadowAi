package com.shadowai.app.exceptions

import java.io.IOException

/**
 * Domain-specific exception hierarchy for better error handling.
 *
 * Use these instead of bare `catch(Exception)` blocks to:
 * 1. Catch only specific, recoverable errors
 * 2. Provide better error messages to users
 * 3. Enable proper error recovery strategies
 * 4. Improve debugging with specific exception types
 */

// ============================================
// Network Exceptions
// ============================================

/**
 * Base class for all network-related errors
 */
sealed class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause) {

    /**
     * Request timed out
     */
    class Timeout(cause: Throwable? = null) : NetworkException("Request timeout", cause)

    /**
     * No network connection available
     */
    class NoConnection(cause: Throwable? = null) : NetworkException("No network connection", cause)

    /**
     * Server returned an error (4xx, 5xx)
     */
    class ServerError(val code: Int, message: String? = null, cause: Throwable? = null)
        : NetworkException(message ?: "Server error: HTTP $code", cause)

    /**
     * Invalid or malformed response from server
     */
    class InvalidResponse(message: String, cause: Throwable? = null)
        : NetworkException("Invalid response: $message", cause)

    /**
     * API rate limit exceeded
     */
    class RateLimitExceeded(val retryAfterSeconds: Int? = null, cause: Throwable? = null)
        : NetworkException("Rate limit exceeded${retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""}", cause)
}

// ============================================
// Model/AI Exceptions
// ============================================

/**
 * Base class for model/AI-related errors
 */
sealed class ModelException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Failed to load model file
     */
    class LoadFailed(val modelPath: String, cause: Throwable? = null)
        : ModelException("Failed to load model: $modelPath", cause)

    /**
     * Model file is invalid or corrupted
     */
    class InvalidModel(val modelPath: String, val reason: String, cause: Throwable? = null)
        : ModelException("Invalid model $modelPath: $reason", cause)

    /**
     * Inference/generation failed
     */
    class InferenceFailed(message: String, cause: Throwable? = null)
        : ModelException("Inference failed: $message", cause)

    /**
     * Insufficient memory to load model
     */
    class InsufficientMemory(val requiredMB: Long, val availableMB: Long)
        : ModelException("Insufficient memory: need ${requiredMB}MB, have ${availableMB}MB")

    /**
     * Model not found
     */
    class NotFound(val modelPath: String)
        : ModelException("Model not found: $modelPath")
}

// ============================================
// Security/Encryption Exceptions
// ============================================

/**
 * Base class for security-related errors
 */
sealed class SecurityException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Encryption operation failed
     */
    class EncryptionFailed(message: String, cause: Throwable? = null)
        : SecurityException("Encryption failed: $message", cause)

    /**
     * Decryption operation failed
     */
    class DecryptionFailed(message: String, cause: Throwable? = null)
        : SecurityException("Decryption failed: $message", cause)

    /**
     * Key generation failed
     */
    class KeyGenerationFailed(val keyType: String, cause: Throwable? = null)
        : SecurityException("Failed to generate $keyType key", cause)

    /**
     * Invalid or corrupted key
     */
    class InvalidKey(message: String, cause: Throwable? = null)
        : SecurityException("Invalid key: $message", cause)

    /**
     * Authentication failed
     */
    class AuthenticationFailed(message: String, cause: Throwable? = null)
        : SecurityException("Authentication failed: $message", cause)

    /**
     * Permission denied
     */
    class PermissionDenied(val permission: String, cause: Throwable? = null)
        : SecurityException("Permission denied: $permission", cause)
}

// ============================================
// Storage/Database Exceptions
// ============================================

/**
 * Base class for storage-related errors
 */
sealed class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause) {

    /**
     * File not found
     */
    class FileNotFound(val path: String, cause: Throwable? = null)
        : StorageException("File not found: $path", cause)

    /**
     * Insufficient disk space
     */
    class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long)
        : StorageException("Insufficient space: need ${requiredBytes / 1024 / 1024}MB, have ${availableBytes / 1024 / 1024}MB")

    /**
     * Read operation failed
     */
    class ReadFailed(val path: String, cause: Throwable? = null)
        : StorageException("Failed to read: $path", cause)

    /**
     * Write operation failed
     */
    class WriteFailed(val path: String, cause: Throwable? = null)
        : StorageException("Failed to write: $path", cause)

    /**
     * Database operation failed
     */
    class DatabaseError(message: String, cause: Throwable? = null)
        : StorageException("Database error: $message", cause)
}

// ============================================
// Parsing/Validation Exceptions
// ============================================

/**
 * Base class for parsing/validation errors
 */
sealed class ParseException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * JSON parsing failed
     */
    class InvalidJson(message: String, cause: Throwable? = null)
        : ParseException("Invalid JSON: $message", cause)

    /**
     * Schema validation failed
     */
    class SchemaViolation(val errors: List<String>)
        : ParseException("Schema validation failed: ${errors.joinToString(", ")}")

    /**
     * Invalid data format
     */
    class InvalidFormat(val expected: String, val actual: String, cause: Throwable? = null)
        : ParseException("Invalid format: expected $expected, got $actual", cause)
}

// ============================================
// Configuration Exceptions
// ============================================

/**
 * Base class for configuration errors
 */
sealed class ConfigurationException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Missing required configuration
     */
    class MissingConfig(val key: String)
        : ConfigurationException("Missing required configuration: $key")

    /**
     * Invalid configuration value
     */
    class InvalidConfig(val key: String, val value: String, val reason: String)
        : ConfigurationException("Invalid configuration for $key='$value': $reason")

    /**
     * Provider not configured
     */
    class ProviderNotConfigured(val providerId: String)
        : ConfigurationException("Provider not configured: $providerId")
}

// ============================================
// Resource Exceptions
// ============================================

/**
 * Base class for resource-related errors
 */
sealed class ResourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * Resource not available
     */
    class NotAvailable(val resource: String, val reason: String)
        : ResourceException("Resource not available: $resource ($reason)")

    /**
     * Resource exhausted
     */
    class Exhausted(val resource: String)
        : ResourceException("Resource exhausted: $resource")

    /**
     * Resource busy/locked
     */
    class Busy(val resource: String, cause: Throwable? = null)
        : ResourceException("Resource busy: $resource", cause)
}
