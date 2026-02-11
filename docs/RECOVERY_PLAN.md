# ShadowAi Recovery Plan — Critical Issues

**Document Version:** 1.0  
**Date:** 2026-02-10  
**Status:** 🔴 CRITICAL — DO NOT BUILD UNTIL COMPLETED

---

## Executive Summary

The codebase has **architectural failures** that will prevent compilation and runtime operation. This plan prioritizes fixes by dependency order — lower layers must be fixed before upper layers can work.

**Estimated Effort:** 40-60 hours  
**Recommended Team:** 2 Android engineers (1 JNI/native specialist, 1 Hilt/architecture)

---

## Phase 1: Foundation Layer — Hilt & DI (Days 1-3)

### 1.1 Fix Hilt Module Constructor Mismatches
**Files:** `di/AgentModule.kt`, `di/SecurityModule.kt`, `di/InferenceModule.kt`

| Current (Broken) | Required Fix |
|------------------|--------------|
| AgentModule provides `SupervisorAgent(gson, dispatcher...)` | Match actual constructor: `Context`, `ShadowAgent`, `TaskExecutor`, etc. |
| AgentModule provides `AgenticLoop(supervisorAgent, dispatcher, shadowAgent)` | Match actual constructor: `taskId`, `taskExecutor`, `routingEngine`, etc. |

**Actions:**
- [ ] Read actual constructor signatures for all 3 agents
- [ ] Rewrite AgentModule with correct parameter mappings
- [ ] Verify `@Provides` methods match class `@Inject` constructors exactly

### 1.2 Break Circular Dependency
**Problem:** ShadowAgent injects `SupervisorAgent?`, which creates AgenticLoop, which needs ShadowAgent.

**Solution:** Introduce factory pattern
```kotlin
// Create: di/AgentFactory.kt
@Singleton
class AgentFactory @Inject constructor(
    private val dependencies: AgentDependencies // DTO with shared deps
) {
    fun createShadowAgent(): ShadowAgent { ... }
    fun createSupervisorAgent(shadowAgent: ShadowAgent): SupervisorAgent { ... }
}
```

**Actions:**
- [ ] Create `AgentDependencies` data class for shared dependencies
- [ ] Create `AgentFactory` with lazy instantiation
- [ ] Update ShadowAgent to use `Provider<SupervisorAgent>` instead of direct injection
- [ ] Remove `@Inject` from agent constructors, use factory-only instantiation

### 1.3 Fix Import Resolution
**Problem:** Missing classes referenced in SupervisorAgent.kt

**Missing Imports to Verify/Create:**
- `VerificationEngine` — check if exists, create stub if not
- `PipelinePlanner` — verify package location
- `PipelineExecutor` — verify package location  
- `DeviceActionExecutor` — verify package location

**Actions:**
- [ ] Search codebase for these classes
- [ ] Create stub implementations if missing: `class X @Inject constructor() { }`
- [ ] Fix package imports in SupervisorAgent

### 1.4 Fix ProviderRepository Type Mismatch
**Problem:** `AgentModule.kt` uses `com.shadowai.provideradapters.ProviderRepository` but actual interface is `com.shadowai.core_contracts.providers.ProviderRepository`

**Actions:**
- [ ] Update all imports to use `core_contracts` version
- [ ] Verify ProviderRepositoryImpl properly implements the interface
- [ ] Fix any method signature mismatches between Impl and interface

---

## Phase 2: AIDL & IPC Layer (Days 4-6)

### 2.1 Generate Real AIDL Interfaces
**Problem:** `IsolatedInferenceManager.kt` has stub AIDL interfaces that won't compile.

**Actions:**
- [ ] Create file: `app/src/main/aidl/com/shadowai/app/ai/IInferenceService.aidl`
```aidl
package com.shadowai.app.ai;

interface IInferenceService {
    Bundle loadModel(in Bundle request);
    void unloadModel(String modelId);
    Bundle generate(in Bundle request);
    void generateStream(in Bundle request, IGenerationCallback callback);
    Bundle getServiceInfo();
    Bundle getMemoryStats();
    void cancelGeneration(String modelId);
}
```
- [ ] Create file: `app/src/main/aidl/com/shadowai/app/ai/IGenerationCallback.aidl`
```aidl
package com.shadowai.app.ai;

interface IGenerationCallback {
    void onToken(String token);
    void onComplete(String fullText);
    void onError(String error);
}
```
- [ ] Add aidl section to `:app/build.gradle.kts`:
```kotlin
android {
    aidlPackagedNames += ["com.shadowai.app.ai"]
}
```
- [ ] Delete stub interfaces from `IsolatedInferenceManager.kt`
- [ ] Use generated `IInferenceService.Stub.asInterface(binder)` instead of custom implementation

### 2.2 Fix Bundle Serialization
**Problem:** Using raw strings for Bundle keys is error-prone.

**Actions:**
- [ ] Create `InferenceServiceContracts.kt`:
```kotlin
object InferenceContracts {
    const val KEY_MODEL_PATH = "modelPath"
    const val KEY_MODEL_ID = "modelId"
    const val KEY_SUCCESS = "success"
    // ... all other keys
}
```
- [ ] Replace all string literals in IsolatedInferenceManager and InferenceService with constants

