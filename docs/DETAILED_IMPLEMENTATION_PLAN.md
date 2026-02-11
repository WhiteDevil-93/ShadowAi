# ShadowAi Detailed Implementation Plan

**Version:** 1.1  
**Date:** 2026-02-10  
**Estimated Effort:** 50-70 hours (accounting for audit findings)  
**Status:** CRITICAL - Build-blocked until completed
**Audit Review:** 9 critical, 8 severe issues identified (see Audit Findings section)

---

## Audit Findings from Code Review

These additional critical issues were identified during code review and must be addressed alongside the phased implementation.

### Priority 1: Build Blockers (Must Fix During Phase 1)

#### A1. Fix ModelDescriptor Parcelize Setup
**Severity:** CRITICAL - Build fails with unresolved `@Parcelize`

**Files:**
- `model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDescriptor.kt` (lines 6, 23)
- `model-catalog/build.gradle.kts` (line 1)

**Issue:** Plugin `kotlin-parcelize` not applied to `:model-catalog` module.

**Fix:**
```kotlin
// model-catalog/build.gradle.kts
plugins {
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    kotlin("android")
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}
```

**Verification:**
```bash
./gradlew.bat :model-catalog:compileDebugKotlin
./gradlew :model-catalog:build
```

---

#### A2. Fix Package Mismatch in PiiMaskingProcessor
**Severity:** CRITICAL - Import failure

**Files:**
- `app/src/main/java/com/shadowai/security/PiiMaskingProcessor.kt` (line 1 - wrong package)
- `app/src/main/java/com/shadowai/app/di/SecurityModule.kt` (line 3 - import failure)

**Issue:** Package declared as `com.shadowai.security` but should be `com.shadowai.app.security`.

**Fix:**
```kotlin
// Change package declaration at line 1
package com.shadowai.app.security  // Was: package com.shadowai.security
```

**Alternative:** Update imports in SecurityModule if the package relocation is intentional.

---

#### A3. ProviderRepository Doesn't Exist in App Module
**Severity:** CRITICAL - ProviderRepository referenced but missing

**Files:**
- `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt` (line 17)
- `app/src/main/java/com/shadowai/app/execution/RoutingEngine.kt` (line 9)
- `app/src/main/java/com/shadowai/app/ShadowApplication.kt` (line 9)

**Issue:** App imports `com.shadowai.app.providers.ProviderRepository` but no such class exists in the source tree.

**Resolution Options:**

**Option A (Recommended):** Create ProviderRepository interface in `:app` as facade:
```kotlin
// app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt
@Singleton
class ProviderRepository @Inject constructor(
    private val impl: com.shadowai.core_contracts.providers.ProviderRepository
) : ProviderRepositoryFacade {
    // Delegate to core-contracts implementation
    override fun getProviders(): List<Provider> = impl.getProviders()
    // ... other delegations
}
```

**Option B:** Update all imports to use `core_contracts` version directly.

---

#### A4. LocalInferenceEngine Import Failure
**Severity:** CRITICAL - Missing interface

**Files:**
- `app/src/main/java/com/shadowai/app/di/InferenceModule.kt` (line 4)
- `app/src/main/java/com/shadowai/app/ai/LocalInferenceEngine.kt` (line 1)

**Issue:** Class `LocalInferenceEngine` referenced but doesn't exist.

**Fix:** Create interface in `:app` module:
```kotlin
// app/src/main/java/com/shadowai/app/ai/LocalInferenceEngine.kt
interface LocalInferenceEngine {
    suspend fun loadModel(modelPath: String, config: InferenceConfig): Result<ModelHandle>
    suspend fun generate(handle: ModelHandle, prompt: String, params: GenerationParams): Flow<String>
    suspend fun unloadModel(handle: ModelHandle): Boolean
}
```

Then bind IsolatedInferenceManager to it:
```kotlin
// InferenceModule.kt - update binding
@Binds
@Singleton
abstract fun bindLocalInferenceEngine(
    impl: IsolatedInferenceManager
): LocalInferenceEngine
```

---

#### A5. TeeKeyManager Missing in App Module
**Severity:** CRITICAL - AppModule references non-existent class

**Files:**
- `app/src/main/java/com/shadowai/app/di/AppModule.kt` (line 16)
- `core-contracts/src/main/kotlin/com/shadowai/core/security/TeeKeyManager.kt` (line 21)

**Issue:** AppModule imports `com.shadowai.app.security.TeeKeyManager` but it's in `core-contracts` module.

**Fix:** Update import in AppModule:
```kotlin
import com.shadowai.core.security.TeeKeyManager  // Was wrong package
```

**And add Hilt module in core-contracts:**
```kotlin
// core-contracts/src/main/kotlin/com/shadowai/core/di/SecurityContractsModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SecurityContractsModule {
    @Provides
    @Singleton
    fun provideTeeKeyManager(): TeeKeyManager = TeeKeyManager()
}
```

---

#### A6. SecretBytes Import Path Mismatch
**Severity:** CRITICAL - Wrong import path

**Files:**
- `app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt` (line 15)

**Issue:** Imports `com.shadowai.core_contracts.SecretBytes` but actual package is `com.shadowai.core.security.SecretBytes`.

**Fix:**
```kotlin
// Change import
import com.shadowai.core.security.SecretBytes  // Was: core_contracts
```

---

### Priority 2: Functionality Issues (Fix During Phases 4-5)

#### B1. DeviceAction.OpenApp/OpenUrl Do Not Exist
**Severity:** SEVERE - AgenticLoop references non-existent actions

**Files:**
- `app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt` (line 401)
- `app/src/main/java/com/shadowai/app/execution/DeviceAction.kt` (line 26)

**Issue:** AgenticLoop uses `DeviceAction.OpenApp` and `DeviceAction.OpenUrl` but they're not defined.

**Fix:** Add to DeviceAction sealed class:
```kotlin
sealed class DeviceAction {
    data class OpenApp(val packageName: String) : DeviceAction()
    data class OpenUrl(val url: String) : DeviceAction()
    data class SendMessage(val recipient: String, val message: String) : DeviceAction()
    // ... existing actions
}
```

