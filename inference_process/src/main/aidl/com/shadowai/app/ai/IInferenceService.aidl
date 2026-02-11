// IInferenceService.aidl
package com.shadowai.app.ai;

import com.shadowai.app.ai.IGenerationCallback;
import android.os.Bundle;

/**
 * AIDL interface for isolated inference service.
 *
 * This interface defines the IPC contract between the main app process
 * and the isolated inference process running llama.cpp.
 */
interface IInferenceService {

    /**
     * Loads a GGUF model into memory.
     *
     * @param modelFileDescriptor ParcelFileDescriptor for the model file
     * @param request Bundle containing ModelLoadRequest parameters:
     *               - contextSize: int
     *               - maxTokens: int
     *               - temperature: float
     *               - topP: float
     *               - topK: int
     *               - repeatPenalty: float
     *               - batchSize: int
     *               - threads: int
     *               - useMmap: boolean
     *               - useMlock: boolean
     *               - gpuLayers: int
     * @return Bundle containing ModelLoadResponse:
     *         - success: boolean
     *         - modelId: String (nullable)
     *         - nativeHandle: long
     *         - errorMessage: String (nullable)
     */
    Bundle loadModel(in android.os.ParcelFileDescriptor modelFileDescriptor, in Bundle request) = 0;

    /**
     * Unloads a model from memory.
     *
     * @param modelId The model ID returned by loadModel
     */
    void unloadModel(String modelId) = 1;

    /**
     * Generates text using a loaded model (blocking call).
     *
     * @param request Bundle containing GenerationRequest parameters:
     *               - modelId: String
     *               - prompt: String
     *               - maxTokens: int
     *               - temperature: float
     *               - topP: float
     *               - topK: int
     *               - repeatPenalty: float
     *               - stopSequences: String[]
     * @return Bundle containing GenerationResponse:
     *         - success: boolean
     *         - text: String (nullable)
     *         - tokensGenerated: int
     *         - errorMessage: String (nullable)
     */
    Bundle generate(in Bundle request) = 2;

    /**
     * Generates text with streaming callback.
     *
     * @param request Bundle containing GenerationRequest parameters
     * @param callback Callback interface for receiving tokens as they're generated
     */
    void generateStream(in Bundle request, IGenerationCallback callback) = 3;

    /**
     * Gets service version information.
     *
     * @return Bundle containing:
     *         - versionCode: int
     *         - versionName: String
     *         - nativeLibraryLoaded: boolean
     */
    Bundle getServiceInfo() = 4;

    /**
     * Gets memory statistics from the inference process.
     *
     * @return Bundle containing:
     *         - totalModelsLoaded: int
     *         - totalMemoryUsedBytes: long
     *         - availableMemoryBytes: long
     */
    Bundle getMemoryStats() = 5;

    /**
     * Cancels an ongoing generation for a model.
     *
     * @param modelId The model ID to cancel generation for
     */
    void cancelGeneration(String modelId) = 6;
}
