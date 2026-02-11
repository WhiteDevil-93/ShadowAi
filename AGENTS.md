# ShadowAi Project Agent Guidance

**Project**: ShadowAi (formerly DarcyAI) - Comprehensive AI Assistant for Android  
**Last Updated**: 2026-02-04  
**Current Phase**: Universal AI Pipeline - Phase 2 (Provider Adapters)  
**Primary Language**: Kotlin  
**Target Platform**: Android (minSdk 24, targetSdk 35)

---

## 1. Project Overview

ShadowAi is a modular Android AI assistant application designed with a **provider-agnostic, capability-driven architecture**. The application supports multiple AI providers, local inference via llama.cpp, image generation through backend services (Novita, PixAI), and implements a zero-trust security model.

---

## Workspace guidance note

The authoritative guides are now at the repo root (`AGENTS.md`, `WORKTREE.md`, `NAVIGATION.md`). `archive/clutter/` still holds historical reports and documentation, but those files should not be treated as part of the active worktree or invoked by CI/build scripts. Follow the root-level instructions and living source tree for everyday development.

### Core Architecture Principles

1. **Provider Abstraction**: All AI interactions flow through standardized interfaces, enabling seamless provider swapping
2. **Zero-Trust Security**: Every component is treated as a potential compromise point with cryptographic isolation
3. **Local-First Data Sovereignty**: User data preferences are respected with optional cloud sync
4. **Modular Gradle Structure**: Clean module boundaries enforced through build logic

### Module Dependency Graph

```
app/ (Android Application)
├── core-contracts/ (Foundation interfaces - ALL modules depend on this)
├── model-catalog/ (Model discovery & deduplication)
├── provider-adapters/ (Provider implementations)
├── artifact-system/ (I/O normalization)
├── pipeline-planner/ (Graph-based transformations)
├── diagnostics/ (Error handling & debugging)
├── hot-swapping/ (Runtime config management)
└── backend/ (Ktor server - Image generation APIs)
```

---

## 2. Current Development Status

### Active Implementation Phase: Phase 2 - Provider Adapters

Per [`plans/todo.md`](plans/todo.md), the current focus is:

- [x] Phase 0: Hard Contracts (Foundation)
- [x] Phase 1: Model Registry (Discovery & Deduplication)
- [ ] **Phase 2: Provider Adapters (In Progress)**
  - [ ] Create `provider-adapters` module structure
  - [ ] Implement `LocalLlamaAdapter` for GGUF models
  - [ ] Implement `FluxAdapter` for image generation
  - [ ] Implement `OpenAICompatibleAdapter` for OpenAI-style APIs
  - [ ] Implement `AnthropicAdapter` for Claude APIs
  - [ ] Implement `GeminiAdapter` for Google Gemini
  - [ ] Implement `NovitaAdapter` for Novita API
  - [ ] Implement `PixAiAdapter` for PixAI API
  - [ ] Create adapter factory pattern
  - [ ] Implement adapter health checking
  - [ ] Add adapter metrics collection
- [ ] Phase 3: Artifact System (I/O Normalization)
- [ ] Phase 4: Pipeline Planner (Graph-Based Transformations)
- [ ] Phase 5-9: UI Composition, Diagnostics, Hot-Swapping, Module Boundaries

**Integration Targets for Phase 2**:
- Existing `LiquidProvider` → Migrate to `LocalLlamaAdapter`
- Existing `NovitaService` → Migrate to `NovitaAdapter`
- Existing `PixAiService` → Migrate to `PixAiAdapter`
- Existing `OpenAiApi` → Migrate to `OpenAICompatibleAdapter`

---

## 3. Key Entry Points & Core Logic

### Application Entry Points

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/ComposeMainActivity.kt`](app/src/main/java/com/shadowai/app/ComposeMainActivity.kt) | Main Compose-based activity, DI entry point |
| [`app/src/main/java/com/shadowai/app/AndroidManifest.xml`](app/src/main/java/com/shadowai/app/AndroidManifest.xml) | App configuration, permissions, service declarations |

### AI & Provider System

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt`](app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt) | Manages AI provider configurations, API keys, model listing |
| [`app/src/main/java/com/shadowai/app/providers/ProviderPlugin.kt`](app/src/main/java/com/shadowai/app/providers/ProviderPlugin.kt) | Plugin interface for custom AI providers |
| [`app/src/main/java/com/shadowai/app/providers/ProviderPluginRegistry.kt`](app/src/main/java/com/shadowai/app/providers/ProviderPluginRegistry.kt) | Manages plugin lifecycle, priority-based selection |

