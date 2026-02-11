# ShadowAi Android App - Security & Privacy Audit Report

**Audit Date:** 2026-02-09  
**Auditor:** Security Skeptic (Subagent)  
**Repository:** /mnt/c/Users/anon3/Downloads/ShadowAi  
**App Module:** app/src/main

---

## Executive Summary

The ShadowAi Android app demonstrates **above-average security practices** with strong encryption for sensitive data (SQLCipher, Tink-based SecureDataStore), hardware-backed keystore usage, and proper ProGuard rules. However, several **privacy and permissions issues** require attention before production release.

**Overall Risk Score:** MEDIUM
- HIGH: 2 issues
- MEDIUM: 5 issues  
- LOW: 4 issues

---

## 1. API Keys & Secrets

### ✅ SECURE - API Key Storage
**Finding:** API keys are stored using `SecureDataStore` with Google Tink (AES-256-GCM) and hardware-backed Android Keystore.

**Location:** 
- `AdminRepository.kt:151,308,310,325,326`
- `SecureDataStore.kt` (Tink AEAD encryption)

**Risk:** LOW ✓

---

### ⚠️ MEDIUM - BuildConfig Exposes GCP Variables
**Finding:** `BuildConfig` fields for GCP_PROJECT_ID, GCP_REGION, and GCP_FUNCTIONS_REGION are exposed. While empty by default (populated from gradle.properties), the structure reveals cloud provider architecture.

**Location:** `app/build.gradle.kts:52-54,78-80`

**Recommendation:** Consider hiding these behind more generic names or obfuscating in release builds.

**Risk:** MEDIUM

---

### ✅ SECURE - No Hardcoded Keys Found
**Finding:** No hardcoded API keys detected in source code. Keys are injected via gradle.properties (not in version control).

**Risk:** LOW ✓

---

## 2. Network Security

### ⚠️ MEDIUM - Debug Build Allows All Cleartext Traffic
**Finding:** Debug builds (`app/src/debug/res/xml/network_security_config.xml`) allow cleartext traffic globally:
```xml
<base-config cleartextTrafficPermitted="true" />
```

**Risk:** MEDIUM (acceptable for development but ensure release builds use strict config)

---

### ✅ SECURE - Release Build Restricts Cleartext
**Finding:** Release builds properly restrict cleartext to localhost only:
```xml
<base-config cleartextTrafficPermitted="false">
<domain-config cleartextTrafficPermitted="true">
    <domain>localhost</domain>
    <domain>127.0.0.1</domain>
    <domain>10.0.2.2</domain>
</domain-config>
```

**Risk:** LOW ✓

---

### 🔴 HIGH - Certificate Pinning Not Implemented
**Finding:** Certificate pinning configuration exists but is fully commented out in `network_security_config_release.xml`. The app relies solely on system certificate authorities.

**Location:** `app/src/release/res/xml/network_security_config.xml:23-54`

**Impact:** Vulnerable to CA compromises and man-in-the-middle attacks on public WiFi.

**Recommendation:** Enable certificate pinning for cloud providers (OpenRouter, Firebase, Google APIs) before production.

**Risk:** HIGH

---

### ✅ SECURE - No TrustManager Bypass Found
**Finding:** No custom TrustManager or HostnameVerifier implementations that bypass certificate validation.

**Risk:** LOW ✓

---

## 3. ProGuard/R8 Rules

### ✅ SECURE - Proper JNI/Native Method Preservation
**Finding:** Native methods in `LlamaNative.kt` are properly preserved:
```proguard
-keep class com.shadowai.app.ai.LlamaNative { *; }
-keepclassmembers class com.shadowai.app.ai.LlamaNative {
    native <methods>;
}
```

**Risk:** LOW ✓

---

### ✅ SECURE - Log Stripping in Release
**Finding:** ProGuard removes DEBUG and VERBOSE logs in release builds:
```proguard
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
```

**Risk:** LOW ✓

---

### ✅ SECURE - Serialized Classes Protected
**Finding:** @Keep annotations used for serialized classes in `OllamaApi.kt` and Room entities preserved.

**Risk:** LOW ✓

---

## 4. Storage Security

### ✅ SECURE - Database Encryption (SQLCipher)
**Finding:** Room database uses SQLCipher with AES-256 encryption:
- Passphrase stored in `EncryptedSharedPreferences`
- MasterKey backed by Android Keystore

**Location:** `ShadowDatabase.kt:65-90`, `EncryptedDatabaseHelper.kt`

**Risk:** LOW ✓