---

#### B2. Artifact Type Not Imported
**Severity:** SEVERE - Type used without import

**Files:**
- `app/src/main/java/com/shadowai/app/execution/TaskExecutionService.kt` (line 91)

**Issue:** Uses `Artifact.Text` but `Artifact` type not imported.

**Fix:** Add import or create Artifact type:
```kotlin
// Either add import
import com.shadowai.app.artifacts.Artifact

// Or if missing, create Artifact.kt
sealed class Artifact {
    data class Text(val content: String) : Artifact()
    data class Image(val uri: Uri, val mimeType: String) : Artifact()
    // ...
}
```

---

#### B3. Core AI Execution Broken (LocalBrainManager)
**Severity:** CRITICAL - Returns null config while executor requires it

**Files:**
- `app/src/main/java/com/shadowai/app/ai/local/LocalBrainManager.kt` (lines 28-29)
- `app/src/main/java/com/shadowai/app/execution/HybridAiExecutor.kt` (lines 164-165)
- `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt` (line 145)

**Issue:** 
- `LocalBrainManager.getActiveModelConfig()` returns null (stub)
- `HybridAiExecutor` expects non-null config (crashes on null)

**Fix Options:**

**Option A (Immediate):** Return default config instead of null:
```kotlin
// LocalBrainManager.kt
fun getActiveModelConfig(): ModelConfig? {
    return activeConfig ?: defaultLocalConfig  // Never return null
}
```

**Option B (Proper):** Make ModelConfig nullable throughout chain:
```kotlin
// HybridAiExecutor.kt
private suspend fun execute(config: ModelConfig?, task: Task): Result<ExecutionResult> {
    if (config == null) {
        return Result.failure(IllegalStateException("No active model"))
    }
    // ...
}
```

---

#### B4. Circuit-Breaker Strategy Inconsistent
**Severity:** WARNING - Architecture mismatch

**Files:**
- `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt` (lines 92, 153, 172)
- `app/src/main/java/com/shadowai/app/adapters/AdapterCircuitBreaker.kt` (line 155)
- `app/src/main/java/com/shadowai/app/adapters/AdapterHealthChecker.kt` (line 11)

**Issue:** 
- Single global circuit-breaker in TaskExecutor
- Explicit local bypass logic
- Adapter-level breaker/health classes exist but not wired

**Resolution:** Choose one architecture:

**Recommended:** Wire adapter-level breakers:
```kotlin
// In ProviderAdapterFactory
fun createAdapter(provider: Provider): ProviderAdapter {
    val healthChecker = AdapterHealthChecker(provider.id)
    val circuitBreaker = AdapterCircuitBreaker(
        providerId = provider.id,
        healthChecker = healthChecker
    )
    return ProviderAdapterImpl(provider, circuitBreaker)
}

// Remove global breaker from TaskExecutor
// Each adapter manages its own health
```

---

#### B5. Unsafe Adapter Cache Keying
**Severity:** SEVERE - Config changes serve stale adapters

**Files:**
- `app/src/main/java/com/shadowai/app/adapters/ProviderAdapterFactory.kt` (lines 35, 41)
- `app/src/main/java/com/shadowai/app/adapters/AdapterBridge.kt` (lines 26, 30)

**Issue:** Cache keyed only by `ProviderId`, so API key/base URL/model changes don't invalidate cache.

**Fix:** Include config hash in cache key:
```kotlin
// ProviderAdapterFactory.kt
private val adapterCache = ConcurrentHashMap<String, ProviderAdapter>()

fun getAdapter(provider: Provider): ProviderAdapter {
    val cacheKey = """${provider.id}:${provider.apiKey.hashCode()}:${provider.baseUrl.hashCode()}"""
    
    return adapterCache.computeIfAbsent(cacheKey) {
        createAdapter(provider)
    }
}

// Invalidate on config change
fun invalidateProvider(providerId: ProviderId) {
    adapterCache.keys.filter { it.startsWith("$providerId:") }
        .forEach { adapterCache.remove(it) }
}
```

---

### Priority 3: Security Issues (Fix During Phase 4)

#### C1. Secret Handling Regressions
**Severity:** CRITICAL - API keys exposed as strings and in URLs

**Files:**
- `app/src/main/java/com/shadowai/app/adapters/ProviderAdapter.kt` (line 70)
- `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderRepository.kt` (line 78)
- `provider-adapters/src/main/kotlin/com/shadowai/provideradapters/GeminiAdapter.kt` (lines 73, 169, 244, 370)

**Issues:**
1. API keys converted to immutable strings (should use SecretBytes)
2. Gemini key put in URL query params (should be in header)

**Fix:**
```kotlin
// ProviderRepository.kt - use SecretBytes
suspend fun getProviderApiKey(providerId: ProviderId): SecretBytes {
    return secretRepository.getSecretBytes(providerId.toString())
        ?: throw SecurityException("API key not found")
}

// GeminiAdapter.kt - use header instead of query param
private fun createRequest(prompt: String, apiKey: SecretBytes): Request {
    // ❌ WRONG: .url("...?key=${apiKey.toString()}")
    // ✅ CORRECT:
    return Request.Builder()
        .url(baseUrl)
        .header("Authorization", "Bearer ${apiKey.toCharArray()}")
        // or .header("x-goog-api-key", apiKey.toCharArray())
        // ...
        .build()
}
```

---

#### C2. PII Masking Implementation/Test Mismatch
**Severity:** SEVERE - Tests expect different behavior than implementation

**Files:**
- `app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt` (line 63)
- `app/src/test/kotlin/com/shadowai/app/security/PiiMaskingProcessorTest.kt` (lines 30, 76, 172)

**Issue:**
- Implementation uses generic `[REDACTED]` placeholder
- Tests expect type-specific placeholders (e.g., `[EMAIL]`, `[PHONE]`)