### Task Execution & Routing

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt`](app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt) | Orchestrates task execution with routing policies, retry logic |
| [`app/src/main/java/com/shadowai/app/execution/CircuitBreaker.kt`](app/src/main/java/com/shadowai/app/execution/CircuitBreaker.kt) | Fault tolerance pattern (CLOSED, OPEN, HALF_OPEN states) |
| [`app/src/main/java/com/shadowai/app/execution/ProviderCircuitBreakerManager.kt`](app/src/main/java/com/shadowai/app/execution/ProviderCircuitBreakerManager.kt) | Isolated circuit breakers per provider/model |

### Agent System

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt`](app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt) | Core agent implementation |
| [`app/src/main/java/com/shadowai/app/agent/SupervisorAgent.kt`](app/src/main/java/com/shadowai/app/agent/SupervisorAgent.kt) | High-level agent orchestration |
| [`app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt`](app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt) | Agent execution loop |

### Local Inference

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/ai/LocalBrainManager.kt`](app/src/main/java/com/shadowai/app/ai/LocalBrainManager.kt) | Local model configuration, Retrofit API |
| [`app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt`](app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt) | Local inference orchestration |
| [`app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt`](app/src/main/java/com/shadowai/app/ai/IsolatedInferenceManager.kt) | Manages isolated inference process (`:inference_process`) |

### Security (Zero-Trust Model)

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/security/SecurityManager.kt`](app/src/main/java/com/shadowai/app/security/SecurityManager.kt) | AES-256-GCM encryption, RSA key management |
| [`app/src/main/java/com/shadowai/app/security/TeeKeyManager.kt`](app/src/main/java/com/shadowai/app/security/TeeKeyManager.kt) | TEE/StrongBox-backed key generation |
| [`app/src/main/java/com/shadowai/app/security/BiometricKeyManager.kt`](app/src/main/java/com/shadowai/app/security/BiometricKeyManager.kt) | Biometric-bound keys |
| [`app/src/main/java/com/shadowai/app/security/PromptInjectionDefense.kt`](app/src/main/java/com/shadowai/app/security/PromptInjectionDefense.kt) | Jailbreak pattern detection, risk scoring |
| [`app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt`](app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt) | PII detection and masking |

### Database & Persistence

| File | Purpose |
|------|---------|
| [`app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt`](app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt) | Room database (WAL enabled) |
| [`app/src/main/java/com/shadowai/app/db/ShadowPersistence.kt`](app/src/main/java/com/shadowai/app/db/ShadowPersistence.kt) | Persistence layer |
| [`app/src/main/java/com/shadowai/app/db/DatabasePrunerWorker.kt`](app/src/main/java/com/shadowai/app/db/DatabasePrunerWorker.kt) | Automated data expiration |

---

## 4. Operational Constraints

### Forbidden Zones (Do Not Modify)

1. **`legacy_archive/`** - Deprecated code kept for reference only
2. **`archive/`** - Build artifacts and legacy implementations
3. **`backend/build/`** - Generated backend build files
4. **Any `build/` directory** - Generated artifacts, do not commit
5. **`model-catalog/build/`** - Generated AAR artifacts

### Safety Rules

1. **Never modify Gradle lockfiles manually** - Use `./gradlew dependencies --update` or rebuild
2. **Never commit changes to `.gradle/` cache directories**
3. **Never modify `AndroidManifest.xml` permissions without explicit justification**
4. **Never hardcode API keys** - Use encrypted SharedPreferences via `SecurityManager`
5. **Never bypass CircuitBreaker** - All provider calls must go through fault tolerance

### Critical Implementation Rules

1. **All new code MUST use Kotlin Coroutines** - No blocking calls on main thread
2. **All new modules MUST depend on `core-contracts`** - Enforces architecture
3. **All UI MUST use Jetpack Compose** - Migration complete, no new XML layouts
4. **All new code MUST use Hilt DI** - Manual instantiation forbidden
5. **All security-sensitive operations MUST use `SecretBytes` pattern** - No immutable string keys

---

## 5. Coding Standards

### Kotlin Conventions

