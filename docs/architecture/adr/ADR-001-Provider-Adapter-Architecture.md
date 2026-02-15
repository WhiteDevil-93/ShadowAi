# ADR-001: Provider Adapter Architecture

## Status
Accepted - Phase 2 Implementation In Progress

## Context
The ShadowAi application needs to support multiple AI providers (OpenAI, Anthropic, Gemini, local models via Llama.cpp, image generation via Novita/PixAI) with a unified interface. The original architecture had tight coupling between provider-specific implementations and the rest of the application.

## Decision
We will implement a Provider Adapter pattern that normalizes all AI provider interactions through a common interface (ProviderExecutor).

## Consequences

### Positive
- **Unified Interface**: All providers implement the same contract, enabling seamless swapping
- **Testability**: Providers can be mocked for testing
- **Capability Discovery**: Runtime capability inspection per provider
- **Fallback Chains**: Automatic provider fallback on failure
- **PII Masking**: Centralized PII handling at adapter boundary

### Negative
- **Maintenance Overhead**: Each new provider requires adapter implementation
- **Least Common Denominator**: Advanced provider features may be abstracted away
- **Performance**: Minimal abstraction overhead from artifact conversion

## Implementation Details

### Core Interface (Phase 2)
```kotlin
interface ProviderExecutor {
    suspend fun execute(
        transform: Transform,
        input: Artifact,
        parameters: Map<String, Any>
    ): Result<Artifact>
    
    val capabilities: Set<Capability>
    val healthStatus: HealthStatus
}
```

### Artifact Normalization (Phase 3)
All I/O normalized through Artifact types:
- `Artifact.Text` - Text content with optional mime-type
- `Artifact.Image` - Image data with format metadata
- `Artifact.Embedding` - Vector representations

### Adapter Factory (Phase 2.5)
Factory pattern for adapter instantiation based on ProviderId and capability requirements.

## Related Decisions
- ADR-002: Artifact System for I/O Normalization
- ADR-003: Pipeline Planner for Multi-Model Workflows

## References
- [`app/src/main/java/com/shadowai/app/providers/AdapterBridge.kt`](../../app/src/main/java/com/shadowai/app/providers/AdapterBridge.kt)
- [`plans/todo.md`](../../plans/todo.md) - Phase 2 implementation checklist
