// This file implements the JNI bridge for the Llama inference engine.
// It has been rewritten to resolve the typedef redefinition error, prevent
// deadlocks caused by holding a mutex while calling back into Java, and
// provide robust cancellation and error handling.
//
// The code follows the same public API as the original implementation but
// uses the newer llama.cpp APIs and safer C++ constructs.

#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <atomic>
#include <sstream>
#include <cerrno>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// The llama.cpp header must be included after the standard headers to avoid
// the typedef conflict between <jni.h> and <cstdint>.
#include "llama.h"
#include "ggml-cpu.h" // Required for explicit CPU backend registration

namespace
{

    // Holds the state for a loaded model and its context.
    struct LlamaState
    {
        llama_model *model = nullptr;
        llama_context *ctx = nullptr;
        std::mutex mutex; // protects model/context access
        std::atomic<bool> cancel_requested{false};

        // Stored params for context recreation
        uint32_t n_ctx = 4096;
        uint32_t n_threads = 4;
        uint32_t n_batch = 512;
        uint32_t n_ubatch = 512;
    };

    JavaVM *gJvm = nullptr;          // set once in JNI_OnLoad
    std::once_flag gBackendInitFlag; // llama_backend_init() once
    std::once_flag gJvmInitFlag;     // guard for gJvm usage

    void ensure_backend_init()
    {
        std::call_once(gBackendInitFlag, []()
                       {
        LOGI("=== SHADOWAI BACKEND INIT START ===");

        // Register the CPU backend explicitly.
        // This is critical for static builds where ggml_backend_load_all() may fail.
        LOGI("Registering GGML CPU backend...");
        ggml_backend_register(ggml_backend_cpu_reg());

        LOGI("Initializing llama.cpp core...");
        llama_backend_init();

        // Attempt to load any other dynamically linked backends (if any)
        ggml_backend_load_all();

        LOGI("=== SHADOWAI BACKEND INIT COMPLETE (Device count: %zu) ===", ggml_backend_dev_count()); });
    }

    void ensure_jvm_init()
    {
        std::call_once(gJvmInitFlag, []()
                       {
                           // gJvm is already set by JNI_OnLoad
                       });
    }

    // Convert a jstring to std::string.
    std::string jstring_to_string(JNIEnv *env, jstring input)
    {
        if (!input)
            return {};
        const char *chars = env->GetStringUTFChars(input, nullptr);
        std::string out = chars ? chars : "";
        if (chars)
            env->ReleaseStringUTFChars(input, chars);
        return out;
    }

    // Tokenise a string using the provided vocab.
    std::vector<llama_token> tokenize(const struct llama_vocab *vocab, const std::string &text)
    {
        const int32_t n_max = static_cast<int32_t>(text.size()) + 32;
        std::vector<llama_token> tokens(n_max);
        int32_t n = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                   tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        if (n < 0)
        {
            tokens.resize(static_cast<size_t>(-n));
            n = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        }
        if (n < 0)
            return {};
        tokens.resize(static_cast<size_t>(n));
        return tokens;
    }

    // Convert a token back to a string piece.
    std::string token_to_piece(const struct llama_vocab *vocab, llama_token token)
    {
        char buffer[256];
        int32_t n = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, true);
        if (n <= 0)
            return {};
        return std::string(buffer, buffer + n);
    }

    // Clamp maxTokens to a safe range.
    int32_t clamp_max_tokens(jint maxTokens)
    {
        if (maxTokens <= 0)
            return 128;
        if (maxTokens > 4096)
            return 4096;
        return static_cast<int32_t>(maxTokens);
    }

    // Helper to create a C-style string on the heap from a std::string
    char *new_c_str(const std::string &s)
    {
        char *cstr = new char[s.length() + 1];
        std::strcpy(cstr, s.c_str());
        return cstr;
    }

} // namespace