---

### ✅ SECURE - Sensitive Data Encrypted (SecureDataStore)
**Finding:** API keys and sensitive prefs use `SecureDataStore` with:
- Google Tink AEAD (AES-256-GCM)
- Hardware-backed Android Keystore
- Associated data for each key

**Location:** `SecureDataStore.kt`

**Risk:** LOW ✓

---

### ⚠️ MEDIUM - UserPreferences Uses Unencrypted DataStore
**Finding:** `UserPreferences.kt` stores user data (email, auth state) in standard DataStore without encryption:
```kotlin
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
```

**Location:** `UserPreferences.kt:20`

**Impact:** User email, login state, and preferences stored in plaintext XML files accessible on rooted devices.

**Recommendation:** Migrate to encrypted DataStore or EncryptedSharedPreferences.

**Risk:** MEDIUM

---

### ⚠️ MEDIUM - External Storage for Models Without Encryption
**Finding:** GGUF model files stored in `getExternalFilesDir()` without explicit encryption:
```kotlin
File(context.getExternalFilesDir(null), DEFAULT_MODEL_DIR)
```

**Location:** `LocalInferenceManager.kt:99`, `LocalLiquidEngine.kt:62-63,115`

**Impact:** Models may be accessible to other apps with storage permissions on older Android versions.

**Note:** The app does check for storage encryption status in `checkStorageEncryption()`, but the model files themselves are not encrypted at rest.

**Risk:** MEDIUM

---

### 🔴 HIGH - requestLegacyExternalStorage Enabled
**Finding:** AndroidManifest.xml contains deprecated flag:
```xml
android:requestLegacyExternalStorage="true"
```

**Location:** `AndroidManifest.xml:37`

**Impact:** This is ignored on Android 11+ but signals intent to use legacy storage APIs. The app also uses `READ_EXTERNAL_STORAGE` (see Permissions section).

**Recommendation:** Remove and fully migrate to Storage Access Framework (SAF) or scoped storage.

**Risk:** HIGH (deprecated API usage)

---

### ✅ SECURE - Backup Disabled
**Finding:** `android:allowBackup="false"` prevents ADB backup of app data.

**Risk:** LOW ✓

---

## 5. Permissions

### 🔴 HIGH - ANSWER_PHONE_CALLS Permission Without Justification
**Finding:** Dangerous permission declared without clear justification:
```xml
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
```

**Location:** `AndroidManifest.xml:14`

**Impact:** This is a highly sensitive permission that allows the app to answer phone calls automatically. Google Play requires extensive justification for this permission.

**Recommendation:** Either:
1. Remove if not essential (appears unused in current agent implementations)
2. Add detailed justification for Play Store review
3. Make optional with runtime permission rationale

**Risk:** HIGH (Play Store rejection risk + user privacy concern)

---

