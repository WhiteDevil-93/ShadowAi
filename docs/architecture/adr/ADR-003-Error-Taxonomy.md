# ADR-003: Error Taxonomy and Categorization

## Status
Accepted - Phase 0.3 Complete

## Context
As ShadowAi integrates more providers and agentic capabilities, error handling became inconsistent with generic `Exception` catches. We need a unified error taxonomy that callers can react to appropriately.

## Decision
Implement a standardized error taxonomy with the `AgentError` class and `ErrorCategory` enum:

```kotlin
enum class ErrorCategory {
    TRANSPORT,    // Network timeouts, DNS issues, API unreachable
    SEMANTIC,     // Malformed JSON, schema violations, invalid arguments
    LOGIC,        // Contradictory intents, hallucinated capabilities
    VIOLATION,    // Policy violations, permission denials, safety filters
    EXECUTION,    // Execution failures not matching other categories
    EXHAUSTION,   // Token limits, retry budgets, timeout budgets exceeded
    SECURITY,     // Prompt injection, PII leakage attempts
    UNKNOWN       // Unclassified errors
}

data class AgentError(
    val category: ErrorCategory,
    val message: String,
    val code: String? = null,
    val requiredPermission: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

## Mapping Guidelines

| Exception Type | Category | Rationale |
|--------------|----------|-----------|
| `SocketTimeoutException` | TRANSPORT | Network layer timeout |
| `JSONException` | SEMANTIC | Malformed response payload |
| `SecurityException` | VIOLATION | Android permission denial |
| `OutOfMemoryError` | EXHAUSTION | Resource exhaustion |
| `PromptInjectionException` | SECURITY | Security policy violation |
| `ProviderQuotaException` | EXHAUSTION | Provider rate limit |

## Consequences

### Positive
- **Actionable Errors**: UI can show appropriate recovery options per category
- **Metrics**: Error categorization enables targeted reliability improvements
- **Safety**: SECURITY category triggers additional logging/alerts

### Negative
- **Extra Boilerplate**: Must map exceptions to categories at boundaries
- **Ambiguity**: Some errors span multiple categories (requires judgment)

## Usage Pattern
```kotlin
// In ShadowAgent.processInput()
try {
    executor.execute(task, policy)
} catch (e: NetworkException) {
    return AgentResult.Failure(AgentError(ErrorCategory.TRANSPORT, e.message ?: "Network error"))
} catch (e: ResourceException) {
    return AgentResult.Failure(AgentError(ErrorCategory.EXHAUSTION, e.message ?: "Resource exhausted"))
} catch (e: Exception) {
    // Map generic to EXHAUSTION if message contains 'budget', else UNKNOWN
    val cat = if (e.message?.contains("budget") == true) ErrorCategory.EXHAUSTION else ErrorCategory.UNKNOWN
    return AgentResult.Failure(AgentError(cat, e.message ?: "Execution Error"))
}
```

## Related Decisions
- ADR-004: PII Masking Strategy
- ADR-005: Agentic Loop Architecture

## Files
- [`app/src/main/java/com/shadowai/app/agent/AgentErrors.kt`](../../app/src/main/java/com/shadowai/app/agent/AgentErrors.kt)