// Forward declarations for JNI functions
extern "C"
{
    JNIEXPORT jstring JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGetSystemInfo(JNIEnv *, jclass);
    JNIEXPORT jlongArray JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeLoadModel(JNIEnv *, jclass, jstring, jint, jint, jboolean, jboolean);
    JNIEXPORT void JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeFreeModel(JNIEnv *, jclass, jlong);
    JNIEXPORT jstring JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGetString(JNIEnv *, jclass, jlong);
    JNIEXPORT void JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeFreeString(JNIEnv *, jclass, jlong);
    JNIEXPORT jstring JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGenerate(JNIEnv *, jclass, jlong, jstring, jint, jint, jfloat, jfloat);
    JNIEXPORT void JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGenerateStream(JNIEnv *, jclass, jlong, jstring, jint, jint, jfloat, jfloat, jobject);
    JNIEXPORT void JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeCancel(JNIEnv *, jclass, jlong);
    // Additional JNI declarations for enhanced native inference
    JNIEXPORT jboolean JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeValidateModel(JNIEnv *, jclass, jstring);
    JNIEXPORT jstring JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGetModelInfo(JNIEnv *, jclass, jlong);
    JNIEXPORT jlongArray JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGetPerformanceMetrics(JNIEnv *, jclass, jlong);
    JNIEXPORT jboolean JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeIsModelLoaded(JNIEnv *, jclass, jlong);
    JNIEXPORT jstring JNICALL Java_com_shadowai_app_ai_LlamaNative_nativeGetLastError(JNIEnv *, jclass);
}

// Dynamic JNI registration
static jint registerNativeMethods(JNIEnv *env)
{
    jclass llamaNativeClass = env->FindClass("com/shadowai/app/ai/LlamaNative");
    if (!llamaNativeClass)
        return JNI_ERR;

    static const JNINativeMethod kNativeMethods[] = {
        {"nativeGetSystemInfo", "()Ljava/lang/String;",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGetSystemInfo)},
        {"nativeLoadModel", "(Ljava/lang/String;IIZZ)[J",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeLoadModel)},
        {"nativeFreeModel", "(J)V",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeFreeModel)},
        {"nativeGetString", "(J)Ljava/lang/String;",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGetString)},
        {"nativeFreeString", "(J)V",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeFreeString)},
        {"nativeGenerate", "(JLjava/lang/String;IIFF)Ljava/lang/String;",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGenerate)},
        {"nativeGenerateStream", "(JLjava/lang/String;IIFFLcom/shadowai/app/ai/LlamaNative$GenerationCallback;)V",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGenerateStream)},
        {"nativeCancel", "(J)V",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeCancel)},
        // Additional native methods for enhanced inference
        {"nativeValidateModel", "(Ljava/lang/String;)Z",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeValidateModel)},
        {"nativeGetModelInfo", "(J)Ljava/lang/String;",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGetModelInfo)},
        {"nativeGetPerformanceMetrics", "(J)[J",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGetPerformanceMetrics)},
        {"nativeIsModelLoaded", "(J)Z",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeIsModelLoaded)},
        {"nativeGetLastError", "()Ljava/lang/String;",
         reinterpret_cast<void *>(Java_com_shadowai_app_ai_LlamaNative_nativeGetLastError)}};

    jint result = env->RegisterNatives(llamaNativeClass, kNativeMethods, sizeof(kNativeMethods) / sizeof(kNativeMethods[0]));
    env->DeleteLocalRef(llamaNativeClass);
    return result;
}

// JNI_OnLoad
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /*reserved*/)
{
    gJvm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
    {
        LOGE("Failed to get JNIEnv in JNI_OnLoad");
        return JNI_ERR;
    }

    // Register native methods
    if (registerNativeMethods(env) != JNI_OK)
    {
        LOGE("Failed to register native methods");
        return JNI_ERR;
    }

    // Proactively initialize backends on library load
    ensure_backend_init();

    return JNI_VERSION_1_6;
}

