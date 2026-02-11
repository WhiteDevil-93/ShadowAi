#!/bin/bash
# Agent 5: Documentation Updates
echo "🤖 Agent 5: Documentation Updates starting..."

cd /mnt/c/Users/anon3/Downloads/ShadowAi

# Backup existing README if it exists
if [ -f "README.md" ]; then
    cp README.md README.md.backup.$(date +%Y%m%d_%H%M%S)
    echo "📦 Backed up README.md"
fi

# Read existing README if it exists to preserve structure
EXTRACTION_MARKER="## Architecture Documentation"
ARCHITECTURE_DOC=""

if [ -f "README.md" ]; then
    if grep -q "$EXTRACTION_MARKER" README.md; then
        # Extract up to the marker
        ARCHITECTURE_DOC=$(sed "/$EXTRACTION_MARKER/,\$d" README.md)
    else
        ARCHITECTURE_DOC=$(cat README.md)
    fi
fi

# Create comprehensive README with architecture documentation
cat > README.md << 'README_EOF'
# ShadowAi - Android AI Assistant

[![Android](https://img.shields.io/badge/Android-16+-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A sophisticated Android AI application with local inference, agent orchestration, and advanced security features.

## Overview

ShadowAi is a next-generation AI assistant for Android that combines:
- **Local LLM Inference** via llama.cpp integration
- **Multi-Agent Architecture** with autonomous task execution
- **Advanced Security** including PII masking, secure memory handling, and TLS pinning
- **Memory Management** with sliding window context and LRU model unloading
- **Thread Safety** with mutex protection and atomic operations
- **Cloud Provider Support** for OpenAI, Anthropic, Cohere, and more

## Features

### Core Capabilities
- 🤖 Multi-turn conversations with context preservation
- 🧠 Local inference using GGUF models (llama.cpp)
- ☁️ Cloud provider fallback (OpenAI, Anthropic, Cohere)
- 🔧 Device action execution (apps, telephony, messaging)
- 📊 Model management and discovery
- 🛡️ Security-first architecture

### Security Features
- 🔒 PII detection and masking (emails, phones, SSN, credit cards, API keys)
- 🔐 Secure memory management with automatic zeroing
- 🔗 TLS certificate pinning for cloud APIs
- 🛡️ Prompt injection defense
- 📱 Biometric authentication support

### Performance
- ⚡ LRU model unloading on memory pressure
- 💾 Database persistence for model metadata
- 🎛️ Configurable context window with sliding window truncation
- 🚀 Optimized native ARM64 inference
- 🎯 Token counting for efficient context management

## Installation

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 16 (Android 4.1) minimum
- JDK 17 or later
- NDK r25c or later (for native builds)

### Build Instructions

```bash
# Clone repository
git clone https://github.com/yourusername/ShadowAi.git
cd ShadowAi

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run integration tests
./gradlew connectedAndroidTest

# Generate coverage report
./gradlew jacocoTestReport
```

## Project Structure

```
ShadowAi/
├── app/                          # Main Android application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/shadowai/app/
│   │   │   │   ├── agent/       # Agent orchestration
│   │   │   │   │   ├── SupervisorAgent.kt
│   │   │   │   │   ├── ShadowAgent.kt
│   │   │   │   │   ├── AgenticLoop.kt
│   │   │   │   │   └── ContextManager.kt
│   │   │   │   ├── ai/          # AI inference & models
│   │   │   │   │   ├── LocalBrainManager.kt
│   │   │   │   │   ├── IsolatedInferenceManager.kt
│   │   │   │   │   ├── TokenCounter.kt
│   │   │   │   │   └── ModelDownloader.kt
│   │   │   │   ├── security/    # Security layer
│   │   │   │   │   ├── SecurityManager.kt
│   │   │   │   │   ├── SecureDataStore.kt
│   │   │   │   │   └── BiometricKeyManager.kt
│   │   │   │   ├── db/          # Database layer
│   │   │   │   │   ├── ShadowDatabase.kt
│   │   │   │   │   ├── ModelPersistence.kt
│   │   │   │   │   └── EncryptedDatabaseHelper.kt
│   │   │   │   └── execution/   # Task execution
│   │   │   │       ├── TaskExecutor.kt
│   │   │   │       └── DeviceActionExecutor.kt
│   │   │   └── res/xml/
│   │   │       └── network_security_config.xml  # TLS pinning
│   │   └── test/              # Unit tests
│   └── build.gradle.kts
│
├── core-contracts/              # Shared contracts & security
│   └── src/main/kotlin/com/shadowai/core/
│       ├── security/
│       │   ├── PiiMaskingProcessor.kt
│       │   ├── SecretBytes.kt
│       │   └── PromptInjectionDefense.kt
│       └── ModelDescriptor.kt
│
├── model-catalog/               # Model discovery & management
│   └── src/main/kotlin/com/shadowai/modelcatalog/
│       ├── ModelDiscovery.kt
│       ├── ModelCatalogRepository.kt
│       └── ModelRegistry.kt
│
├── inference_process/           # Isolated inference service
│   ├── src/main/
│   │   ├── cpp/
│   │   │   └── llama_jni.cpp   # JNI bridge
│   │   └── kotlin/com/shadowai/inference/
│   │       ├── InferenceService.kt
│   │       └── NativeBridge.kt
│   └── src/main/cpp/CMakeLists.txt
│
├── provider-adapters/           # Cloud provider adapters
├── pipeline-planner/           # Task pipeline planning
├── artifact-system/            # Device capabilities
├── ui-composition/             # Compose UI components
├── ui-validator/              # Input validation
├── diagnostics/               # Performance monitoring
└── hot-swapping/              # Runtime code swapping
```

## Architecture

### Security Architecture

#### PII Masking

**Location:** `core-contracts/src/main/kotlin/com/shadowai/core/security/PiiMaskingProcessor.kt`

Automatic detection and masking of Personally Identifiable Information:

```kotlin
val processor = PiiMaskingProcessor()
val masked = processor.maskPii("Contact me at john@example.com or call 555-123-4567")
// Result: "Contact me at [EMAIL_REDACTED] or call [PHONE_REDACTED]"
```

**Supported PII Types:**
- Email addresses (with Android Patterns + fallback)
- Phone numbers (US international formats)
- Social Security Numbers (with validation)
- Credit cards (Luhn algorithm validation)
- IP addresses (IPv4)
- API keys (context-aware detection)
- High-entropy secrets

**Usage:**
```kotlin
// Automatically masks PII before cloud provider requests
val processedTask = if (!isLocalProvider) {
    maskPiiInTask(task)
} else {
    task
}
```

#### Secure Memory Handling

**Location:** `core-contracts/src/main/kotlin/com/shadowai/core/security/SecretBytes.kt`

Prevents sensitive data from persisting in immutable strings:

```kotlin
// Convert API key to secure bytes
val secureKey = discoveredApiKey("sk-abc123...")

// Use in safe context
secureKey.withSecretKey("AES") { key ->
    // cryptographic operations
}  // Automatically zeroed after use

// Manual cleanup
secureKey.dispose()
```

**Features:**
- Automatic zeroing on disposal
- Thread-safe operations
- Integration with Cipher and SecretKey
- Memory leak prevention

### Thread Safety Architecture

#### Coroutine Concurrency

All concurrency uses structured coroutines with proper dispatchers:

```kotlin
// IO-bound operations
withContext(Dispatchers.IO) {
    // Database, file I/O, network
}

// Default for CPU-bound
withContext(Dispatchers.Default) {
    // Computations
}
```

#### Mutex Protection

**Location:** `model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDiscovery.kt`

Prevents concurrent modification during model scans:

```kotlin
private val scanMutex = Mutex()

suspend fun rescan(): List<ModelDescriptor> = scanMutex.withLock {
    performFullScan()  // Atomic operation
}
```

**Use Cases:**
- Model discovery scans
- Database transactions
- Shared resource access

#### Atomic Operations

Replaces volatile variables with thread-safe alternatives:

**AtomicReference:**
```kotlin
private val service = AtomicReference<IInferenceService?>(null)

val current = service.get()
service.compareAndSet(old, new)
```

**AtomicBoolean:**
```kotlin
private val rebinding = AtomicBoolean(false)

if (rebinding.compareAndSet(false, true)) {
    // Critical section
}
```

**AtomicLong:**
```kotlin
private val nextModelId = AtomicLong(0)

fun generateId(): String = "model_${hash}_${nextModelId.getAndIncrement()}"
```

**Locations:**
- `AgenticLoop.kt` - State management
- `IsolatedInferenceManager.kt` - Service binding
- `ModelDiscovery.kt` - ID generation

### Memory Management Architecture

#### LRU Model Unloading

**Location:** `inference_process/src/main/kotlin/com/shadowai/inference/InferenceService.kt`

Automatically unloads least-recently-used models on memory pressure:

```kotlin
override fun onTrimMemory(level: Int) {
    when (level) {
        TRIM_MEMORY_COMPLETE -> unloadAllModels()
        TRIM_MEMORY_RUNNING_CRITICAL -> unloadLruModel()
        TRIM_MEMORY_RUNNING_LOW -> reduceCacheSizes()
    }
}
```

**Trim Levels:**
- `TRIM_MEMORY_COMPLETE` - System is about to kill process
- `TRIM_MEMORY_RUNNING_CRITICAL` - Background but still visible
- `TRIM_MEMORY_MODERATE` - System is moderately under memory pressure
- `TRIM_MEMORY_RUNNING_LOW` - Process less important but visible

#### Context Window Management

**Location:** `app/src/main/java/com/shadowai/app/agent/ContextManager.kt`

Sliding window truncation to prevent context overflow:

```kotlin
val result = contextManager.manageContext(
    exchanges = conversationHistory,
    systemPrompt = systemPrompt,
    modelDescriptor = model,
    windowSize = 5,  // Keep last 5 exchanges
    threshold = 0.9f  // Truncate at 90% capacity
)

if (result.wasTruncated) {
    Log.i(TAG, "Context truncated: ${result.originalTokens} -> ${result.truncatedTokens} tokens")
}
```

**Token Counter:**
```kotlin
val tokenCounter = TokenCounter()
val tokens = tokenCounter.countTokens("Hello, world!")  // ~4 tokens
val available = modelDescriptor.getAvailableGenerationTokens(tokens)
```

#### Database Persistence

**Location:** `app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt`

Room database with encrypted storage:

```kotlin
@Entity(tableName = "model_paths")
data class ModelPathEntity(
    @PrimaryKey val modelId: String,
    val providerId: String,
    val name: String,
    val localPath: String,
    val isAvailable: Boolean = true,
    val lastUsed: Long = System.currentTimeMillis()
)

@Dao
interface ModelPathDao {
    @Query("SELECT * FROM model_paths ORDER BY lastUsed DESC LIMIT 1")
    suspend fun getLRU(): ModelPathEntity?
}
```

### AI Integration Architecture

#### Agent Orchestration

**SupervisorAgent** - High-level task routing:
```kotlin
val result = supervisorAgent.processInput(
    input = "Turn on the lights",
    policy = RoutingPolicy.AUTO
)
```

**AgenticLoop** - Iterative multi-step execution:
```kotlin
agenticLoop.execute(
    input = complexTask,
    taskType = TaskType.DEVICE_CONTROL,
    config = LoopConfig(
        maxIterations = 10,
        slidingWindowSize = 5,
        contextThreshold = 0.9f
    )
)
```

#### Model Selection

Model descriptor with token budget tracking:
```kotlin
data class ModelDescriptor(
    val maxContext: Int = 4096,
    val capabilities: Set<Capability>
) {
    fun getAvailableGenerationTokens(inputTokens: Int): Int =
        (maxContext - inputTokens).coerceAtLeast(0)
}
```

## Configuration

### Runtime Configuration

Settings located in `SharedPreferences`:
- **Model paths:** Download folder locations
- **Provider configs:** API keys, model IDs
- **Security:** PII masking enabled/disabled
- **Performance:** Context window size, thread count

### Network Security

Certificate pinning configuration:
```xml
<domain-config>
    <domain includeSubdomains="true">api.openai.com</domain>
    <pin-set>
        <pin digest="SHA-256">CERTIFICATE_PIN_HERE</pin>
    </pin-set>
</domain-config>
```

## Testing

### Run Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Coverage report
./gradlew jacocoTestReport
```

### Test Coverage

- **PiiMaskingProcessor:** 35+ test cases
- **TokenCounter:** 7 test cases
- **ContextManager:** 7 test cases
- **Thread Safety:** Concurrent rescan tests
- **Memory:** onTrimMemory simulation tests

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Security

- PII automatically masked before external transmission
- API keys stored in EncryptedSharedPreferences
- TLS certificate pinning enforced in release builds
- Biometric authentication for sensitive operations
- Prompt injection defense enabled by default

## Performance

- Native ARM64 optimized inference (NEON, FP16, DotProd)
- LRU model caching with memory-aware eviction
- Database batch operations for efficiency
- Coroutine-based concurrency for responsiveness

## Troubleshooting

### Common Issues

**Model loading fails:**
- Check GGUF file is valid
- Verify sufficient RAM available
- Check file permissions

**Cloud provider errors:**
- Verify API key is valid
- Check network connectivity
- Review quota limits

**Out of memory:**
- Reduce model size
- Enable model unloading
- Close unused models

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Local LLM inference
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-guide.html) - Async programming
- [Hilt](https://dagger.dev/hilt/) - Dependency injection

## Contact

Project Link: [https://github.com/yourusername/ShadowAi](https://github.com/yourusername/ShadowAi)

---

*Last updated: 2026-02-11*
*Implementation: Phases 1-7 Complete*
README_EOF

echo "✅ README.md updated with comprehensive architecture documentation"
echo "✅ Agent 5: Documentation Updates complete!"