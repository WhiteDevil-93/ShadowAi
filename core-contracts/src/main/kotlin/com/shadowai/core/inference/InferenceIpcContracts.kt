package com.shadowai.core.inference

/**
 * Shared IPC key contracts for app <-> isolated inference service communication.
 *
 * Keeping keys in core-contracts prevents key drift between the app process and
 * the :inference process implementation.
 */
object InferenceIpcContracts {

    object Request {
        const val CONTEXT_SIZE = "contextSize"
        const val THREADS = "threads"
        const val MAX_TOKENS = "maxTokens"
        const val TOP_K = "topK"
        const val TOP_P = "topP"
        const val TEMPERATURE = "temperature"
        const val REPEAT_PENALTY = "repeatPenalty"
        const val BATCH_SIZE = "batchSize"
        const val USE_MMAP = "useMmap"
        const val USE_MLOCK = "useMlock"
        const val GPU_LAYERS = "gpuLayers"
        const val ENABLE_PII_MASKING = "enablePiiMasking"
        const val MODEL_ID = "modelId"
        const val PROMPT = "prompt"
    }

    object Response {
        const val SUCCESS = "success"
        const val ERROR_MESSAGE = "errorMessage"
        const val MODEL_ID = "modelId"
        const val NATIVE_HANDLE = "nativeHandle"
        const val TEXT = "text"
        const val TOKENS_GENERATED = "tokensGenerated"
    }

    object ServiceInfo {
        const val VERSION_CODE = "versionCode"
        const val VERSION_NAME = "versionName"
        const val NATIVE_LIBRARY_LOADED = "nativeLibraryLoaded"
    }

    object MemoryStats {
        const val TOTAL_MODELS_LOADED = "totalModelsLoaded"
        const val TOTAL_MEMORY_USED_BYTES = "totalMemoryUsedBytes"
        const val AVAILABLE_MEMORY_BYTES = "availableMemoryBytes"
    }
}