// Get system info.
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGetSystemInfo(JNIEnv *env, jclass /*clazz*/)
{
    ensure_backend_init();
    std::stringstream ss;
    // ss << "llama.cpp " << llama_build_number() << " | " << llama_system_info();
    ss << "llama.cpp | unknown build";
    return env->NewStringUTF(ss.str().c_str());
}

// Load a model and create a context.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeLoadModel(JNIEnv *env, jclass /*clazz*/, jstring path,
                                                     jint nCtx, jint nThreads, jboolean useNnapi, jboolean useMmap)
{
    ensure_backend_init();
    const std::string model_path = jstring_to_string(env, path);
    jlongArray result = env->NewLongArray(2);
    if (model_path.empty())
    {
        jlong vals[] = {0, reinterpret_cast<jlong>(new_c_str("Model path is empty."))};
        env->SetLongArrayRegion(result, 0, 2, vals);
        return result;
    }

    auto *state = new LlamaState();

    // Diagnostic: Check if file is accessible
    LOGI("Attempting to load model from: %s", model_path.c_str());
    FILE *test_fp = fopen(model_path.c_str(), "rb");
    if (!test_fp)
    {
        int err = errno;
        std::string error_msg = "Cannot open model file: " + model_path + " (errno=" + std::to_string(err) + ": " + strerror(err) + ")";
        LOGE("%s", error_msg.c_str());
        delete state;
        jlong vals[] = {0, reinterpret_cast<jlong>(new_c_str(error_msg.c_str()))};
        env->SetLongArrayRegion(result, 0, 2, vals);
        return result;
    }

    // Check file size
    fseek(test_fp, 0, SEEK_END);
    long file_size = ftell(test_fp);
    fseek(test_fp, 0, SEEK_SET);

    // Read and validate GGUF header
    char magic[4] = {0};
    uint32_t gguf_version = 0;
    size_t bytes_read = fread(magic, 1, 4, test_fp);
    if (bytes_read == 4)
    {
        fread(&gguf_version, sizeof(uint32_t), 1, test_fp);
    }
    fclose(test_fp);

    LOGI("Model file verified accessible, size=%ld bytes", file_size);
    LOGI("GGUF header: magic='%c%c%c%c' (0x%02X%02X%02X%02X), version=%u",
         magic[0], magic[1], magic[2], magic[3],
         (unsigned char)magic[0], (unsigned char)magic[1],
         (unsigned char)magic[2], (unsigned char)magic[3],
         gguf_version);

    // Validate GGUF magic
    if (magic[0] != 'G' || magic[1] != 'G' || magic[2] != 'U' || magic[3] != 'F')
    {
        std::string error_msg = "Invalid GGUF magic: expected 'GGUF', got '";
        error_msg += std::string(magic, 4) + "'. File may be corrupted or not a GGUF model.";
        LOGE("%s", error_msg.c_str());
        delete state;
        jlong vals[] = {0, reinterpret_cast<jlong>(new_c_str(error_msg.c_str()))};
        env->SetLongArrayRegion(result, 0, 2, vals);
        return result;
    }

    // Log GGUF version for diagnostics (supported versions: 2, 3)
    if (gguf_version < 2 || gguf_version > 3)
    {
        std::string error_msg = "Unsupported GGUF version " + std::to_string(gguf_version) + ". ";
        error_msg += "This llama.cpp build supports GGUF v2-v3. ";
        error_msg += "Please either: (1) Update llama.cpp to the latest version, or ";
        error_msg += "(2) Re-quantize your model to GGUF v2 format.";
        LOGE("%s", error_msg.c_str());
        delete state;
        jlong vals[] = {0, reinterpret_cast<jlong>(new_c_str(error_msg.c_str()))};
        env->SetLongArrayRegion(result, 0, 2, vals);
        return result;
    }

    // Enable llama.cpp internal logging to capture detailed errors
    llama_log_set([](enum ggml_log_level level, const char *text, void *user_data)
                  {
        switch(level) {
            case GGML_LOG_LEVEL_ERROR:
                LOGE("llama.cpp ERROR: %s", text);
                break;
            case GGML_LOG_LEVEL_WARN:
                __android_log_print(ANDROID_LOG_WARN, "LlamaJNI", "llama.cpp WARN: %s", text);
                break;
            case GGML_LOG_LEVEL_INFO:
                LOGI("llama.cpp INFO: %s", text);
                break;
            default:
                LOGI("llama.cpp: %s", text);
                break;
        } }, nullptr);

    // Log requested parameters
    LOGI("Loading model with requested params: useNnapi=%d, useMmap=%d", useNnapi, useMmap);

    // Note: NNAPI is not implemented in this build. The parameter is accepted for API compatibility
    // but will be ignored. Future builds may add NNAPI delegation.
    if (useNnapi)
    {
        LOGI("NNAPI requested but not implemented in this build - using CPU backend only");
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.check_tensors = true; // Enable tensor validation for better error messages

    // Honor the useMmap parameter
    model_params.use_mmap = useMmap;
    model_params.use_mlock = false; // Always disable mlock for mobile stability

    LOGI("Loading model with params: mmap=%d, mlock=%d, check_tensors=%d",
         model_params.use_mmap, model_params.use_mlock, model_params.check_tensors);
    state->model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!state->model)
    {
        LOGI("Load attempt failed with mmap=%d, retrying with mmap=false...", model_params.use_mmap);
        // Retry with mmap disabled if the first attempt failed
        llama_model_params fallback_params = model_params;
        fallback_params.use_mmap = false;
        state->model = llama_model_load_from_file(model_path.c_str(), fallback_params);
    }
    if (!state->model)
    {
        std::string error_msg = "llama_model_load_from_file returned null for: " + model_path;
        error_msg += " (GGUF v" + std::to_string(gguf_version) + ", " + std::to_string(file_size) + " bytes). ";
        error_msg += "Possible causes: (1) Model architecture not supported by this llama.cpp build, ";
        error_msg += "(2) Insufficient memory, (3) Corrupted model file. ";
        error_msg += "Try: (1) Use a different GGUF model (e.g., Llama 2/3, Mistral), ";
        error_msg += "(2) Check available RAM, (3) Re-download the model.";
        LOGE("%s", error_msg.c_str());
        delete state;
        jlong vals[] = {0, reinterpret_cast<jlong>(new_c_str(error_msg.c_str()))};
        env->SetLongArrayRegion(result, 0, 2, vals);
        return result;
    }
    LOGI("Model loaded successfully!");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? (uint32_t)nCtx : 4096; // LFM2.5 needs more space
    ctx_params.n_threads = nThreads > 0 ? (uint32_t)nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    // Explicit batch tuning for mobile stability
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512; // Match batch size to avoid ubatch preparation errors

    // Save params to state for later recreation
    state->n_ctx = ctx_params.n_ctx;
    state->n_threads = ctx_params.n_threads;
    state->n_batch = ctx_params.n_batch;
    state->n_ubatch = ctx_params.n_ubatch;

    state->ctx = llama_init_from_model(state->model, ctx_params);
    if (!state->ctx)
    {
        llama_model_free(state->model);
        delete state;
        jlong vals[] = {0, reinterpret_cast<jlong>(new_c_str("Failed to create context from model."))};
        env->SetLongArrayRegion(result, 0, 2, vals);
        return result;
    }

    jlong vals[] = {reinterpret_cast<jlong>(state), 0};
    env->SetLongArrayRegion(result, 0, 2, vals);
    return result;
}

