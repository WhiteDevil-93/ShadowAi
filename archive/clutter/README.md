# DarcyAI Android

> **Workspace hygiene note:** Only runtime build sources remain at the repo root. Reference documentation, audit reports, and tangential artifacts now live under `archive/clutter/` to prevent workspace clutter and keep CI/build contexts fast.

## Minimal Build Tree (see also `WORKTREE.md`)

- `app/` — Android application (Jetpack Compose UI, providers, security, execution engine, JNI integrations).
- `backend/` — Ktor-based image-generation services (Novita, PixAI, task cache).
- `buildSrc/`, `core-contracts/`, `model-catalog/`, `provider-adapters/`, `artifact-system/`, `pipeline-planner/`, `diagnostics/`, `hot-swapping/`, `ui-params/`, `ui-composition/`, `ui-validator/` — modular library/runtime logic.
- Root scripts/configs: `gradlew`, `gradlew.bat`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `.editorconfig`, `.gitattributes`, `.gitignore`, `.firebaserc`, `.pre-commit-config.yaml`.

## Archived Clutter

The following directories and files were archived to clean the workspace; use them from `archive/clutter/` when you need documentation, runbooks, or temporary outputs:

- `docs/`, `plans/`, `reports/`, `lint-results/`, `lint-baseline/`
- Audit/README artifacts (`README.md`, `AGENTS.md`, `CLAUDE.md`, `WORKTREE.md`, `CHANGELOG.md`, `FIX_SUMMARY*.txt`)
- Local/developer artifacts (`local.properties`, stray `nul` file)

Refer to `WORKTREE.md` for the definitive list of essential files that should remain tracked in this workspace.

---

A comprehensive AI assistant application for Android with support for multiple AI providers, local inference, image generation, and more.

## 🎨 UI/UX Governance

This project enforces strict UI/UX architectural standards through a comprehensive governance framework:

- **[UI Orchestrator System Prompt](./UI_ORCHESTRATOR_SYSTEM_PROMPT.md)** - Authoritative specification for all UI/UX decisions
- **[Governance Framework](./GOVERNANCE.md)** - Actionable rules and code review guidelines
- **[Quick Reference](./QUICK_REFERENCE.md)** - Developer cheat sheet for common patterns
- **[Compliance Checklist](./UI_ORCHESTRATOR_COMPLIANCE_CHECKLIST.md)** - Manual verification checklist
- **[PR Template](./.github/PULL_REQUEST_TEMPLATE.md)** - Mandatory checklist for all UI/UX changes

**All UI/UX changes must comply with the governance framework before merge.**

---

## Architecture Overview

### Core Layers

```text
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                      │
│  MainActivity, ProviderSetupWizard, ChatAdapter, UI Dialogs │
└─────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────┐
│                       Domain Layer                           │
│  TaskExecutor, CircuitBreaker, ShadowAgent, RoutingEngine   │
└─────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────┐
│                      Data Layer                              │
│  AdminRepository, ProviderRepository, ShadowDatabase, Cache │
└─────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────┐
│                    Provider Layer                            │
│  LiquidProvider, ActiveProviderManager, ProviderPlugin      │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. AI Providers System

**ProviderRepository** (`app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt`)

- Manages AI provider configurations (OpenAI, Anthropic, Gemini, Groq, etc.)
- Handles API key storage with encrypted SharedPreferences
- Provides provider discovery and model listing
- Key classes:
  - `Provider` - Provider configuration data class
  - `ProviderId` - Enum of supported providers
  - `ApiStyle` - API compatibility styles (OpenAI, Anthropic, Gemini, etc.)

**ProviderPlugin** (`app/src/main/java/com/shadowai/app/providers/ProviderPlugin.kt`)

- Plugin interface for custom AI providers
- `ProviderPluginRegistry` manages plugin lifecycle
- Supports priority-based provider selection

### 2. Task Execution System

**TaskExecutor** (`app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt`)

- Orchestrates task execution with routing policies
- Implements retry logic with exponential backoff
- Integrates with CircuitBreaker for fault tolerance
- Uses HybridAiExecutor for cloud/local provider routing

**CircuitBreaker** (`app/src/main/java/com/shadowai/app/execution/CircuitBreaker.kt`)

- Implements circuit breaker pattern for failure handling
- States: CLOSED, OPEN, HALF_OPEN
- Configurable thresholds for failure/success and timeout

**PlanParser** (`app/src/main/java/com/shadowai/app/tasks/PlanParser.kt`)

- Parses JSON plans into executable task graphs
- Supports DAG-based multi-step plans
- Validates dependencies and detects cycles

### 3. Local Inference

**LocalBrainManager** (`app/src/main/java/com/shadowai/app/ai/LocalBrainManager.kt`)

- Manages local model configuration
- Provides Retrofit-based API for local LLM servers
- Supports custom authentication headers

**LocalLiquidEngine** (injected via DI)

- Local inference engine integration
- Llama.cpp JNI bindings for native inference

### 4. Image Generation

**Backend Services** (`backend/src/main/kotlin/com/shadowai/backend/`)

- **NovitaService** - Novita AI image generation API
- **PixAiService** - PixAI image generation API
- **TaskCache** - Caches async generation tasks with TTL
- **WebhookVerifier** - Validates webhook signatures

### 5. Security

**SecurityManager** (`app/src/main/java/com/shadowai/app/security/SecurityManager.kt`)

- AES-256-GCM encryption for sensitive data
- RSA key pair management via Android Keystore
- EncryptedSharedPreferences integration

**AccessControlManager** - Role-based access control

- Permissions: READ_MESSAGES, SEND_MESSAGES, DEVICE_CONTROL, etc.
- Roles: GUEST, USER, POWER_USER, ADMIN

### 6. Feedback & Quality

**FeedbackManager** (`app/src/main/java/com/shadowai/app/feedback/FeedbackManager.kt`)

- User feedback collection and ratings
- Feedback types: HELPFUL, ACCURATE, RELEVANT, etc.
- ResponseQualityEvaluator for quality metrics

### 7. Caching

**TaskCache** (Backend) - In-memory task cache with LRU eviction
**LRUCache** (Android) - Generic LRU cache implementation
**DiskCache** - Persistent disk-based caching

### 8. Database

**ShadowDatabase** - Room database with:

- `ChatMessageEntity` - Chat message storage
- `MemoryEntity` - AI memory/long-term context
- `FeedbackEntity` - User feedback records
- `LedgerEntity` - Execution ledger
- `FailureEntity` - Failure tracking

## Dependency Injection

Hilt-based dependency injection throughout:

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var providerRepo: ProviderRepository
    @Inject lateinit var adminRepository: AdminRepository
    @Inject lateinit var agent: ShadowAgent
}
```

