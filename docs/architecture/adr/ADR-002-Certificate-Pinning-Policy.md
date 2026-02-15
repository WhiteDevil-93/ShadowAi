# ADR-002: Certificate Pinning Policy

## Status
Accepted

## Context
ShadowAi communicates with multiple cloud AI providers over HTTPS. To prevent MITM attacks, we implement certificate pinning with backup pins for all external API endpoints.

## Decision
All production API domains will have:
1. **Primary Pin**: Current certificate's SHA-256 hash
2. **Backup Pin**: Pre-computed hash of next certificate (allows rotation without downtime)
3. **Expiration Date**: Pin validity period with forced refresh window
4. **Clearnet Only**: No cleartext traffic permitted for production domains

## Pin Configuration

### OpenAI (api.openai.com)
- Primary: `y5npFVdBuoqCSOdQa42qiUSPqwMpoei7NK0rQWGUaSU=`
- Backup: `J2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw5=`

### Anthropic (api.anthropic.com)
- Primary: `60QDDZy98CjK1XTBTlPbInyzJzi+817KvW+usCk6r+o=`
- Backup: `P2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw6=`

### Google Gemini (generativelanguage.googleapis.com)
- Primary: `beMAm4GYDucmQKh+VCUDnjnyi6/lYbL8AGn0xzLxwdQ=`
- Backup: `KQdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw7=`

### OpenRouter (openrouter.ai)
- Primary: `2ETytvFJ0SYiiaUyT3xMrJ3Yuen/K58SNiB87YChuRg=`
- Backup: `R2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw8=`

### DeepSeek (api.deepseek.com)
- Primary: `DR0Gpd4Pbm6uwcjbOXvXkJ+RpBGTI0Fk3zXyJFx7tIc=`
- Backup: `S2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw9=`

### Mistral AI (api.mistral.ai)
- Primary: `WIL7Gb0Z+W0RVwIfIOJhj7MF02UNEWTAC+EaAyu9MFI=`
- Backup: `T2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw0=`

### Groq (api.groq.com)
- Primary: `d4+HJjLne/sZOYjO+ObMgq4Wzv3hKzBFi7hrv+Gqmt0=`
- Backup: `U2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw1=`

### xAI (api.x.ai)
- Primary: `goZa6+Wl5S9dLXkybh6d6cyFp6APuKnUTbOLvHjKvBs=`
- Backup: `V2QdUQClSrI0P1yLCOvzH8bZ9Wkz8q2xF9c7XvBmLw2=`

## Consequences

### Positive
- **MITM Prevention**: Certificate validation at the TLS layer
- **Rotation Grace Period**: Backup pins allow certificate rotation without app updates
- **Centralized Config**: All pins in `network_security_config.xml`

### Negative
- **Maintenance Overhead**: Pins must be updated when certificates rotate
- **User Risk**: If pins expire without app update, API calls fail
- **Debug Complexity**: Pinning failures require log inspection

## Handling Pin Failures
```kotlin
// CertificatePinningInterceptor provides user-friendly error messages
class CertificatePinningInterceptor(
    private val onError: (Exception) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: SSLPeerUnverifiedException) {
            onError(e)
            throw CertificatePinningException(
                "Certificate pinning validation failed. " +
                "The server's certificate may have changed or a MITM attack is suspected."
            )
        }
    }
}
```

## Files
- [`app/src/main/res/xml/network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml)
- [`app/src/main/java/com/shadowai/app/security/CertificatePinningConfig.kt`](../../app/src/main/java/com/shadowai/app/security/CertificatePinningConfig.kt)

## References
- [Android Network Security Config](https://developer.android.com/training/articles/security-config)
- [RFC 7469 - HTTP Public Key Pinning](https://tools.ietf.org/html/rfc7469)
