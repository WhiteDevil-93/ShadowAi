package com.shadowai.app.ai

/**
 * Exception thrown when model loading fails.
 * Provides detailed error information about why a model could not be loaded.
 */
class ModelLoadingException(
    message: String,
    cause: Throwable? = null,
    val modelPath: String? = null,
    val errorCode: ErrorCode? = null
) : Exception(message, cause) {

    /**
     * Error codes for model loading failures.
     */
    enum class ErrorCode {
        FILE_NOT_FOUND,
        INVALID_FORMAT,
        UNSUPPORTED_VERSION,
        INSUFFICIENT_MEMORY,
        CORRUPTED_FILE,
        PERMISSION_DENIED,
        UNKNOWN_ERROR
    }

    companion object {
        /**
         * Create an exception for file not found errors.
         */
        fun fileNotFound(modelPath: String): ModelLoadingException {
            return ModelLoadingException(
                message = "Model file not found: $modelPath",
                modelPath = modelPath,
                errorCode = ErrorCode.FILE_NOT_FOUND
            )
        }

        /**
         * Create an exception for invalid format errors.
         */
        fun invalidFormat(modelPath: String, details: String = ""): ModelLoadingException {
            return ModelLoadingException(
                message = "Invalid model format: $modelPath${if (details.isNotEmpty()) " - $details" else ""}",
                modelPath = modelPath,
                errorCode = ErrorCode.INVALID_FORMAT
            )
        }

        /**
         * Create an exception for unsupported version errors.
         */
        fun unsupportedVersion(modelPath: String, version: String): ModelLoadingException {
            return ModelLoadingException(
                message = "Unsupported GGUF version $version in model: $modelPath",
                modelPath = modelPath,
                errorCode = ErrorCode.UNSUPPORTED_VERSION
            )
        }

        /**
         * Create an exception for insufficient memory errors.
         */
        fun insufficientMemory(modelPath: String, required: Long, available: Long): ModelLoadingException {
            return ModelLoadingException(
                message = "Insufficient memory to load model: $modelPath. Required: ${required / 1024 / 1024}MB, Available: ${available / 1024 / 1024}MB",
                modelPath = modelPath,
                errorCode = ErrorCode.INSUFFICIENT_MEMORY
            )
        }

        /**
         * Create an exception for corrupted file errors.
         */
        fun corruptedFile(modelPath: String, details: String = ""): ModelLoadingException {
            return ModelLoadingException(
                message = "Corrupted model file: $modelPath${if (details.isNotEmpty()) " - $details" else ""}",
                modelPath = modelPath,
                errorCode = ErrorCode.CORRUPTED_FILE
            )
        }

        /**
         * Create an exception for permission denied errors.
         */
        fun permissionDenied(modelPath: String): ModelLoadingException {
            return ModelLoadingException(
                message = "Permission denied accessing model file: $modelPath",
                modelPath = modelPath,
                errorCode = ErrorCode.PERMISSION_DENIED
            )
        }

        /**
         * Create an exception for unknown errors.
         */
        fun unknown(modelPath: String, cause: Throwable): ModelLoadingException {
            return ModelLoadingException(
                message = "Unknown error loading model: $modelPath - ${cause.message}",
                modelPath = modelPath,
                cause = cause,
                errorCode = ErrorCode.UNKNOWN_ERROR
            )
        }
    }
}