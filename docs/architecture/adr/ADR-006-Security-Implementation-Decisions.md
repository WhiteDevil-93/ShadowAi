# ADR-006: Security Implementation Decisions

## Status
Accepted - Implemented

## Context

ShadowAi processes sensitive user data including:
- API keys for cloud AI providers
- User conversation history
- Potentially PII in user inputs
- Biometric authentication data

We needed a comprehensive security strategy addressing:
1. Data in transit (network communication)
2. Data at rest (local storage)
3. Data in memory (runtime protection)
4. Authentication and authorization

## Decision

We will implement a **zero-trust, defense-in-depth security model** with five layers:

### Layer 1: Network Security (TLS + Certificate Pinning)

All cloud provider APIs use HTTPS with certificate pinning to prevent MITM attacks.

```kotlin
// network_security_config.xml
<domain-config>
    <domain includeSubdomains="true">api.openai.com</domain>
    <pin-set>
        <pin digest="SHA-256">y5npFVdBuoqCSOdQa42qiUSPqwMpoei7NK0rQWGUaSU=</pin>
    </pin-set>
</domain-config>
```

**Providers pinned:** OpenAI, Anthropic, Gemini, OpenRouter, DeepSeek, Mistral, Groq, xAI

### Layer 2: PII Protection (Bidirectional Masking)

Before any data leaves the device:

```kotlin
class PiiMaskingProcessor @Inject constructor() {
    fun maskPii(input: String): MaskedResult {
        // Email → [EMAIL_1]
        // Phone → [PHONE_1]
        // SSN → [SSN_1]
        // Returns masked string + token mapping for unmasking
    }
}
```

**Processing flow:**
```
User Input → Mask PII → Send to Cloud → Receive Response → Unmask PII → Display
```

### Layer 3: Secure Memory (SecretBytes Pattern)

API keys and sensitive data use mutable byte arrays that are zeroed after use:

```kotlin
class SecretBytes(private val size: Int) {
    fun withSecretBytes(block: (ByteArray) -> Unit) {
        val bytes = ByteArray(size)
        try {
            block(bytes)
        } finally {
            bytes.fill(0) // Cryptographic zeroing
        }
    }
}
```

**Why not String?** Java/Kotlin strings are immutable and may persist in memory until GC.

### Layer 4: Encryption at Rest

- API keys: AES-256-GCM via EncryptedSharedPreferences
- Biometric keys: TEE/StrongBox-backed when available
- Database: SQLCipher with WAL mode enabled

```kotlin
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setUserAuthenticationRequired(true) // Biometric binding
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context, "secure_prefs", masterKey
    )
}
```

### Layer 5: Prompt Injection Defense

Input validation before LLM processing:

```kotlin
class PromptInjectionDefense @Inject constructor() {
    fun verifyPrompt(input: String): VerificationResult {
        val riskScore = calculateRiskScore(input)
        return if (riskScore > THRESHOLD) {
            VerificationResult.Blocked(reason = "Potential prompt injection")
        } else {
            VerificationResult.Safe
        }
    }
}
```

## Consequences

### Positive
- **Defense in depth**: Multiple security layers; breach of one doesn't compromise all
- **Privacy-first**: PII never transmitted to third parties
- **Compliance ready**: GDPR/CCPA data minimization requirements met
- **Auditability**: All security operations logged without exposing sensitive data
- **Hardware-backed**: TEE/StrongBox usage on supported devices

### Negative
- **Performance overhead**: PII scanning, encryption/decryption add latency
- **Certificate maintenance**: Pins must be updated when provider certificates rotate
- **User friction**: Biometric auth may be required for sensitive operations
- **Memory complexity**: SecretBytes pattern requires careful usage to avoid leaks

## Implementation Details

### Files
- `core-contracts/src/main/kotlin/com/shadowai/core/security/PiiMaskingProcessor.kt`
- `core-contracts/src/main/kotlin/com/shadowai/core/security/SecretBytes.kt`
- `app/src/main/java/com/shadowai/app/security/SecurityManager.kt`
- `app/src/main/java/com/shadowai/app/security/BiometricKeyManager.kt`
- `app/src/main/java/com/shadowai/app/security/PromptInjectionDefense.kt`
- `app/src/main/res/xml/network_security_config.xml`

### Testing
- `PiiMaskingProcessorTest.kt`: 35+ test patterns
- Certificate pinning validation tests
- SecretBytes lifecycle tests

## Related Decisions
- ADR-002: Certificate Pinning Policy
- ADR-004: PII Masking Strategy
- ADR-005: Rate Limiting Strategy ( complements security with abuse prevention)

## References
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [NIST SP 800-57: Key Management](https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
