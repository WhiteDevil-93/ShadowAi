# Certificate Pinning Maintenance Guide

## Overview

This guide explains how to maintain and update certificate pins for cloud AI providers in the ShadowAI app. Certificate pinning prevents MITM (Man-in-the-Middle) attacks by ensuring the app only trusts specific SSL certificates.

## Location of Files

- Configuration: `app/src/main/java/com/shadowai/app/security/CertificatePinningConfig.kt`
- Interceptor: `app/src/main/java/com/shadowai/app/network/CertificatePinningInterceptor.kt`
- Error Handler: `app/src/main/java/com/shadowai/app/security/CertificateErrorHandler.kt`
- Exception: `app/src/main/java/com/shadowai/app/exceptions/CertificatePinningException.kt`
- DI Configuration: `app/src/main/java/com/shadowai/app/di/AppModule.kt`

## Current Certificate Pins

| Provider | Hostname | SHA256 Pin | Last Updated |
|----------|----------|------------|--------------|
| OpenAI | api.openai.com | `y5npFVdBuoqCSOdQa42qiUSPqwMpoei7NK0rQWGUaSU=` | 2026-02-11 |
| Anthropic | api.anthropic.com | `60QDDZy98CjK1XTBTlPbInyzJzi+817KvW+usCk6r+o=` | 2026-02-11 |
| Google Gemini | generativelanguage.googleapis.com | `beMAm4GYDucmQKh+VCUDnjnyi6/lYbL8AGn0xzLxwdQ=` | 2026-02-11 |
| OpenRouter | openrouter.ai | `2ETytvFJ0SYiiaUyT3xMrJ3Yuen/K58SNiB87YChuRg=` | 2026-02-11 |
| DeepSeek | api.deepseek.com | `DR0Gpd4Pbm6uwcjbOXvXkJ+RpBGTI0Fk3zXyJFx7tIc=` | 2026-02-11 |
| Mistral AI | api.mistral.ai | `WIL7Gb0Z+W0RVwIfIOJhj7MF02UNEWTAC+EaAyu9MFI=` | 2026-02-11 |
| xAI (X) | api.x.ai | `goZa6+Wl5S9dLXkybh6d6cyFp6APuKnUTbOLvHjKvBs=` | 2026-02-11 |
| Groq | api.groq.com | `d4+HJjLne/sZOYjO+ObMgq4Wzv3hKzBFi7hrv+Gqmt0=` | 2026-02-11 |

## When to Update Pins

You should update certificate pins when:

1. **Certificate Rotation**: The cloud provider rotates their SSL certificate
2. **Connection Failures**: Users report connection failures with certificate errors
3. **Regular Maintenance**: Recommended to check pins every 3-6 months
4. **New Providers**: Adding support for a new cloud AI provider

## How to Extract a Certificate Pin

### Using OpenSSL (Linux/macOS/WSL)

```bash
echo "api.example.com" | openssl s_client -servername api.example.com \
  -connect api.example.com:443 -showcerts 2>/dev/null | \
  openssl x509 -noout -pubkey | openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | openssl enc -base64
```

### Output example:
```
y5npFVdBuoqCSOdQa42qiUSPqwMpoei7NK0rQWGUaSU=
```

## How to Update Pins

### Step 1: Extract the new pin

Run the OpenSSL command for the provider whose certificate has changed.

```bash
# Example for OpenAI
echo "api.openai.com" | openssl s_client -servername api.openai.com \
  -connect api.openai.com:443 -showcerts 2>/dev/null | \
  openssl x509 -noout -pubkey | openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | openssl enc -base64
```

### Step 2: Update CertificatePinningConfig.kt

Edit `app/src/main/java/com/shadowai/app/security/CertificatePinningConfig.kt`:

```kotlin
val HOST_PINS: Map<String, List<String>> = mapOf(
    "api.openai.com" to listOf(
        "NEW_PIN_HERE==" // Replace with the new pin
    ),
    // ... other providers
)
```