```kotlin
// CORRECT: Using withSecretBytes for secure operations
secret.withSecretBytes({ decryptKey(encryptedKey) }) { key ->
    cipher.doFinal(key)
}

// CORRECT: CircuitBreaker wrapped calls
val result = circuitBreaker.execute {
    provider.generate(prompt)
}

// CORRECT: Flow-based reactive patterns
fun observeMessages(): Flow<List<ChatMessage>> = messageDao.getAll()

// INCORRECT: Blocking calls
val messages = messageDao.getAll().blockingFirst()
```

### Dependency Injection Pattern

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var providerRepo: ProviderRepository
    @Inject lateinit var agent: ShadowAgent
}
```

### Error Handling Pattern

```kotlin
// Use Result<T> for operations that can fail
suspend fun withProviderApiKey(providerId: ProviderId, block: suspend (ByteArray) -> Unit): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            val key = secureRepository.getApiKey(providerId)
            block(key)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Compose UI Pattern

```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.messages) { message ->
                ChatMessageItem(message = message)
            }
        }
        MessageInput(
            value = uiState.inputText,
            onValueChange = viewModel::onInputChanged,
            onSend = viewModel::onSend
        )
    }
}
```

---

## 6. Build Commands

### Standard Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test

# Lint checks
./gradlew lint
```

### Module-Specific Builds

```bash
# Build specific module AAR
./gradlew :provider-adapters:assembleDebug

# Build all modules
./gradlew assembleDebug

# Clean build (use with caution - slow)
./gradlew clean assembleDebug
```

### Backend Service (Ktor)

```bash
# Run backend (requires JAVA_HOME or JVM 17+)
cd backend && ./gradlew run

# Build backend fat JAR
cd backend && ./gradlew shadowJar
```

---

## 7. Security Patterns

### SecretBytes Pattern

All API keys MUST use the `SecretBytes` pattern to prevent immutable strings in memory:

```kotlin
val secret = SecretBytes(32)
secret.fill { java.security.SecureRandom().nextBytes(this) }

secret.withSecretBytes({ it }) { key ->
    // Key is zeroed after this block
    cipher.doFinal(key)
}
```

### Prompt Injection Defense

All user prompts MUST be validated before LLM processing:

```kotlin
val defense = PromptInjectionDefense()
val result = defense.verifyPrompt(userInput)
if (!result.isSafe) {
    throw PromptInjectionException(result.reason)
}
```

### Circuit Breaker Usage

All external provider calls MUST use circuit breakers:

```kotlin
val breakerManager = ProviderCircuitBreakerManager()
val breaker = breakerManager.getBreaker(providerId, modelId)

val response = breaker.execute {
    apiClient.generate(request)
}
```

---

## 8. Testing Requirements

### Test Coverage Expectations

| Component | Minimum Coverage |
|-----------|-----------------|
| Security classes | 90% |
| CircuitBreaker | 95% |
| Provider plugins | 80% |
| Database operations | 85% |

### Test Structure

```
app/src/test/kotlin/com/shadowai/app/
├── execution/
│   ├── CircuitBreakerTest.kt
│   └── TaskExecutorTest.kt
├── security/
│   ├── SecurityManagerTest.kt
│   └── PromptInjectionDefenseTest.kt
└── providers/
    └── ProviderPluginRegistryTest.kt
```

---

## 9. Documentation Requirements

### KDoc Standards

All public classes and functions MUST have KDoc:

```kotlin
/**
 * Manages AI provider configurations and API key storage.
 *
 * This class provides secure storage for provider credentials using
 * AES-256-GCM encryption and handles provider discovery.
 *
 * @property securityManager The security manager for encryption operations
 * @see ProviderPlugin
 */
class ProviderRepository @Inject constructor(
    private val securityManager: SecurityManager
) { }
```

### Architecture Decisions

Document non-trivial changes in:
- [`plans/`](plans/) directory for architectural decisions
- PR descriptions for implementation details
- Code comments for complex logic

---

## 10. Git Workflow

### Branch Naming

- `feature/` - New features (e.g., `feature/new-provider-adapter`)
- `fix/` - Bug fixes (e.g., `fix/circuit-breaker-timeout`)
- `refactor/` - Code improvements (e.g., `refactor/security-manager`)
- `chore/` - Maintenance tasks

### Commit Messages

```
<type>(<scope>): <subject>

<body>

