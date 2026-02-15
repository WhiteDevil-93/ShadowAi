# ADR-004: PII Masking Strategy

## Status
Accepted - Phase 1 Security

## Context
ShadowAi processes user inputs that may contain sensitive personal information (PII). As a privacy-first application, PII must be masked before sending to cloud providers and unmasked when displaying responses to users.

## Decision
Implement bidirectional PII masking using the `PiiMaskingProcessor`:

1. **Masking Pattern**: Token-based replacement with reversible mappings
   - Email: `user@example.com` → `[EMAIL_1]`
   - Phone: `+1-555-123-4567` → `[PHONE_1]`
   - SSN: `123-45-6789` → `[SSN_1]`

2. **Processing Points**:
   - **Before Cloud Send**: Mask PII in user input
   - **After Cloud Receive**: Unmask PII in AI response
   - **Local Storage**: Store unmasked (encrypted at rest)

3. **Audit Trail**: Log masking operations without exposing actual PII

## Implementation

```kotlin
class PiiMaskingProcessor @Inject constructor() {
    
    fun maskPii(input: String): String {
        // Pattern matching for PII types
        // Return masked string with token references
    }
    
    fun unmaskPii(masked: String): String {
        // Reverse token substitution
        // Return original with PII restored
    }
    
    fun containsPii(input: String): Boolean {
        // Quick check if masking is needed
    }
}
```

## Consequences

### Positive
- **Privacy**: PII never sent to third-party providers
- **Compliance**: GDPR/CCPA requirements met for data minimization
- **Auditability**: Token references allow debugging without exposing data

### Negative
- **Token Overhead**: Tokens increase message size slightly
- **Masking Errors**: Complex PII patterns may be missed
- **Performance**: Regex scanning overhead on every request/response

## Security Considerations
- Token mappings stored only in-memory (never persisted)
- Tokens use cryptographically random identifiers
- Masking occurs before any network transmission

## Files
- [`app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt`](../../app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt)
- [`app/src/test/java/com/shadowai/app/security/PiiMaskingProcessorTest.kt`](../../app/src/test/java/com/shadowai/app/security/PiiMaskingProcessorTest.kt)

## Related Decisions
- ADR-002: Certificate Pinning (encryption in transit)
- ADR-005: TEE Key Storage (encryption at rest)
