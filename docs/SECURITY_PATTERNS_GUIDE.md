# ShadowAi Security Patterns Guide

This guide explains the security patterns and best practices used in ShadowAi, designed specifically for junior developers to understand and implement secure code.

## Table of Contents

1. [Security Architecture Overview](#security-architecture-overview)
2. [PII Masking Pattern](#pii-masking-pattern)
3. [Secure Memory Management](#secure-memory-management)
4. [Circuit Breaker Pattern](#circuit-breaker-pattern)
5. [Secure Key Management](#secure-key-management)
6. [Prompt Injection Defense](#prompt-injection-defense)
7. [Common Security Mistakes](#common-security-mistakes)
8. [Security Testing](#security-testing)

## Security Architecture Overview

ShadowAi follows a **Zero-Trust Security Model** where every component is treated as a potential compromise point. The security architecture consists of multiple layers:

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │   PII Masking   │  │ Prompt Injection  │               │
│  │   (Input/Output)│  │     Defense       │               │
│  └─────────────────┘  └─────────────────┘               │
├─────────────────────────────────────────────────────────┤
│                    Service Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │  Circuit        │  │   Rate Limiter    │               │
│  │  Breaker        │  │                   │               │
│  └─────────────────┘  └─────────────────┘               │
├─────────────────────────────────────────────────────────┤
│                    Data Layer                           │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │ Secure Memory   │  │ Encrypted DB    │               │
│  │   Management    │  │                 │               │
│  └─────────────────┘  └─────────────────┘               │
└─────────────────────────────────────────────────────────┘
```

## PII Masking Pattern

### What is PII?

Personally Identifiable Information (PII) includes:
- Email addresses
- Phone numbers
- Credit card numbers
- Social Security Numbers
- IP addresses
- API keys and secrets

### How PII Masking Works

The `PiiMaskingProcessor` automatically detects and masks PII before sending data to cloud providers:

```kotlin
// Example usage in TaskExecutor
val processedTask = if (!isLocalProvider) {
    piiMaskingProcessor.maskPii(task)
} else {
    task
}
```

### PII Detection Patterns

```kotlin
// Email detection using Android Patterns
val emailMatches = findPatternMatches(Patterns.EMAIL_ADDRESS, text)

// Fallback pattern for testing
private val FALLBACK_EMAIL_PATTERN = Regex(
    """(?i)\b[\p{L}\p{N}._%+\-]+@[\p{L}\p{N}\-]+(?:\.[\p{L}\p{N}\-]+)+\b"""
)

// Credit card validation with Luhn algorithm
private fun isValidLuhn(digits: String): Boolean {
    var sum = 0
    var shouldDouble = false
    for (i in digits.length - 1 downTo 0) {
        var digit = digits[i].digitToIntOrNull() ?: return false
        if (shouldDouble) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        shouldDouble = !shouldDouble
    }
    return sum % 10 == 0
}
```

### Masking Examples

```kotlin
// Before masking
val input = "Contact me at john@example.com or call 555-123-4567"

// After masking
val masked = piiMaskingProcessor.maskPii(input)
// Result: "Contact me at [EMAIL_REDACTED] or call [PHONE_REDACTED]"
```

### When to Use PII Masking

- **Always** mask PII for cloud providers
- **Optional** for local inference (configurable)
- **Required** before logging sensitive data
- **Automatic** in TaskExecutor for external requests

### Junior Developer Tips

1. **Never log raw user input** that might contain PII
2. **Always use the processor** instead of custom regex
3. **Test with real PII patterns** to ensure detection works
4. **Check configuration** to see if masking is enabled

## Secure Memory Management

### The SecretBytes Pattern

The `SecretBytes` pattern prevents sensitive data from persisting in immutable strings:

```kotlin
// CORRECT: Using SecretBytes
val secret = SecretBytes(32)
secret.fill { java.security.SecureRandom().nextBytes(this) }

secret.withSecretBytes({ decryptKey(encryptedKey) }) { key ->
    cipher.doFinal(key)
}  // Automatically zeroed after use

// INCORRECT: Using String
val apiKey = "sk-abc123..."  // Stays in memory!
```

### Why SecretBytes?

1. **Prevents memory leaks**: Sensitive data is zeroed immediately
2. **Thread-safe**: Safe concurrent access
3. **Automatic cleanup**: No manual disposal needed
4. **Integration ready**: Works with Cipher and SecretKey

### Common Usage Patterns

```kotlin
// 1. Creating secure keys
val secret = SecretBytes(32)
secret.fill { java.security.SecureRandom().nextBytes(this) }

// 2. Using in cryptographic operations
secret.withSecretBytes({ key -> 
    cipher.init(Cipher.ENCRYPT_MODE, key)
    cipher.doFinal(data)
}) { encryptedData ->
    // Use encryptedData
}

// 3. Manual cleanup (if needed)
secret.dispose()
```

### Junior Developer Tips

1. **Never store API keys as Strings**
2. **Always use SecretBytes for sensitive data**
3. **Use the withSecretBytes pattern** for operations
4. **Test memory cleanup** in unit tests

## Circuit Breaker Pattern

### What is a Circuit Breaker?

A circuit breaker prevents cascading failures by temporarily stopping requests to a failing service:

```
States:
CLOSED → Normal operation
OPEN → Service is failing, block requests
HALF_OPEN → Testing if service recovered
```

### Circuit Breaker Implementation

```kotlin
class CircuitBreaker(
    val failureThreshold: Int = 5,    // Open after 5 failures
    val successThreshold: Int = 2,     // Close after 2 successes
    val timeoutMs: Long = 30000        // Wait 30 seconds before retry
) {
    suspend fun <T> execute(block: suspend () -> T): T {
        return when (currentState) {
            State.CLOSED -> executeClosed(block)
            State.OPEN -> executeOpen(block)
            State.HALF_OPEN -> executeHalfOpen(block)
        }
    }
}
```

### Usage Example

```kotlin
// Protect external API calls
val result = circuitBreaker.execute {
    apiClient.generate(prompt)
}
```

### Circuit Breaker States

1. **CLOSED**: Requests pass through normally
2. **OPEN**: Requests fail fast with CircuitOpenException
3. **HALF_OPEN**: Limited requests allowed to test recovery

### Junior Developer Tips

1. **Use for all external dependencies**
2. **Configure appropriate thresholds** for your use case
3. **Monitor circuit state** in logs
4. **Handle CircuitOpenException** gracefully

## Secure Key Management

### SecurityManager Class

The `SecurityManager` handles encryption and key management:

```kotlin
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun generateHardwareBackedKey(alias: String): SecretKey {
        // Uses Android Keystore for hardware-backed keys
    }
    
    fun encrypt(data: String, key: SecretKey): String {
        // AES-256-GCM encryption
    }
    
    fun decrypt(data: String, key: SecretKey): String {
        // AES-256-GCM decryption
    }
}
```

### Key Storage Best Practices

1. **Use Android Keystore** for hardware-backed keys
2. **Never hardcode keys** in source code
3. **Use biometric authentication** for key access
4. **Implement key rotation** when needed

### Junior Developer Tips

1. **Always use SecurityManager** for encryption
2. **Never store keys in SharedPreferences** as plain text
3. **Use hardware-backed keys** when available
4. **Implement proper key lifecycle** management

## Prompt Injection Defense

### What is Prompt Injection?

Prompt injection is when users try to manipulate AI responses by including malicious instructions in their input.

### Defense Implementation

```kotlin
class PromptInjectionDefense @Inject constructor() {
    fun verifyPrompt(prompt: String): PromptVerificationResult {
        val score = calculateRiskScore(prompt)
        return if (score > THRESHOLD) {
            PromptVerificationResult.unsafe("High risk detected")
        } else {
            PromptVerificationResult.safe(prompt)
        }
    }
}
```

### Risk Scoring Factors

1. **Keyword density**: Malicious keywords in input
2. **Context analysis**: Suspicious patterns
3. **Heuristic scoring**: Multiple risk factors

### Usage

```kotlin
val scanResult = promptInjectionDefense.scan(input)
if (!scanResult.isSafe) {
    return AgentResult.Failure(
        AgentError(ErrorCategory.SECURITY, scanResult.reason ?: "Prompt injection detected")
    )
}
```

### Junior Developer Tips

1. **Always scan user input** before processing
2. **Use configurable thresholds** for different risk levels
3. **Log security events** for monitoring
4. **Test with known attack patterns**

## Common Security Mistakes

### 1. Logging Sensitive Data

```kotlin
// WRONG
Log.d("API", "API Key: $apiKey")

// RIGHT
Log.d("API", "API Key: ${apiKey.take(4)}...")
```

### 2. Storing Keys in Strings

```kotlin
// WRONG
val apiKey = "sk-abc123..."

// RIGHT
val secret = discoveredApiKey("sk-abc123...")
```

### 3. Skipping Input Validation

```kotlin
// WRONG
val result = apiClient.call(userInput)

// RIGHT
val safeInput = piiMaskingProcessor.maskPii(userInput)
val result = apiClient.call(safeInput)
```

### 4. Ignoring Circuit Breaker States

```kotlin
// WRONG
try {
    apiClient.call()
} catch (e: Exception) {
    // Silent failure
}

// RIGHT
val result = circuitBreaker.execute {
    apiClient.call()
}
```

### 5. Hardcoding Configuration

```kotlin
// WRONG
val timeout = 30000  // Magic number

// RIGHT
@Value("\${app.circuit-breaker.timeout:30000}")
val timeout: Long
```

## Security Testing

### Unit Tests for Security

```kotlin
@Test
fun `PII masking should detect and mask email addresses`() = runTest {
    val processor = PiiMaskingProcessor()
    val input = "Contact me at john@example.com"
    val result = processor.maskPii(input)
    
    assertEquals("Contact me at [EMAIL_REDACTED]", result)
}

@Test
fun `Circuit breaker should open after threshold`() = runTest {
    val breaker = CircuitBreaker(failureThreshold = 2)
    
    // Trigger failures
    repeat(2) {
        try {
            breaker.execute { throw RuntimeException("test") }
        } catch (_: RuntimeException) {}
    }
    
    assertEquals(CircuitBreaker.State.OPEN, breaker.currentState)
}
```

### Security Test Checklist

- [ ] PII detection works for all supported types
- [ ] Circuit breaker opens/closes correctly
- [ ] SecretBytes are properly zeroed
- [ ] Prompt injection is detected
- [ ] Encryption/decryption works correctly
- [ ] Key management is secure

### Integration Tests

```kotlin
@Test
fun `TaskExecutor should mask PII for cloud providers`() = runTest {
    val executor = TaskExecutor(/* dependencies */)
    val task = Task(
        input = "My email is john@example.com",
        type = TaskType.CONVERSATION
    )
    
    val result = executor.execute(task, RoutingPolicy.CLOUD)
    
    // Verify PII was masked in the process
    assertFalse(result.task.input.contains("john@example.com"))
}
```

## Security Code Review Checklist

### For Junior Developers

- [ ] No hardcoded secrets or API keys
- [ ] All user input is validated and sanitized
- [ ] PII masking is applied where needed
- [ ] Circuit breakers protect external calls
- [ ] Sensitive data uses SecretBytes pattern
- [ ] Proper error handling without information leakage
- [ ] Security tests are included

### For Senior Developers

- [ ] Security patterns are consistently applied
- [ ] Attack vectors are considered
- [ ] Performance impact of security measures is acceptable
- [ ] Security documentation is up to date
- [ ] Dependencies are secure and up to date

## Emergency Security Procedures

### If You Suspect a Security Issue

1. **Stop development** on the affected area
2. **Document the issue** with details
3. **Notify senior developers** immediately
4. **Do not commit** potentially vulnerable code
5. **Follow incident response procedures**

### Common Security Alerts

- **PII in logs**: Immediately remove and rotate any exposed data
- **Circuit breaker stuck open**: Check for service issues
- **Failed security tests**: Investigate and fix before proceeding
- **Dependency vulnerabilities**: Update to secure versions

## Additional Resources

### Android Security
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)

### Kotlin Security
- [Kotlin Secure Coding Guidelines](https://kotlinlang.org/docs/idioms.html#null-safety)
- [Coroutines Security Patterns](https://kotlinlang.org/docs/coroutines-guide.html)

### General Security
- [OWASP Mobile Security Testing Guide](https://owasp.org/www-project-mobile-security-testing-guide/)
- [Secure Software Development Lifecycle](https://cwe.mitre.org/)

---

**Remember**: Security is everyone's responsibility. When in doubt, ask for help and err on the side of caution.