**Fix Option A (Update implementation):**
```kotlin
// Add type-specific masking
fun maskPiiWithTypes(text: String): MaskedResult {
    val masks = mutableMapOf<String, PiiType>()
    var masked = text
    
    // Replace with type-specific placeholders
    masked = EMAIL_PATTERN.replace(masked) { match ->
        masks[match.value] = PiiType.EMAIL
        "[EMAIL]"
    }
    masked = PHONE_PATTERN.replace(masked) { 
        masks[it.value] = PiiType.PHONE
        "[PHONE]"
    }
    // ... etc
    
    return MaskedResult(masked, masks)
}
```

**Fix Option B (Update tests):** Change test expectations to use `[REDACTED]`. Not recommended for UX clarity.

---

#### C3. TLS Pinning with Placeholder Markers
**Severity:** HIGH - Security risk if deployed

**Files:**
- `app/src/main/res/xml/network_security_config.xml` (lines 38, 40)

**Issue:** Contains "replace with actual pin" markers - creates outage risk if left as-is.

**Fix:** Either remove pinning (safer default) or add real pins:
```xml
<!-- Option A: Remove pinning (recommended for development) -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>

<!-- Option B: Add real pins (for production) -->
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set expiration="2025-01-01">
            <pin digest="SHA-256">sha256/AAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <pin digest="SHA-256">sha256/BBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

## Additional Build Configuration

### Cross-module Parcelize
All modules using `@Parcelize` need the plugin:

```kotlin
// model-catalog/build.gradle.kts
plugins {
    id("kotlin-parcelize")
}

// app/build.gradle.kts
plugins {
    id("kotlin-parcelize")
}

// provider-adapters/build.gradle.kts (if using Parcelable)
plugins {
    id("kotlin-parcelize")
}
```

### Provider-Adapters DI Module
Create module wiring:

```kotlin
// provider-adapters/src/main/kotlin/com/shadowai/provideradapters/di/ProviderAdaptersModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderAdaptersModule {
    @Binds
    @Singleton
    abstract fun bindProviderRepository(
        impl: ProviderRepositoryImpl
    ): com.shadowai.core_contracts.providers.ProviderRepository
}
```

---

## Updated Dependency Graph

```
Audit Issues ───────────────────┐
  A1 (Parcelize)                │
  A3 (ProviderRepo)             │
  A5 (TeeKeyManager)            │
       │                        │
       ▼                        ▼
  Phase 1 (Hilt) ──────────► ModelCatalog
       │                        │
       ▼                        ▼
  Phase 2 (AIDL)           ModelDescriptor
       │                        │
       ▼                        ▼
  Phase 3 (JNI) ◄──────────┐   Phase 5 (AI)
       │                   │        │
       ▼                   │        ▼
  Phase 4 (Security) ◄─────┴─── B3 (Execution)
       │                        C1 (Secrets)
       ▼                        C2 (PII)
  Phase 6 (Thread)
       │
       ▼
  Phase 7 (Optimize)
```

**Critical Chain:** A1 → Phase 1 → Phase 4 → Security Fixes

---

## Table of Contents
1. [Phase 1: Hilt & DI Foundation (Days 1-3)](#phase-1-hilt--di-foundation-days-1-3)
2. [Phase 2: AIDL & IPC (Days 4-6)](#phase-2-aidl--ipc-days-4-6)
3. [Phase 3: JNI & Native Bridge (Days 7-12)](#phase-3-jni--native-bridge-days-7-12)
4. [Phase 4: Security Layer (Days 13-15)](#phase-4-security-layer-days-13-15)
5. [Phase 5: AI Integration (Days 16-20)](#phase-5-ai-integration-days-16-20)
6. [Phase 6: Thread Safety (Days 21-23)](#phase-6-thread-safety-days-21-23)
7. [Phase 7: Optimization (Days 24-30)](#phase-7-optimization-days-24-30)

---

## Phase 1: Hilt & DI Foundation (Days 1-3)

**Objective:** Fix all Hilt module constructor mismatches and circular dependencies to enable compilation.

**Validation Gate:** `./gradlew :app:compileDebugKotlin` must pass before proceeding.

### 1.1 Create ModelCatalogModule.kt

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/di/ModelCatalogModule.kt`

**Purpose:** Provide ModelDiscovery with its required dependencies using proper Hilt patterns.

**Dependencies Required:**
- `@ApplicationContext Context` - For file system access
- `Gson` - For JSON parsing
- `LocalModelEngine` - Abstraction for local model discovery
- `ProviderSecretRepository` - Secure API key storage
- `ProviderCrudRepository` - Provider CRUD operations
- `ProviderNetworkTester` - Network validation

**Implementation Details:**
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
    ): ModelDiscovery
}
```

**Build Configuration Changes:**
- Add to `app/build.gradle.kts` dependencies:
  - `implementation("com.google.code.gson:gson:2.10.1")`
  - `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")`

**Test Strategy:**
- Unit test: Verify module compiles and provides all dependencies
- Integration test: Inject ModelDiscovery in test class, verify all sub-dependencies resolve

**Blocking Issue if Skipped:** ModelDiscovery cannot be instantiated; KSP will fail with `error.NonExistentClass`

---

### 1.2 Fix AgentModule.kt Constructor Mismatches

**Current Broken Code:**
```kotlin
@Provides
@Singleton
fun provideSupervisorAgent(
    gson: Gson,
    dispatcher: TaskDispatcher
): SupervisorAgent  // ❌ WRONG - actual constructor needs Context, ShadowAgent, etc.
```

**Required Fix:**
Match actual `SupervisorAgent` constructor signature:
```kotlin
class SupervisorAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowAgent: ShadowAgent,  // This creates circular dependency!
    private val taskExecutor: TaskExecutor,
    private val routingEngine: RoutingEngine,
    private val pipelinePlanner: PipelinePlanner,
    private val verificationEngine: VerificationEngine
)
```