### ⚠️ MEDIUM - READ_EXTERNAL_STORAGE on Android 11+
**Finding:** App declares `READ_EXTERNAL_STORAGE` with targetSdk=36 (Android 12L+):
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
```

**Location:** `AndroidManifest.xml:18-19`

**Impact:** On Android 11+, this permission is deprecated. Should use Storage Access Framework (SAF) with `androidx.documentfile`.

**Note:** The app does use `androidx.documentfile` dependency but still declares the legacy permission.

**Recommendation:** Remove `READ_EXTERNAL_STORAGE` and fully migrate to SAF.

**Risk:** MEDIUM

---

### ✅ SECURE - RECORD_AUDIO Justified
**Finding:** `RECORD_AUDIO` permission is used for voice input (accessibility features). Properly justified.

**Risk:** LOW ✓

---

### ⚠️ MEDIUM - FOREGROUND_SERVICE Without Detailed Justification
**Finding:** `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` declared.

**Location:** `AndroidManifest.xml:8,15`

**Impact:** Requires Play Store declaration of foreground service type and detailed justification.

**Recommendation:** Ensure Play Store declaration explains why background model loading/inference needs foreground service.

**Risk:** MEDIUM

---

## 6. Data Exposure

### ⚠️ MEDIUM - Cloud Provider URLs Logged to Logcat
**Finding:** CloudLlmExecutor logs request details including provider URLs:
```kotlin
Log.d(TAG, "Executing cloud request to $url with model $model...")
```

**Location:** `CloudLlmExecutor.kt:98,156`

**Impact:** Logcat may contain:
- Provider endpoint URLs
- Model identifiers
- Token counts

**Note:** ProGuard strips DEBUG logs in release, but direct APK installs or debug builds may expose this.

**Recommendation:** Use conditional logging or remove sensitive details from logs.

**Risk:** MEDIUM

---

### ⚠️ MEDIUM - ErrorContext Captures Metadata (Potential for PII)
**Finding:** `ErrorContext` class captures:
- taskId, providerId, modelId
- routingSource
- metadata Map<String, Any>

**Location:** `ErrorContext.kt`, `PipelineError.kt`

**Impact:** If task input or error messages are logged without sanitization, PII could leak to:
- Logcat (local)
- Error reports (if crashlytics enabled)
- Analytics (in-memory aggregations)

**Current status:** ErrorAnalytics only aggregates error type counts (safe), but the full error context is logged to Logcat via `DiagnosticsLogger.kt:20-25`.

**Recommendation:** Ensure PII masking is applied before logging errors with context.

**Risk:** MEDIUM

---

### ⚠️ MEDIUM - User Prompts Sent to Cloud Without Explicit Consent
**Finding:** The app sends user input (`task.input`) to cloud LLM providers:
```kotlin
val payload = OpenAiChatRequest(
    messages = listOf(OpenAiMessage(role = "user", content = task.input)),
    ...
)
```

**Location:** `CloudLlmExecutor.kt:85-93`, `CloudLlmExecutor.kt:138-146`

**Impact:** User prompts are sent to OpenAI-compatible endpoints and Gemini without explicit per-prompt consent. While the PII masker exists, it's only applied selectively.

**Note:** `PiiMaskingProcessor.kt` exists and masks emails/phone/SSN/credit cards, but it's not clear if it's applied to all cloud-bound prompts.

**Recommendation:** 
1. Apply PII masking to ALL cloud-bound prompts
2. Add user-visible notice when cloud mode is active
3. Store user consent for cloud processing in preferences

**Risk:** MEDIUM (privacy/compliance concern)

---

### ✅ SECURE - No Firebase Crashlytics Active
**Finding:** Firebase Crashlytics is commented out and disabled. No crash reports sent.

**Location:** `ShadowApplication.kt:20-26`

**Note:** If re-enabled, ensure crash data doesn't include PII.

**Risk:** LOW ✓

---

### ✅ SECURE - PII Masking Implementation Present
**Finding:** `PiiMaskingProcessor.kt` properly masks:
- Email addresses
- Phone numbers  
- SSN patterns
- Credit card numbers
- IP addresses

**Risk:** LOW ✓

---

## Recommendations Summary

### Immediate (Before Production):
1. **Enable certificate pinning** for cloud providers (HIGH)
2. **Remove ANSWER_PHONE_CALLS permission** or add detailed justification (HIGH)
3. **Apply PII masking** to all cloud-bound prompts (MEDIUM)
4. **Encrypt UserPreferences** DataStore (MEDIUM)

### Short-term:
5. Remove `requestLegacyExternalStorage` and migrate to SAF (MEDIUM)
6. Add Play Store foreground service declaration (MEDIUM)
7. Sanitize error logging to prevent PII leakage (MEDIUM)
8. Remove or justify `READ_EXTERNAL_STORAGE` permission (MEDIUM)

### Long-term:
9. Encrypt model files at rest or use internal storage only
10. Add user consent UI for cloud processing mode
11. Implement certificate rotation strategy for pinning

---

## Files Reviewed

**Configuration:**
- AndroidManifest.xml
- app/build.gradle.kts
- proguard-rules.pro
- network_security_config.xml (debug/main/release)

**Security-Related Code:**
- SecureDataStore.kt
- SecurityManager.kt
- EncryptedDatabaseHelper.kt
- ShadowDatabase.kt
- UserPreferences.kt
- ProviderSecretRepository.kt

**Network/Cloud:**
- CloudLlmExecutor.kt
- AuthInterceptor.kt
- AppModule.kt (OkHttp client)

**Privacy/Data:**
- PiiMaskingProcessor.kt
- PromptManager.kt
- ErrorContext.kt
- DiagnosticsModule.kt

---

## Appendix: ProGuard Rules Analysis

The ProGuard rules (`app/proguard-rules.pro`) are **well-configured**:

**Strengths:**
- Native JNI methods preserved
- Retrofit/OkHttp reflection targets kept
- Room entities and DAOs preserved
- Security-crypto classes kept
- DEBUG/VERBOSE logs stripped in release
- Firebase attribution preserved for crash diagnostics

**No issues identified.**

---

*End of Security Audit Report*
