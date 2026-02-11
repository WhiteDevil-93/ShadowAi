# ShadowAI Implementation Plan - Phases 2-7

## Current Status
✅ **Phase 1 Complete**: All build blockers resolved, project compiles successfully

## Phase 2: AIDL & IPC (Days 4-6)

### Objectives
- Establish secure inter-process communication between app and inference service
- Create AIDL interfaces for model loading and generation
- Implement streaming token callbacks

### Tasks

#### 2.1 Create AIDL Interface Files
**Location**: `inference_process/src/main/aidl/com/shadowai/inference/`

**Files to Create**:
1. `IInferenceService.aidl` - Main service interface
2. `IGenerationCallback.aidl` - Streaming callback interface
3. `IServiceHealthCallback.aidl` - Health monitoring

**Priority**: HIGH
**Estimated Time**: 4 hours

#### 2.2 Implement AIDL Service
**Location**: `inference_process/src/main/java/com/shadowai/inference/InferenceService.kt`

**Changes**:
- Extend `IInferenceService.Stub()`
- Implement all AIDL methods
- Add proper error handling and logging
- Implement DeathRecipient for client death detection

**Priority**: HIGH
**Estimated Time**: 6 hours

#### 2.3 Update IsolatedInferenceManager
**Location**: `app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt`

**Changes**:
- Use generated AIDL classes instead of manual Bundle passing
- Add DeathRecipient for service death detection
- Implement automatic reconnection logic
- Use ParcelFileDescriptor for cross-process file access

**Priority**: HIGH
**Estimated Time**: 8 hours

#### 2.4 Create InferenceServiceContracts
**Location**: `inference_process/src/main/kotlin/com/shadowai/inference/InferenceServiceContracts.kt`

**Purpose**: Centralize all Bundle keys and constants for AIDL IPC

**Priority**: MEDIUM
**Estimated Time**: 2 hours

### Verification
```bash
# Verify AIDL files are generated
find app/build -name "IInferenceService.java"
find app/build -name "IGenerationCallback.java"

# Test service binding
./gradlew :app:connectedDebugAndroidTest --tests "*InferenceServiceTest"
```

---

## Phase 3: JNI & Native Bridge (Days 7-12)

### Objectives
- Integrate llama.cpp for on-device inference
- Create JNI bridge for Kotlin ↔ C++ communication
- Optimize for ARM64 architecture

### Tasks

#### 3.1 Setup Native Build System
**Location**: `inference_process/src/main/cpp/`

**Files to Create**:
1. `CMakeLists.txt` - Build configuration
2. `InferenceEngine.cpp` - Main JNI implementation
3. `InferenceEngine.h` - Header file
4. `llama_wrapper.cpp` - llama.cpp wrapper

**Priority**: CRITICAL
**Estimated Time**: 12 hours

#### 3.2 Integrate llama.cpp
**Location**: `inference_process/src/main/cpp/llama.cpp/`

**Steps**:
1. Add llama.cpp as git submodule or copy sources
2. Configure CMake to build llama.cpp
3. Enable ARM NEON optimizations
4. Add llamafile support for GGUF loading

**Priority**: CRITICAL
**Estimated Time**: 8 hours

#### 3.3 Create NativeBridge
**Location**: `inference_process/src/main/kotlin/com/shadowai/inference/NativeBridge.kt`

**Methods**:
```kotlin
external fun loadModel(path: String, ctxSize: Int, gpuLayers: Int): Long
external fun unloadModel(handle: Long): Boolean
external fun generate(handle: Long, prompt: String, maxTokens: Int, callback: TokenCallback): String
external fun getModelInfo(handle: Long): Bundle
external fun cancelGeneration(handle: Long): Boolean
```

**Priority**: HIGH
**Estimated Time**: 6 hours

#### 3.4 Memory Management
**Implementation**:
- Store `llama_context*` as jlong handle
- Implement proper cleanup in finalizers
- Add memory pressure monitoring
- Implement model unloading on low memory

**Priority**: HIGH
**Estimated Time**: 6 hours