**Resolution Strategy:**
Replace direct injection with `Lazy<SupervisorAgent>` in ShadowAgent to break cycle:

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt` (modify existing)

**Change:**
```kotlin
class ShadowAgent @Inject constructor(
    ...
    private val supervisorAgent: Lazy<SupervisorAgent> // ✅ Lazy breaks cycle
)
```

**Then in AgentModule.kt:**
```kotlin
@Provides
@Singleton
fun provideSupervisorAgent(
    @ApplicationContext context: Context,
    taskExecutor: TaskExecutor,
    routingEngine: RoutingEngine,
    pipelinePlanner: PipelinePlanner,
    verificationEngine: VerificationEngine
): SupervisorAgent {
    // ShadowAgent removed from constructor - set lazily via property injection
    return SupervisorAgent(
        context = context,
        taskExecutor = taskExecutor,
        routingEngine = routingEngine,
        pipelinePlanner = pipelinePlanner,
        verificationEngine = verificationEngine
    )
}
```

**Alternative Approach (if Lazy fails):**
Use event bus pattern with `Flow<AgentCommand>` to decouple ShadowAgent and SupervisorAgent completely.

**Build Configuration:** No changes required.

**Test Strategy:**
- Unit test: Instantiate SupervisorAgent with mocked dependencies
- Verify KSP generates valid `SupervisorAgent_Factory` class
- Run `./gradlew kaptDebugKotlin` to verify annotation processing succeeds

**Blocking Issue if Skipped:** Hilt injection will fail at runtime with circular dependency exception; compilation may succeed but app will crash

---

### 1.3 Create PipelinePlanner Stub

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelinePlanner.kt`

**Purpose:** Unblock model-catalog and agent modules that reference PipelinePlanner.

**Minimum Viable Implementation:**
```kotlin
@Singleton
class PipelinePlanner @Inject constructor(
    private val adapterRegistry: AdapterRegistry
) {
    fun createTransformGraph(): TransformGraph { 
        return TransformGraph()  // Stub implementation
    }
    
    fun planPipeline(
        inputModality: Modality,
        outputModality: Modality,
        policy: RoutingPolicy = RoutingPolicy.BALANCED
    ): PipelinePlan? = null  // Full implementation in Phase 5
}
```

**Missing Definitions to Create:**
- `TransformGraph` - Graph data structure for modality transformations
- `PipelinePlan` - Data class containing ordered stages
- `RoutingPolicy` enum - MIN_LATENCY, MIN_COST, MAX_QUALITY, BALANCED
- `Modality` enum - TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT

**Build Configuration Changes:**
Add to `pipeline-planner/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":core-contracts"))
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
}
```

**Test Strategy:**
- Unit test: Verify PipelinePlanner can be instantiated via Hilt
- Verify createTransformGraph() returns non-null

**Blocking Issue if Skipped:** SupervisorAgent cannot be instantiated; compilation fails

---

### 1.4 Fix ProviderRepository Type Imports

**Current Issue:** `AgentModule.kt` may import wrong ProviderRepository path.

**Correct Import:**
```kotlin
import com.shadowai.core_contracts.providers.ProviderRepository
```

**Verify:**
- `ProviderRepositoryImpl` in `:provider-adapters` implements interface correctly
- All method signatures match between interface and implementation
- No references to old `:app` module ProviderRepository

**Files to Check:**
- `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/di/AgentModule.kt`
- `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/di/ProviderModule.kt`
- `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt`

**Build Configuration:** No changes.

**Test Strategy:**
- Run `./gradlew :app:compileDebugKotlin` - should pass without type mismatch errors

---

## Phase 2: AIDL & IPC (Days 4-6)

**Objective:** Create proper AIDL interfaces for cross-process communication.

**Validation Gate:** AIDL files generate Java interfaces; `IsolatedInferenceManager` uses generated classes, not stubs.

### 2.1 Create IInferenceService.aidl

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/aidl/com/shadowai/app/ai/IInferenceService.aidl`

**Content Requirements:**
```aidl
package com.shadowai.app.ai;

import com.shadowai.app.ai.IGenerationCallback;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IInferenceService {
    // Core inference operations
    Bundle loadModel(in ParcelFileDescriptor modelPfd, in Bundle config);
    Bundle generate(in Bundle request);
    void generateStreaming(in Bundle request, IGenerationCallback callback);
    Bundle unloadModel(in String modelId);
    
    // Service management
    Bundle getServiceInfo();
    Bundle getMemoryStats();
    void ping();
}
```

**Key Design Decisions:**
- Use `ParcelFileDescriptor` instead of `File` - required for cross-process file access
- All methods return `Bundle` for flexibility in passing complex data
- `generateStreaming()` uses callback pattern for token-by-token delivery

**Build Configuration Changes:**
Add to `app/build.gradle.kts`:
```kotlin
android {
    buildFeatures {
        aidl true
    }
    
    aidlPackagedNames += ["com.shadowai.app.ai"]
}
```

**Generated Output:** After build, Android Gradle Plugin generates:
- `IInferenceService.java` in `app/build/generated/aidl_source_output_dir/...`
- `IInferenceService.Stub` abstract class for service implementation
- `IInferenceService.Stub.Proxy` for client-side IPC

---

### 2.2 Create IGenerationCallback.aidl

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/aidl/com/shadowai/app/ai/IGenerationCallback.aidl`

**Content:**
```aidl
package com.shadowai.app.ai;

interface IGenerationCallback {
    oneway void onToken(String token);
    oneway void onComplete(String fullText, int tokensGenerated);
    oneway void onError(int errorCode, String message);
    oneway void onProgress(float progress);
}
```

**Key Design Decisions:**
- `oneway` keyword allows client to call without waiting (async)
- `onToken()` delivers each generated token individually
- `onProgress()` provides percentage for UI progress bars

---

### 2.3 Update IsolatedInferenceManager

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt`

**Current Issue:** Has stub interfaces with `TODO("Generated from AIDL")` - won't compile.

**Implementation Details:**

1. **Delete stub IInferenceService interface** - will use generated class instead
2. **Import generated AIDL class:**
```kotlin
import com.shadowai.app.ai.IInferenceService
import com.shadowai.app.ai.IGenerationCallback
```

3. **Update loadModel() to use ParcelFileDescriptor:**
```kotlin
suspend fun loadModel(modelFile: File, config: InferenceConfig): ModelHandle {
    val pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
    
    val bundle = Bundle().apply {
        putParcelable(InferenceServiceContracts.LoadModel.MODEL_PFD, pfd)
        putInt(InferenceServiceContracts.LoadModel.CONTEXT_SIZE, config.contextSize)
        // ... other config
    }
    
    val result = service?.loadModel(pfd, bundle)
    return ModelHandle(result?.getString("model_id") ?: "")
}
```

4. **Add DeathRecipient for service death detection:**
```kotlin
private val deathRecipient = IBinder.DeathRecipient {
    _serviceConnectionState.value = ServiceConnectionState.DISCONNECTED
    reconnect()
}

