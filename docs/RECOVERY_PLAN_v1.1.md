# ShadowAi Recovery Plan — Critical Issues (v1.1)

**Document Version:** 1.1  
**Date:** 2026-02-10  
**Status:** 🔴 CRITICAL — DO NOT BUILD UNTIL COMPLETED

---

## Executive Summary

The codebase has **architectural failures** that will prevent compilation and runtime operation. This plan prioritizes fixes by dependency order — lower layers must be fixed before upper layers can work.

Contributions from technical review integrated for surgical precision.

**Estimated Effort:** 40-60 hours  
**Recommended Team:** 2 Android engineers (1 JNI/native specialist, 1 Hilt/architecture)

---

## Phase 1: Foundation Layer — Hilt & DI (Days 1-3)

### 1.1 Fix Hilt Module Constructor Mismatches
**Files:** `di/AgentModule.kt`, `di/SecurityModule.kt`, `di/InferenceModule.kt`, `di/ModelCatalogModule.kt`

| Current (Broken) | Required Fix |
|------------------|--------------|
| AgentModule provides `SupervisorAgent(gson, dispatcher...)` | Match actual constructor: `Context`, `ShadowAgent`, `TaskExecutor`, etc. |
| AgentModule provides `AgenticLoop(supervisorAgent, dispatcher, shadowAgent)` | Match actual constructor: `taskId`, `taskExecutor`, `routingEngine`, etc. |
| **NEW:** ModelDiscovery needs Context + Gson | Create `ModelCatalogModule` with `@ApplicationContext` |

**Actions:**
- [ ] Read actual constructor signatures for all 3 agents
- [ ] Rewrite AgentModule with correct parameter mappings
- [ ] **Create `ModelCatalogModule.kt`:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ModelCatalogModule {
    @Provides
    @Singleton
    fun provideModelDiscovery(
        @ApplicationContext context: Context,
        gson: Gson,
        localModelEngine: LocalModelEngine,
        secretRepository: ProviderSecretRepository,
        crudRepository: ProviderCrudRepository,
        networkTester: ProviderNetworkTester
    ): ModelDiscovery = ModelDiscovery(...)
}
```
- [ ] Verify `@Provides` methods match class `@Inject` constructors exactly

### 1.2 Break Circular Dependency
**Problem:** ShadowAgent injects `SupervisorAgent?`, which creates AgenticLoop, which needs ShadowAgent.

**Solution:** Use `Lazy<SupervisorAgent>` injection or Flow-based event bus

**Actions:**
- [ ] Update ShadowAgent to use `Lazy<SupervisorAgent>`:
```kotlin
class ShadowAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supervisorAgent: Lazy<SupervisorAgent>, // Lazy breaks cycle
    ...
) {
    suspend fun processInput(input: String): AgentResult {
        if (isComplexTask) {
            supervisorAgent.get().processInput(input) // Resolve lazily
        }
    }
}
```
- [ ] Alternative: Remove direct injection, use event bus pattern with `Flow<AgentCommand>`

### 1.3 Fix Import Resolution — Define PipelinePlanner
**Problem:** Missing PipelinePlanner referenced in SupervisorAgent.kt

**Actions:**
- [ ] Create stub in `:pipeline-planner` module immediately:
```kotlin
// pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelinePlanner.kt
@Singleton
class PipelinePlanner @Inject constructor(
    private val adapterRegistry: AdapterRegistry
) {
    fun createTransformGraph(): TransformGraph { ... }
}
```
- [ ] **Critical:** This unblocks `:model-catalog` from referencing Transform logic correctly

### 1.4 Fix ProviderRepository Type Mismatch
**Problem:** `AgentModule.kt` uses wrong import path.

**Actions:**
- [ ] Update all imports to use `com.shadowai.core_contracts.providers.ProviderRepository`
- [ ] Verify ProviderRepositoryImpl properly implements interface
- [ ] Fix any method signature mismatches

---

## Phase 2: AIDL Layer — Process Boundary (Days 4-6)

### 2.1 Generate Real AIDL Interfaces
**Problem:** Stub AIDL interfaces won't compile.

**Actions:**
- [ ] Create: `app/src/main/aidl/com/shadowai/app/ai/IInferenceService.aidl`
- [ ] Create: `app/src/main/aidl/com/shadowai/app/ai/IGenerationCallback.aidl`
- [ ] Add to `:app/build.gradle.kts`:
```kotlin
android {
    aidlPackagedNames += ["com.shadowai.app.ai"]
}
```
- [ ] Delete stub interfaces from `IsolatedInferenceManager.kt`

### 2.2 Use ParcelFileDescriptor for Model Files
**Problem:** Cannot pass `File` objects across AIDL — breaks zero-trust boundary.

**Actions:**
- [ ] In `ModelDiscovery.kt`, use `ContentResolver.openFileDescriptor()`:
```kotlin
// When passing model to inference service:
val file = File(modelPath)
val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
// Pass pfd through AIDL
```
- [ ] Update AIDL signature:
```aidl
Bundle loadModel(in ParcelFileDescriptor modelPfd, in Bundle config);
```

### 2.3 Implement Death Recipient
**Actions:** (unchanged from v1.0)

---

## Phase 3: JNI Layer — Native Bridge (Days 7-12)

### 3.1 Create JNI Glue Layer
**Problem:** `InferenceService.kt` declares `external fun` with no implementation.

**Actions:**
- [ ] Create `InferenceEngine.cpp` linking llama.cpp:
```cpp
// inference_process/src/main/cpp/InferenceEngine.cpp
#include <jni.h>
#include <llama.h>