## Build Configuration

### Gradle Modules

- `app/` - Android application
- `backend/` - Ktor backend server for image generation

### Key Dependencies

- **Kotlin** - Coroutines, Flow, stdlib
- **Jetpack Compose** - UI (where used)
- **Hilt** - Dependency injection
- **Room** - Local database
- **Retrofit/OkHttp** - Networking
- **Coil** - Image loading
- **Ktor** - Backend server

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Lint checks
./gradlew lint
```

## Configuration

### Application Configuration (`application.conf` - Backend)

```hocon
novita {
  apiKey = ${NOVITA_API_KEY}
  baseUrl = "https://api.novita.ai/v3"
  webhookSecret = ${NOVITA_WEBHOOK_SECRET}
}

pixai {
  apiKey = ${PIXAI_API_KEY}
  baseUrl = "https://api.pixai.art"
  webhookSecret = ${PIXAI_WEBHOOK_SECRET}
}
```

### Provider Settings

- API keys stored in encrypted SharedPreferences
- Per-provider base URLs and model configurations
- Custom model support per provider

## Recent Refactoring

### Phase 1: Core Repository & Config Fixes

- Synchronous SharedPreferences writes with `commit()`
- Thread-safe disabled model operations with mutex
- Configurable API key from environment

### Phase 2: Caching & Performance

- O(n) LRU eviction algorithm
- Efficient batch removal with `removeIf`

### Phase 3: UI & Safety

- Explicit GCM tag length specification
- Proper AES-256 key generation
- Synchronous secure storage writes

### Phase 4: Execution & Architecture

- Result-based CircuitBreaker API
- Thread-safe random for jitter
- Comprehensive KDoc documentation

## Zero-Trust Architecture

DarcyAI implements a true zero-trust security model where every internal component is treated as a potential point of compromise. The architecture focuses on **continuous verification**, **micro-segmentation of AI logic**, and **cryptographic isolation**.

### 1. Security Architecture: Cryptographic Hardening

The `SecurityManager` uses AES-256-GCM, but a zero-trust model requires that no high-level application code ever touches raw key material.

#### A. TEE-Backed Isolation

**`TeeKeyManager`** (`app/src/main/java/com/shadowai/app/security/TeeKeyManager.kt`)

- Hardware-bound keys generated within StrongBox or TEE
- No-export policy with `setIsStrongBoxBacked(true)`
- `setUnlockedDeviceRequired(true)` for additional security
- Automatic detection of StrongBox/TEE availability

**`BiometricKeyManager`** (`app/src/main/java/com/shadowai/app/security/BiometricKeyManager.kt`)

- Biometric auth-bound keys requiring `BiometricPrompt` for access
- Keys never enter application process space in plain text
- Supports biometric strong authentication
- Integration with hardware-backed key storage

**`SecureKeyRepository`** (`app/src/main/java/com/shadowai/app/security/SecureKeyRepository.kt`)

- TEE-backed encryption for all provider API keys
- Separate encryption keys per provider
- Security audit reporting

#### B. Input/Output Sanitization

**`PromptInjectionDefense`** (`app/src/main/java/com/shadowai/app/security/PromptInjectionDefense.kt`)

- Scans user prompts for jailbreak patterns before LLM processing
- Detects: system instruction leakage, context manipulation, escape sequences
- Risk scoring with CRITICAL/HIGH/MEDIUM/LOW/NONE levels
- Automatic sanitization with pattern blocking

**`PiiMaskingProcessor`** (`app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt`)

- Automatic PII detection and masking (email, phone, SSN, credit card, etc.)
- Compliance reporting for data handling requirements
- Placeholder-based masking with restoration capability

### 2. Execution Layer: Resilient Concurrency

A zero-trust environment expects failure. The `TaskExecutor` prevents cascade failures where one broken provider hangs the entire app.

#### A. Provider Micro-Segmentation

**`ProviderCircuitBreakerManager`** (`app/src/main/java/com/shadowai/app/execution/ProviderCircuitBreakerManager.kt`)

- Isolated circuit breakers per provider/model
- If Gemini-1.5-Flash is down, OpenAI breaker remains CLOSED
- Force reset/open capabilities for manual intervention
- Status monitoring for all circuit breakers

#### B. Adaptive Jitter Strategy

**`AdaptiveRetryManager`** (`app/src/main/java/com/shadowai/app/execution/AdaptiveRetryManager.kt`)

- Decorrelated jitter algorithm preventing "thundering herd"
- Formula: `delay = min(maxDelay, rand(baseDelay, lastDelay * 3))`
- Natural spread of retry times on service recovery
- Token bucket rate limiting for API call throttling

### 3. Execution Isolation: JNI Sandboxing

Since `LocalLiquidEngine` uses Llama.cpp JNI bindings, local inference runs in a **separate Android Process** (`:inference_process`).

**`LocalInferenceService`** (`app/src/main/java/com/shadowai/app/ai/LocalInferenceService.kt`)

- Runs in isolated process: `:inference_process`
- Prevents native memory exploits from accessing main app memory
- IPC via AIDL with `ILocalInferenceService` and `IInferenceCallback`
- Status flow for monitoring service state

**`IsolatedInferenceManager`** (`app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt`)

- Manages binding to isolated inference service
- Provides async inference with timeout support
- Automatic service connection management

### 4. Data & Storage: Persistent Integrity

`ShadowDatabase` tracks sensitive chat logs and AI "memories" with radioactive data handling.

#### A. Database Optimization

**`ShadowDatabase`** (`app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt`)

- **WAL (Write-Ahead Logging)** enabled for concurrent reads/writes
- Prevents UI locking during heavy AI response logging
- TTL constants for automatic data expiration

#### B. Automatic Ledger Pruning

**`DatabasePrunerWorker`** (`app/src/main/java/com/shadowai/app/db/DatabasePrunerWorker.kt`)

- WorkManager background task runs every 24 hours
- Pruning logic:
  - `LedgerEntity` older than 48 hours deleted
  - `FailureEntity` older than 24 hours deleted
  - `MemoryEntity` older than 7 days deleted
  - Low-confidence memories (< 0.3) auto-cleaned
- Integrity checks for failure accumulation

**`DatabasePrunerScheduler`** - Schedules periodic pruning with battery/storage constraints

#### C. Model Integrity Verification

**`ModelIntegrityChecker`** (`app/src/main/java/com/shadowai/app/ai/ModelIntegrityChecker.kt`)

- SHA-256 checksum verification before model loading
- Detects "bit-rot" or malicious tampering
- Checksum file generation and verification
- Integrity report generation for all local models

### 5. Implementation Timeline

| Phase | Focus | Key Deliverable |
| --- | --- | --- |
| **Week 1** | **Identity & Keys** | Migrate all keys to TEE; implement Biometric-bound key access for API calls. |
| **Week 2** | **Isolation** | Move `LocalLiquidEngine` to separate Android process; sandbox JNI calls. |
| **Week 3** | **Failure Logic** | Implement per-model Circuit Breakers and token-bucket rate limiting for the `TaskExecutor`. |
| **Week 4** | **Data Integrity** | Enable Room WAL; set up automated DB pruning and model checksum verification. |

### 6. New Security Components

```kotlin
// Dependency Injection registration in AppModule
@Provides @Singleton
fun provideTeeKeyManager(context: Context): TeeKeyManager = TeeKeyManager(context)

