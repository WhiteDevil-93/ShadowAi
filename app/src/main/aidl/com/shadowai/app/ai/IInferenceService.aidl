package com.shadowai.app.ai;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.shadowai.app.ai.IGenerationCallback;

interface IInferenceService {
    Bundle loadModel(in ParcelFileDescriptor modelFileDescriptor, in Bundle request);
    void unloadModel(String modelId);
    Bundle generate(in Bundle request);
    void generateStream(in Bundle request, IGenerationCallback callback);
    Bundle getServiceInfo();
    Bundle getMemoryStats();
    void cancelGeneration(String modelId);
}
