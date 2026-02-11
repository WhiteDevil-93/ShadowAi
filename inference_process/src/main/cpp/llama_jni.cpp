#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <map>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <memory>
#include <sstream>
#include <algorithm>
#include <cstring>

// llama.cpp includes
#include "llama.h"
#include "ggml.h"

#define LOG_TAG "LlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==================== Internal State ====================

struct LlamaContext {
    struct llama_model* model = nullptr;
    struct llama_context* ctx = nullptr;
    std::string model_path;
    std::mutex mutex;
    std::atomic<bool> cancel_requested{false};

    LlamaContext() = default;
    ~LlamaContext() {
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

class ModelRegistry {
private:
    std::mutex mutex_;
    std::map<jlong, std::unique_ptr<LlamaContext>> contexts_;
    std::atomic<jlong> next_handle_{1};

public:
    jlong registerModel(std::unique_ptr<LlamaContext> ctx) {
        std::lock_guard<std::mutex> lock(mutex_);
        jlong handle = next_handle_++;
        contexts_[handle] = std::move(ctx);
        return handle;
    }

    LlamaContext* getModel(jlong handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = contexts_.find(handle);
        return (it != contexts_.end()) ? it->second.get() : nullptr;
    }

    bool unregisterModel(jlong handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        return contexts_.erase(handle) > 0;
    }
};

static ModelRegistry g_model_registry;
static JavaVM* g_vm = nullptr;

// ==================== JNI Helpers ====================

JNIEnv* getJNIEnv() {
    JNIEnv* env;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    }
    return env;
}

std::string jstring_to_str(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return str;
}

// ==================== Llama Helpers ====================

static void llama_log_callback(ggml_log_level level, const char* text, void* user_data) {
    if (level == GGML_LOG_LEVEL_ERROR) LOGE("llama.cpp: %s", text);
    else LOGI("llama.cpp: %s", text);
}

static void llama_batch_clear(llama_batch& batch) {
    batch.n_tokens = 0;
}

static void llama_batch_add(
    llama_batch& batch,
    llama_token token,
    llama_pos pos,
    std::initializer_list<llama_seq_id> seq_ids,
    bool logits
) {
    const int index = batch.n_tokens++;
    batch.token[index] = token;
    batch.pos[index] = pos;
    batch.n_seq_id[index] = static_cast<int32_t>(seq_ids.size());
    int32_t seqIndex = 0;
    for (llama_seq_id seqId : seq_ids) {
        batch.seq_id[index][seqIndex++] = seqId;
    }
    batch.logits[index] = logits;
}

// ==================== JNI Lifecycle ====================

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    llama_backend_init();
    llama_log_set(llama_log_callback, nullptr);
    LOGI("JNI_OnLoad: llama.cpp backend initialized");
    return JNI_VERSION_1_6;
}

// ==================== Native Implementations ====================

extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_NativeBridge_nativeLoadModel(
    JNIEnv* env, jobject thiz, jstring modelPath,
    jint contextSize, jint threads, jint gpuLayers,
    jboolean useMmap, jboolean useMlock
) {
    std::string path = jstring_to_str(env, modelPath);
    LOGI("Loading model: %s", path.c_str());

    auto state = std::make_unique<LlamaContext>();
    state->model_path = path;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpuLayers;
    mparams.use_mmap = useMmap;
    mparams.use_mlock = useMlock;

    state->model = llama_model_load_from_file(path.c_str(), mparams);
    if (!state->model) {
        LOGE("Failed to load model file");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = contextSize > 0 ? contextSize : 2048;
    cparams.n_threads = threads > 0 ? threads : std::thread::hardware_concurrency();
    cparams.n_threads_batch = cparams.n_threads;

    state->ctx = llama_init_from_model(state->model, cparams);
    if (!state->ctx) {
        LOGE("Failed to create llama context");
        return 0;
    }

    jlong handle = g_model_registry.registerModel(std::move(state));
    LOGI("Model loaded successfully, handle: %lld", handle);
    return handle;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_shadowai_inference_NativeBridge_nativeFreeModel(JNIEnv* env, jobject thiz, jlong handle) {
    return g_model_registry.unregisterModel(handle) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGenerate(
    JNIEnv* env, jobject thiz, jlong handle, jstring prompt,
    jint maxTokens, jfloat temp, jfloat topP, jint topK, jfloat repeatPenalty
) {
    LlamaContext* state = g_model_registry.getModel(handle);
    if (!state) return nullptr;

    std::string prompt_str = jstring_to_str(env, prompt);
    std::lock_guard<std::mutex> lock(state->mutex);

    const llama_vocab* vocab = llama_model_get_vocab(state->model);
    std::vector<llama_token> tokens(prompt_str.size() + 32);
    int n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) return nullptr;
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(512, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, i == n_tokens - 1);
    }

    if (llama_decode(state->ctx, batch) != 0) {
        llama_batch_free(batch);
        return nullptr;
    }

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    int n_cur = n_tokens;
    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(sampler, state->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char buf[128];
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        llama_batch_clear(batch);
        llama_batch_add(batch, token, n_cur++, {0}, true);
        if (llama_decode(state->ctx, batch) != 0) break;
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGenerateStream(
    JNIEnv* env, jobject thiz, jlong handle, jstring prompt,
    jint maxTokens, jfloat temp, jfloat topP, jint topK, jfloat repeatPenalty, jobject callback
) {
    LlamaContext* state = g_model_registry.getModel(handle);
    if (!state) return JNI_FALSE;

    std::string prompt_str = jstring_to_str(env, prompt);
    jobject cb_ref = env->NewGlobalRef(callback);

    std::thread([handle, prompt_str, maxTokens, temp, topP, topK, cb_ref]() {
        JNIEnv* env = getJNIEnv();
        LlamaContext* state = g_model_registry.getModel(handle);
        if (!state) {
            env->DeleteGlobalRef(cb_ref);
            return;
        }

        jclass cb_class = env->GetObjectClass(cb_ref);
        jmethodID onToken = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
        jmethodID onComplete = env->GetMethodID(cb_class, "onCompleted", "()V");
        jmethodID onError = env->GetMethodID(cb_class, "onError", "(Ljava/lang/String;)V");

        std::lock_guard<std::mutex> lock(state->mutex);
        state->cancel_requested = false;

        const llama_vocab* vocab = llama_model_get_vocab(state->model);
        std::vector<llama_token> tokens(prompt_str.size() + 32);
        int n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(), tokens.data(), tokens.size(), true, true);
        
        llama_batch batch = llama_batch_init(512, 0, 1);
        for (int i = 0; i < n_tokens; i++) {
            llama_batch_add(batch, tokens[i], i, {0}, i == n_tokens - 1);
        }

        if (llama_decode(state->ctx, batch) != 0) {
            env->CallVoidMethod(cb_ref, onError, env->NewStringUTF("Prompt decode failed"));
            goto cleanup;
        }

        {
            llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(temp));
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

            int n_cur = n_tokens;
            for (int i = 0; i < maxTokens; i++) {
                if (state->cancel_requested) break;

                llama_token token = llama_sampler_sample(sampler, state->ctx, -1);
                if (llama_vocab_is_eog(vocab, token)) break;

                char buf[128];
                int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
                if (n > 0) {
                    jstring tstr = env->NewStringUTF(std::string(buf, n).c_str());
                    env->CallVoidMethod(cb_ref, onToken, tstr);
                    env->DeleteLocalRef(tstr);
                }

                llama_batch_clear(batch);
                llama_batch_add(batch, token, n_cur++, {0}, true);
                if (llama_decode(state->ctx, batch) != 0) break;
            }
            llama_sampler_free(sampler);
        }

        if (!state->cancel_requested) env->CallVoidMethod(cb_ref, onComplete);

    cleanup:
        llama_batch_free(batch);
        env->DeleteGlobalRef(cb_ref);
        g_vm->DetachCurrentThread();
    }).detach();

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_shadowai_inference_NativeBridge_nativeCancel(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaContext* state = g_model_registry.getModel(handle);
    if (state) state->cancel_requested = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGetModelInfo(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaContext* state = g_model_registry.getModel(handle);
    if (!state) return nullptr;

    jclass cls = env->FindClass("com/shadowai/inference/NativeBridge$ModelInfo");
    jmethodID constr = env->GetMethodID(cls, "<init>", "(IIIIIILjava/lang/String;)V");

    return env->NewObject(cls, constr,
        llama_vocab_n_tokens(llama_model_get_vocab(state->model)),
        llama_n_ctx(state->ctx),
        llama_model_n_embd(state->model),
        llama_model_n_layer(state->model),
        0, 0, // heads placeholder
        env->NewStringUTF("GGUF")
    );
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGetMemoryUsage(JNIEnv* env, jobject thiz, jlong handle) {
    // Placeholder
    return 1024 * 1024 * 512;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_shadowai_inference_NativeBridge_nativeGetSystemInfo(JNIEnv* env, jobject thiz) {
    jclass cls = env->FindClass("com/shadowai/inference/NativeBridge$SystemInfo");
    jmethodID constr = env->GetMethodID(cls, "<init>", "(IJJZZZ)V");

    return env->NewObject(cls, constr,
        (int)std::thread::hardware_concurrency(),
        (jlong)0, (jlong)0, // memory placeholder
        (jboolean)true, (jboolean)true, (jboolean)true // ARM features placeholder
    );
}