### 2.3 Implement Death Recipient
**Problem:** No handling when inference process dies.

**Actions:**
- [ ] Add to `IsolatedInferenceManager`:
```kotlin
private val deathRecipient = IBinder.DeathRecipient {
    Log.w(TAG, "Inference service died")
    currentStatus = ConnectionStatus.FAILED
    inferenceService = null
    _isNativeAvailable.set(false)
    attemptReconnection()
}
```
- [ ] In `onServiceConnected()`, call `service.asBinder().linkToDeath(deathRecipient, 0)`

---

## Phase 3: JNI & Native Layer (Days 7-12)

### 3.1 Create JNI Glue Layer
**Problem:** `InferenceService.kt` declares `external fun` with no JNI implementation.

**Actions:**
- [ ] Create directory: `inference_process/src/main/cpp/`
- [ ] Create `CMakeLists.txt` to link llama.cpp:
```cmake
cmake_minimum_required(VERSION 3.10.2)
project("shadowai_inference")

find_package(llama REQUIRED)

add_library(inference_jni SHARED inference_jni.cpp)

target_link_libraries(inference_jni llama android log)
```
- [ ] Create `inference_jni.cpp`:
```cpp
#include <jni.h>
#include <llama.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_InferenceService_loadModelNative(
    JNIEnv* env, jobject thiz,
    jstring modelPath, jint contextSize, jint batchSize,
    jint threads, jboolean useMmap, jboolean useMlock, jint gpuLayers) {
    // Implementation using llama_load_model_from_file
}

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_inference_InferenceService_unloadModelNative(
    JNIEnv* env, jobject thiz, jlong nativeHandle) {
    // Cleanup
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_inference_InferenceService_generateNative(
    JNIEnv* env, jobject thiz, jlong nativeHandle,
    jstring prompt, jint maxTokens, jfloat temperature,
    jfloat topP, jint topK, jfloat repeatPenalty, jobjectArray stopSequences) {
    // Implementation using llama generation APIs
}
```

### 3.2 Fix Native Library Loading
**Problem:** `System.loadLibrary()` in companion init block crashes on error.

**Actions:**
- [ ] Move load to `onCreate()` with error handling:
```kotlin
override fun onCreate() {
    super.onCreate()
    try {
        System.loadLibrary("llama")
        System.setProperty("llama.native.loaded", "true")
    } catch (e: UnsatisfiedLinkError) {
        Log.e(TAG, "Native library failed to load", e)
        // Service should fail fast here
        stopSelf()
    }
}
```
- [ ] Remove companion init block

### 3.3 Add ABI Validation
**Problem:** No checking for correct architecture.

**Actions:**
- [ ] In `IsolatedInferenceManager.bindService()`:
```kotlin
val supportedAbis = Build.SUPPORTED_ABIS
Log.i(TAG, "Device ABIs: ${supportedAbis.joinToString()}")
// Check if native library exists for any supported ABI
```

---

## Phase 4: Security Layer (Days 13-15)

### 4.1 Fix PII Regex Escape Sequences
**Problem:** Double-escaped backslashes in Kotlin strings.

**Current (Broken):**
```kotlin
Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com")
```

**Fixed:**
```kotlin
Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
```

**Actions:**
- [ ] Fix all patterns in `PiiMaskingProcessor.kt` with correct regex
- [ ] Add comprehensive test coverage:
```kotlin
@Test
fun testEmailDetection() {
    val processor = PiiMaskingProcessor()
    assertTrue(processor.containsPii("Contact john@example.com"))
    assertTrue(processor.containsPii("Contact john@example.co.uk"))
    assertFalse(processor.containsPii("No email here"))
}
```

### 4.2 Expand PII Coverage
**Actions:**
- [ ] Add patterns for:
  - Credit cards (with Luhn validation)
  - IBAN numbers
  - API keys ( regex: `[a-zA-Z0-9_-]{32,}` )
  - SSN with validation
- [ ] Add confidence scoring based on context

### 4.3 Fix Prompt Injection Defense
**Problem:** Regex-based detection trivially bypassed.

**Actions (Short-term):**
- [ ] Add normalization layer (lowercase, remove extra spaces, leetspeak decode)
- [ ] Add semantic checks (if prompt length > 2x task description, flag)

**Actions (Long-term — Phase 7):**
- [ ] Integrate small on-device model for prompt classification
- [ ] Use embedding similarity to detect semantic jailbreaks

---

## Phase 5: AI Integration (Days 16-20)

### 5.1 Fix Context Management in AgenticLoop
**Problem:** `_accumulatedContext` grows infinitely → OOM crash.

