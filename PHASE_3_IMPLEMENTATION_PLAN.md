# Phase 3: JNI & Native Bridge - Implementation Plan

## Overview

Phase 3 focuses on creating the JNI (Java Native Interface) layer that bridges Kotlin/Android code with the native llama.cpp C++ library. This is critical for enabling on-device AI inference.

## Objectives

1. Create JNI wrapper for llama.cpp
2. Implement NativeBridge class
3. Add memory management
4. Ensure thread safety
5. Add ARM64 optimizations
6. Implement error handling

## Timeline

- **Target**: Days 5-6 (16 hours)
- **Current**: Day 4
- **Status**: Starting ahead of schedule

## Architecture

```
┌─────────────────────────────────────┐
│   IsolatedInferenceManager.kt      │
│   (Kotlin - App Process)            │
└──────────────┬──────────────────────┘
               │ AIDL IPC
               ▼
┌─────────────────────────────────────┐
│   InferenceService.kt               │
│   (Kotlin - Inference Process)      │
└──────────────┬──────────────────────┘
               │ JNI
               ▼
┌─────────────────────────────────────┐
│   NativeBridge.kt                   │
│   (Kotlin JNI Wrapper)              │
└──────────────┬──────────────────────┘
               │ JNI
               ▼
┌─────────────────────────────────────┐
│   llama_jni.cpp                     │
│   (C++ JNI Implementation)          │
└──────────────┬──────────────────────┘
               │ Native Calls
               ▼
┌─────────────────────────────────────┐
│   llama.cpp Library                 │
│   (C++ Inference Engine)            │
└─────────────────────────────────────┘
```

## Components to Implement

### 1. NativeBridge.kt (Kotlin Side)

**Location**: `inference_process/src/main/kotlin/com/shadowai/inference/NativeBridge.kt`

**Responsibilities**:
- Load native library
- Declare native methods
- Provide Kotlin-friendly API
- Handle JNI exceptions
- Manage native handles

**Key Methods**:
```kotlin
class NativeBridge {
    companion object {
        init {
            System.loadLibrary("llama-jni")
        }
    }
    
    // Model management
    external fun loadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        gpuLayers: Int,
        useMmap: Boolean,
        useMlock: Boolean
    ): Long  // Returns native handle
    
    external fun unloadModel(handle: Long): Boolean
    
    // Generation
    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): String
    
    external fun generateStreaming(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: (String) -> Unit
    ): Boolean
    
    external fun cancelGeneration(handle: Long): Boolean
    
    // Model info
    external fun getModelInfo(handle: Long): ModelInfo
    external fun getMemoryUsage(handle: Long): Long
    
    // System info
    external fun getSystemInfo(): SystemInfo
}
```

### 2. llama_jni.cpp (C++ Side)

**Location**: `inference_process/src/main/cpp/llama_jni.cpp`

**Responsibilities**:
- Implement JNI methods
- Interface with llama.cpp
- Memory management
- Thread safety
- Error handling

**Key Functions**:
```cpp
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_NativeBridge_loadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint contextSize,
    jint threads,
    jint gpuLayers,
    jboolean useMmap,
    jboolean useMlock
);

JNIEXPORT jboolean JNICALL
Java_com_shadowai_inference_NativeBridge_unloadModel(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
);

JNIEXPORT jstring JNICALL
Java_com_shadowai_inference_NativeBridge_generate(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jfloat repeatPenalty
);

// ... other methods

}
```

### 3. CMakeLists.txt Updates

**Location**: `inference_process/src/main/cpp/CMakeLists.txt`

**Updates Needed**:
- Add llama_jni.cpp to sources
- Link llama.cpp library
- Configure ARM64 optimizations
- Set compiler flags

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("llama-jni")

# Add llama.cpp
add_subdirectory(llama.cpp)

# JNI library
add_library(llama-jni SHARED
    llama_jni.cpp
    jni_utils.cpp
)

target_include_directories(llama-jni PRIVATE
    llama.cpp/include
    llama.cpp/common
)

target_link_libraries(llama-jni
    llama
    common
    log
    android
)

# ARM64 optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(llama-jni PRIVATE
        -march=armv8-a
        -mtune=cortex-a76
        -O3
    )