#### 3.5 ABI Configuration
**Location**: `inference_process/build.gradle.kts`

**Changes**:
```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
}
```

**Priority**: MEDIUM
**Estimated Time**: 2 hours

### Verification
```bash
# Verify native library is built
find inference_process/build -name "*.so"

# Test native methods
./gradlew :inference_process:testDebugUnitTest --tests "*NativeBridgeTest"

# Verify ABI
./gradlew :inference_process:assembleDebug
unzip -l inference_process/build/outputs/aar/*.aar | grep "lib/arm64-v8a"
```

---

## Phase 4: Security Layer (Days 13-15)

### Objectives
- Fix PII detection and masking
- Implement secure secret handling
- Add TLS certificate pinning
- Enhance prompt injection defense

### Tasks

#### 4.1 Fix PII Regex Patterns
**Location**: `app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt`

**Changes**:
- Use raw strings for regex patterns
- Integrate Android Patterns for EMAIL/PHONE
- Add SSN, credit card detection
- Improve international phone number support

**Priority**: HIGH
**Estimated Time**: 4 hours

#### 4.2 SecretBytes Integration
**Location**: Multiple files using API keys

**Changes**:
- Replace String API keys with SecretBytes
- Implement secure key storage using EncryptedSharedPreferences
- Add key rotation support
- Implement secure memory wiping

**Priority**: CRITICAL
**Estimated Time**: 8 hours

#### 4.3 Update Test Suite
**Location**: `app/src/test/java/com/shadowai/app/security/`

**Tests to Add**:
- Email validation (valid/invalid, international)
- Phone number formats (US, international)
- Credit card Luhn validation
- API key context-aware detection
- SSN validation

**Target Coverage**: 90%+ line, 85%+ branch

**Priority**: HIGH
**Estimated Time**: 6 hours

#### 4.4 TLS Certificate Pinning
**Location**: `app/src/main/res/xml/network_security_config.xml`

**Changes**:
- Add actual certificate pins for production APIs
- Configure backup pins
- Add expiration monitoring
- Implement pin update mechanism

**Priority**: MEDIUM
**Estimated Time**: 4 hours

### Verification
```bash
# Run security tests
./gradlew test --tests "*Pii*"
./gradlew test --tests "*Security*"

# Check coverage
./gradlew jacocoTestReport
# Verify coverage >90% in build/reports/jacoco/
```

---

## Phase 5: AI Integration (Days 16-20)

### Objectives
- Implement context window management
- Add token counting and truncation
- Fix task detection ordering
- Enhance model selection logic

### Tasks

#### 5.1 Add maxContext to ModelDescriptor
**Location**: `model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDescriptor.kt`

**Changes**:
```kotlin
@Parcelize
data class ModelDescriptor(
    val maxContext: Int = 4096,
    val capabilities: Set<Capability> = emptySet(),
    // ... existing fields
) {
    fun getAvailableGenerationTokens(inputTokens: Int) = 
        (maxContext - inputTokens).coerceAtLeast(0)
}
```

**Priority**: HIGH
**Estimated Time**: 2 hours

#### 5.2 Implement Token Counter
**Location**: `app/src/main/java/com/shadowai/app/ai/TokenCounter.kt`

**Implementation**:
- Simple heuristic: ~4 chars/token
- Add proper tokenizer integration later
- Cache token counts for messages

**Priority**: MEDIUM
**Estimated Time**: 3 hours

#### 5.3 Sliding Window Context Management
**Location**: `app/src/main/java/com/shadowai/app/agent/ContextManager.kt`

**Features**:
- Keep last N exchanges when context exceeds threshold
- Configurable window size (default: 5)
- Configurable threshold (default: 90%)
- Preserve system messages

**Priority**: HIGH
**Estimated Time**: 6 hours

#### 5.4 Fix Task Detection Order
**Location**: `app/src/main/java/com/shadowai/app/agent/SupervisorAgent.kt`

**Critical Fix**:
```kotlin
// CORRECT ORDER:
// 1. Detect task type from ORIGINAL input
// 2. Run prompt injection defense
// 3. Use sanitized prompt but ORIGINAL task type
```

