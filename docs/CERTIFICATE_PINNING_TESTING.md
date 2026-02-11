# Certificate Pinning Testing Guide

This guide provides instructions for testing the certificate pinning implementation in ShadowAI.

## Prerequisites

- Android test device or emulator
- Charles Proxy, Burp Suite, or similar MITM proxy tool
- ShadowAI app with certificate pinning enabled (debug or release build)

## Test 1: Normal Operation (Without Proxy)

**Purpose**: Verify that the app works correctly with proper SSL certificates.

### Steps

1. Build and install the app:
   ```bash
   ./gradlew :app:installDebug
   ```

2. Ensure no proxy is configured on the device

3. Add a cloud provider configuration (e.g., OpenAI API key)

4. Make an API call to the provider

5. **Expected Result**: Connection succeeds and response is received

**Verification Checklist**:
- [ ] No certificate errors in logs
- [ ] API request completes successfully
- [ ] Response data is valid

---

## Test 2: MITM Proxy Detection

**Purpose**: Verify that the app rejects connections when a MITM proxy attempts to intercept traffic.

### Setup Charles Proxy

1. Install Charles Proxy on your computer
2. Enable SSL Proxying: Proxy > SSL Proxying Settings
3. Add host to SSL Proxying: `*.api.openai.com` (or any pinned host)
4. Install Charles Root Certificate on your test device

### Configure Device to Use Proxy

1. Get your computer's IP address:
   - macOS/Linux: `ifconfig` or `ip addr`
   - Windows: `ipconfig`

2. On your Android device:
   - Go to Settings > WiFi
   - Long-press your network and select "Modify Network"
   - Show advanced options
   - Set Proxy to "Manual"
   - Set Proxy hostname to your computer's IP
   - Set Proxy port to `8888` (Charles default)

### Perform the Test

1. Make an API call to a pinned provider (e.g., api.openai.com)

2. **Expected Result**: Connection fails with a certificate pinning error

### Verify Error Handling

Check logs for:
```
CertificatePinningException: Certificate pinning validation failed for api.openai.com
```

The app should:
- [ ] Reject the connection
- [ ] Show/Log appropriate error message
- [ ] Not proceed with the request

**Verification Checklist**:
- [ ] Connection is rejected
- [ ] CertificatePinningException is thrown
- [ ] Error is logged with details
- [ ] User notification is shown (if implemented)

---

## Test 3: Pinning Disabled (Debug Build)

**Purpose**: Verify that you can disable pinning for development/debugging purposes.

### Option A: Comment out in Code

1. Edit `CertificatePinningConfig.kt`:
   ```kotlin
   fun createPinner(): CertificatePinner {
       return CertificatePinner.Builder()  // No pins added
           .build()
   }
   ```

2. Rebuild and reinstall
3. Configure proxy as in Test 2
4. Make API call

**Expected Result**: Connection succeeds through the proxy

### Option B: Build Variant

1. Create a debug build variant that doesn't use pinning
2. In `AppModule.kt`, add a debug-specific provider:
   ```kotlin
   @Provides
   @Singleton
   fun provideOkHttpClientDebug(): OkHttpClient {
       return OkHttpClient.Builder()
           .connectTimeout(30, TimeUnit.SECONDS)
           .readTimeout(120, TimeUnit.SECONDS)
           .writeTimeout(30, TimeUnit.SECONDS)
           // No certificate pinning for debug builds
           .build()
   }
   ```

**Verification Checklist**:
- [ ] Debug build can connect through proxy
- [ ] Release build still has pinning enabled
- [ ] No security bypass in production builds

---

## Test 4: Certificate Rotation Simulation

**Purpose**: Verify that updating pins correctly handles certificate changes.

### Steps

1. Extract the CURRENT certificate pin for a provider:
   ```bash
   echo "api.openai.com" | openssl s_client -servername api.openai.com \
     -connect api.openai.com:443 -showcerts 2>/dev/null | \
     openssl x509 -noout -pubkey | openssl pkey -pubin -outform der | \
     openssl dgst -sha256 -binary | openssl enc -base64
   ```

2. Temporarily change the pin in `CertificatePinningConfig.kt` to an INVALID pin:
   ```kotlin
   "api.openai.com" to listOf("INVALID_PIN_XXXXXXXXXXXXXXXXXXXXX==")
   ```

3. Rebuild and test:

4. Make an API call to OpenAI

5. **Expected Result**: Connection fails with certificate pinning error

6. Restore the correct pin from step 1

7. **Expected Result**: Connection succeeds

**Verification Checklist**:
- [ ] Invalid pin causes connection failure
- [ ] Correct pin restores functionality
- [ ] Error is clear and informative