@Provides @Singleton
fun provideBiometricKeyManager(context: Context): BiometricKeyManager = BiometricKeyManager(context)

@Provides @Singleton
fun provideSecureKeyRepository(
    context: Context,
    gson: Gson,
    teeKeyManager: TeeKeyManager
): SecureKeyRepository = SecureKeyRepository(context, gson, teeKeyManager)

@Provides @Singleton
fun providePromptInjectionDefense(): PromptInjectionDefense = PromptInjectionDefense()

@Provides @Singleton
fun providePiiMaskingProcessor(): PiiMaskingProcessor = PiiMaskingProcessor()

@Provides @Singleton
fun provideProviderCircuitBreakerManager(): ProviderCircuitBreakerManager = ProviderCircuitBreakerManager()

@Provides @Singleton
fun provideAdaptiveRetryManager(): AdaptiveRetryManager = AdaptiveRetryManager()

@Provides @Singleton
fun provideModelIntegrityChecker(
    @ApplicationContext context: Context
): ModelIntegrityChecker = ModelIntegrityChecker(context)

@Provides @Singleton
fun provideTemplateVerifier(): TemplateVerifier = TemplateVerifier()

@Provides @Singleton
fun provideProviderSecretRepository(
    @ApplicationContext context: Context
): ProviderSecretRepository = ProviderSecretRepository(context)

@Provides @Singleton
fun provideIsolatedInferenceManager(
    @ApplicationContext context: Context
): IsolatedInferenceManager = IsolatedInferenceManager(context)
```

### 7. SecretBytes Security Pattern

The **SecretBytes** pattern ensures API keys never exist as immutable strings in memory:

```kotlin
// Secure key storage with automatic zeroing
val secret = SecretBytes(32) // Allocate 32 bytes
secret.fill { java.security.SecureRandom().nextBytes(this) }

// Scoped access - key is wiped after use
secret.withSecretBytes({ it }) { key ->
    // Use key here
    cipher.doFinal(key)
} // Key is automatically zeroed here
```

**ProviderSecretRepository** uses this pattern for all API key access:

```kotlin
suspend fun withProviderApiKey(
    providerId: ProviderId,
    block: suspend (ByteArray) -> Unit
): Result<Unit> {
    withSecretBytes({
        decryptKey(encryptedKey)
    }) { secretBytes ->
        block(secretBytes.asByteArrayUnsafe())
    }
}
```

### 8. Template Verification

**TemplateVerifier** provides prompt injection detection with pattern matching:

```kotlin
val verifier = TemplateVerifier()

// Verify user input before sending to LLM
val result = verifier.verifyPrompt(userInput)
if (!result.isSafe) {
    throw PromptInjectionException(result.reason)
}

// Sanitize potentially dangerous content
val sanitized = verifier.sanitizePrompt(userInput)
```

Detects patterns including:

- Jailbreak attempts (DAN, STAN, etc.)
- System instruction leakage
- Unicode manipulation (zero-width characters)
- Delimiter-based prompt injection
- Base64-encoded payloads

## Core Source Code

### SecurityManager.kt

```kotlin
package com.shadowai.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64 as AndroidBase64

/**
 * Security manager for handling encryption, access control, and sensitive data protection.
 */