**Actions:**
- [ ] Add token counting approximation:
```kotlin
private fun estimateTokens(text: String): Int = text.length / 4

private fun trimContextIfNeeded() {
    val estimatedTokens = estimateTokens(_accumulatedContext.toString())
    if (estimatedTokens > MAX_CONTEXT_TOKENS) {
        // Keep most recent exchanges, drop oldest
        val exchanges = _accumulatedContext.toString().split("[Step")
        val recent = exchanges.takeLast(MAX_EXCHANGES_TO_KEEP)
        _accumulatedContext.clear()
        _accumulatedContext.append(recent.joinToString("[Step"))
    }
}
```
- [ ] Add `MAX_CONTEXT_TOKENS = 4000` constant
- [ ] Call `trimContextIfNeeded()` before each append

### 5.2 Fix Task Type Detection Order
**Problem:** Task type checked on `currentPrompt` after injection filter — jailbreaks hidden.

**Current (Broken):**
```kotlin
val scanResult = promptInjectionDefense.scan(input)
val currentPrompt = scanResult.sanitizedPrompt
val taskType = determineTaskType(currentPrompt) // ❌ Uses filtered text
```

**Fixed:**
```kotlin
val taskType = determineTaskType(input) // ✓ Use original input
val scanResult = promptInjectionDefense.scan(input)
...
```

### 5.3 Fix Streaming Implementation
**Problem:** `generateStream()` calls single-shot, not streaming.

**Actions:**
- [ ] Implement real streaming in JNI layer (see Phase 3)
- [ ] Use callback bridging:
```kotlin
val callback = object : IGenerationCallback.Stub() {
    override fun onToken(token: String) {
        runBlocking { channel.send(token) }
    }
    override fun onComplete(fullText: String) { channel.close() }
    override fun onError(error: String) { channel.close(CancellationException(error)) }
}
service.generateStream(request.toBundle(), callback)
```

---

## Phase 6: Thread Safety (Days 21-23)

### 6.1 Fix ConcurrentHashMap Iteration
**Problem:** `activeModels.keys.toList()` not atomic with modifications.

**Actions:**
- [ ] Wrap in synchronized block:
```kotlin
fun unbindService() {
    val modelsToUnload = synchronized(activeModels) {
        activeModels.keys.toList().also { activeModels.clear() }
    }
    modelsToUnload.forEach { unloadModelById(it) }
}
```

### 6.2 Fix Atomic Model ID Generation
**Problem:** `nextModelId++` not thread-safe.

**Actions:**
- [ ] Use AtomicInteger:
```kotlin
private val nextModelId = AtomicInteger(0)
// In loadModel:
val modelId = "model_${nextModelId.getAndIncrement()}"
```

### 6.3 Replace @Volatile State Holder
**Problem:** `MutableAgenticState` uses `@Volatile` incorrectly.

**Actions:**
- [ ] Replace with atomic reference:
```kotlin
private val _state = AtomicReference<AgenticState>(AgenticState.INITIALIZING)
var state: AgenticState
    get() = _state.get()
    set(value) = _state.set(value)
```

---

## Phase 7: Long-term Architectural Fixes (Days 24-30)

### 7.1 Replace Bundle IPC with Parcelable Data Classes
**Actions:**
- [ ] Create `ModelLoadRequest`, `ModelLoadResponse`, `GenerationRequest` as `@Parcelize` data classes
- [ ] Replace Bundle usage throughout IPC layer

### 7.2 Implement Real Prompt Injection Model
**Actions:**
- [ ] Quantize small transformer (TinyLlama 1.1B) for on-device prompt classification
- [ ] Add binary classifier: safe/unsafe prompt
- [ ] Run on every input before model invocation

### 7.3 Add Memory Pressure Handling
**Actions:**
- [ ] Implement `onTrimMemory()` in InferenceService
- [ ] Unload models by LRU when memory pressure detected

---

## Validation Checklist

Before marking any phase complete:

- [ ] `./gradlew :app:compileDebugKotlin` passes
- [ ] `./gradlew :inference_process:compileDebugKotlin` passes
- [ ] KSP code generation completes without `error.NonExistentClass`
- [ ] Hilt compilation succeeds (`./gradlew hiltJavaCompileDebug`)
- [ ] Unit tests for SecurityModule pass (90%+ coverage)
- [ ] Integration test: Service binds successfully
- [ ] Integration test: Model loads without crash
- [ ] Integration test: PII detection works (test email/phone masking)

---

## Dependencies

```
Phase 1 ─┬─> Phase 2 ─┬─> Phase 3 ─┬─> Phase 4 ─┬─> Phase 5 ─┬─> Phase 6
         │            │            │            │            │
         └────────────┴────────────┴────────────┴────────────┘
                                                      │
                                                      v
                                              Phase 7 (Optional)
```

**Cannot start Phase N+1 until Phase N validation passes.**

---

## Success Criteria

All tests must pass:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :inference_process:testDebugUnitTest
./gradlew connectedAndroidTest  # Instrumented tests (device/emulator)
```

**Recovery complete when:**
1. App compiles without errors
2. Hilt generates valid factories
3. Inference service binds and loads model
4. PII masking correctly detects/masks email and phone
5. Prompt blocking catches obvious jailbreaks
6. Agentic loop runs without OOM over 50-turn conversation

---

*Document maintained by: Android Architecture Team*  
*Review cycle: Weekly during recovery*