**Best practices:**
- Keep the old pin for a transition period (pin multiple certificates)
- Update the "Last Updated" date in this documentation
- Test thoroughly before deploying to production

### Step 3: Test the new pin

1. Build the app: `./gradlew assembleDebug`
2. Install on a test device
3. Connect to the provider's API
4. Verify connections work correctly

### Step 4: Verify MITM protection

Test with a MITM proxy (e.g., Charles Proxy):

1. Install Charles Proxy or Burp Suite
2. Configure the device to use the proxy
3. Try to connect to the pinned provider
4. **Expected result**: Connection should fail with a certificate error
5. **If connection succeeds**: Pinning is not enabled correctly

## Adding a New Provider

### Step 1: Extract the pin

```bash
echo "new-provider-api.com" | openssl s_client \
  -servername new-provider-api.com -connect new-provider-api.com:443 \
  -showcerts 2>/dev/null | openssl x509 -noout -pubkey | \
  openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | \
  openssl enc -base64
```

### Step 2: Add to CertificatePinningConfig.kt

```kotlin
val HOST_PINS: Map<String, List<String>> = mapOf(
    // ... existing providers
    "new-provider-api.com" to listOf(
        "SHA256_PIN_HERE=="
    ),
)
```

### Step 3: Test

Follow the testing steps in the "How to Update Pins" section above.

## Troubleshooting

### Issue: "Certificate pinning failure" errors

**Possible causes:**
1. Provider rotated their certificate
2. Expired pin in configuration
3. MITM proxy intercepting connections

**Solutions:**
1. Use OpenSSL to extract the current certificate
2. Update the pin in `CertificatePinningConfig.kt`
3. If using Charles/Burp for development, temporarily disable pinning

### Issue: Cannot connect during development with a proxy

**Temporary solution for development:**
Create a debug-only variant that doesn't use pinning:

```kotlin
// In a debug build variant
@Provides
@Singleton
fun provideOkHttpClientDebug(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Skip certificate pinning for debug builds
        .build()
}
```

**Better solution:**
Pin the proxy's certificate instead of disabling pinning entirely.

### Issue: Multiple pins for same provider

Some providers use multiple certificates (load balancing, different regions). Add all pins:

```kotlin
"api.example.com" to listOf(
    "sha256/PIN1==",    // Certificate 1
    "sha256/PIN2==",    // Certificate 2
    "sha256/PIN3==",    // Certificate 3
),
```

OkHttp will accept any of the pinned certificates.

## Security Considerations

### Why use Certificate Pinning?

1. **Prevent MITM attacks**: Even with a malicious CA installed on the device
2. **Trust only known certificates**: Not just any valid certificate from a CA
3. **Early detection of certificate rotation**: Be aware when provider updates

### When NOT to use pinning

- **Development with proxy**: Temporarily disable or pin the proxy's certificate
- **Testing with MITM tools**: Use debug builds without pinning
- **Frequent certificate rotation**: May cause maintenance overhead

### Best Practices

1. **Pin multiple certificates**: Include both current and upcoming certificates
2. **Monitor for rotation**: Subscribe to provider security bulletins
3. **Have an update mechanism**: Consider implementing remote pin updates
4. **Document everything**: Keep records of pin changes with dates
5. **Test regularly**: Verify pins work and reject invalid certificates

## References

- [OkHttp CertificatePinner Documentation](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [OWASP Certificate Pinning Guide](https://cheatsheetseries.owasp.org/cheatsheets/Pinning_Cheat_Sheet.html)
- [Android Security Best Practices](https://developer.android.com/training/articles/security-tips#Network)

## Changelog

### 2026-02-11
- Initial implementation of certificate pinning
- Added pins for OpenAI, Anthropic, Gemini, OpenRouter, DeepSeek, Mistral AI, xAI, Groq
- Created monitoring and error handling infrastructure
- Created comprehensive maintenance guide

---

**Last updated**: 2026-02-11
**Maintained by**: ShadowAI Security Team