// In onServiceConnected():
service?.asBinder()?.linkToDeath(deathRecipient, 0)
```

---

### 2.4 Create InferenceServiceContracts.kt

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/ai/InferenceServiceContracts.kt`

**Purpose:** Centralize all Bundle keys used in AIDL IPC to prevent typos.

**Content Structure:**
```kotlin
object InferenceServiceContracts {
    object LoadModel {
        const val MODEL_PFD = "model_pfd"
        const val CONTEXT_SIZE = "context_size"
        const val MAX_TOKENS = "max_tokens"
        const val TEMPERATURE = "temperature"
        // ... etc
    }
    
    object Response {
        const val SUCCESS = "success"
        const val ERROR_MESSAGE = "error_message"
        const val MODEL_ID = "model_id"
    }
}
```

---

### 2.5 Update InferenceService in :inference_process

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/inference_process/src/main/kotlin/com/shadowai/inference/InferenceService.kt`

**Current Issue:** Has `external fun` declarations with no JNI implementation.

**Implementation Strategy:**

1. Extend generated stub:
```kotlin
class InferenceService : Service() {
    private val binder = object : IInferenceService.Stub() {
        override fun loadModel(pfd: ParcelFileDescriptor?, config: Bundle?): Bundle {
            // Implementation
        }
        // ... implement all AIDL methods
    }
    
    override fun onBind(intent: Intent): IBinder = binder
}
```

2. JNI loading with safety check:
```kotlin
companion object {
    init {
        try {
            System.loadLibrary("inference_jni")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
}
```

---

### 2.6 Verify AndroidManifest.xml Service Declaration

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/AndroidManifest.xml`

**Required Entries:**
```xml
<service
    android:name="com.shadowai.inference.InferenceService"
    android:process=":inference"
    android:exported="false"
    android:isolatedProcess="true">
    <intent-filter>
        <action android:name="com.shadowai.inference.LOCAL_INFERENCE" />
    </intent-filter>
</service>
```

**isolatedProcess="true"** is critical for zero-trust security boundary.

---

## Phase 3: JNI & Native Bridge (Days 7-12)

**Objective:** Implement C++ JNI layer bridging Kotlin InferenceService to llama.cpp

**Validation Gate:** Native library loads successfully; model can be loaded via JNI.

### 3.1 Create JNI Directory Structure

**Structure:**
```
inference_process/src/main/
├── cpp/
│   ├── CMakeLists.txt
│   ├── InferenceEngine.cpp
│   ├── InferenceEngine.h
│   └── llama.cpp/ (submodule or prebuilt)
├── kotlin/com/shadowai/inference/
│   └── NativeBridge.kt
└── Android.mk (optional, if not using CMake)
```

---

### 3.2 Create InferenceEngine.cpp

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/inference_process/src/main/cpp/InferenceEngine.cpp`

**Core Implementation Strategy:**

1. **Store llama_context as jlong in Kotlin:**
```cpp
// Store pointer as long for Kotlin
static std::unordered_map<jlong, llama_context*> contexts;
static jlong nextHandle = 1;

// In loadModel:
llama_context* ctx = llama_new_context_with_model(model, ctx_params);
jlong handle = nextHandle++;
contexts[handle] = ctx;
return handle;  // Return to Kotlin as "nativeHandle"
```

2. **JNI Method Signatures:**
```cpp
extern "C" JNIEXPORT jlong JNICALL
Java_com_shadowai_inference_NativeBridge_loadModel(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath,
    jint contextSize,
    jint gpuLayers
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    
    // Load llama model
    llama_model_params model_params = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path, model_params);
    
    env->ReleaseStringUTFChars(modelPath, path);
    
    if (!model) return -1;
    
    // Create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    
    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) return -1;
    
    jlong handle = nextHandle++;
    contexts[handle] = ctx;
    return handle;
}
```

3. **Generation with callback:**
```cpp
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_inference_NativeBridge_generate(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jstring prompt,
    jint maxTokens,
    jobject callback  // IGenerationCallback
) {
    llama_context* ctx = contexts[handle];
    if (!ctx) return nullptr;
    
    // Tokenize prompt
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::vector<llama_token> tokens;
    // ... tokenization
    
    // Generation loop
    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sample_token(ctx, ...);
        
        // Convert token to string
        char piece[128];
        llama_token_to_piece(ctx, token, piece, sizeof(piece), 0);
        
        // Call Java callback
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
        jstring tokenStr = env->NewStringUTF(piece);
        env->CallVoidMethod(callback, onTokenMethod, tokenStr);
        env->DeleteLocalRef(tokenStr);
        
        if (token == llama_token_eos(ctx)) break;
    }
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(fullResponse.c_str());
}
```

---

### 3.3 Create CMakeLists.txt

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/inference_process/src/main/cpp/CMakeLists.txt`

**Content:**
```cmake
cmake_minimum_required(VERSION 3.10.2)
project(inference_jni)

# Find Android NDK
find_library(log-lib log)

# llama.cpp integration
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Add llama.cpp source files
set(LLAMA_SOURCES
    ${LLAMA_DIR}/llama.cpp
    ${LLAMA_DIR}/ggml.c
    ${LLAMA_DIR}/ggml-alloc.c
    ${LLAMA_DIR}/ggml-backend.c
    ${LLAMA_DIR}/ggml-quants.c
    ${LLAMA_DIR}/sampling.cpp
    ${LLAMA_DIR}/unicode.cpp
    ${LLAMA_DIR}/unicode-data.cpp
)

# Create shared library
add_library(inference_jni SHARED
    InferenceEngine.cpp
    ${LLAMA_SOURCES}
)

# Include directories
target_include_directories(inference_jni PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/include
)

# Compiler flags
target_compile_options(inference_jni PRIVATE
    -O3
    -DNDEBUG
    -DLLAMA_USE_LLAMAFILE
    -mcpu=native
)

# Link libraries
target_link_libraries(inference_jni
    ${log-lib}
    android
)
```