**Priority**: CRITICAL
**Estimated Time**: 4 hours

#### 5.5 Enhance Model Selection
**Location**: `app/src/main/java/com/shadowai/app/providers/ProviderSelector.kt`

**Changes**:
- Consider context requirements
- Prefer models with larger context for long conversations
- Add capability-based filtering
- Implement fallback logic

**Priority**: MEDIUM
**Estimated Time**: 5 hours

### Verification
```bash
# Test 50-turn conversation
./gradlew :app:connectedDebugAndroidTest --tests "*LongConversationTest"

# Verify no OOM
adb logcat | grep -i "OutOfMemory"

# Check task routing
./gradlew test --tests "*TaskDetectionTest"
```

---

## Phase 6: Thread Safety (Days 21-23)

### Objectives
- Add proper synchronization to shared state
- Replace volatile with atomic operations
- Implement mutex protection for critical sections

### Tasks

#### 6.1 Add Mutex to ModelDiscovery
**Location**: `app/src/main/java/com/shadowai/app/models/ModelDiscovery.kt`

**Changes**:
```kotlin
private val scanMutex = Mutex()

suspend fun rescan(): List<ModelInfo> = scanMutex.withLock {
    performFullScan()
}
```

**Priority**: HIGH
**Estimated Time**: 3 hours

#### 6.2 Fix Model ID Generation
**Location**: `app/src/main/java/com/shadowai/app/models/ModelDiscovery.kt`

**Changes**:
```kotlin
private val nextModelId = AtomicLong(0)

fun generateModelId(path: String, name: String): String {
    val hash = (path.hashCode().toLong() shl 32) or name.hashCode().toLong()
    return "model_${hash}_${nextModelId.getAndIncrement()}"
}
```

**Priority**: MEDIUM
**Estimated Time**: 2 hours

#### 6.3 Replace @Volatile with AtomicReference
**Locations**:
- `app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt`
- `app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt`

**Changes**:
- Replace `@Volatile var` with `AtomicReference<T>`
- Use `get()`, `set()`, `compareAndSet()` for access

**Priority**: HIGH
**Estimated Time**: 4 hours

#### 6.4 Thread-Safe Adapter Cache
**Location**: `app/src/main/java/com/shadowai/app/providers/ActiveProviderManager.kt`

**Changes**:
- Use ConcurrentHashMap for adapter cache
- Add proper cache invalidation on config changes
- Implement cache key that includes config hash

**Priority**: HIGH
**Estimated Time**: 5 hours

### Verification
```bash
# Run concurrent tests
./gradlew test --tests "*ConcurrentTest"
./gradlew test --tests "*ThreadSafetyTest"

# Stress test model discovery
./gradlew :app:connectedDebugAndroidTest --tests "*ConcurrentRescanTest"
```

---

## Phase 7: Optimization (Days 24-30)

### Objectives
- Add database persistence for model paths
- Implement memory pressure handling
- Optimize model loading/unloading
- Add performance monitoring

### Tasks

#### 7.1 Create ModelPathEntity
**Location**: `app/src/main/java/com/shadowai/app/db/ModelPathEntity.kt`

**Implementation**:
```kotlin
@Entity(tableName = "model_paths")
data class ModelPathEntity(
    @PrimaryKey val path: String,
    val modelId: String,
    val lastDiscovered: Long = System.currentTimeMillis(),
    val valid: Boolean = true,
    val lastUsed: Long = 0L,
    val useCount: Int = 0
)
```

**Priority**: MEDIUM
**Estimated Time**: 3 hours

#### 7.2 Add ModelPathDao
**Location**: `app/src/main/java/com/shadowai/app/db/ModelPathDao.kt`

**Methods**:
- `insertOrUpdate()`
- `getAllValid()`
- `markInvalid(path: String)`
- `getLRU()` - Least recently used
- `incrementUseCount(path: String)`

**Priority**: MEDIUM
**Estimated Time**: 2 hours

#### 7.3 Implement Memory Pressure Handling
**Location**: `inference_process/src/main/java/com/shadowai/inference/InferenceService.kt`

