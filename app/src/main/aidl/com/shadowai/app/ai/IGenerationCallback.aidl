package com.shadowai.app.ai;

interface IGenerationCallback {
    void onToken(String token);
    void onComplete(String fullText);
    void onError(String errorMessage);
}
