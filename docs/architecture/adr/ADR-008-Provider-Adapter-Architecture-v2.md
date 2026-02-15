# ADR-008: Provider Adapter Architecture v2

## Status
Accepted - Phase 2 Implementation In Progress

## Context

ShadowAi requires support for multiple AI providers:
- **Local**: llama.cpp (GGUF models)
- **Cloud Text**: OpenAI, Anthropic, Gemini, Mistral, Groq, etc.
- **Cloud Image**: Novita, PixAI, OpenAI DALL-E
- **Future**: Additional providers (Cohere, AI21, custom endpoints)

The original architecture had tight coupling between provider implementations and business logic. We needed:
1. Unified interface for all providers
2. Runtime provider selection/swapping
3. Capability-based routing
4. Consistent error handling
5. Easy addition of new providers

## Decision

We will implement a **Provider Adapter pattern** with capability-driven routing.

### Core Concepts

```
┌─────────────────────────────────────────────────────────────┐
│                    SupervisorAgent                          │
│         (High-level orchestration & routing)                │
└─────────────────────┬───────────────────────────────────────┘
                      │ Transform + Artifact
┌─────────────────────▼───────────────────────────────────────┐
│                 ProviderExecutor                            │
│         (Unified interface - suspend function)              │
└──────┬──────────────┬──────────────┬──────────────┬─────────┘
       │              │              │              │
┌──────▼──────┐ ┌────▼─────┐ ┌──────▼──────┐ ┌────▼─────┐
│  LocalLlama │ │  OpenAI  │ │   Novita    │ │  Custom  │
│   Adapter   │ │ Adapter  │ │   Adapter   │ │ Adapters │
└─────────────┘ └──────────┘ └─────────────┘ └──────────┘
```

### Core Interface

```kotlin
interface ProviderExecutor {
    /**
     * Execute a transform on the provider.
     * 
     * @param transform The operation to perform (GenerateText, Embed, etc.)
     * @param input Input artifact
     * @param parameters Provider-specific parameters
     * @return Result containing output artifact or error
     */
    suspend fun execute(
        transform: Transform,
        input: Artifact,
        parameters: Map<String, Any>
    ): Result<Artifact>
    
    /**
     * Capabilities this provider supports.
     */
    val capabilities: Set<Capability>
    
    /**
     * Current health status.
     */
    val healthStatus: HealthStatus
    
    /**
     * Provider identifier.
     */
    val providerId: ProviderId
}
```

### Capability System

```kotlin
sealed class Capability {
    object TextGeneration : Capability()
    object ImageGeneration : Capability()
    object TextEmbedding : Capability()
    object FunctionCalling : Capability()
    object Streaming : Capability()
    data class MaxContextTokens(val tokens: Int) : Capability()
    data class SupportedLanguages(val languages: List<String>) : Capability()
}
```

### Routing Policies

```kotlin
enum class RoutingPolicy {
    /** Use specified provider or fail */
    EXPLICIT,
    
    /** Prefer local, fallback to cloud */
    LOCAL_FIRST,
    
    /** Prefer fastest available */
    SPEED_FIRST,
    
    /** Prefer cheapest (local > free tier > paid) */
    COST_FIRST,
    
    /** Choose based on capability requirements */
    CAPABILITY_BASED,
    
    /** Automatic selection based on task complexity */
    AUTO
}
```

### Adapter Implementations

| Adapter | Provider | Status | Capabilities |
|---------|----------|--------|--------------|
| LocalLlamaAdapter | llama.cpp | ✅ Implemented | TextGeneration, Streaming |
| OpenAICompatibleAdapter | OpenAI, Mistral, Groq | 🚧 Scheduled | TextGen, Images, Functions |
| AnthropicAdapter | Claude API | 🚧 Scheduled | TextGen, Functions |
| GeminiAdapter | Google AI | 🚧 Scheduled | TextGen, Images, Embeddings |
| NovitaAdapter | Novita AI | 🚧 Scheduled | ImageGeneration |
| PixAiAdapter | PixAI | 🚧 Scheduled | ImageGeneration, Anime |

### Factory Pattern