---

### 3.4 Create NativeBridge.kt

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/inference_process/src/main/kotlin/com/shadowai/inference/NativeBridge.kt`

**Purpose:** Kotlin wrapper for JNI calls with type safety.

**Implementation:**
```kotlin
class NativeBridge {
    companion object {
        init {
            NativeLoader.load()
        }
    }
    
    external fun loadModel(modelPath: String, contextSize: Int, gpuLayers: Int): Long
    external fun unloadModel(handle: Long): Boolean
    external fun generate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String
    external fun generateStreaming(handle: Long, prompt: String, maxTokens: Int, callback: TokenCallback)
    external fun getModelInfo(handle: Long): ModelInfo
    
    interface TokenCallback {
        fun onToken(token: String)
        fun onComplete(fullText: String)
        fun onError(error: String)
    }
}

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

**Alternative (NativeLoader in dedicated class):** - prevents multiple loads during rescan

---

### 3.5 Add ABI Filter in build.gradle.kts

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/inference_process/build.gradle.kts`

**Changes:**
```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a") // Only 64-bit
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

---

### 3.6 Add ABI Validation in IsolatedInferenceManager

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt` (add method)

**Implementation:**
```kotlin
fun validateDeviceSupport(): Boolean {
    val supportedAbis = Build.SUPPORTED_ABIS
    return supportedAbis.any { it == "arm64-v8a" }
}

// In bindService():
require(validateDeviceSupport()) {
    "Device does not support arm64-v8a. GGUF models require 64-bit architecture."
}
```

---

## Phase 4: Security Layer (Days 13-15)

**Objective:** Fix PII regex patterns and integrate SecretBytes for API keys.

**Validation Gate:** `PiiMaskingProcessorTest` achieves 90%+ coverage; all test cases pass.

### 4.1 Fix PiiMaskingProcessor Regex

**Current Issue:** Double-escaped backslashes in regex patterns.

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt`

**Fix Strategy:**

