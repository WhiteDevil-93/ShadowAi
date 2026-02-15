# ADR-005: Rate Limiting Strategy

## Status
Accepted

## Context
ShadowAi communicates with multiple AI providers that have rate limits. To provide a good user experience and avoid provider penalties, we implement client-side rate limiting per endpoint.

## Decision
Implement a token-bucket rate limiter with per-endpoint configuration:

```kotlin
@Singleton
class RateLimiter @Inject constructor() {
    
    data class EndpointConfig(
        val requestsPerMinute: Int = 60,
        val maxRetries: Int = 3,
        val baseDelayMs: Long = 1000L
    )
    
    suspend fun <T> execute(endpoint: String, block: suspend () -> T): T
    suspend fun <T> executeOnce(endpoint: String, block: suspend () -> T): T
}
```

## Configuration

| Provider | RPM | Burst | Base Delay |
|----------|-----|-------|------------|
| OpenAI | 60 | 3 | 1000ms |
| Anthropic | 50 | 3 | 1000ms |
| Gemini | 60 | 3 | 1000ms |
| OpenRouter | 30 | 2 | 1000ms |
| Local Models | Unlimited | 0 | 0ms |

## Backoff Strategy
Exponential backoff with jitter:
```kotlin
private fun calculateBackoff(retryCount: Int, baseDelayMs: Long): Long {
    val safeBase = baseDelayMs.coerceAtLeast(1L)
    val exponentialDelay = safeBase * (1L shl (retryCount - 1))
    val jitter = Random.nextLong(0L, (exponentialDelay * 3 / 10).coerceAtLeast(1L))
    return min(exponentialDelay + jitter, MAX_DELAY_MS)
}
```

## Consequences

### Positive
- **Provider Compliance**: Respects API rate limits
- **User Experience**: Predictable behavior under load
- **Automatic Retry**: Handles transient rate limit errors

### Negative
- **Latency**: Waiting for token refill adds latency
- **Configuration**: Must update RPM when provider limits change

## Integration Points
- **Provider Adapters**: Call `rateLimiter.execute(endpoint)` before API calls
- **Circuit Breaker**: Rate limiting works alongside circuit breaker pattern
- **UI Feedback**: Show rate limit status in debug UI

## Files
- [`app/src/main/java/com/shadowai/app/execution/RateLimiter.kt`](../../app/src/main/java/com/shadowai/app/execution/RateLimiter.kt)

## Related Decisions
- ADR-001: Provider Adapter Architecture
- ADR-006: Circuit Breaker Pattern
