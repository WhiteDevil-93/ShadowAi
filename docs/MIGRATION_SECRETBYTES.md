# SecretBytes Migration Guide for Provider Adapters

## Overview
ProviderAdapterConfig now supports secure API key storage via `SecretBytes` in addition to the legacy plain-text `apiKey` field. Adapters should migrate to use `config.getApiKey()` which securely retrieves the key from either source.

## Migration Pattern

### 1. Update validateConfig()
```kotlin
// Before
override suspend fun validateConfig(): Boolean {
    return config.baseUrl.isNotBlank() &&
           config.apiKey?.isNotBlank() == true
}

// After
override suspend fun validateConfig(): Boolean {
    return config.baseUrl.isNotBlank() &&
           !config.getApiKey().isNullOrBlank()
}
```

### 2. Update API key usage in requests
```kotlin
// Before
val request = Request.Builder()
    .url("${config.baseUrl}/endpoint")
    .header("Authorization", "Bearer ${config.apiKey}")
    // ...

// After
val apiKey = config.getApiKey()
    ?: return@withTimeoutOrNull Result.failure(ProviderException.AuthenticationError())

val request = Request.Builder()
    .url("${config.baseUrl}/endpoint")
    .header("Authorization", "Bearer $apiKey")
    // ...
```

## Migration Status

| Adapter | Status | Notes |
|---------|--------|-------|
| OpenAICompatibleAdapter | ✅ Complete | Reference implementation |
| AnthropicAdapter | ✅ Complete | All API key usages migrated |
| GeminiAdapter | ✅ Complete | All API key usages migrated |
| NovitaAdapter | ✅ Complete | All API key usages migrated |
| PixAIAdapter | ✅ Complete | All API key usages migrated |
| NovelAIAdapter | ✅ Complete | All API key usages migrated |
| FluxAdapter | ✅ Complete | All API key usages migrated |
| ReplicateAdapter | ✅ Complete | All API key usages migrated |
| OllamaCloudAdapter | ✅ Complete | All API key usages migrated |
| LocalLlamaAdapter | ✅ N/A | Uses local inference, no API key |

**Migration Complete: All 9 cloud provider adapters now use secure API key access.**

## SecretBytes Location

The `SecretBytes` class has been moved from `:app` to `:core-contracts` to allow shared access:

- **New location**: `core-contracts/src/main/kotlin/com/shadowai/core/security/SecretBytes.kt`
- **Usage**: `com.shadowai.core.security.SecretBytes`

## Security Benefits

1. **Memory clearing**: Secret bytes are wiped from memory immediately after use via `useBytes { }`
2. **No persistent strings**: API keys aren't stored as immutable Strings in memory
3. **Explicit lifecycle**: Callers must explicitly handle the key within a limited scope
4. **Zero-copy access**: The key is copied only when needed, then wiped

## Usage in App Layer

When creating ProviderAdapterConfig, prefer the secure form:

```kotlin
val config = ProviderAdapterConfig(
    providerId = ProviderId.OPENAI,
    baseUrl = "https://api.openai.com/v1",
    apiKeySecret = SecretBytes.from(apiKey.toByteArray(Charsets.UTF_8)),
    modelId = "gpt-4o"
)
```

The legacy `apiKey: String?` field remains for backward compatibility, but new code should use `apiKeySecret`.