1. **Use raw strings ("""):**
```kotlin
// ❌ BROKEN (escaped newlines):
val PHONE_PATTERN = Regex("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b")

// ✅ FIXED (raw string):
val PHONE_PATTERN = Regex(r"""\b\d{3}[-.]?\d{3}[-.]?\d{4}\b""")
```

2. **Use Android's Patterns for EMAIL:**
```kotlin
import android.util.Patterns

val emailMatcher = Patterns.EMAIL_ADDRESS.matcher(text)
while (emailMatcher.find()) {
    // Use Android's OS-maintained pattern
}
```

**Complete Pattern Set:**
- EMAIL: `Patterns.EMAIL_ADDRESS`
- PHONE: `Patterns.PHONE`
- CREDIT CARD: Raw regex with Luhn validation
- SSN: `r"""\b(?!000|666|9\d{2})\d{3}[-\s]?(?!00)\d{2}[-\s]?(?!0000)\d{4}\b"""`
- IP ADDRESS: `r"""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b"""`
- API KEY: Context-aware detection (high entropy + keywords)

---

### 4.2 Integrate SecretBytes for API Keys

**File:** Modify `/mnt/c/Users/anon3/Downloads/ShadowAi/model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDiscovery.kt`

**When:** ModelDiscovery encounters API keys in model.json

**Implementation:**
```kotlin
import com.shadowai.core.security.SecretBytes

fun discoveredApiKey(key: String): SecretBytes {
    return SecretBytes(key.toByteArray(Charsets.UTF_8)).also {
        // Clear original string from memory
        (key as java.lang.String).apply { 
            kotlin.text.replace(0, length, '0') 
        }
    }
}
```

---

### 4.3 Create Comprehensive Test Suite

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/test/kotlin/com/shadowai/app/security/PiiMaskingProcessorTest.kt`

**Test Structure:**

1. **Email Detection Tests:**
- Valid emails: user@example.com, user+tag@example.co.uk
- Invalid: @example.com, user@, user@.com, no-at-sign
- International domains: пользователь@пример.рф

2. **Phone Detection Tests:**
- Formats: 123-456-7890, (123) 456-7890, 123.456.7890, 1234567890
- International: +1 123-456-7890
- Invalid: 123-45-6789 (SSN pattern)

3. **Credit Card Tests:**
- Valid: 4532-0151-1283-0356 (Visa), 5555 5555 5555 4444 (Mastercard)
- With Luhn validation
- Invalid cards should not match

4. **API Key Tests:**
- Context-aware: "api_key": "32charstring" should match
- High entropy without context: should not match

**Coverage Target:** 90%+ line coverage, 85%+ branch coverage

---

## Phase 5: AI Integration (Days 16-20)

**Objective:** Fix context management, task detection order, and capability-based routing.

**Validation Gate:** AgenticLoop can run 50-turn conversation without OOM; task types correctly detected.

### 5.1 Add maxContext to ModelDescriptor

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDescriptor.kt`

**Additions:**
```kotlin
@Parcelize
data class ModelDescriptor(
    val id: String,
    val name: String,
    val providerId: ProviderId,
    val maxContext: Int = 4096, // NEW
    val capabilities: Set<Capability> = emptySet(),
    // ... rest
) : Parcelable {
    fun getAvailableGenerationTokens(inputTokens: Int): Int {
        return (maxContext - inputTokens).coerceAtLeast(0)
    }
}
```

**Annotation:** Requires `@Parcelize` plugin in:
- `app/build.gradle.kts`
- `model-catalog/build.gradle.kts`

```kotlin
plugins {
    id("kotlin-parcelize")
}
```

---

### 5.2 Implement TikToken-Style Token Counter

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt`

**Implementation:**
```kotlin
private fun countTokens(text: String): Int {
    // Approximation: ~4 characters per token for English text
    // TikToken average: 4.5-5 chars per token
    return text.length / 4
}
```

**Note:** Full TikToken in Kotlin requires porting the BPE algorithm. For MVP, character approximation is acceptable (~10-20% variance).

---

### 5.3 Implement Sliding Window Context Truncation

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt`

**Implementation:**
```kotlin
private fun trimToAvailableContext(state: AgenticLoopState): AgenticLoopState {
    val maxTokens = state.modelDescriptor.maxContext
    val currentTokens = countTokens(state.context)
    
    if (currentTokens <= maxTokens * 0.9) {
        return state // Within limit (90% threshold for safety)
    }
    
    // Sliding window: Keep last N exchanges
    val exchanges = state.context.split("\n", ">>> ", "<<< ")
        .filter { it.isNotBlank() }
    
    // Keep system prompt + last 5 exchanges
    val recentExchanges = exchanges.takeLast(5)
    val truncatedContext = recentExchanges.joinToString("\n>>> ")
    
    // Update state
    return state.copy(
        context = truncatedContext,
        truncated = true,
        truncationPoint = state.iterationCount
    )
}
```

**Configuration:**
```kotlin
data class AgenticLoopConfig(
    val maxContextWindow: Int = 0, // 0 = use model default
    val slidingWindowSize: Int = 5, // Keep last 5 exchanges
    val contextThreshold: Float = 0.9f // Start trimming at 90% capacity
)
```

---

### 5.4 Fix Task Type Detection Order

**Current Issue:** Task type determined AFTER prompt injection filter (hides jailbreaks).

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt`

**Required Change:**
```kotlin
suspend fun processInput(input: String): AgentResult {
    // 1. FIRST: Determine task type from ORIGINAL input
    val taskType = determineTaskType(input)
    
    // 2. THEN: Run prompt injection defense
    val scanResult = promptInjectionDefense.scan(input)
    // ...
    
    // 3. Use sanitized prompt, but task type from original
    val currentPrompt = scanResult.sanitizedPrompt
    
    // Route based on original taskType (not affected by filters)
    if (isComplexTaskType(taskType, currentPrompt)) {
        // ...
    }
}
```

**Why:** If a jailbreak is hidden by injection filter, the filtered text might look like a simple question when it was actually a complex task with hidden instructions.

---

### 5.5 Ensure PromptInjectionDefense Runs Before PipelinePlanner

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/SupervisorAgent.kt`

**Required Ordering:**
```kotlin
suspend fun processInput(input: String, policy: RoutingPolicy): SupervisorResult {
    // 1. Security first
    val scanResult = promptInjectionDefense.scan(input)
    if (!scanResult.isSafe) {
        return SupervisorResult.Error(...)
    }
    
    // 2. Determine if we need pipeline
    val taskType = determineTaskType(input)
    val requiresPipeline = isMultimodalTask(taskType)
    
    // 3. Only allocate PipelinePlanner if needed
    if (requiresPipeline) {
        val pipeline = pipelinePlanner.createPlan(...)
        
        // 4. Security check on generated plan
        val pipelineScan = promptInjectionDefense.scan(pipeline.toString())
        if (!pipelineScan.isSafe) {
            return SupervisorResult.Error(...)
        }
    }
    
    // ... continue execution
}
```

---

## Phase 6: Thread Safety (Days 21-23)

**Objective:** Fix concurrent model discovery and state management.

**Validation Gate:** Concurrent rescans don't create duplicate entries; atomic state changes are safe.

### 6.1 Add Mutex to ModelDiscovery

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDiscovery.kt`

**Implementation:**
```kotlin
class ModelDiscovery @Inject constructor(...) {
    private val scanMutex = Mutex()
    
    suspend fun rescan(): List<ModelInfo> = scanMutex.withLock {
        // Only one scan runs at a time
        return performFullScan()
    }
}
```

**Alternative:** AtomicBoolean guard for simpler cases:
```kotlin
private val isScanning = AtomicBoolean(false)

suspend fun rescan(): List<ModelInfo> {
    if (!isScanning.compareAndSet(false, true)) {
        return emptyList() // Already scanning
    }
    try {
        return performFullScan()
    } finally {
        isScanning.set(false)
    }
}
```

---

### 6.2 Fix Model ID Generation

**Current Issue:** String concatenation causes UI glitches with duplicate filenames.

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDiscovery.kt`

**Implementation:**
```kotlin
class ModelDiscovery {
    private val nextModelId = AtomicLong(0)
    
    fun generateModelId(path: String, name: String): String {
        // Create unique hash from path + name
        val hash = (path.hashCode().toLong() shl 32) or name.hashCode().toLong()
        val sequence = nextModelId.getAndIncrement()
        return "model_${hash}_$sequence"
    }
}
```

**Benefits:**
- Same file in different directories gets different IDs
- Sequence number ensures uniqueness even with hash collision
- Deterministic for same path+name combination

---

### 6.3 Replace @Volatile with AtomicReference

**File:** Various agent state holders

**Current Issue:**
```kotlin
@Volatile
private var currentState: State? = null  // Not thread-safe for complex updates
```

**Fix:**
```kotlin
private val currentState = AtomicReference<State>(null)

// Usage:
fun updateState(newState: State) {
    currentState.set(newState)
}

fun getState(): State? {
    return currentState.get()
}

// Atomic compare-and-set:
fun transitionState(expected: State, newState: State): Boolean {
    return currentState.compareAndSet(expected, newState)
}
```

**Files to Update:**
- `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt`
- `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt`

---

## Phase 7: Optimization (Days 24-30)

**Objective:** Add @Parcelize, database persistence, and memory pressure handling.

**Validation Gate:** Model paths persist after SD card path changes; memory pressure triggers model unloading.

### 7.1 Add @Parcelize to ModelDescriptor

**Already covered in 5.1** - requires `kotlin-parcelize` plugin.

**Additional Parcelable Classes:**
```kotlin
@Parcelize
data class ModelInfo(
    val id: String,
    val descriptor: ModelDescriptor,
    val localPath: String?
) : Parcelable

@Parcelize
data class LocalModelInfo(
    val path: String,
    val name: String,
    val size: Long
) : Parcelable
```

---

### 7.2 Create ShadowDatabase for Model Path Persistence

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/app/src/main/java/com/shadowai/app/data/db/ShadowDatabase.kt`

**Entities:**
```kotlin
@Entity(tableName = "model_paths")
data class ModelPathEntity(
    @PrimaryKey
    val path: String,
    val modelId: String,
    val lastDiscovered: Long = System.currentTimeMillis(),
    val valid: Boolean = true
)
```

**DAO:**
```kotlin
@Dao
interface ModelPathDao {
    @Query("SELECT * FROM model_paths WHERE valid = 1")
    suspend fun getValidPaths(): List<ModelPathEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePath(entity: ModelPathEntity)
    
    @Query("UPDATE model_paths SET valid = 0 WHERE path = :path")
    suspend fun invalidatePath(path: String)
}
```

**Database Class:**
```kotlin
@Database(entities = [ModelPathEntity::class], version = 1)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun modelPathDao(): ModelPathDao
}
```

**Hilt Module:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShadowDatabase {
        return Room.databaseBuilder(
            context,
            ShadowDatabase::class.java,
            "shadow_database"
        ).build()
    }
}
```

**Modification to ModelDiscovery:**
```kotlin
class ModelDiscovery @Inject constructor(
    private val modelPathDao: ModelPathDao,
    // ... other deps
) {
    suspend fun getDefaultLocalModelDirs(): List<String> {
        // Check database first
        val cached = modelPathDao.getValidPaths()
        if (cached.isNotEmpty()) {
            return cached.map { it.path }
        }
        
        // Fallback to file walk
        val discovered = walkFileTree()
        discovered.forEach { path ->
            modelPathDao.savePath(ModelPathEntity(path = path, modelId = generateModelId(path, "")))
        }
        return discovered
    }
}
```

---

### 7.3 Implement onTrimMemory in InferenceService

**File:** `/mnt/c/Users/anon3/Downloads/ShadowAi/inference_process/src/main/kotlin/com/shadowai/inference/InferenceService.kt`

**Implementation:**
```kotlin
class InferenceService : Service() {
    private val loadedModels = ConcurrentHashMap<String, ModelInstance>()
    
    override fun onTrimMemory(level: Int) {
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Critical memory pressure, unloading LRU model")
                unloadLRUModel()
            }
            TRIM_MEMORY_RUNNING_LOW -> {
                Log.i(TAG, "Low memory, considering model unload")
                // Could reduce cache sizes here
            }
        }
    }
    
    private fun unloadLRUModel() {
        val lruModel = loadedModels.values.minByOrNull { it.lastUsed }
        lruModel?.let {
            unloadModel(it.modelId)
            loadedModels.remove(it.modelId)
        }
    }
    
    data class ModelInstance(
        val modelId: String,
        val nativeHandle: Long,
        var lastUsed: Long = System.currentTimeMillis()
    )
}
```

---

### 7.4 Add RecyclerView Optimization

**File:** Any RecyclerView showing model lists

**Implementation:**
```kotlin
recyclerView.setHasFixedSize(true)
recyclerView.itemAnimator = null  // Disable animations for fast updates
recyclerView.layoutManager = LinearLayoutManager(context).apply {
    recycleChildrenOnDetach = true
}
adapter.setHasStableIds(true)  // Enable ID-based diffing
```

---

## Integration Testing Strategy

### Phase 1 Tests
```bash
./gradlew :app:compileDebugKotlin
./gradlew :pipeline-planner:compileDebugKotlin
./gradlew kaptDebugKotlin
```

### Phase 2 Tests
```bash
./gradlew :app:compileDebugAidl
# Verify IInferenceService.java generated in build directory
find app/build -name "IInferenceService.java" -type f
```

### Phase 3 Tests
```bash
./gradlew :inference_process:externalNativeBuildDebug
# Check .so files generated
find inference_process/build -name "*.so" -type f
```

### Phase 4 Tests
```bash
./gradlew :app:testDebugUnitTest --tests "*PiiMaskingProcessorTest*"
# Verify coverage > 90%
```

### Phase 5 Tests (Instrumented)
```bash
./gradlew :app:connectedDebugAndroidTest
# Test on ARM64 device/emulator
```

### E2E Test
```kotlin
@Test
fun testFiftyTurnConversation() = runTest {
    val agent = // ... setup
    repeat(50) { i ->
        val result = agent.processInput("Tell me something interesting #${i+1}")
        assertTrue(result is AgentResult.Conversation)
    }
}
```

---

## Dependency Graph

```
Phase 1 (Hilt) ─────────────────┐
         │                      │
         ▼                      ▼
  PipelinePlanner ────────► ModelCatalog
         │                      │
         ▼                      ▼
   Phase 5 (AI) ◄────────── ModelDescriptor
         │                      │
         ▼                      ▼
   Phase 2 (AIDL) ◄───────── InferenceContracts
         │                      │
         ▼                      ▼
   Phase 3 (JNI) ◄───────── NativeBridge
         │                      │
         ▼                      ▼
   Phase 6 (Thread)         Phase 4 (Security)
         │                      │
         └──────────┬───────────┘
                    ▼
             Phase 7 (Optimize)
```

**Critical Path:** Phase 1 → Phase 2 → Phase 3 → Phase 5

---

## Success Criteria

| Phase | Criterion | How to Verify |
|-------|-----------|---------------|
| 1 | Hilt compiles | `./gradlew :app:compileDebugKotlin` passes |
| 2 | AIDL generates | `find app/build -name "IInferenceService.java"` |
| 3 | Native lib loads | `.so` files exist and load without `UnsatisfiedLinkError` |
| 4 | PII > 90% coverage | JaCoCo report shows > 90% line coverage |
| 5 | No OOM at 50 turns | Instrumented test runs 50 iterations |
| 6 | No duplicates | Concurrent rescan produces same count |
| 7 | Paths persist | Uninstall/reinstall preserves model paths |

All phases must pass before production deployment.

---

**Document maintained by:** Architecture Team  
**Review cycle:** Weekly during recovery execution  
**Last updated:** 2026-02-10
