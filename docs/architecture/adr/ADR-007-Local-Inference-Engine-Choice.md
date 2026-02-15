# ADR-007: Local Inference Engine Choice

## Status
Accepted - Implemented

## Context

ShadowAi needed a local LLM inference solution for:
1. Offline capability (no internet required)
2. Data privacy (no data leaves device)
3. Cost reduction (no API fees)
4. Low latency (no network round-trips)

We evaluated several options:
- llama.cpp (C/C++ with JNI)
- TensorFlow Lite (Google)
- ONNX Runtime Mobile (Microsoft)
- MediaPipe LLM Task (Google)
- Custom implementation

## Decision

We will use **llama.cpp via JNI** as our primary local inference engine.

### Rationale

| Criterion | llama.cpp | TFLite | ONNX | MediaPipe |
|-----------|-----------|--------|------|-----------|
| GGUF Support | ✅ Native | ❌ No | ⚠️ Limited | ⚠️ Limited |
| ARM64 Optimization | ✅ NEON/FP16 | ✅ Good | ✅ Good | ✅ Good |
| Memory Mapping | ✅ Yes | ❌ No | ❌ No | ❌ No |
| Quantization Range | ✅ Q2_K-F32 | ⚠️ INT8 | ⚠️ INT8/FP16 | ⚠️ INT8/FP16 |
| Community/Ecosystem | ✅ Massive | Large | Moderate | Growing |
| Kotlin JNI Ease | ⚠️ Moderate | ✅ Easy | ⚠️ Moderate | ✅ Easy |
| Binary Size | ⚠️ Large | Small | Moderate | Small |

**Key deciding factors:**

1. **GGUF Format**: llama.cpp's native GGUF format is the de facto standard for quantized models
2. **Memory Mapping**: Critical for running models larger than device RAM
3. **Broad Quantization Support**: Q2_K through F32 gives users flexibility
4. **Active Development**: Weekly updates, rapid security patches
5. **Community Models**: Hugging Face primarily distributes GGUF models

### Architecture

```
┌─────────────────────────────────────────┐
│  Kotlin Layer (App Process)             │
│  - LocalInferenceManager.kt             │
│  - IsolatedInferenceManager.kt          │
└─────────────────────┬───────────────────┘
                      │ AIDL/Binder
┌─────────────────────▼───────────────────┐
│  JNI Bridge (llama_jni.cpp)             │
│  - Model loading                        │
│  - Token generation                     │
│  - Context management                   │
└─────────────────────┬───────────────────┘
                      │ Native calls
┌─────────────────────▼───────────────────┐
│  llama.cpp Core (C/C++)                 │
│  - GGML compute backend                 │
│  - ARM64 optimizations (NEON/FP16)      │
│  - Metal/NNAPI backends                 │
└─────────────────────────────────────────┘
```

### Isolated Inference Process

For crash and memory isolation, we run inference in a separate process (`:inference_process`):

**Benefits:**
- Native crashes don't bring down main app
- Memory pressure in inference doesn't affect UI
- Can be killed/restarted independently

**Trade-offs:**
- AIDL overhead for cross-process communication
- More complex lifecycle management
- Additional memory for second process

```kotlin
// IsolatedInferenceManager.kt
class IsolatedInferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var service: IInferenceService? = null
    
    fun bind() {
        val intent = Intent(context, InferenceService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
}
```

## Consequences

### Positive
- **Best-in-class performance**: llama.cpp is the fastest CPU inference for LLMs
- **Model compatibility**: Supports virtually all open-source GGUF models
- **Advanced features**: Speculative decoding, continuous batching, grammar constraints
- **Hardware acceleration**: Metal (iOS), NNAPI/CUDA (via backends)
- **Memory efficiency**: mmap allows models 2-3x device RAM

### Negative
- **Binary size**: ~5-15MB per architecture (ARM64, x86)
- **NDK complexity**: Requires NDK toolchain and CMake
- **JNI overhead**: Java ↔ C++ boundary has cost
- **Debugging difficulty**: Native crashes require ndk-stack
- **Build time**: Native compilation slower than pure Kotlin

## Implementation Details

### Files
- `inference_process/src/main/cpp/llama_jni.cpp` - JNI bridge
- `inference_process/src/main/cpp/CMakeLists.txt` - Build config
- `inference_process/src/main/kotlin/.../InferenceService.kt` - Isolated service
- `app/src/main/java/.../IsolatedInferenceManager.kt` - Process manager
- `app/src/main/java/.../ILlamaEngine.kt` - Abstraction interface

### Supported Models
Any GGUF format model including:
- Llama 2/3 (Meta)
- Mistral (Mistral AI)
- Qwen (Alibaba)
- Phi (Microsoft)
- Gemma (Google)
- And 1000+ more on Hugging Face

### Quantization Support
| Format | Bits | Memory | Quality | Use Case |
|--------|------|--------|---------|----------|
| Q2_K | 2-3 | Ultra-low | Low | Edge devices |
| Q4_0 | 4 | Low | Good | Mobile default |
| Q4_K_M | 4.5 | Low | Very Good | Balanced |
| Q5_K_M | 5.5 | Medium | Excellent | Quality priority |
| Q8_0 | 8 | High | Near FP16 | Quality critical |
| F16 | 16 | Very High | Lossless | Best quality |

### Build Configuration
```kotlin
// build.gradle.kts (app module)
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a") // Target modern devices
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}
```

## Alternatives Considered

### TensorFlow Lite
- **Pros**: Easy Android integration, good tooling
- **Cons**: Limited LLM support, no GGUF, no memory mapping
- **Verdict**: Better for CV tasks than LLMs

### ONNX Runtime Mobile
- **Pros**: Cross-platform, Microsoft backing
- **Cons**: Complex model conversion, limited quantization
- **Verdict**: Good for enterprise, less flexible for mobile

### MediaPipe LLM Task
- **Pros**: Google official, easy API
- **Cons**: Limited model support, restricted to approved models
- **Verdict**: Too restrictive for general LLM use

## Related Decisions
- ADR-001: Provider Adapter Architecture (local is one provider type)
- ADR-008: Memory Management Strategy (mmap, LRU unloading)
- ADR-009: Thread Safety Architecture (inference thread isolation)

## References
- [llama.cpp GitHub](https://github.com/ggerganov/llama.cpp)
- [GGUF Format Specification](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md)
- [Android NDK Guide](https://developer.android.com/ndk/guides)