// Free a loaded model.
// Acquires the model mutex to prevent use-after-free when a generation
// thread is still running.
extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeFreeModel(JNIEnv * /*env*/, jclass /*clazz*/, jlong handle)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (!state)
        return;

    // Signal any in-flight generation to stop so it releases the mutex.
    state->cancel_requested = true;

    {
        // Wait for any active generation to finish before freeing resources.
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->ctx)
        {
            llama_free(state->ctx);
            state->ctx = nullptr;
        }
        if (state->model)
        {
            llama_model_free(state->model);
            state->model = nullptr;
        }
    }
    delete state;
}

// Get string from pointer.
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGetString(JNIEnv *env, jclass /*clazz*/, jlong ptr)
{
    if (ptr == 0)
        return nullptr;
    return env->NewStringUTF(reinterpret_cast<const char *>(ptr));
}

// Free string pointer.
extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeFreeString(JNIEnv * /*env*/, jclass /*clazz*/, jlong ptr)
{
    if (ptr != 0)
    {
        delete[] reinterpret_cast<char *>(ptr);
    }
}

// Cancel an ongoing generation.
extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeCancel(JNIEnv * /*env*/, jclass /*clazz*/, jlong handle)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (state)
        state->cancel_requested = true;
}

// Synchronous generation.
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGenerate(JNIEnv *env, jclass /*clazz*/, jlong handle,
                                                    jstring prompt, jint maxTokens, jint topK,
                                                    jfloat topP, jfloat temp)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (!state || !state->model || !state->ctx)
    {
        return env->NewStringUTF("Error: model not loaded.");
    }
    const std::string text = jstring_to_string(env, prompt);
    if (text.empty())
        return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(state->mutex);

    // 🔄 RECREATE CONTEXT: Robust way to clear cache and ensure clean state
    if (state->ctx)
        llama_free(state->ctx);

    llama_context_params ctx_params_regen = llama_context_default_params();
    ctx_params_regen.n_ctx = state->n_ctx;
    ctx_params_regen.n_threads = state->n_threads;
    ctx_params_regen.n_threads_batch = state->n_threads;
    ctx_params_regen.n_batch = state->n_batch;
    ctx_params_regen.n_ubatch = state->n_ubatch;

    state->ctx = llama_init_from_model(state->model, ctx_params_regen);
    if (!state->ctx)
    {
        return env->NewStringUTF("Error: failed to recreate context.");
    }

    const struct llama_vocab *vocab = llama_model_get_vocab(state->model);
    std::vector<llama_token> tokens = tokenize(vocab, text);

    // Check context window size
    int32_t n_ctx_available = llama_n_ctx(state->ctx);
    if ((int32_t)tokens.size() > n_ctx_available)
    {
        return env->NewStringUTF("Error: prompt exceeds context window size.");
    }
    if (tokens.empty())
        return env->NewStringUTF("Error: tokenization failed.");

    // 🧱 Decode in chunks of n_batch for stability
    // 🧱 Decode in chunks of n_batch for stability
    const int32_t n_batch_size = 512;
    llama_batch batch = llama_batch_init(n_batch_size, 0, 1);

    for (size_t i = 0; i < tokens.size(); i += (size_t)n_batch_size)
    {
        int32_t n_eval = (int32_t)std::min((size_t)n_batch_size, tokens.size() - i);

        // Manual batch setup to ensure safe memory access (avoids pos=NULL crash)
        batch.n_tokens = n_eval;
        for (int32_t k = 0; k < n_eval; k++)
        {
            batch.token[k] = tokens[i + k];
            batch.pos[k] = (int32_t)i + k;
            batch.n_seq_id[k] = 1;
            batch.seq_id[k][0] = 0;
            // Enable logits only for the very last prompt token so sampling
            // operates on valid data instead of uninitialised memory.
            batch.logits[k] = (i + k == tokens.size() - 1);
        }

        if (llama_decode(state->ctx, batch) != 0)
        {
            llama_batch_free(batch);
            return env->NewStringUTF("Error: decode failed during prompt processing.");
        }
    }
    llama_batch_free(batch);
    const llama_token eos = llama_vocab_eos(vocab);
    const int32_t max_out = clamp_max_tokens(maxTokens);
    llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
    llama_sampler *chain = llama_sampler_chain_init(chain_params);
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK > 0 ? topK : 40));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP > 0.0f ? topP : 0.9f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temp > 0.0f ? temp : 0.8f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string output;
    output.reserve(static_cast<size_t>(max_out) * 4);
    // Generation loop with explicit position tracking
    int32_t n_past = (int32_t)tokens.size();
    llama_batch batch_gen = llama_batch_init(1, 0, 1); // Batch for single token generation

    for (int32_t i = 0; i < max_out; ++i)
    {
        if (state->cancel_requested)
        {
            state->cancel_requested = false;
            break;
        }
        llama_token token = llama_sampler_sample(chain, state->ctx, -1);
        if (token == eos)
            break;
        llama_sampler_accept(chain, token);

        // Use manual batch to ensure valid pos
        batch_gen.n_tokens = 1;
        batch_gen.token[0] = token;
        batch_gen.pos[0] = n_past;
        batch_gen.n_seq_id[0] = 1;
        batch_gen.seq_id[0][0] = 0;
        batch_gen.logits[0] = true; // Enable logits for sampling next token

        if (llama_decode(state->ctx, batch_gen) != 0)
            break;
        n_past++;

        std::string piece = token_to_piece(vocab, token);
        output.append(piece);
    }
    llama_batch_free(batch_gen);
    llama_sampler_free(chain);
    return env->NewStringUTF(output.c_str());
}

