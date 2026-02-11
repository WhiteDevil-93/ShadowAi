package com.shadowai.app.ai

import android.net.Uri

/**
 * Optional runtime controls for local inference engines.
 *
 * Implementations can expose lifecycle warmup behavior and model-tree URI support
 * without extending the core-contracts LocalInferenceEngine API.
 */
interface InferenceEngineControl {
    /**
     * Prepare engine resources before availability checks.
     *
     * Isolated engines can use this to bind their remote service.
     */
    suspend fun warmup(): Result<Unit> = Result.success(Unit)
}

/**
 * Optional capability for engines that support SAF tree-based model discovery.
 */
interface ModelTreeUriConfigurable {
    fun setCustomModelTreeUri(treeUri: Uri?)
}
