# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- QuantizationHelper for automatic GGUF quantization detection (Q2_K through F32)
- ConversationSummarizer core logic with 70% threshold auto-trigger
- SummaryViewModel for UI state management
- SummaryIndicator and SummaryDetail Compose components
- GenerationSettingsScreen with performance optimization toggles
- ConversationRepository for DataStore-based summary persistence
- NNAPI delegation detection (DeviceCapabilities.kt)
- Memory-mapped model loading support in JNI layer
- Isolated inference process module (:inference_process)
- UserPreferences integration for all performance settings

### Changed
- Updated AGENTS.md with current architecture guidance
- Restructured documentation in docs/ directory
- Improved memory estimation algorithm to avoid double-multiplication

### Fixed
- SummaryIndicator duration formatting (correct hours/minutes display)
- VoiceRecognitionManager state consistency (removed duplicate isListening)
- QuantizationHelper memory estimation (eliminated double 2.0x multiplier)

### Security
- TLS certificate pinning for all cloud providers
- SecretBytes pattern for secure API key handling
- PiiMaskingProcessor with 35+ test patterns
- Prompt injection defense with risk scoring

### Technical Debt / Known Issues
- Conversation summarization auto-trigger needs ChatScreen integration
- Summary restoration UI exists but end-to-end flow pending
- Biometric auth components exist but not wired to UI flows
- Share sheet intent filters present but handling code missing
- Export formats (PDF, Markdown) planned but not implemented
- Some unit tests pending for new components

## [1.0.0] - 2026-02-11

### Added
- Initial modular architecture with 12 Gradle modules
- Local LLM inference via llama.cpp JNI integration
- Core security layer (PII masking, SecretBytes, TLS pinning)
- Provider adapter framework with ProviderExecutor interface
- Circuit breaker pattern for fault tolerance
- Error taxonomy (7 categories: TRANSPORT, SEMANTIC, LOGIC, VIOLATION, EXECUTION, EXHAUSTION, SECURITY)
- Model catalog with deduplication and discovery
- Room database with encrypted storage
- Structured concurrency with Kotlin Coroutines
- Hilt dependency injection throughout
- Jetpack Compose UI architecture

### Architecture
- Modular design: app, core-contracts, model-catalog, provider-adapters, inference_process
- Clean separation between local and cloud inference
- Thread-safe operations with Mutex and AtomicReference
- LRU model unloading on memory pressure
- Sliding window context management

### Security
- AES-256-GCM encryption for API keys
- TEE/StrongBox-backed key generation support
- Biometric authentication key binding
- Network security config with certificate pinning
- PII detection for email, phone, SSN, credit cards, API keys

### Testing
- PiiMaskingProcessor: 35+ test cases
- TokenCounter: 7 test cases
- ContextManager: 7 test cases
- Concurrent rescan thread safety tests
- Atomic state consistency tests

[Unreleased]: https://github.com/yourusername/ShadowAi/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/yourusername/ShadowAi/releases/tag/v1.0.0