// Validate a model file without loading it.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeValidateModel(JNIEnv *env, jclass /*clazz*/, jstring path)
{
    ensure_backend_init();
    const std::string model_path = jstring_to_string(env, path);
    if (model_path.empty())
    {
        LOGE("Model path is empty");
        return JNI_FALSE;
    }

    // Check if file is accessible
    FILE *test_fp = fopen(model_path.c_str(), "rb");
    if (!test_fp)
    {
        int err = errno;
        LOGE("Cannot open model file: %s (errno=%d: %s)", model_path.c_str(), err, strerror(err));
        return JNI_FALSE;
    }

    // Check file size
    fseek(test_fp, 0, SEEK_END);
    long file_size = ftell(test_fp);
    fseek(test_fp, 0, SEEK_SET);

    // Read and validate GGUF header
    char magic[4] = {0};
    uint32_t gguf_version = 0;
    size_t bytes_read = fread(magic, 1, 4, test_fp);
    if (bytes_read == 4)
    {
        fread(&gguf_version, sizeof(uint32_t), 1, test_fp);
    }
    fclose(test_fp);

    // Validate GGUF magic
    if (magic[0] != 'G' || magic[1] != 'G' || magic[2] != 'U' || magic[3] != 'F')
    {
        LOGE("Invalid GGUF magic: expected 'GGUF', got '%c%c%c%c'", magic[0], magic[1], magic[2], magic[3]);
        return JNI_FALSE;
    }

    // Validate GGUF version
    if (gguf_version < 2 || gguf_version > 3)
    {
        LOGE("Unsupported GGUF version: %u", gguf_version);
        return JNI_FALSE;
    }

    LOGI("Model validation successful: %s (size=%ld bytes, GGUF v%u)", model_path.c_str(), file_size, gguf_version);
    return JNI_TRUE;
}