<footer>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Example:
```
feat(providers): add OpenAICompatibleAdapter

Implement adapter for OpenAI-compatible APIs including:
- Chat completions endpoint
- Streaming response support
- Model listing

Closes #123
```

---

## 11. Debugging & Diagnostics

### Key Log Tags

| Tag | Component |
|-----|-----------|
| `ShadowAI` | Main application tag |
| `ProviderRepo` | Provider management |
| `TaskExecutor` | Task execution |
| `CircuitBreaker` | Fault tolerance |
| `LocalInference` | Local model inference |
| `SecurityManager` | Security operations |

### Diagnostic Files

| File | Purpose |
|------|---------|
| [`diagnostics/`](diagnostics/) | Diagnostics module (Phase 7 deliverable) |
| [`app/src/main/java/com/shadowai/app/notifications/NotificationManager.kt`](app/src/main/java/com/shadowai/app/notifications/NotificationManager.kt) | Runtime notification diagnostics |

---

## 12. External Integrations

### Backend Services

| Service | Purpose | Config File |
|---------|---------|-------------|
| Novita AI | Image generation | [`backend/src/main/kotlin/com/shadowai/backend/NovitaService.kt`](backend/src/main/kotlin/com/shadowai/backend/NovitaService.kt) |
| PixAI | Image generation | [`backend/src/main/kotlin/com/shadowai/backend/PixAiService.kt`](backend/src/main/kotlin/com/shadowai/backend/PixAiService.kt) |

### Firebase Services

- Authentication (`firebase-auth`)
- Analytics (`firebase-analytics`)
- Firestore (`firebase-firestore`)
- Cloud Functions (`firebase-functions`)
- Remote Config (`firebase-config`)
- Crashlytics (`firebase-crashlytics`)

---

## 13. Mode-Specific Guidance

### Code Mode

- Follow existing patterns in [`app/src/main/java/com/shadowai/app/`](app/src/main/java/com/shadowai/app/)
- Use surgical edits with `search_and_replace` tool
- Prefer `edit_file` over full file rewrites
- Always run `./gradlew assembleDebug` after changes

### Architect Mode

- Review [`plans/todo.md`](plans/todo.md) for current phase
- Update architecture diagrams in [`plans/`](plans/)
- Ensure new modules follow dependency rules
- No code implementation in this mode

### Debug Mode

- Use `CircuitBreaker` state logs for fault analysis
- Check `SecurityManager` logs for encryption issues
- Review `TaskExecutor` logs for routing problems
- Check `ShadowDatabase` WAL status for performance

---

## 14. Quick Reference

| Concept | Key File |
|---------|----------|
| Provider system | [`app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt`](app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt) |
| Task execution | [`app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt`](app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt) |
| Circuit breaker | [`app/src/main/java/com/shadowai/app/execution/CircuitBreaker.kt`](app/src/main/java/com/shadowai/app/execution/CircuitBreaker.kt) |
| Security manager | [`app/src/main/java/com/shadowai/app/security/SecurityManager.kt`](app/src/main/java/com/shadowai/app/security/SecurityManager.kt) |
| Local inference | [`app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt`](app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt) |
| Agent system | [`app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt`](app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt) |
| Compose UI | [`app/src/main/java/com/shadowai/app/ui/chat/ChatScreen.kt`](app/src/main/java/com/shadowai/app/ui/chat/ChatScreen.kt) |
| Database | [`app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt`](app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt) |
| Build config | [`app/build.gradle.kts`](app/build.gradle.kts) |
| Architecture plan | [`plans/todo.md`](plans/todo.md) |
| Essential worktree | [`WORKTREE.md`](WORKTREE.md) |

---

## 15. Troubleshooting

### Build Failures

1. **Clean and rebuild**: `./gradlew clean assembleDebug`
2. **Invalidate caches**: File → Invalidate Caches / Restart
3. **Check Gradle version**: `cat gradle/wrapper/gradle-wrapper.properties`
4. **Check Java version**: Must be JDK 17 (`java -version`)

### Runtime Issues

1. **Circuit breaker open**: Check `ProviderCircuitBreakerManager` logs
2. **Security exceptions**: Verify `SecurityManager` initialization
3. **Database locked**: Enable WAL in `ShadowDatabase` builder
4. **Native crashes**: Check `app/src/main/cpp/CMakeLists.txt` for NDK config

---

*This document is auto-generated and should be updated when architecture or key components change.*