```kotlin
@Singleton
class ProviderAdapterFactory @Inject constructor(
    private val localAdapter: LocalLlamaAdapter,
    private val cloudAdapters: Set<@JvmSuppressWildcards CloudProviderAdapter>
) {
    fun createExecutor(
        providerId: ProviderId,
        requiredCapabilities: Set<Capability>
    ): Result<ProviderExecutor> {
        return when (providerId) {
            ProviderId.LOCAL -> Success(localAdapter)
            else -> cloudAdapters
                .find { it.providerId == providerId }
                ?.let { Success(it) }
                ?: Failure(ProviderNotFoundException(providerId))
        }
    }
}
```

### Error Normalization

All adapter errors mapped to standard taxonomy:

```kotlin
sealed class AgentError {
    data class Transport(val message: String, val retryable: Boolean) : AgentError()
    data class Semantic(val message: String, val invalidInput: String?) : AgentError()
    data class Violation(val message: String, val policy: String) : AgentError()
    data class Exhaustion(val message: String, val limit: String) : AgentError()
    data class Security(val message: String, val risk: RiskLevel) : AgentError()
}
```

## Consequences

### Positive
- **Plug-and-play providers**: New provider = new adapter class
- **Testability**: Adapters mockable for testing
- **Graceful degradation**: Capability-based fallback chains
- **A/B testing**: Easy to swap providers for experimentation
- **Consistent API**: Same interface regardless of provider complexity

### Negative
- **Abstraction overhead**: Minimal performance cost per request
- **Least common denominator**: Advanced provider features may be hidden
- **Maintenance burden**: N adapters require N maintainers
- **Debugging complexity**: Extra layer in stack traces

## Implementation Status

### Completed ✅
- `ProviderExecutor` interface defined
- `LocalLlamaAdapter` fully implemented
- `Capability` sealed class hierarchy
- `RoutingPolicy` enum
- Error taxonomy (ADR-003)

### In Progress 🚧
- Cloud provider adapter implementations (Phase 2)
- Adapter factory with dependency injection
- Capability-based routing algorithm

### Planned 📋
- Plugin architecture for third-party adapters
- Adapter marketplace/discovery
- Dynamic adapter loading

## Integration Points

### With Circuit Breaker
```kotlin
class CircuitBreakerAdapter(
    private val inner: ProviderExecutor,
    private val breaker: CircuitBreaker
) : ProviderExecutor {
    override suspend fun execute(...): Result<Artifact> {
        return breaker.execute { inner.execute(...) }
    }
}
```

### With PII Masking
```kotlin
class PiiMaskingAdapter(
    private val inner: ProviderExecutor,
    private val piiProcessor: PiiMaskingProcessor
) : ProviderExecutor {
    override suspend fun execute(...): Result<Artifact> {
        val maskedInput = piiProcessor.mask(input)
        val result = inner.execute(transform, maskedInput, params)
        return result.map { piiProcessor.unmask(it) }
    }
}
```

## Files

- `provider-adapters/src/main/kotlin/.../ProviderExecutor.kt` - Core interface
- `provider-adapters/src/main/kotlin/.../LocalLlamaAdapter.kt` - Local implementation
- `app/src/main/java/.../providers/ProviderRepository.kt` - Provider management
- `app/src/main/java/.../providers/ProviderPlugin.kt` - Plugin interface
- `core-contracts/.../error/AgentErrors.kt` - Error taxonomy

## Migration Path

Existing code migration:

| Old Component | New Component |
|---------------|---------------|
| `LiquidProvider` | `LocalLlamaAdapter` |
| `OpenAiApi` | `OpenAICompatibleAdapter` |
| `NovitaService` | `NovitaAdapter` |
| `PixAiService` | `PixAiAdapter` |

Migration steps:
1. Implement adapter wrapping old code
2. Add to factory
3. Deprecate old component
4. Remove after validation

## Related Decisions
- ADR-001: Original Provider Adapter Architecture (superseded by this v2)
- ADR-003: Error Taxonomy
- ADR-004: PII Masking Strategy
- ADR-006: Security Implementation Decisions
- ADR-007: Local Inference Engine Choice (local provider)

## References
- [Adapter Pattern (GoF)](https://en.wikipedia.org/wiki/Adapter_pattern)
- [Strategy Pattern for Routing](https://en.wikipedia.org/wiki/Strategy_pattern)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