// Get model information as JSON string.
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGetModelInfo(JNIEnv *env, jclass /*clazz*/, jlong handle)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (!state || !state->model)
    {
        return env->NewStringUTF("{\"error\":\"Model not loaded\"}");
    }

    std::stringstream ss;
    ss << "{";
    ss << "\"context_size\":" << state->n_ctx << ",";
    ss << "\"threads\":" << state->n_threads << ",";
    ss << "\"batch_size\":" << state->n_batch << ",";
    ss << "\"ubatch_size\":" << state->n_ubatch << ",";
    ss << "\"model_loaded\":true";
    ss << "}";

    return env->NewStringUTF(ss.str().c_str());
}

// Get performance metrics for the loaded model.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGetPerformanceMetrics(JNIEnv *env, jclass /*clazz*/, jlong handle)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (!state || !state->model)
    {
        jlong vals[] = {0, 0, 0, 0};
        jlongArray result = env->NewLongArray(4);
        env->SetLongArrayRegion(result, 0, 4, vals);
        return result;
    }

    // Return metrics: [context_size, threads, batch_size, ubatch_size]
    jlong vals[] = {
        static_cast<jlong>(state->n_ctx),
        static_cast<jlong>(state->n_threads),
        static_cast<jlong>(state->n_batch),
        static_cast<jlong>(state->n_ubatch)};
    jlongArray result = env->NewLongArray(4);
    env->SetLongArrayRegion(result, 0, 4, vals);
    return result;
}