class SecurityManager(private val context: Context) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ShadowAI_Key"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false)
            .build()
    }
    
    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "shadowai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Encrypt sensitive data using AES-GCM with explicit tag length.
     */
    fun encrypt(data: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, generateIv())
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // Combine IV and encrypted data
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        
        return AndroidBase64.encodeToString(combined, AndroidBase64.NO_WRAP)
    }
    
    /**
     * Generate a random IV for GCM encryption.
     */
    private fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        java.security.SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * Decrypt sensitive data using AES-GCM.
     */
    fun decrypt(encryptedData: String, key: SecretKey): String {
        val combined = AndroidBase64.decode(encryptedData, AndroidBase64.NO_WRAP)
        
        val iv = ByteArray(GCM_IV_LENGTH)
        val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.size)
        
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
    
    /**
     * Generate a new AES secret key using proper KeyGenerator.
     */
    fun generateSecretKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, java.security.SecureRandom())
        return keyGen.generateKey()
    }
    
    /**
     * Get or create the RSA key pair for asymmetric encryption.
     */
    fun getOrCreateKeyPair(): Pair<PublicKey, PrivateKey> {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }
        
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Pair(publicKey, privateKey)
    }
    
    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )
        
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        
        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Encrypt data using RSA public key.
     */
    fun encryptWithPublicKey(data: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return AndroidBase64.encodeToString(encrypted, AndroidBase64.NO_WRAP)
    }
    
    /**
     * Decrypt data using RSA private key.
     */
    fun decryptWithPrivateKey(encryptedData: String, privateKey: PrivateKey): String {
        val encrypted = AndroidBase64.decode(encryptedData, AndroidBase64.NO_WRAP)
        
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
    
    /**
     * Store sensitive data securely using commit for immediate persistence.
     */
    fun storeSecureData(key: String, value: String) {
        securePrefs.edit().putString(key, value).commit()
    }
    
    /**
     * Retrieve sensitive data securely.
     */
    fun getSecureData(key: String): String? {
        return securePrefs.getString(key, null)
    }
    
    /**
     * Clear all secure data.
     */
    fun clearSecureData() {
        securePrefs.edit().clear().commit()
    }
    
    /**
     * Hash sensitive data (one-way) using SHA-256.
     */
    fun hash(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Role-based access control manager.
 */
class AccessControlManager(private val securityManager: SecurityManager) {
    
    enum class Permission {
        READ_MESSAGES,
        SEND_MESSAGES,
        ACCESS_CONTACTS,
        ACCESS_LOCATION,
        MAKE_CALLS,
        SEND_SMS,
        ACCESS_CALENDAR,
        ACCESS_STORAGE,
        ACCESS_CAMERA,
        ACCESS_MICROPHONE,
        DEVICE_CONTROL,
        ADMIN_FEATURES
    }
    
    enum class Role(val permissions: Set<Permission>) {
        GUEST(setOf(Permission.READ_MESSAGES, Permission.SEND_MESSAGES)),
        USER(setOf(
            Permission.READ_MESSAGES,
            Permission.SEND_MESSAGES,
            Permission.ACCESS_CAMERA,
            Permission.ACCESS_MICROPHONE
        )),
        POWER_USER(setOf(
            Permission.READ_MESSAGES,
            Permission.SEND_MESSAGES,
            Permission.ACCESS_CONTACTS,
            Permission.ACCESS_LOCATION,
            Permission.ACCESS_CAMERA,
            Permission.ACCESS_MICROPHONE,
            Permission.ACCESS_STORAGE,
            Permission.DEVICE_CONTROL
        )),
        ADMIN(setOf(
            Permission.READ_MESSAGES,
            Permission.SEND_MESSAGES,
            Permission.ACCESS_CONTACTS,
            Permission.ACCESS_LOCATION,
            Permission.ACCESS_CALENDAR,
            Permission.ACCESS_STORAGE,
            Permission.ACCESS_CAMERA,
            Permission.ACCESS_MICROPHONE,
            Permission.MAKE_CALLS,
            Permission.SEND_SMS,
            Permission.DEVICE_CONTROL,
            Permission.ADMIN_FEATURES
        ))
    }
    
    private var currentRole: Role = Role.GUEST
    
    /**
     * Set the current user's role.
     */
    fun setRole(role: Role) {
        currentRole = role
    }
    
    /**
     * Check if the current role has the specified permission.
     */
    fun hasPermission(permission: Permission): Boolean {
        return currentRole.permissions.contains(permission)
    }
    
    /**
     * Get all permissions for the current role.
     */
    fun getCurrentPermissions(): Set<Permission> {
        return currentRole.permissions
    }
    
    /**
     * Check if the user can perform an action that requires the given permission.
     */
    fun canPerform(action: String): Boolean {
        val requiredPermission = when (action.lowercase()) {
            "read_messages" -> Permission.READ_MESSAGES
            "send_messages" -> Permission.SEND_MESSAGES
            "access_contacts" -> Permission.ACCESS_CONTACTS
            "access_location" -> Permission.ACCESS_LOCATION
            "make_calls" -> Permission.MAKE_CALLS
            "send_sms" -> Permission.SEND_SMS
            "access_calendar" -> Permission.ACCESS_CALENDAR
            "access_storage" -> Permission.ACCESS_STORAGE
            "access_camera" -> Permission.ACCESS_CAMERA
            "access_microphone" -> Permission.ACCESS_MICROPHONE
            "device_control" -> Permission.DEVICE_CONTROL
            "admin_features" -> Permission.ADMIN_FEATURES
            else -> null
        }
        
        return requiredPermission?.let { hasPermission(it) } ?: false
    }
    
    /**
     * Request elevation of permissions (e.g., for admin actions).
     */
    fun requestPermissionElevation(): Boolean {
        return currentRole == Role.ADMIN
    }
    
    /**
     * Get the current role name.
     */
    fun getCurrentRoleName(): String {
        return currentRole.name
    }
}
```

### ProviderRepository.kt

```kotlin
package com.shadowai.app.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("DEPRECATION")
class ProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val PREFS_NAME = "providers"
        private const val SECURE_PREFS_NAME = "providers_secure"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val KEY_SELECTED_MODELS_PREFIX = "selected_models_"
        private const val KEY_CUSTOM_MODELS_PREFIX = "custom_models_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun loadProviders(): List<Provider> {
        val providersJson = prefs.getString(KEY_PROVIDERS, null)
        if (!providersJson.isNullOrBlank()) {
            return try {
                val type = object : TypeToken<List<Provider>>() {}.type
                gson.fromJson<List<Provider>>(providersJson, type) ?: getDefaultProviders()
            } catch (e: JsonSyntaxException) {
                getDefaultProviders()
            }
        }
        return getDefaultProviders()
    }

    /**
     * Persist the provider list to {@link SharedPreferences}. The original
     * implementation used {@code apply()}, which writes asynchronously and
     * may lead to lost updates when called from the UI thread.  For
     * consistency with the rest of the repository we now use {@code commit()}
     * which blocks until the data is written.
     */
    private fun persistProviders(providers: List<Provider>) {
        prefs.edit().putString(KEY_PROVIDERS, gson.toJson(providers)).commit()
    }

    // --- Suspend API (Preferred for new code) ---

    suspend fun getAllProviders(): List<Provider> = withContext(Dispatchers.IO) {
        loadProviders()
    }

    suspend fun getProviderById(id: ProviderId): Provider? = withContext(Dispatchers.IO) {
        loadProviders().find { it.id == id }
    }

    suspend fun saveProviders(providers: List<Provider>) = withContext(Dispatchers.IO) {
        persistProviders(providers)
    }

    suspend fun saveProvider(provider: Provider) = withContext(Dispatchers.IO) {
        val providers = loadProviders().toMutableList()
        val existingIndex = providers.indexOfFirst { it.id == provider.id }
        if (existingIndex >= 0) {
            providers[existingIndex] = provider
        } else {
            providers.add(provider)
        }
        persistProviders(providers)
    }

    suspend fun listProvidersSync(): List<Provider> = getAllProviders() // Compatibility alias

    // --- legacy/synchronous API for existing UI ---
    
    fun listProviders(): List<Provider> = loadProviders()
    
    fun getProvider(providerId: ProviderId): Provider? = loadProviders().firstOrNull { it.id == providerId }

    /**
     * Enable or disable a provider.  The method now reuses the common
     * {@link #updateProviderList} helper to avoid code duplication.
     */
    fun setProviderEnabled(providerId: ProviderId, enabled: Boolean) {
        val provider = getProvider(providerId) ?: return
        if (provider.enabled != enabled) {
            val updated = provider.copy(enabled = enabled)
            updateProviderList { list ->
                val index = list.indexOfFirst { it.id == providerId }
                if (index >= 0) list[index] = updated
            }
        }
    }

    // --- API Key Management ---

    fun getApiKey(providerId: ProviderId): String? =
        securePrefs.getString("$KEY_API_KEY_PREFIX${providerId.name}", null)?.takeIf { it.isNotBlank() }

    fun saveApiKey(providerId: ProviderId, apiKey: String?) {
        val keyName = "$KEY_API_KEY_PREFIX${providerId.name}"
        securePrefs.edit().apply {
            if (apiKey.isNullOrBlank()) {
                remove(keyName)
            } else {
                putString(keyName, apiKey.trim())
            }
        }.commit()

        // Sync with Provider object's auth metadata
        val provider = getProvider(providerId) ?: return
        val updated = provider.copy(
            enabled = !apiKey.isNullOrBlank(), // Auto‑enable
            auth = provider.auth.copy(
                hasCredential = !apiKey.isNullOrBlank(),
                credentialAlias = apiKey?.let { hashApiKey(it) }
            )
        )
        saveSync(updated)
    }

    /**
     * Update the provider list in a thread‑safe way.  The helper accepts a
     * lambda that mutates the mutable list and then persists the result.
     */
    private fun updateProviderList(mutator: (MutableList<Provider>) -> Unit) {
        val providers = loadProviders().toMutableList()
        mutator(providers)
        persistProviders(providers)
    }

    // --- Model Management ---

    fun getSelectedModels(providerId: ProviderId): List<String> =
        readStringList("$KEY_SELECTED_MODELS_PREFIX${providerId.name}")

    fun setSelectedModels(providerId: ProviderId, models: List<String>) {
        writeStringList("$KEY_SELECTED_MODELS_PREFIX${providerId.name}", models)
        // Also sync to Provider object
        getProvider(providerId)?.let {
            saveSync(it.copy(selectedModels = models))
        }
    }

    fun getCustomModels(providerId: ProviderId, fallbackCapabilities: List<Capability> = emptyList()): List<ModelInfo> {
        val key = "$KEY_CUSTOM_MODELS_PREFIX${providerId.name}"
        val cached = readModelInfoList(key)
        if (cached.isNotEmpty()) return cached
        val provider = getProvider(providerId)
        if (provider != null && provider.models.isNotEmpty()) return provider.models
        return provider?.let {
            listOf(
                ModelInfo(
                    id = it.name,
                    displayName = it.name,
                    provider = providerId,
                    capabilities = fallbackCapabilities.toSet().ifEmpty { it.capabilities.toSet() }
                )
            )
        } ?: emptyList()
    }

    fun saveCustomModels(providerId: ProviderId, modelIds: List<String>) {
        val key = "$KEY_CUSTOM_MODELS_PREFIX${providerId.name}"
        if (modelIds.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            val capabilities = getProvider(providerId)?.capabilities?.toSet() ?: emptySet()
            val models = modelIds.map { id ->
                ModelInfo(
                    id = id,
                    displayName = id,
                    provider = providerId,
                    capabilities = capabilities
                )
            }
            writeModelInfoList(key, models)
        }

        // Also sync to Provider object and auto-enable
        getProvider(providerId)?.let {
            saveSync(it.copy(
                customModels = modelIds,
                enabled = modelIds.isNotEmpty() || !getApiKey(providerId).isNullOrBlank()
            ))
        }
    }

    // --- Helper for UI Compatibility ---
    
    fun getApiStyle(providerId: ProviderId): ApiStyle {
        return when (providerId) {
            ProviderId.OPENAI, ProviderId.OPENROUTER, ProviderId.XAI, ProviderId.GROQ,
            ProviderId.COHERE, ProviderId.SILICON_FLOW, ProviderId.MISTRAL,
            ProviderId.DEEPSEEK, ProviderId.ATLASCLOUD, ProviderId.SIRAY -> ApiStyle.OPENAI_COMPAT
            ProviderId.ANTHROPIC -> ApiStyle.ANTHROPIC
            ProviderId.GEMINI -> ApiStyle.GEMINI
            ProviderId.LOCAL_TEXT -> ApiStyle.LOCAL_TEXT
            ProviderId.LOCAL_IMAGE -> ApiStyle.LOCAL_IMAGE
            ProviderId.PIXAI -> ApiStyle.PIXAI
            ProviderId.NOVELAI -> ApiStyle.NOVELAI
            ProviderId.NOVITA -> ApiStyle.NOVITA_IMAGE
            ProviderId.LIQUID -> ApiStyle.LIQUID
            ProviderId.AMAZON_BEDROCK -> ApiStyle.OPENAI_COMPAT // Simplified for now
            else -> ApiStyle.OPENAI_COMPAT
        }
    }

    // --- Connection Testing ---

    suspend fun testConnection(provider: Provider): Boolean = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(provider.id) ?: return@withContext false
        val probeUrl = when (provider.id) {
            ProviderId.OPENAI -> "${provider.baseUrl}/models"
            ProviderId.ANTHROPIC -> "${provider.baseUrl}/messages"
            ProviderId.OPENROUTER -> "${provider.baseUrl}/models"
            ProviderId.GEMINI -> "${provider.baseUrl}/models"
            ProviderId.GROQ -> "${provider.baseUrl}/models"
            ProviderId.MISTRAL -> "${provider.baseUrl}/models"
            ProviderId.DEEPSEEK -> "${provider.baseUrl}/models"
            ProviderId.XAI -> "${provider.baseUrl}/models"
            ProviderId.COHERE -> "${provider.baseUrl}/models"
            ProviderId.SILICON_FLOW -> "${provider.baseUrl}/models"
            ProviderId.ATLASCLOUD -> "${provider.baseUrl}/models"
            ProviderId.SIRAY -> "${provider.baseUrl}/models"
            ProviderId.NOVELAI -> "${provider.baseUrl}/models"
            else -> return@withContext false
        }

        return@withContext try {
            val request = Request.Builder()
                .url(probeUrl)
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- Discovery & Probe ---

    suspend fun fetchProviderModels(providerId: ProviderId): List<ModelInfo> {
        val provider = getProviderById(providerId) ?: return emptyList()
        return discoverModels(provider)
    }

    suspend fun discoverModels(provider: Provider): List<ModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(provider.id) ?: return@withContext emptyList()
        val modelsUrl = when (provider.id) {
            ProviderId.OPENAI, ProviderId.ANTHROPIC, ProviderId.OPENROUTER,
            ProviderId.GEMINI, ProviderId.GROQ, ProviderId.MISTRAL,
            ProviderId.DEEPSEEK, ProviderId.XAI, ProviderId.COHERE,
            ProviderId.SILICON_FLOW, ProviderId.ATLASCLOUD, ProviderId.SIRAY,
            ProviderId.NOVELAI -> "${provider.baseUrl}/models"
            else -> return@withContext emptyList()
        }
        
        try {
            val request = Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer $apiKey")
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            parseModelsResponse(responseBody, provider.id)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseModelsResponse(json: String, providerId: ProviderId): List<ModelInfo> {
        return try {
            val root = JSONObject(json)
            val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: return emptyList()
            (0 until data.length()).mapNotNull { i ->
                val modelObj = data.optJSONObject(i) ?: return@mapNotNull null
                val id = modelObj.optString("id")
                ModelInfo(
                    id = id,
                    displayName = id,
                    provider = providerId,
                    capabilities = setOf(Capability.TEXT)
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    // --- Private Serialization Helpers ---

    private fun readStringList(key: String): List<String> {
        val json = prefs.getString(key, null)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeStringList(key: String, values: List<String>) {
        prefs.edit().putString(key, gson.toJson(values)).apply()
    }

    private fun readModelInfoList(key: String): List<ModelInfo> {
        val json = prefs.getString(key, null)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<ModelInfo>>(json, object : TypeToken<List<ModelInfo>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeModelInfoList(key: String, models: List<ModelInfo>) {
        prefs.edit().putString(key, gson.toJson(models)).apply()
    }

    private fun hashApiKey(apiKey: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getDefaultProviders(): List<Provider> {
        return listOf(
            // Cloud AI Providers
            Provider(ProviderId.OPENAI, "OpenAI", false, "https://api.openai.com/v1", ProviderAuth()),
            Provider(ProviderId.ANTHROPIC, "Anthropic", false, "https://api.anthropic.com/v1", ProviderAuth()),
            Provider(ProviderId.OPENROUTER, "OpenRouter", false, "https://openrouter.ai/api/v1", ProviderAuth()),
            Provider(ProviderId.GEMINI, "Google Gemini", false, "https://generativelanguage.googleapis.com/v1", ProviderAuth()),
            Provider(ProviderId.GROQ, "Groq", false, "https://api.groq.com/openai/v1", ProviderAuth()),
            Provider(ProviderId.MISTRAL, "Mistral", false, "https://api.mistral.ai/v1", ProviderAuth()),
            Provider(ProviderId.DEEPSEEK, "DeepSeek", false, "https://api.deepseek.com/v1", ProviderAuth()),
            Provider(ProviderId.XAI, "xAI", false, "https://api.x.ai/v1", ProviderAuth()),
            Provider(ProviderId.COHERE, "Cohere", false, "https://api.cohere.ai/v1", ProviderAuth()),
            Provider(ProviderId.SILICON_FLOW, "Silicon Flow", false, "https://api.siliconflow.cn/v1", ProviderAuth()),
            // Image Generation Providers
            Provider(ProviderId.PIXAI, "PixAI", false, "https://api.pixai.art/v1", ProviderAuth()),
            Provider(ProviderId.NOVITA, "Novita", false, "https://api.novita.ai/v3", ProviderAuth()),
            Provider(ProviderId.NOVELAI, "NovelAI", false, "https://image.novelai.net/v1", ProviderAuth()),
            // Local Providers
            Provider(ProviderId.LIQUID, "Liquid AI (Local)", true, "file:///internal storage/liquid-main", ProviderAuth(type = AuthType.NONE)),
            Provider(ProviderId.LOCAL_TEXT, "Local Text Model", false, "http://localhost:8080/v1", ProviderAuth(type = AuthType.NONE)),
            Provider(ProviderId.LOCAL_IMAGE, "Local Image Model", false, "http://localhost:8081/v1", ProviderAuth(type = AuthType.NONE)),
            // Additional Providers
            Provider(ProviderId.ATLASCLOUD, "AtlasCloud", false, "https://api.atlascloud.ai/v1", ProviderAuth()),
            Provider(ProviderId.SIRAY, "SirayAI", false, "https://api.siray.ai/v1", ProviderAuth())
        )
    }
}
```

### CircuitBreaker.kt

```kotlin
package com.shadowai.app.execution

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Circuit Breaker implementation for robust failure handling.
 * 
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Circuit is open, requests fail fast
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 * 
 * @param failureThreshold Number of failures before opening the circuit (default: 5)
 * @param successThreshold Number of successes in HALF_OPEN state before closing (default: 2)
 * @param timeoutMs Time in milliseconds before attempting to transition from OPEN to HALF_OPEN (default: 30000)
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val successThreshold: Int = 2,
    private val timeoutMs: Long = 30000
) {
    enum class State {
        CLOSED, OPEN, HALF_OPEN
    }

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val mutex = Mutex()

    val currentState: State
        get() = state.get()

    /**
     * Execute a block of code with circuit breaker protection.
     * Throws CircuitOpenException if the circuit is open.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        return when (currentState) {
            State.CLOSED -> executeClosed(block)
            State.OPEN -> executeOpen(block)
            State.HALF_OPEN -> executeHalfOpen(block)
        }
    }

    /**
     * Execute a block of code with circuit breaker protection, returning a Result.
     * This is a non-throwing alternative to execute().
     */
    suspend fun <T> tryExecute(block: suspend () -> T): Result<T> {
        return try {
            Result.success(execute(block))
        } catch (e: CircuitOpenException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeClosed(block: suspend () -> T): T {
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private suspend fun <T> executeOpen(block: suspend () -> T): T {
        if (isTimeoutElapsed()) {
            // Transition to HALF_OPEN
            mutex.withLock {
                if (state.get() == State.OPEN && isTimeoutElapsed()) {
                    state.set(State.HALF_OPEN)
                    successCount.set(0)
                }
            }
            return executeHalfOpen(block)
        }
        throw CircuitOpenException("Circuit breaker is open. Service temporarily unavailable.")
    }

    private suspend fun <T> executeHalfOpen(block: suspend () -> T): T {
        return try {
            val result = block()
            onHalfOpenSuccess()
            result
        } catch (e: Exception) {
            onHalfOpenFailure()
            throw e
        }
    }

    private fun onSuccess() {
        failureCount.set(0)
    }

    private fun onFailure() {
        val newCount = failureCount.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())
        if (newCount >= failureThreshold) {
            state.set(State.OPEN)
        }
    }

    private fun onHalfOpenSuccess() {
        val newCount = successCount.incrementAndGet()
        if (newCount >= successThreshold) {
            state.set(State.CLOSED)
            failureCount.set(0)
        }
    }

    private fun onHalfOpenFailure() {
        state.set(State.OPEN)
        lastFailureTime.set(System.currentTimeMillis())
    }

    private fun isTimeoutElapsed(): Boolean {
        return System.currentTimeMillis() - lastFailureTime.get() > timeoutMs
    }

    /**
     * Reset the circuit breaker to initial CLOSED state.
     */
    fun reset() {
        state.set(State.CLOSED)
        failureCount.set(0)
        successCount.set(0)
        lastFailureTime.set(0)
    }

    /**
     * Get the current failure count.
     */
    fun getFailureCount(): Int = failureCount.get()

    /**
     * Get the configured failure threshold.
     */
    fun getFailureThreshold(): Int = failureThreshold

    /**
     * Get the configured success threshold.
     */
    fun getSuccessThreshold(): Int = successThreshold

    /**
     * Get the configured timeout in milliseconds.
     */
    fun getTimeoutMs(): Long = timeoutMs
}

/**
 * Exception thrown when the circuit breaker is open.
 */
class CircuitOpenException(message: String) : Exception(message)
```

### PlanParser.kt

```kotlin
package com.shadowai.app.tasks

import com.shadowai.app.execution.DeviceActionParser
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.1: Explicit Planning Graph (DAGs)
 * Parses JSON into a Plan object. Supports both single-action and multi-node formats.
 */
@Singleton
class PlanParser @Inject constructor() {

    companion object {
        private const val MAX_NODES = 100
        private const val MAX_JSON_SIZE = 1024 * 1024 // 1MB limit
    }

    fun parse(json: String): Result<Plan> {
        return try {
            validateInput(json)?.let { return Result.failure(IllegalArgumentException(it)) }

            val obj = JSONObject(json)
            val version = obj.optInt("version", 1)
            
            when {
                obj.has("plan") -> parsePlanObject(obj, version)
                obj.has("action") -> parseSingleAction(obj, version)
                else -> Result.failure(IllegalArgumentException("Root JSON must contain 'plan' or 'action' field."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateInput(json: String): String? {
        if (json.isBlank()) return "Input JSON cannot be empty"
        if (json.length > MAX_JSON_SIZE) return "Input JSON exceeds maximum size limit"
        return null
    }

    private fun parsePlanObject(obj: JSONObject, version: Int): Result<Plan> {
        val planObj = obj.getJSONObject("plan")
        val nodesArray = planObj.getJSONArray("nodes")
        
        if (nodesArray.length() > MAX_NODES) {
            return Result.failure(IllegalArgumentException("Plan exceeds maximum node limit of $MAX_NODES"))
        }

        // First pass: collect all node IDs to support forward references
        val allNodeIds = mutableSetOf<String>()
        for (i in 0 until nodesArray.length()) {
            val nodeObj = nodesArray.getJSONObject(i)
            if (!nodeObj.has("id")) {
                return Result.failure(IllegalArgumentException("Node at index $i missing required 'id' field"))
            }
            val id = nodeObj.getString("id")
            if (!allNodeIds.add(id)) {
                return Result.failure(IllegalArgumentException("Duplicate node ID: $id"))
            }
        }

        // Second pass: parse nodes and validate dependencies against all collected IDs
        val nodes = mutableListOf<PlanNode>()
        
        for (i in 0 until nodesArray.length()) {
            val nodeObj = nodesArray.getJSONObject(i)
            
            // Validate required fields
            if (!nodeObj.has("action")) {
                return Result.failure(IllegalArgumentException("Node at index $i missing required 'action' field"))
            }

            val id = nodeObj.getString("id")

            val actionObj = nodeObj.getJSONObject("action")
            val actionResult = DeviceActionParser.parse(actionObj.toString())
            
            if (actionResult.isFailure) {
                return Result.failure(actionResult.exceptionOrNull()
                    ?: IllegalArgumentException("Failed to parse action for node $id"))
            }

            val dependencies = parseDependencies(nodeObj)
            
            // Validate dependencies exist in the full set of node IDs (supports forward references)
            val invalidDeps = dependencies.filter { dep -> !allNodeIds.contains(dep) }
            if (invalidDeps.isNotEmpty()) {
                return Result.failure(IllegalArgumentException("Node $id references non-existent dependencies: ${invalidDeps.joinToString(", ")}"))
            }
            
            // Prevent self-referential dependencies
            if (dependencies.contains(id)) {
                return Result.failure(IllegalArgumentException("Node $id cannot depend on itself"))
            }

            nodes.add(PlanNode(id, actionResult.getOrThrow(), dependencies))
        }

        // Validate no cycles exist
        if (hasCycles(nodes)) {
            return Result.failure(IllegalArgumentException("Plan contains cyclic dependencies"))
        }

        return Result.success(Plan(version, nodes))
    }

    private fun parseSingleAction(obj: JSONObject, version: Int): Result<Plan> {
        val actionResult = DeviceActionParser.parse(obj.toString())
        if (actionResult.isFailure) {
            return Result.failure(actionResult.exceptionOrNull() 
                ?: IllegalArgumentException("Failed to parse single action"))
        }
        
        val node = PlanNode(id = "step_1", action = actionResult.getOrThrow())
        return Result.success(Plan(version, listOf(node)))
    }

    private fun parseDependencies(nodeObj: JSONObject): List<String> {
        if (!nodeObj.has("dependencies")) return emptyList()
        
        val depsArray = nodeObj.getJSONArray("dependencies")
        return List(depsArray.length()) { i -> depsArray.getString(i) }
    }

    private fun hasCycles(nodes: List<PlanNode>): Boolean {
        val graph = nodes.associate { node -> 
            node.id to node.dependencies.toSet()
        }
        
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        
        fun dfs(nodeId: String): Boolean {
            if (nodeId in stack) return true
            if (nodeId in visited) return false
            
            visited.add(nodeId)
            stack.add(nodeId)
            
            if (graph[nodeId]?.any { dfs(it) } == true) return true
            
            stack.remove(nodeId)
            return false
        }
        
        return nodes.any { dfs(it.id) }
    }
}
```

### ShadowDatabase.kt

```kotlin
package com.shadowai.app.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.WriteAheadLogging
import com.shadowai.app.BuildConfig

/**
 * Zero-Trust Data Integrity: ShadowDatabase.
 * 
 * Enables Write-Ahead Logging (WAL) for concurrent reads/writes.
 * Implements TTL-based data retention for security and performance.
 */
@Database(
    entities = [
        ChatMessageEntity::class, 
        MemoryEntity::class, 
        LedgerEntry::class, 
        TaskEntity::class, 
        AgentFailureEntity::class, 
        FeedbackEntity::class
    ], 
    version = 6, 
    exportSchema = false
)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun taskDao(): TaskDao
    abstract fun failureDao(): FailureDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowDatabase? = null
        
        // TTL configurations (in milliseconds)
        const val DEFAULT_TTL_MS = 48 * 60 * 60 * 1000L // 48 hours
        const val MEMORY_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days for memories
        const val LEDGER_TTL_MS = 48 * 60 * 60 * 1000L // 48 hours for ledger
        const val FAILURE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours for failures
        
        fun getDatabase(context: Context): ShadowDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    ShadowDatabase::class.java,
                    "shadow_ai_db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING) // Enable WAL for concurrent access
                
                // Only allow destructive migration in debug builds with warning
                if (com.shadowai.app.BuildConfig.DEBUG) {
                    Log.w("ShadowDatabase", "Destructive migration enabled - data may be lost on schema change")
                    builder.fallbackToDestructiveMigration()
                }
                // In release builds, app will crash if schema changes without proper migration
                // This forces developers to implement proper migrations before release
                
                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Get database with custom TTL settings.
         */
        fun getDatabaseWithTtl(
            context: Context,
            memoryTtlMs: Long = MEMORY_TTL_MS,
            ledgerTtlMs: Long = LEDGER_TTL_MS,
            failureTtlMs: Long = FAILURE_TTL_MS
        ): ShadowDatabase {
            // Store TTL values in SharedPreferences for the pruner worker
            val prefs = context.getSharedPreferences("db_ttl_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("memory_ttl", memoryTtlMs)
                .putLong("ledger_ttl", ledgerTtlMs)
                .putLong("failure_ttl", failureTtlMs)
                .apply()
            
            return getDatabase(context)
        }
    }
}
```

## Technical Limits & Constraints

To ensure system stability and security, the following hard limits are enforced:

| limit | Value | Scope | Description |
| :--- | :--- | :--- | :--- |
| **Max Prompt Size** | 128KB | Input | Maximum size for a single user prompt before rejection. |
| **Max JSON Plan** | 1MB | Planning | Maximum size of an agent plan JSON to prevent OOM. |
| **Max Plan Nodes** | 100 | Planning | Maximum number of steps in a single execution plan. |
| **Inference Timeout** | 120s | Execution | Hard timeout for `LocalInferenceService` calls. |
| **Circuit Threshold** | 5 Failures | Resilience | Failures required to open a circuit breaker. |
| **Database TTL** | 48 Hours | Storage | Retention period for operational logs (`LedgerEntity`). |
| **Memory TTL** | 7 Days | Storage | Retention period for ephemeral agent memories (`MemoryEntity`). |

## Component Manifest

### Security components

- `TeeKeyManager.kt`: Hardware-backed key generation.
- `BiometricKeyManager.kt`: Biometric-bound crypto operations.
- `SecureKeyRepository.kt`: Encrypted storage for API keys.
- `PromptInjectionDefense.kt`: Input sanitization and analysis.
- `PiiMaskingProcessor.kt`: Output redaction.

### Resilience Components

- `ProviderCircuitBreakerManager.kt`: Isolated failure domains.
- `AdaptiveRetryManager.kt`: Decorrelated jitter backoff.
- `ModelIntegrityChecker.kt`: SHA-256 verification.
- `DatabasePrunerWorker.kt`: Automated radioactive data cleanup.

### Isolation Components

- `LocalInferenceService.kt`: Isolated process service.
- `IsolatedInferenceManager.kt`: IPC process manager.
- `ILocalInferenceService.aidl`: IPC contract.

