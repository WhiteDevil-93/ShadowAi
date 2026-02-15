package com.shadowai.app.exceptions

import java.io.IOException

/**
 * Base interface for app exceptions with context support.
 */
interface AppException {
    val context: String?
    fun withContext(newContext: ExceptionMapper.ExceptionContext?): AppException
}

// ============================================
// Network Exceptions
// ============================================

sealed class NetworkException(
    message: String,
    cause: Throwable? = null,
    override val context: String? = null
) : IOException(message, cause), AppException {

    class Timeout(cause: Throwable? = null, override val context: String? = null) : NetworkException("Request timeout", cause, context)
    class NoConnection(cause: Throwable? = null, override val context: String? = null) : NetworkException("No network connection", cause, context)
    class ServerError(val code: Int, message: String? = null, cause: Throwable? = null, override val context: String? = null) : NetworkException(message ?: "Server error: HTTP $code", cause, context)
    class ConnectionError(message: String, cause: Throwable? = null, override val context: String? = null) : NetworkException(message, cause, context)
    class TimeoutError(message: String, cause: Throwable? = null, override val context: String? = null) : NetworkException(message, cause, context)
    class UnknownError(message: String, cause: Throwable? = null, override val context: String? = null) : NetworkException(message, cause, context)
    class InvalidResponse(message: String, cause: Throwable? = null, val httpCode: Int? = null, override val context: String? = null) : NetworkException("Invalid response: $message", cause, context)
    class RateLimitExceeded(val retryAfterSeconds: Int? = null, cause: Throwable? = null, override val context: String? = null) : NetworkException("Rate limit exceeded", cause, context)

    override fun withContext(newContext: ExceptionMapper.ExceptionContext?): NetworkException {
        val newCtx = newContext?.toSummary() ?: context
        return when (this) {
            is Timeout -> Timeout(cause, newCtx)
            is NoConnection -> NoConnection(cause, newCtx)
            is ServerError -> ServerError(code, message, cause, newCtx)
            is InvalidResponse -> InvalidResponse(message ?: "", cause, httpCode, newCtx)
            is RateLimitExceeded -> RateLimitExceeded(retryAfterSeconds, cause, newCtx)
            is ConnectionError -> ConnectionError(message ?: "", cause, newCtx)
            is TimeoutError -> TimeoutError(message ?: "", cause, newCtx)
            is UnknownError -> UnknownError(message ?: "", cause, newCtx)
        }
    }
}

// ============================================
// Model/AI Exceptions
// ============================================

sealed class ModelException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class LoadFailed(val modelPath: String, cause: Throwable? = null) : ModelException("Failed to load model: $modelPath", cause)
    class InvalidModel(val modelPath: String, val reason: String, cause: Throwable? = null) : ModelException("Invalid model $modelPath: $reason", cause)
    class InferenceFailed(message: String, cause: Throwable? = null) : ModelException("Inference failed: $message", cause)
    class InsufficientMemory(val requiredMB: Long, val availableMB: Long) : ModelException("Insufficient memory")
    class NotFound(val modelPath: String) : ModelException("Model not found: $modelPath")
}

// ============================================
// Security/Encryption Exceptions
// ============================================

sealed class SecurityException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class EncryptionFailed(message: String, cause: Throwable? = null) : SecurityException("Encryption failed: $message", cause)
    class DecryptionFailed(message: String, cause: Throwable? = null) : SecurityException("Decryption failed: $message", cause)
    class KeyGenerationFailed(val keyType: String, cause: Throwable? = null) : SecurityException("Failed to generate $keyType key", cause)
    class InvalidKey(message: String, cause: Throwable? = null) : SecurityException("Invalid key: $message", cause)
    class AuthenticationFailed(message: String, cause: Throwable? = null) : SecurityException("Authentication failed: $message", cause)
    class PermissionDenied(val permission: String, cause: Throwable? = null) : SecurityException("Permission denied: $permission", cause)
}

// ============================================
// Storage/Database Exceptions
// ============================================

sealed class StorageException(
    message: String,
    cause: Throwable? = null,
    override val context: String? = null
) : IOException(message, cause), AppException {

    class FileNotFound(val path: String, cause: Throwable? = null, val operation: String? = null, override val context: String? = null) : StorageException("File not found: $path", cause, context)
    class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long, override val context: String? = null) : StorageException("Insufficient space", context = context)
    class ReadFailed(val path: String, cause: Throwable? = null, val operation: String? = null, override val context: String? = null) : StorageException("Failed to read: $path", cause, context)
    class WriteFailed(val path: String, cause: Throwable? = null, val operation: String? = null, override val context: String? = null) : StorageException("Failed to write: $path", cause, context)
    class DatabaseError(message: String, cause: Throwable? = null, override val context: String? = null) : StorageException("Database error: $message", cause, context)
    class PermissionDenied(val path: String, cause: Throwable? = null, val operation: String? = null, override val context: String? = null) : StorageException("Permission denied: $path", cause, context)

    override fun withContext(newContext: ExceptionMapper.ExceptionContext?): StorageException {
        val newCtx = newContext?.toSummary() ?: context
        return when (this) {
            is FileNotFound -> FileNotFound(path, cause, operation, newCtx)
            is InsufficientSpace -> InsufficientSpace(requiredBytes, availableBytes, newCtx)
            is ReadFailed -> ReadFailed(path, cause, operation, newCtx)
            is WriteFailed -> WriteFailed(path, cause, operation, newCtx)
            is DatabaseError -> DatabaseError(message ?: "", cause, newCtx)
            is PermissionDenied -> PermissionDenied(path, cause, operation, newCtx)
        }
    }
}

// ============================================
// Parsing/Validation Exceptions
// ============================================

sealed class ParseException(
    message: String,
    cause: Throwable? = null,
    override val context: String? = null
) : Exception(message, cause), AppException {

    class InvalidJson(message: String, cause: Throwable? = null, val contentSample: String? = null, override val context: String? = null) : ParseException("Invalid JSON: $message", cause, context)
    class SchemaViolation(val errors: List<String>, override val context: String? = null) : ParseException("Schema validation failed", context = context)
    class InvalidFormat(val expected: String, val actual: String, cause: Throwable? = null, override val context: String? = null) : ParseException("Invalid format", cause, context)

    override fun withContext(newContext: ExceptionMapper.ExceptionContext?): ParseException {
        val newCtx = newContext?.toSummary() ?: context
        return when (this) {
            is InvalidJson -> InvalidJson(message ?: "", cause, contentSample, newCtx)
            is SchemaViolation -> SchemaViolation(errors, newCtx)
            is InvalidFormat -> InvalidFormat(expected, actual, cause, newCtx)
        }
    }
}

// ============================================
// Configuration Exceptions
// ============================================

sealed class ConfigurationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingConfig(val key: String) : ConfigurationException("Missing configuration: $key")
    class InvalidConfig(val key: String, val value: String, val reason: String) : ConfigurationException("Invalid configuration")
    class ProviderNotConfigured(val providerId: String) : ConfigurationException("Provider not configured")
}

// ============================================
// Resource Exceptions
// ============================================

sealed class ResourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAvailable(val resource: String, val reason: String) : ResourceException("Resource not available")
    class Exhausted(val resource: String) : ResourceException("Resource exhausted")
    class Busy(val resource: String, cause: Throwable? = null) : ResourceException("Resource busy", cause)
}