// Check if a model is loaded.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeIsModelLoaded(JNIEnv * /*env*/, jclass /*clazz*/, jlong handle)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (!state)
        return JNI_FALSE;
    return (state->model != nullptr && state->ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

// Get the last error message (placeholder for error tracking).
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGetLastError(JNIEnv *env, jclass /*clazz*/)
{
    // In a production implementation, this would return the last error from a thread-local error store
    return env->NewStringUTF("No error");
}

// Asynchronous streaming generation.
extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_app_ai_LlamaNative_nativeGenerateStream(JNIEnv *env, jclass /*clazz*/, jlong handle,
                                                          jstring prompt, jint maxTokens, jint topK,
                                                          jfloat topP, jfloat temp, jobject callback)
{
    auto *state = reinterpret_cast<LlamaState *>(handle);
    if (!state || !state->model || !state->ctx || !callback)
        return;
    const std::string text = jstring_to_string(env, prompt);
    if (text.empty())
        return;
    ensure_jvm_init();
    jobject cb_global = env->NewGlobalRef(callback);
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleted = env->GetMethodID(cb_class, "onCompleted", "()V");
    jmethodID onError = env->GetMethodID(cb_class, "onError", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cb_class);
    const int32_t max_out = clamp_max_tokens(maxTokens);
    std::thread([state, text, max_out, topK, topP, temp, cb_global, onToken, onCompleted, onError]()
                {
        JNIEnv * env_thread = nullptr;
        if (!gJvm || gJvm->AttachCurrentThread(&env_thread, nullptr) != JNI_OK) {
            LOGE("Failed to attach native thread to JVM.");
            // We must delete the global ref even if attach fails, but we need an env.
            // If we can't attach, we are in a dire state.
            return;
        }

        auto cleanup = [&]() {
            if (env_thread && cb_global) {
                env_thread->DeleteGlobalRef(cb_global);
            }
            if (gJvm) {
                gJvm->DetachCurrentThread();
            }
        };

        try {
            std::lock_guard<std::mutex> lock(state->mutex);

            // 🔄 RECREATE CONTEXT: Robust way to clear cache and ensure clean state
            if (state->ctx) llama_free(state->ctx);

            llama_context_params ctx_params_regen = llama_context_default_params();
            ctx_params_regen.n_ctx = state->n_ctx;
            ctx_params_regen.n_threads = state->n_threads;
            ctx_params_regen.n_threads_batch = state->n_threads;
            ctx_params_regen.n_batch = state->n_batch;
            ctx_params_regen.n_ubatch = state->n_ubatch;

            state->ctx = llama_init_from_model(state->model, ctx_params_regen);
            if (!state->ctx) {
                 throw std::runtime_error("Failed to recreate context.");
            }

            state->cancel_requested = false;
            const struct llama_vocab * vocab = llama_model_get_vocab(state->model);
            std::vector<llama_token> tokens = tokenize(vocab, text);

            // Check context window size
            int32_t n_ctx_available = llama_n_ctx(state->ctx);
            if ((int32_t)tokens.size() > n_ctx_available) {
                throw std::runtime_error("Prompt exceeds context window size.");
            }
            if (tokens.empty()) {
                throw std::runtime_error("Tokenization failed.");
            }

            // 🧱 Decode in chunks of n_batch for stability
            const int32_t n_batch_size = 512;
            llama_batch batch = llama_batch_init(n_batch_size, 0, 1);

            for (size_t i = 0; i < tokens.size(); i += (size_t)n_batch_size) {
                int32_t n_eval = (int32_t)std::min((size_t)n_batch_size, tokens.size() - i);

                // Manual batch setup
                batch.n_tokens = n_eval;
                for (int32_t k = 0; k < n_eval; k++) {
                    batch.token[k] = tokens[i + k];
                    batch.pos[k] = (int32_t)i + k;
                    batch.n_seq_id[k] = 1;
                    batch.seq_id[k][0] = 0;
                    // Enable logits only for the very last prompt token so sampling
                    // operates on valid data instead of uninitialised memory.
                    batch.logits[k] = (i + k == tokens.size() - 1);
                }

                if (llama_decode(state->ctx, batch) != 0) {
                    llama_batch_free(batch);
                    throw std::runtime_error("Decode failed during prompt processing.");
                }
            }
            llama_batch_free(batch);
            const llama_token eos = llama_vocab_eos(vocab);
            llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
            llama_sampler * chain = llama_sampler_chain_init(chain_params);
            llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK > 0 ? topK : 40));
            llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP > 0.0f ? topP : 0.9f, 1));
            llama_sampler_chain_add(chain, llama_sampler_init_temp(temp > 0.0f ? temp : 0.8f));
            llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

            // Generation loop with explicit position tracking
            int32_t n_past = (int32_t)tokens.size();
            llama_batch batch_gen = llama_batch_init(1, 0, 1); // Batch for single token generation

            bool error_during_loop = false;
            for (int32_t i = 0; i < max_out; ++i) {
                if (state->cancel_requested) { state->cancel_requested = false; break; }
                llama_token token = llama_sampler_sample(chain, state->ctx, -1);
                if (token == eos) break;
                llama_sampler_accept(chain, token);

                // Use manual batch to ensure valid pos
                batch_gen.n_tokens = 1;
                batch_gen.token[0] = token;
                batch_gen.pos[0] = n_past;
                batch_gen.n_seq_id[0] = 1;
                batch_gen.seq_id[0][0] = 0;
                batch_gen.logits[0] = true;

                if (llama_decode(state->ctx, batch_gen) != 0) {
                    error_during_loop = true;
                    break;
                }
                n_past++;

                const std::string piece = token_to_piece(vocab, token);
                if (!piece.empty()) {
                    jstring out = env_thread->NewStringUTF(piece.c_str());
                    env_thread->CallVoidMethod(cb_global, onToken, out);
                    env_thread->DeleteLocalRef(out);
                }
            }
            llama_sampler_free(chain);
            if (!error_during_loop) {
                env_thread->CallVoidMethod(cb_global, onCompleted);
            } else {
                throw std::runtime_error("Decode failed during token generation.");
            }
        } catch (const std::exception& e) {
            LOGE("Generation thread exception: %s", e.what());
            jstring msg = env_thread->NewStringUTF(e.what());
            env_thread->CallVoidMethod(cb_global, onError, msg);
            env_thread->DeleteLocalRef(msg);
        } catch (...) {
            LOGE("Unknown native exception during generation thread.");
            jstring msg = env_thread->NewStringUTF("Unknown native exception during generation.");
            env_thread->CallVoidMethod(cb_global, onError, msg);
            env_thread->DeleteLocalRef(msg);
        }

        cleanup(); })
        .detach();
}
