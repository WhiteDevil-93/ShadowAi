# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

#### Critical Fixes (P0)

- **AndroidManifest.xml**: Fixed broken application class reference from Hilt-generated name (`ShadowApplication_HiltApplication`) to actual class name (`.ShadowApplication`)
- **AndroidManifest.xml**: Removed invalid `android:exported="false"` attribute from `<application>` tag (only valid on components)
- **AppModule.kt**: Removed fake placeholder certificate pinning that was security theater (A's, B's, C's placeholders)
- **build.gradle.kts**: Added comprehensive documentation about applicationId Play Store compliance issues

#### High-Priority Fixes (P1)

- **SecureDataStore.kt**: Changed `runBlocking` calls to use `Dispatchers.IO` to prevent main thread blocking
- **SecureDataStore.kt**: Added `@Deprecated` annotations to blocking methods to encourage migration
- **ProviderRepository.kt**: Fixed `saveSync` to use `Dispatchers.IO` and added deprecation warning
- **ShadowApplication.kt**: Changed application scope from `Dispatchers.Main` to `Dispatchers.Default` for background work
- **ShadowDatabase.kt**: Improved migration strategy with clear debug/release differentiation and better error logging

#### Medium-Priority Fixes (P2)

- **ShadowAgent.kt**: Completely rewrote `determineTaskType()` to use word boundaries and command-prefix matching, significantly reducing false positives (e.g., "don't call me" no longer triggers TELEPHONY)
- **MemoryPressureMonitor.kt**: Replaced `Thread.sleep()` with coroutine `delay()` to avoid blocking threads
- **proguard-rules.pro**: Changed to only strip DEBUG/VERBOSE logs, preserving INFO/WARN/ERROR for crash diagnostics
- **ShadowAgentTest.kt**: Fixed incorrect DeviceAction class names (AppLaunch, Sms, Call)

### Added

#### New Unit Tests

- **ChatViewModelTest.kt**: Comprehensive tests for chat UI state management, message loading, sending, and error handling
- **ShadowAgentTest.kt**: Tests for prompt injection defense, safe input processing, action verification
- **SecureDataStoreTest.kt**: Tests for encryption/decryption patterns, tampering detection, unicode handling
- **ProviderRepositoryTest.kt**: Tests for provider management, API key storage, connection testing
- **TaskTypeDetectionTest.kt**: Tests for improved NLP-based task routing with false positive prevention
- **LocalInferenceManagerTest.kt**: Tests for utility functions like byte formatting, path normalization
- **CircuitBreakerTest.kt**: Tests for circuit breaker state transitions and failure handling
- **DeviceActionExecutorTest.kt**: Tests for device action routing to system contracts
- **DeviceActionParserTest.kt**: Tests for JSON parsing/serialization with strict schema validation
- **RateLimiterTest.kt**: Tests for rate limiting, exponential backoff, and error detection

#### New Infrastructure

- **RateLimiter.kt**: Token bucket rate limiter with exponential backoff for API calls
- **SafetySettingsManager.kt**: Configurable Gemini safety filters (BLOCK_NONE in debug, BLOCK_MEDIUM_AND_ABOVE in release)
- **ShadowMigrations.kt**: Room migration infrastructure with template migrations and validation helpers
- **ErrorHandler.kt**: Centralized error logging and crash reporting (Category enum, debug crash files)
- **NetworkMonitor.kt**: Real-time network connectivity validation with reactive flows
- **RetryPolicy.kt**: Configurable retry strategies with exponential backoff and jitter
- **Validate.kt**: Defensive programming utility for input validation

#### New Unit Tests (Continued)

- **SafetySettingsManagerTest.kt**: Tests for safety level configuration and persistence
- **ShadowMigrationsTest.kt**: Tests for migration infrastructure and helpers
- **ErrorHandlerTest.kt**: Tests for error categorization and logging extensions
- **NetworkMonitorTest.kt**: Tests for network status detection logic
- **RetryPolicyTest.kt**: Tests for retry behavior and backoff calculation
- **ValidateTest.kt**: Tests for all validation functions
- **SecurityManagerTest.kt**: Tests for AES-GCM encryption patterns without Keystore
- **HybridAiExecutorTest.kt**: Integration tests for multi-agent coordination logic

### Security

- Removed placeholder certificate pins that provided false sense of security
- Added proper IO dispatcher isolation for blocking cryptographic operations
- Improved prompt injection defense with template verification
- Configurable Gemini safety settings (no longer hardcoded to BLOCK_NONE in release builds)
- Rate limiting prevents API abuse and credential revocation

### Documentation

- Added detailed comments explaining applicationId constraints and migration path
- Added migration strategy documentation in ShadowDatabase
- Created this CHANGELOG to track changes

## [1.0.0] - 2026-02-04

### Initial Release

- Multi-provider AI assistant with cloud and local inference support
- Device control capabilities (telephony, messaging, media, system)
- Secure API key storage with Tink encryption
- SQLCipher-encrypted local database
- Google Sign-In authentication
- Prompt injection defense system
- Zero-trust security architecture with TEE support

---

## Migration Notes

### For Developers

#### runBlocking Deprecation

The following methods are now deprecated and will show warnings:

- `SecureDataStore.getStringBlocking()` → Use `getString()` suspend function
- `SecureDataStore.putStringBlocking()` → Use `putString()` suspend function
- `SecureDataStore.removeBlocking()` → Use `remove()` suspend function
- `ProviderRepository.saveSync()` → Use `saveProvider()` suspend function

#### Application ID Change (Future)

The current applicationId `Shadow_Ai.V1` violates Play Store conventions. To migrate:

1. Create new Firebase app with proper ID (e.g., `com.shadowai.app`)
2. Download new `google-services.json`
3. Update applicationId in `build.gradle.kts`
4. Note: This creates a NEW app on Play Store

### For Users

No user-facing changes in this update.