**Implementation**:
```kotlin
override fun onTrimMemory(level: Int) {
    when (level) {
        TRIM_MEMORY_RUNNING_CRITICAL, TRIM_MEMORY_COMPLETE -> {
            unloadLRUModel()
        }
        TRIM_MEMORY_RUNNING_LOW -> {
            reduceCacheSizes()
        }
        TRIM_MEMORY_MODERATE -> {
            clearUnusedResources()
        }
    }
}
```

**Priority**: HIGH
**Estimated Time**: 4 hours

#### 7.4 Add Performance Monitoring
**Location**: `app/src/main/java/com/shadowai/app/diagnostics/PerformanceMonitor.kt`

**Metrics to Track**:
- Model load time
- Generation tokens/second
- Memory usage
- Cache hit rates
- API latency

**Priority**: MEDIUM
**Estimated Time**: 6 hours

#### 7.5 Optimize Model Loading
**Changes**:
- Implement model preloading for frequently used models
- Add background model preparation
- Implement smart model caching based on usage patterns

**Priority**: MEDIUM
**Estimated Time**: 8 hours

### Verification
```bash
# Test memory pressure
./gradlew :app:connectedDebugAndroidTest --tests "*MemoryPressureTest"

# Verify DB persistence
./gradlew :app:connectedDebugAndroidTest --tests "*ModelPathPersistenceTest"

# Check performance metrics
adb logcat | grep "PerformanceMonitor"
```

---

## Implementation Timeline

| Phase | Duration | Dependencies | Risk Level |
|-------|----------|--------------|------------|
| Phase 2 (AIDL) | 3 days | Phase 1 complete | Medium |
| Phase 3 (JNI) | 6 days | Phase 2 complete | High |
| Phase 4 (Security) | 3 days | Phase 1 complete | Medium |
| Phase 5 (AI) | 5 days | Phase 3, 4 complete | Medium |
| Phase 6 (Thread Safety) | 3 days | Phase 2, 5 complete | Low |
| Phase 7 (Optimization) | 7 days | All phases complete | Low |

**Total Estimated Time**: 27 days

---

## Critical Path

```
Phase 1 (Complete) 
    ↓
Phase 2 (AIDL) ──────┐
    ↓                │
Phase 3 (JNI) ←──────┤
    ↓                │
Phase 4 (Security) ←─┘
    ↓
Phase 5 (AI Integration)
    ↓
Phase 6 (Thread Safety)
    ↓
Phase 7 (Optimization)
```

---

## Success Criteria

### Phase 2
- ✅ AIDL files generated successfully
- ✅ Service binding works reliably
- ✅ Streaming callbacks functional
- ✅ Service survives client death

### Phase 3
- ✅ Native library builds for arm64-v8a
- ✅ JNI methods callable from Kotlin
- ✅ Model loads successfully
- ✅ Generation produces coherent output
- ✅ Memory managed properly

### Phase 4
- ✅ PII detection >90% accuracy
- ✅ Test coverage >90% line, >85% branch
- ✅ API keys stored securely
- ✅ TLS pinning active

### Phase 5
- ✅ 50-turn conversation without OOM
- ✅ Context truncation works correctly
- ✅ Task routing accurate
- ✅ Model selection optimal

### Phase 6
- ✅ No race conditions in concurrent tests
- ✅ Atomic operations verified
- ✅ No duplicate model entries
- ✅ Cache invalidation correct

### Phase 7
- ✅ Model paths persisted across restarts
- ✅ LRU unload on memory pressure
- ✅ Performance metrics collected
- ✅ Load times optimized

---

## Next Steps

1. **Immediate**: Start Phase 2 - Create AIDL interface files
2. **Review**: Ensure Phase 1 changes are committed
3. **Setup**: Prepare development environment for native builds (NDK, CMake)
4. **Documentation**: Update README with build instructions

---

## Notes

- Each phase should be completed and tested before moving to the next
- Maintain backward compatibility where possible
- Document all API changes
- Keep security as top priority throughout
- Regular code reviews after each phase