endif()
```

### 4. Data Classes

**ModelInfo.kt**:
```kotlin
data class ModelInfo(
    val vocabSize: Int,
    val contextSize: Int,
    val embeddingSize: Int,
    val layerCount: Int,
    val headCount: Int,
    val kvHeadCount: Int,
    val modelType: String
)
```

**SystemInfo.kt**:
```kotlin
data class SystemInfo(
    val cpuCores: Int,
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val hasNeon: Boolean,
    val hasFp16: Boolean,
    val hasDotProd: Boolean
)
```

## Implementation Steps

### Step 1: Create NativeBridge.kt (2 hours)
- [ ] Create class structure
- [ ] Declare external methods
- [ ] Add library loading
- [ ] Create data classes
- [ ] Add error handling

### Step 2: Create llama_jni.cpp (4 hours)
- [ ] Set up JNI boilerplate
- [ ] Implement loadModel
- [ ] Implement unloadModel
- [ ] Implement generate
- [ ] Implement generateStreaming
- [ ] Add error handling
- [ ] Add logging

### Step 3: Memory Management (2 hours)
- [ ] Create handle registry
- [ ] Implement reference counting
- [ ] Add cleanup on unload
- [ ] Prevent memory leaks

### Step 4: Thread Safety (2 hours)
- [ ] Add mutexes for model access
- [ ] Ensure thread-safe generation
- [ ] Handle concurrent requests
- [ ] Add cancellation support

### Step 5: Update CMakeLists.txt (1 hour)
- [ ] Add llama_jni.cpp
- [ ] Configure linking
- [ ] Add ARM64 flags
- [ ] Test build

### Step 6: Integration with InferenceService (2 hours)
- [ ] Update InferenceService to use NativeBridge
- [ ] Replace stub implementations
- [ ] Add proper error handling
- [ ] Test end-to-end

### Step 7: Testing & Optimization (3 hours)
- [ ] Unit tests for JNI layer
- [ ] Integration tests
- [ ] Memory leak testing
- [ ] Performance profiling
- [ ] ARM64 optimization verification

## Memory Management Strategy

### Handle Registry
```cpp
class ModelRegistry {
private:
    std::mutex mutex_;
    std::unordered_map<jlong, std::unique_ptr<llama_context>> contexts_;
    std::atomic<jlong> next_handle_{1};

public:
    jlong registerModel(std::unique_ptr<llama_context> ctx) {
        std::lock_guard<std::mutex> lock(mutex_);
        jlong handle = next_handle_++;
        contexts_[handle] = std::move(ctx);
        return handle;
    }
    
    llama_context* getModel(jlong handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = contexts_.find(handle);
        return it != contexts_.end() ? it->second.get() : nullptr;
    }
    
    bool unregisterModel(jlong handle) {
        std::lock_guard<std::mutex> lock(mutex_);
        return contexts_.erase(handle) > 0;
    }
};
```

## Error Handling Strategy

### JNI Exception Handling
```cpp
void throwJniException(JNIEnv* env, const char* message) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(exClass, message);
}

jlong safeLoadModel(JNIEnv* env, const char* path, ...) {
    try {
        // Load model
        return handle;
    } catch (const std::exception& e) {
        throwJniException(env, e.what());
        return 0;
    }
}
```

## Thread Safety Strategy

### Generation Mutex
```cpp
class ThreadSafeModel {
private:
    llama_context* ctx_;
    std::mutex generation_mutex_;
    std::atomic<bool> is_generating_{false};

public:
    std::string generate(const std::string& prompt, ...) {
        std::lock_guard<std::mutex> lock(generation_mutex_);
        
        if (is_generating_) {
            throw std::runtime_error("Generation already in progress");
        }
        
        is_generating_ = true;
        // ... generate
        is_generating_ = false;
        
        return result;
    }
    
    bool cancel() {
        return is_generating_.exchange(false);
    }
};
```

## ARM64 Optimizations

### Compiler Flags
```cmake
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(llama-jni PRIVATE
        -march=armv8-a+fp16+dotprod
        -mtune=cortex-a76
        -O3
        -ffast-math
        -DGGML_USE_NEON
    )
endif()
```

### Runtime Detection
```cpp
bool hasNeonSupport() {
    #ifdef __ARM_NEON
        return true;
    #else
        return false;
    #endif
}

bool hasFp16Support() {
    #ifdef __ARM_FEATURE_FP16_VECTOR_ARITHMETIC
        return true;
    #else
        return false;
    #endif
}
```

## Testing Strategy

### Unit Tests
- JNI method signatures
- Handle management
- Memory cleanup
- Thread safety

### Integration Tests
- End-to-end model loading
- Text generation
- Streaming generation
- Cancellation
- Error handling

### Performance Tests
- Load time
- Generation speed
- Memory usage
- Thread overhead

## Success Criteria

- [ ] All JNI methods implemented
- [ ] No memory leaks
- [ ] Thread-safe operations
- [ ] ARM64 optimizations working
- [ ] Integration tests passing
- [ ] Performance targets met:
  - Model load: < 5 seconds
  - Generation: > 5 tokens/sec
  - Memory: < 2GB for 7B model

## Risks & Mitigations

### Risk 1: JNI Complexity
**Mitigation**: Use helper utilities, thorough testing

### Risk 2: Memory Leaks
**Mitigation**: RAII patterns, smart pointers, leak detection tools

### Risk 3: Thread Safety Issues
**Mitigation**: Proper mutex usage, atomic operations, testing

### Risk 4: ARM64 Compatibility
**Mitigation**: Runtime feature detection, fallback implementations

## Next Steps

1. Create NativeBridge.kt skeleton
2. Create llama_jni.cpp skeleton
3. Implement loadModel
4. Implement generate
5. Add memory management
6. Add thread safety
7. Test and optimize

---

**Ready to start Phase 3!** 🚀