---

## Test 5: Multiple Certificate Pins

**Purpose**: Verify that multiple pins for the same host work correctly.

### Steps

1. Add multiple pins for a host in `CertificatePinningConfig.kt`:
   ```kotlin
   "api.openai.com" to listOf(
       "ACTUAL_PIN_1==",
       "ACTUAL_PIN_2=="  // Can be the same for testing
   )
   ```

2. Rebuild and test

3. Make API call

4. **Expected Result**: Connection succeeds

**Verification Checklist**:
- [ ] Multiple pins are supported
- [ ] Connection succeeds when any pin matches

---

## Test 6: Non-Pinned Hosts (Optional)

**Purpose**: Verify that unpinned hosts can still connect (if you choose not to pin everything).

### Steps

1. Ensure a provider's host is NOT in `HOST_PINS` map

2. Make API call to that provider

3. **Expected Result**: Connection succeeds (no pinning applied)

**Note**: This test is optional if you plan to pin all providers.

**Verification Checklist**:
- [ ] Unpinned hosts work normally
- [ ] No unnecessary blocking of non-pinned hosts

---

## Test 7: Error Recovery

**Purpose**: Verify that the app handles certificate errors gracefully.

### Steps

1. Intentionally invalidate a pin (as in Test 4)

2. Attempt to make an API call

3. Verify error handling:
   - Application doesn't crash
   - User sees appropriate error message
   - Logs contain useful debugging information

4. Restore correct pin

5. Verify recovery:
   - API call succeeds on retry
   - No persistent error state

**Verification Checklist**:
- [ ] App doesn't crash on pinning errors
- [ ] User receives clear error feedback
- [ ] Recovery is possible after fix
- [ ] No memory leaks or state corruption

---

## Automated Tests

### Unit Test for CertificatePinningConfig

```kotlin
@Test
fun `createPinner returns configured pins`() {
    val pinner = CertificatePinningConfig.createPinner()

    assertNotNull(pinner)
    // Verify specific hosts are pinned
}
```

### Integration Test for Interceptor

```kotlin
@Test
fun `interceptor handles certificate errors`() {
    val exception = CertificatePinningException(
        hostname = "api.test.com",
        message = "Test failure"
    )

    val interceptor = CertificatePinningInterceptor(
        onError = { e ->
            assertEquals("api.test.com", e.hostname)
        }
    )

    // ... test implementation
}
```

---

## Common Issues and Solutions

### Issue: "SSLPeerUnverifiedException" appears

**Diagnosis**: Check if the proxy is enabled and configured correctly.

**Solution**: Ensure proxy intercepts the traffic and presents its own certificate.

### Issue: Connections work with proxy enabled

**Diagnosis**: Pinning might not be enabled correctly.

**Solution**:
1. Verify `CertificatePinningConfig.createPinner()` is called in `provideOkHttpClient()`
2. Check build logs for any compilation errors
3. Verify the interceptor is added to the OkHttpClient

### Issue: Can't connect without proxy

**Diagnosis**: Certificate pinning may be rejecting valid certificates.

**Solution**:
1. Extract the current certificate pin using the OpenSSL command
2. Update `CertificatePinningConfig.kt` with the correct pin
3. Verify the pin format is correct (base64 encoded SHA-256 hash)

### Issue: "No route to host" errors

**Diagnosis**: Network connectivity issue, not related to certificate pinning.

**Solution**:
1. Check device internet connection
2. Verify firewall settings
3. Confirm the provider's API is operational

---

## Performance Considerations

Certificate pinning has minimal performance impact:
- **Connection overhead**: Negligible (hash comparison only)
- **TLS handshake**: No additional delay
- **Battery impact**: None

---

## Security Checklist

Before releasing to production:

- [ ] All cloud providers have certificate pins configured
- [ ] Debug builds can disable pinning (for development)
- [ ] Release builds have pinning enforced
- [ ] Certificate update process is documented
- [ ] Error handling is user-friendly
- [ ] Monitoring/alerting for certificate failures is configured
- [ ] Pins are reviewed regularly (3-6 months recommended)
- [ ] Multiple pins support is tested
- [ ] MITM proxy detection is verified
- [ ] Recovery from certificate rotation is tested

---

## References

- [Main Maintenance Guide](CERTIFICATE_PINNING_MAINTENANCE.md)
- [OkHttp CertificatePinner](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [OWASP Certificate and Public Key Pinning](https://cheatsheetseries.owasp.org/cheatsheets/Pinning_Cheat_Sheet.html)

---

**Last updated**: 2026-02-11
**Tested on**: Android API Level 21+ (minSdkVersion)