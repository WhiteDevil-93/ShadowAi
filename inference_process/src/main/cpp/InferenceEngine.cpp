#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <fstream>

// Placeholder for llama.cpp includes
// #include "llama.h"

// Define a simple logger for C++
#define LOG_TAG "InferenceEngine_CPP"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations for native methods
extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_NativeBridge_nativeLoadModel(
    JNIEnv* env,
    jobject thiz,
    jstring model_path,
    jobject config_obj);

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_NativeBridge_nativeFreeModel(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle);

extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGenerate(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle,
    jstring prompt,
    jobject config_obj);

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGenerateStream(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle,
    jstring prompt,
    jobject config_obj,
    jobject callback_obj);

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_NativeBridge_nativeCancel(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle);

// Global JVM and callback method IDs
JavaVM* g_vm = nullptr;
jmethodID g_onTokenMethod = nullptr;
jmethodID g_onCompletedMethod = nullptr;
jmethodID g_onErrorMethod = nullptr;

// Store model contexts (placeholder for llama_context)
std::map<jlong, std::string> g_model_contexts; // Map native_handle to model_path
std::atomic<bool> g_cancel_generation(false);

// Mutex for protecting access to g_model_contexts and other shared resources
std::mutex g_mutex;

// JNI_OnLoad is called when the .so library is loaded.
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Find the NativeBridge$GenerationCallback class
    jclass callbackClass = env->FindClass("com/shadowai/inference/NativeBridge$GenerationCallback");
    if (callbackClass == nullptr) {
        LOGE("Failed to find NativeBridge$GenerationCallback class");
        return JNI_ERR;
    }

    // Get method IDs for the callback interface
    g_onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    g_onCompletedMethod = env->GetMethodID(callbackClass, "onCompleted", "()V");
    g_onErrorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");

    if (g_onTokenMethod == nullptr || g_onCompletedMethod == nullptr || g_onErrorMethod == nullptr) {
        LOGE("Failed to get method IDs for GenerationCallback interface");
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad: GenerationCallback method IDs obtained.");

    return JNI_VERSION_1_6;
}

// JNI_OnUnload is called when the .so library is unloaded.
void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload: Cleaning up global references.");
    // No global references are currently stored, but this is where they would be released.
}

// Helper function to get JNIEnv for current thread
JNIEnv* getJNIEnv() {
    JNIEnv* env;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        // If the current thread is not attached, attach it
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("Failed to attach current thread to JVM");
            return nullptr;
        }
    }
    return env;
}