// Store llama_context pointer as jlong in Kotlin
static std::unordered_map<jlong, llama_context*> contexts;
static jlong nextHandle = 1;

extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_InferenceService_loadModelNative(...) {
    // Return pointer as jlong for Kotlin storage
}
```
- [ ] Use pointer (long) pattern as recommended — store `llama_context*` in Kotlin via jlong

### 3.2 Fix Native Library Loading
**Problem:** System.loadLibrary in companion object causes early crashes.

**Actions:**
- [ ] Create dedicated `NativeLoader` class:
```kotlin
// inference_process/.../NativeLoader.kt
class NativeLoader {
    companion object {
        @Volatile
        private var loaded = false
        
        fun load() {
            if (!loaded) {
                System.loadLibrary("inference_jni")
                loaded = true
            }
        }
    }
}
```
- [ ] Call `NativeLoader.load()` in `InferenceService.onCreate()` — prevents multiple loads during model rescan

### 3.3 Add ABI Safety
**Problem:** No ABI filtering for arm64-v8a vs armeabi-v7a.

**Actions:**
- [ ] Add to `:inference_process/build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a") // Most GGUF models need 64-bit
        }
    }
}
```
- [ ] Add ABI check in `IsolatedInferenceManager.bindService()`:
```kotlin
val supportedAbis = Build.SUPPORTED_ABIS
require(supportedAbis.any { it == "arm64-v8a" }) {
    "Device does not support arm64-v8a. GGUF models require 64-bit architecture."
}
```

---

## Phase 4: Security Layer — Zero-Trust Core (Days 13-15)

### 4.1 Fix PII Regex Escape Sequences
**Problem:** Double-escaped backslashes fail in Kotlin.

**Current (Broken):**
```kotlin
Pattern.compile("\b\d{3}[-.]?\d{3}[-.]?\d{4}\b") // ❌ Broken
```

**Fixed:**
```kotlin
// Use raw strings to avoid escape hell
val PHONE_PATTERN = Regex(r"""\b\d{3}[-.]?\d{3}[-.]?\d{4}\b""")
val EMAIL_PATTERN = Regex(r"""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
```

**Enhanced:**
- [ ] Use `android.util.Patterns.EMAIL_ADDRESS` instead of custom regex:
```kotlin
import android.util.Patterns
fun containsEmail(text: String): Boolean {
    return Patterns.EMAIL_ADDRESS.matcher(text).find()
}
```
- [ ] OS-maintained pattern stays updated with new TLDs

### 4.2 Integrate SecretBytes for API Key Handling
**Problem:** ModelDiscovery may encounter API keys in model.json — must not store as String.

**Actions:**
- [ ] Update `ModelDiscovery.kt` to use `SecretBytes` pattern:
```kotlin
// When discovering model metadata with API keys:
fun discoveredApiKey(key: String): SecretBytes {
    return SecretBytes(key.toByteArray(Charsets.UTF_8)).also {
        // Clear original string from memory
        (key as java.lang.String).apply { kotlin.text.replace(0, length, '0') }
    }
}
```

### 4.3 Security Test Coverage
**Actions:** (unchanged) Target 90%+ for PiiMaskingProcessor

---

## Phase 5: AI Integration — Logic Layer (Days 16-20)

### 5.1 Fix Context Management with Sliding Window
**Problem:** `_accumulatedContext` grows infinitely.

**Actions:**
- [ ] Add `maxContext: Int` to `ModelDescriptor`:
```kotlin
// model-catalog/.../ModelDescriptor.kt
data class ModelDescriptor(
    val id: String,
    val maxContext: Int = 4096, // NEW
    val capabilities: Set<Capability>,
    ...
)
```
- [ ] Implement TikToken-style token counting before JNI call:
```kotlin
// AgenticLoop.kt
private fun countTokens(text: String): Int {
    // Approximation: ~4 chars per token for English
    return text.length / 4
}

private fun trimToContext(text: String, maxTokens: Int): String {
    if (countTokens(text) <= maxTokens) return text
    
    // Sliding window: keep system prompt + most recent exchanges
    val exchanges = text.split("\n[User]: ", "\n[Assistant]: ")
    val recent = exchanges.takeLast(5) // Keep last 5 turns
    return recent.joinToString("\n")
}
```

### 5.2 Fix Task Type Detection Order
**Actions:** (unchanged) Check task type *before* injection filter

### 5.3 Ensure PromptInjectionDefense Runs Before PipelinePlanner
**Problem:** Resource allocation happens before security check.

**Actions:**
- [ ] Update `SupervisorAgent.processInput()`:
```kotlin
suspend fun processInput(input: String, policy: RoutingPolicy): SupervisorResult {
    // 1. Security first
    val scanResult = promptInjectionDefense.scan(input)
    if (!scanResult.isSafe) return SupervisorResult.Error(...)
    
    // 2. Now determine if we need pipeline
    val taskType = determineTaskType(input)
    val requiresPipeline = isMultimodalTask(taskType)
    
    // 3. Only allocate PipelinePlanner resources if needed
    if (requiresPipeline) {
        val pipeline = pipelinePlanner.createPlan(...)
        ...
    }
}
```

---

## Phase 6: Thread Safety — Stability (Days 21-23)

### 6.1 Fix Concurrent Model Discovery
**Problem:** `rescan()` called while scan in progress = duplicate entries.

**Actions:**
- [ ] Add Mutex to `ModelDiscovery`:
```kotlin
class ModelDiscovery @Inject constructor(...) {
    private val scanMutex = Mutex()
    
    suspend fun rescan(): List<ModelInfo> = scanMutex.withLock {
        // Only one scan runs at a time
        return performScan()
    }
}
```

### 6.2 Fix Model ID Generation
**Problem:** String concatenation ("local:$modelName") causes UI glitches with duplicate filenames.

**Actions:**
- [ ] Replace with unique hash or AtomicLong:
```kotlin
class ModelDiscovery {
    private val nextModelId = AtomicLong(0)
    
    fun generateModelId(path: String, name: String): String {
        val hash = (path.hashCode().toLong() shl 32) or name.hashCode().toLong()
        return "model_${hash}_${nextModelId.getAndIncrement()}"
    }
}
```

### 6.3 Fix @Volatile State Holder
**Actions:** (unchanged) Replace with AtomicReference

---

## Phase 7: Long-term — Optimization (Days 24-30)

### 7.1 Parcelize ModelDescriptor
**Problem:** Passing model metadata requires re-parsing JSON.

**Actions:**
- [ ] Annotate `ModelDescriptor` with `@Parcelize`:
```kotlin
// model-catalog/.../ModelDescriptor.kt
@Parcelize
data class ModelDescriptor(
    val id: String,
    val name: String,
    val providerId: ProviderId,
    val capabilities: Set<Capability>,
    val maxContext: Int,
    val metadata: Map<String, String>
) : Parcelable
```
- [ ] Enables passing via Intent/Between services without re-serialization

### 7.2 Persist Discovered Model Paths
**Problem:** `getExternalFilesDir()` changes if app moves to SD card.

**Actions:**
- [ ] Store discovered model paths in ShadowDatabase (from AGENTS.md):
```kotlin
// After first scan, persist to database
@Dao
interface ModelPathDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveModelPath(path: ModelPathEntity)
    
    @Query("SELECT * FROM model_paths")
    suspend fun getAllPaths(): List<ModelPathEntity>
}

// In ModelDiscovery:
suspend fun getDefaultLocalModelDirs(): List<String> {
    // Check database first
    val cached = modelPathDao.getAllPaths()
    if (cached.isNotEmpty()) return cached.map { it.path }
    
    // Fallback to file walk, then persist results
    val discovered = walkFileTree()
    discovered.forEach { modelPathDao.saveModelPath(it) }
    return discovered
}
```
- [ ] Avoids re-walking entire file tree on every startup

### 7.3 Memory Pressure Handling
**Problem:** No `onTrimMemory()` means system OOM kills app unpredictably.

**Actions:**
- [ ] Implement in `InferenceService`:
```kotlin
override fun onTrimMemory(level: Int) {
    when (level) {
        TRIM_MEMORY_RUNNING_CRITICAL,
        TRIM_MEMORY_COMPLETE -> {
            // Unload LRU model
            val lruModel = loadedModels.values.minByOrNull { it.lastUsed }
            lruModel?.let { unloadModel(it.modelId) }
        }
    }
}
```
- [ ] Track `lastUsed` timestamp in `ModelInstance` data class

---

## Validation Checklist

Before marking any phase complete:

- [ ] `./gradlew :app:compileDebugKotlin` passes
- [ ] `./gradlew :inference_process:compileDebugKotlin` passes
- [ ] `./gradlew :model-catalog:compileDebugKotlin` passes
- [ ] KSP code generation completes without `error.NonExistentClass`
- [ ] Hilt compilation succeeds (`./gradlew hiltJavaCompileDebug`)
- [ ] Unit tests for SecurityModule pass (90%+ coverage)
- [ ] Integration test: Service binds successfully
- [ ] Integration test: Model loads via ParcelFileDescriptor (not File)
- [ ] Integration test: PII detection works via `android.util.Patterns`
- [ ] Integration test: Agentic loop runs 50-turn conversation without OOM

---

## Technical Dependency Graph

```
Phase 1 ─┬─> Phase 2 ─┬─> Phase 3 ─┬─> Phase 4 ─┬─> Phase 5 ─┬─> Phase 6
         │            │            │            │            │
         │            │            │            │            └─> 7.1 (@Parcelize)
         │            │            │            │
         │            │            │            └─> 7.3 (onTrimMemory)
         │            │            │
         │            │            └─> 7.2 (ShadowDatabase)
         │            │
         │            └─> Defines :pipeline-planner stubs
         │
         └─> ModelCatalogModule enables ModelDiscovery
```

**Cannot start Phase N+1 until Phase N validation passes.**

---

## Success Criteria

All tests must pass:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :inference_process:testDebugUnitTest
./gradlew :model-catalog:testDebugUnitTest
./gradlew connectedAndroidTest  # Instrumented tests
```

**Recovery complete when:**
1. App compiles without errors
2. Hilt generates valid factories
3. Inference service binds using ParcelFileDescriptor (not File)
4. PII masking uses `android.util.Patterns.EMAIL_ADDRESS` correctly
5. Prompt blocking catches obvious jailbreaks
6. Agentic loop runs 50-turn conversation without OOM (sliding window works)
7. Model paths persist in ShadowDatabase across app restarts

---

## Changelog

**v1.0 → v1.1:**
- Added `ModelCatalogModule` with `@ApplicationContext` (Reviewer contribution)
- Changed circular dependency fix from factory to `Lazy<SupervisorAgent>`
- Added requirement to define `PipelinePlanner` in `:pipeline-planner` module
- Added `ParcelFileDescriptor` pattern for cross-process file handling
- Changed PII regex to use `android.util.Patterns.EMAIL_ADDRESS`
- Added `SecretBytes` integration for API key handling in ModelDiscovery
- Added `maxContext: Int` to ModelDescriptor for sliding window implementation
- Added TikToken-style token counter approximation
- Added Mutex to ModelDiscovery.scanMutex for concurrent scan prevention
- Changed model ID generation to AtomicLong with hash-based uniqueness
- Added `@Parcelize` for ModelDescriptor (Phase 7)
- Added ShadowDatabase persistence for model paths (Phase 7)
- Added `lastUsed` tracking for LRU model unloading (Phase 7)

---

*Document maintained by: Android Architecture Team*  
*Review cycle: Weekly during recovery*  
*Technical contributions integrated from code review*
