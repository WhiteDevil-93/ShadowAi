# ShadowAi - Android AI Assistant

[![Android](https://img.shields.io/badge/Android-16+-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A modular Android AI application with local inference, multi-provider support, and security-first architecture.

**⚠️ Status: Development / Pre-Release**

This project is actively under development. Some documented features are partially implemented or pending UI integration.

---

## Overview

ShadowAi is a modular Android AI assistant built with a **provider-agnostic, capability-driven architecture**:

- **Local LLM Inference** via llama.cpp integration
- **Multi-Provider Support** with unified adapter interface
- **Security-First Design** including PII masking and secure memory handling
- **Modular Gradle Structure** for clean architecture boundaries

### Architecture Philosophy

1. **Provider Abstraction** - All AI interactions flow through standardized interfaces
2. **Zero-Trust Security** - Cryptographic isolation at every boundary
3. **Local-First Data Sovereignty** - User data stays on device by default
4. **Clean Module Boundaries** - Enforced through Gradle dependencies

---

## Features

### ✅ Implemented & Working

| Feature | Status | Notes |
|---------|--------|-------|
| Local LLM inference (llama.cpp) | ✅ Complete | JNI bridge with GGUF support |
| PII masking for cloud providers | ✅ Complete | Email, phone, SSN detection |
| Provider adapter framework | ✅ Complete | Core contracts defined |
| Circuit breaker pattern | ✅ Complete | Per-provider fault tolerance |
| Token counting | ✅ Complete | Accurate context estimation |
| Thread-safe model discovery | ✅ Complete | Mutex-protected scanning |
| Room database persistence | ✅ Complete | Model metadata storage |
| Coroutine-based concurrency | ✅ Complete | Structured async patterns |
| Error taxonomy | ✅ Complete | 7 error categories |
| Quantization detection | ✅ Complete | Q2_K through F32 support |
| Memory estimation | ✅ Complete | Per-quantization RAM estimates |
| TLS certificate pinning | ✅ Complete | Network security config |

### ⚠️ Implemented (Partial/Needs Integration)

| Feature | Status | Notes |
|---------|--------|-------|
| NNAPI Delegation | ⚠️ Backend ready | UI toggles wired, NPU detection present |
| Memory-mapped models | ⚠️ Backend ready | JNI layer supports, UI toggles present |
| Conversation summarization | ⚠️ Core logic done | Auto-trigger pending UI integration |
| Isolated inference process | ⚠️ Module exists | `:inference_process` separate APK |
| Summary indicator UI | ⚠️ Components ready | SummaryViewModel, SummaryIndicator exist |
| Generation settings | ⚠️ UI complete | NNAPI, mmap, summarization toggles wired |

### 🚧 In Progress / Planned

| Feature | Status | Notes |
|---------|--------|-------|
| Provider adapters (OpenAI, Anthropic, etc.) | 🚧 Phase 2 | Interface defined, implementations pending |
| Artifact system | 🚧 Phase 3 | I/O normalization planned |
| Pipeline planner | 🚧 Phase 4 | Graph-based transformations |
| Biometric auth integration | 🚧 Partial | BiometricKeyManager exists, UI integration pending |
| Share sheet handling | 🚧 Missing | Intent filters present, handling code needed |
| Export formats (PDF, Markdown) | 🚧 Planned | Not yet implemented |

---

## Architecture

### Module Structure

```
ShadowAi/
├── app/                          # Main Android application
├── core-contracts/               # Shared interfaces & security
│   └── security/
│       ├── PiiMaskingProcessor.kt
│       ├── SecretBytes.kt
│       └── PromptInjectionDefense.kt
├── model-catalog/                # Model discovery & deduplication
├── provider-adapters/            # Cloud provider implementations (Phase 2)
├── artifact-system/              # I/O normalization (Phase 3)
├── pipeline-planner/             # Graph-based transformations (Phase 4)
├── inference_process/            # Isolated inference service
├── diagnostics/                  # Error handling & debugging
├── hot-swapping/                 # Runtime config management
├── ui-composition/               # Compose UI components
├── ui-params/                    # Parameter validation
├── ui-validator/                 # Input validation
└── backend/                      # Ktor server (image gen APIs)
```

### Key Components

| Component | Purpose | File |
|-----------|---------|------|
| SupervisorAgent | High-level routing | `agent/SupervisorAgent.kt` |
| AgenticLoop | Multi-step execution | `agent/AgenticLoop.kt` |
| ContextManager | Sliding window truncation | `agent/ContextManager.kt` |
| IsolatedInferenceManager | Process isolation | `ai/IsolatedInferenceManager.kt` |
| ProviderRepository | Provider config management | `providers/ProviderRepository.kt` |
| CircuitBreaker | Fault tolerance | `execution/CircuitBreaker.kt` |
| PiiMaskingProcessor | Privacy protection | `security/PiiMaskingProcessor.kt` |

### Security Architecture

```
User Input → PiiMaskingProcessor → [Cloud Provider]
    ↓
Secure Memory (SecretBytes)
    ↓
EncryptedSharedPreferences
```

**Security Features Implemented:**
- PII detection and masking (email, phone, SSN, credit card, API keys)
- Secure memory handling with automatic zeroing (`SecretBytes`)
- TLS certificate pinning (`network_security_config.xml`)
- Prompt injection defense with risk scoring

---

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 24 (Android 7.0) minimum
- JDK 17 or later
- NDK r25c or later (for native builds)

### Build Commands

```bash
# Clone repository
git clone https://github.com/yourusername/ShadowAi.git
cd ShadowAi

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (requires signing config)
./gradlew :app:assembleRelease

# Run unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest

# Build all modules
./gradlew assembleDebug
```

### Module-Specific Builds

```bash
# Build core-contracts AAR
./gradlew :core-contracts:assembleDebug

# Build model-catalog AAR
./gradlew :model-catalog:assembleDebug

# Clean build
./gradlew clean assembleDebug
```

### Backend (Ktor Server)

```bash
cd backend
./gradlew run          # Run development server
./gradlew shadowJar    # Build fat JAR
```

---

## Provider Setup

### Local Inference (llama.cpp)

1. Download GGUF models from Hugging Face or other sources
2. Place models in `/sdcard/Android/data/com.shadowai.app/files/models/`
3. App will auto-detect on startup

**Supported Quantizations:** Q2_K, Q3_K*, Q4_0, Q4_1, Q4_K*, Q5_0, Q5_1, Q5_K*, Q6_K, Q8_0, Q8_K, F16, F32

### Cloud Providers

1. Open **Settings > AI Providers**
2. Select provider (OpenAI, Anthropic, Gemini, etc.)
3. Enter API key (stored encrypted)
4. Select desired model

**Security Note:** API keys are encrypted with AES-256-GCM and stored in `EncryptedSharedPreferences`.

---

## Configuration

### Runtime Settings

Settings are stored in `DataStore` and managed via UI:

| Setting | Location | Default |
|---------|----------|---------|
| NNAPI Acceleration | Settings > Generation | Device-dependent |
| Memory-Mapped Models | Settings > Generation | Enabled |
| Auto-Summarization | Settings > Generation | Enabled at 70% |
| Isolated Inference | Settings > Generation | Build-dependent |
| Temperature | Settings > Generation | 0.7 |
| Top P / Top K | Settings > Generation | 0.9 / 40 |

### Network Security

Certificate pinning is configured in:
- `app/src/main/res/xml/network_security_config.xml`

Pins are maintained for:
- OpenAI (api.openai.com)
- Anthropic (api.anthropic.com)
- Google Gemini (generativelanguage.googleapis.com)
- OpenRouter (openrouter.ai)
- DeepSeek (api.deepseek.com)
- Mistral AI (api.mistral.ai)
- Groq (api.groq.com)
- xAI (api.x.ai)

---

## Testing

### Test Structure

```
app/src/
├── test/
│   └── java/com/shadowai/app/
│       ├── security/
│       │   └── PiiMaskingProcessorTest.kt     (35+ test cases)
│       ├── ai/
│       │   ├── TokenCounterTest.kt
│       │   └── QuantizationHelperTest.kt
│       ├── agent/
│       │   └── ContextManagerTest.kt
│       └── execution/
│           └── CircuitBreakerTest.kt
└── androidTest/
    └── kotlin/com/shadowai/app/
        ├── thread/
        │   ├── ConcurrentRescanTest.kt
        │   └── AtomicStateTest.kt
        └── OnTrimMemoryTest.kt
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumentation tests
./gradlew connectedAndroidTest

# Single test class
./gradlew test --tests "PiiMaskingProcessorTest"
```

---

## Troubleshooting

### Build Issues

**NDK not found:**
```bash
# Set NDK path in local.properties
echo "ndk.dir=/path/to/ndk" >> local.properties
```

**Gradle sync failures:**
```bash
./gradlew clean
rm -rf ~/.gradle/caches
./gradlew assembleDebug
```

### Runtime Issues

**Model loading fails:**
- Verify GGUF file integrity
- Check available RAM vs model requirements
- Review logs: `adb logcat | grep ShadowAI`

**Out of memory:**
- Enable memory-mapped models in settings
- Use smaller quantization (Q4_0 instead of Q8_0)
- Enable auto-summarization

**Provider connection errors:**
- Verify API key is valid
- Check certificate pinning logs
- Review CircuitBreaker state

---

## Documentation

- [CHANGELOG.md](CHANGELOG.md) - Version history
- [AGENTS.md](AGENTS.md) - Development guidance
- [WORKTREE.md](WORKTREE.md) - Project structure
- [docs/adr/](docs/adr/) - Architecture Decision Records
- [docs/PERFORMANCE_OPTIMIZATION.md](docs/PERFORMANCE_OPTIMIZATION.md) - Performance features
- [docs/CODE_AUDIT_REPORT.md](docs/CODE_AUDIT_REPORT.md) - Issue tracking

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'feat: add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

See [AGENTS.md](AGENTS.md) for coding standards and architecture guidance.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Local LLM inference
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-guide.html) - Async programming
- [Hilt](https://dagger.dev/hilt/) - Dependency injection

---

*Last updated: 2026-02-11*
*Version: 1.0.0*
