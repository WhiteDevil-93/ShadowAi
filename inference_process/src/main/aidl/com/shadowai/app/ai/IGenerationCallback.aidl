// IGenerationCallback.aidl
package com.shadowai.app.ai;

/**
 * Callback interface for streaming text generation.
 *
 * Implemented by the client (main app process) to receive
 * generated tokens as they're produced by the inference process.
 */
oneway interface IGenerationCallback {

    /**
     * Called for each generated token.
     *
     * @param token The generated token string (may be partial word/subword)
     */
    void onToken(String token) = 0;

    /**
     * Called when generation is complete.
     *
     * @param fullText The complete generated text
     */
    void onComplete(String fullText) = 1;

    /**
     * Called if generation fails.
     *
     * @param error Error message describing the failure
     */
    void onError(String error) = 2;
}