// Helper function to detach current thread from JVM
void detachJNIEnv() {
    g_vm->DetachCurrentThread();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_NativeBridge_nativeLoadModel(
    JNIEnv* env,
    jobject thiz,
    jstring model_path_jstr,
    jobject config_obj) {

    if (!g_vm) {
        LOGE("JVM not initialized.");
        return 0;
    }

    const char* model_path_cstr = env->GetStringUTFChars(model_path_jstr, nullptr);
    std::string model_path = model_path_cstr;
    env->ReleaseStringUTFChars(model_path_jstr, model_path_cstr);

    LOGI("nativeLoadModel: Loading model from %s", model_path.c_str());

    // Simulate model loading
    // In a real scenario, this would involve calling llama_init_from_file or similar
    // and returning a pointer to the llama_context.
    std::lock_guard<std::mutex> lock(g_mutex);
    jlong native_handle = reinterpret_cast<jlong>(new std::string(model_path)); // Placeholder handle
    g_model_contexts[native_handle] = model_path;

    LOGI("nativeLoadModel: Model %s loaded, native handle: %lld", model_path.c_str(), native_handle);
    return native_handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_NativeBridge_nativeFreeModel(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle) {

    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_model_contexts.find(native_handle);
    if (it != g_model_contexts.end()) {
        // In a real scenario, this would involve calling llama_free or similar
        delete reinterpret_cast<std::string*>(native_handle); // Free placeholder
        g_model_contexts.erase(it);
        LOGI("nativeFreeModel: Model with handle %lld freed.", native_handle);
    } else {
        LOGW("nativeFreeModel: Attempted to free unknown model handle %lld", native_handle);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGenerate(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle,
    jstring prompt_jstr,
    jobject config_obj) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model_contexts.find(native_handle) == g_model_contexts.end()) {
        LOGE("nativeGenerate: Invalid model handle %lld", native_handle);
        return env->NewStringUTF("Error: Invalid model handle.");
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt_jstr, nullptr);
    std::string prompt = prompt_cstr;
    env->ReleaseStringUTFChars(prompt_jstr, prompt_cstr);

    LOGI("nativeGenerate: Generating text for model %lld with prompt: %s", native_handle, prompt.c_str());

    // Simulate text generation
    // In a real scenario, this would involve calling llama_generate or similar.
    std::string result = "Simulated generated text for: " + prompt;

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGenerateStream(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle,
    jstring prompt_jstr,
    jobject config_obj,
    jobject callback_obj) {

    // Detach/attach logic for callback thread
    std::thread([=]() {
        JNIEnv* callbackEnv = nullptr;
        bool attached = false;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&callbackEnv), JNI_VERSION_1_6) != JNI_OK) {
            if (g_vm->AttachCurrentThread(&callbackEnv, nullptr) != JNI_OK) {
                LOGE("Failed to attach callback thread to JVM");
                return;
            }
            attached = true;
        }

        if (!callbackEnv) {
            LOGE("Callback JNIEnv is null.");
            return;
        }

        // Create a global reference to the callback object to prevent it from being garbage collected
        jobject g_callback_obj = callbackEnv->NewGlobalRef(callback_obj);
        if (g_callback_obj == nullptr) {
            LOGE("Failed to create global reference for callback object");
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        std::string current_prompt;
        {
            std::lock_guard<std::mutex> lock(g_mutex);
            if (g_model_contexts.find(native_handle) == g_model_contexts.end()) {
                LOGE("nativeGenerateStream: Invalid model handle %lld", native_handle);
                callbackEnv->CallVoidMethod(g_callback_obj, g_onErrorMethod, callbackEnv->NewStringUTF("Error: Invalid model handle."));
                callbackEnv->DeleteGlobalRef(g_callback_obj);
                if (attached) g_vm->DetachCurrentThread();
                return;
            }
            const char* prompt_cstr = env->GetStringUTFChars(prompt_jstr, nullptr);
            current_prompt = prompt_cstr;
            env->ReleaseStringUTFChars(prompt_jstr, prompt_cstr);
        }

        LOGI("nativeGenerateStream: Streaming text for model %lld with prompt: %s", native_handle, current_prompt.c_str());

        g_cancel_generation.store(false); // Reset cancellation flag for new generation

        std::string simulated_response = "This is a simulated streaming response for the prompt: " + current_prompt;
        std::string current_token;
        for (char c : simulated_response) {
            if (g_cancel_generation.load()) {
                LOGI("nativeGenerateStream: Generation cancelled for handle %lld", native_handle);
                callbackEnv->CallVoidMethod(g_callback_obj, g_onErrorMethod, callbackEnv->NewStringUTF("Generation cancelled."));
                break;
            }
            current_token += c;
            if (c == ' ') { // Simulate token by splitting on space
                callbackEnv->CallVoidMethod(g_callback_obj, g_onTokenMethod, callbackEnv->NewStringUTF(current_token.c_str()));
                current_token.clear();
                std::this_thread::sleep_for(std::chrono::milliseconds(50)); // Simulate delay
            }
        }
        if (!current_token.empty() && !g_cancel_generation.load()) {
            callbackEnv->CallVoidMethod(g_callback_obj, g_onTokenMethod, callbackEnv->NewStringUTF(current_token.c_str()));
        }

        if (!g_cancel_generation.load()) {
            callbackEnv->CallVoidMethod(g_callback_obj, g_onCompletedMethod);
        }

        callbackEnv->DeleteGlobalRef(g_callback_obj);
        if (attached) g_vm->DetachCurrentThread();
    }).detach(); // Detach the thread to allow it to run independently
}

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_NativeBridge_nativeCancel(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle) {
    LOGI("nativeCancel: Setting cancellation flag for handle %lld", native_handle);
    g_cancel_generation.store(true);
}
