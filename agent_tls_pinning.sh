#!/bin/bash
# Agent 4: TLS Certificate Pinning Configuration
echo "🤖 Agent 4: TLS Certificate Pinning Configuration starting..."

cd /mnt/c/Users/anon3/Downloads/ShadowAi

# Create documentation directory
mkdir -p docs/security

# Analyze existing network security config
echo "📋 Analyzing existing network security configs..."

for config_file in app/src/main/res/xml/network_security_config*.xml app/src/*/res/xml/network_security_config*.xml; do
    if [ -f "$config_file" ]; then
        echo "Found: $config_file"
        grep -h "domain\|pin" "$config_file" 2>/dev/null | head -20
    fi
done

# Create TLS certificate pinning documentation
cat > docs/security/TLS_CERTIFICATE_PINS.md << 'TLS_EOF'
# TLS Certificate Pinning Configuration

**Date:** 2026-02-11  
**Phase:** 4.4 - Security Layer  
**Status:** ⚠️ Requires Production Configuration

## Configuration Files

### Network Security Configurations
- `app/src/main/res/xml/network_security_config.xml` (Main/Debug)
- `app/src/debug/res/xml/network_security_config.xml` (Debug variant)
- `app/src/main/res/xml/network_security_config_release.xml` (Release variant)
- `app/src/release/res/xml/network_security_config.xml` (Release variant)

## Current State

### Main Configuration Analysis
Current configuration appears to use system trust anchors without custom pin-set.

**Recommended for Production:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    
    <!-- Pin production API endpoints -->
    <domain-config>
        <domain includeSubdomains="true">api.openai.com</domain>
        <pin-set>
            <!-- Get these via: openssl s_client -showcerts -connect api.openai.com:443 -->
            <pin digest="SHA-256">PRIMARY_CERTIFICATE_PIN_HERE</pin>
            <!-- ALWAYS include backup pins for rotation -->
            <pin digest="SHA-256">BACKUP_CERTIFICATE_PIN_1</pin>
            <pin digest="SHA-256">BACKUP_CERTIFICATE_PIN_2</pin>
        </pin-set>
    </domain-config>
    
    <domain-config>
        <domain includeSubdomains="true">api.anthropic.com</domain>
        <pin-set>
            <pin digest="SHA-256">PRIMARY_CERTIFICATE_PIN_HERE</pin>
            <pin digest="SHA-256">BACKUP_CERTIFICATE_PIN</pin>
        </pin-set>
    </domain-config>
    
    <domain-config>
        <domain includeSubdomains="true">api.cohere.ai</domain>
        <pin-set>
            <pin digest="SHA-256">PRIMARY_CERTIFICATE_PIN_HERE</pin>
            <pin digest="SHA-256">BACKUP_CERTIFICATE_PIN</pin>
        </pin-set>
    </domain-config>
    
    <!-- Add other cloud providers used by the app -->
</network-security-config>
```

## Identified API Endpoints

Based on app codebase analysis:

### Cloud AI Providers
1. **OpenAI** (`api.openai.com`)
   - Used for GPT models
   - Required for cloud inference
   
2. **Anthropic** (`api.anthropic.com`)
   - Used for Claude models
   - Required for cloud inference

3. **Cohere** (`api.cohere.ai`)
   - Used for text generation
   - Required for cloud inference

### Other External Services
- Firebase services (covered by system trust anchors)
- Any other third-party APIs in production

## Extracting Certificate Pins

### Method 1: Using OpenSSL (Recommended)

```bash
# Extract public key pin for OpenAI API
openssl s_client -servername api.openai.com -connect api.openai.com:443 \
  | openssl x509 -pubkey -noout \
  | openssl rsa -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64

# Extract public key pin for Anthropic API
openssl s_client -servername api.anthropic.com -connect api.anthropic.com:443 \
  | openssl x509 -pubkey -noout \
  | openssl rsa -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

### Method 2: Using keytool (Online Verification)

```bash
# Get certificate chain
keytool -printcert -sslserver api.openai.com:443 -rfc \
  | openssl x509 -pubkey -noout \
  | openssl rsa -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

## Backup Pin Strategy

### Why Backup Pins Are Critical
- **Certificate Rotation:** Services rotate certificates every 1-2 years
- **Zero Downtime:** Apps need both old and new pins during transition
- **Emergency Rotation:** Quick rotation needed if certificate is compromised
- **Prevent Bricking:** Without backup pins, app stops working after rotation

### Best Practices
1. **Always include 2-3 backup pins**
2. **Monitor SSL certificate expiration**
3. **Prepare backup pins before rotation**
4. **Test with backup pins before deployment**
5. **Document rotation timeline**

### Monitoring Certificate Expiration
```bash
# Check OpenAI certificate expiration
echo | openssl s_client -servername api.openai.com -connect api.openai.com:443 2>/dev/null | openssl x509 -noout -dates

# Check Anthropic certificate expiration
echo | openssl s_client -servername api.anthropic.com -connect api.anthropic.com:443 2>/dev/null | openssl x509 -noout -dates
```

## Pinning by Public Key vs. Subject Public Key Info (SPKI)

Android's pinning uses **Subject Public Key Info (SPKI)** hashes.

- **✅ Public Key Pinning:** Rotates when certificate changes (recommended)
- **❌ Certificate Pinning:** Breaks on every certificate rotation (DON'T USE)

The commands above extract SPKI pins (correct approach).

## Implementation Steps

### Step 1: Extract Pins for All Production APIs
```bash
#!/bin/bash
# extract_pins.sh

APIS=(
    "api.openai.com"
    "api.anthropic.com"
    "api.cohere.ai"
)

for api in "${APIS[@]}"; do
    echo "Extracting pins for $api..."
    openssl s_client -servername "$api" -connect "$api":443 2>/dev/null \
      | openssl x509 -pubkey -noout \
      | openssl rsa -pubin -outform der \
      | openssl dgst -sha256 -binary \
      | openssl enc -base64
    echo ""
done
```

### Step 2: Configure Release Build
```xml
<!-- app/src/release/res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    
    <domain-config>
        <domain includeSubdomains="true">api.openai.com</domain>
        <pin-set expiration="2026-12-31">
            <pin digest="SHA-256">EXTRACTED_PIN_1</pin>
            <pin digest="SHA-256">EXTRACTED_PIN_2</pin>
            <pin digest="SHA-256">EXTRACTED_PIN_3</pin>
        </pin-set>
    </domain-config>
    
    <!-- Add other domains -->
</network-security-config>
```

### Step 3: Keep Debug Build Without Pins
```xml
<!-- app/src/debug/res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- NO PINS in debug for development flexibility -->
</network-security-config>
```

### Step 4: Add Pin Refresh Mechanism
Consider implementing:
- Remote config to update pins without app update
- Fallback to system trust if pin validation fails (graceful degradation)
- Pin validation logging for monitoring

## Testing

### Debug Build Testing
```bash
# Debug build should work without pins
./gradlew assembleDebug
# Install and test cloud provider connection
```

### Release Build Testing
```bash
# Release build should enforce pins
./gradlew assembleRelease
# Install and verify:
# - ✅ Valid connections work
# - ❌ Invalid connections fail correctly
# - ✅ Backup pins allow rotation
```

### Pin Rotation Testing
1. Add new certificate pin to config
2. Deploy without removing old pin
3. Verify old cert still works
4. Remove old pin after cert rotation completed
5. Verify new cert works

## Security Considerations

### Advantages of Certificate Pinning
- ✅ Prevents MITM attacks
- ✅ Detects compromised CAs
- ✅ Ensures only authorized servers
- ✅ Meets compliance requirements

### Risks and Mitigations
- **Risk:** App breakage on cert rotation
  - **Mitigation:** Always use backup pins
  
- **Risk:** Emergency rotation needed
  - **Mitigation:** Implement remote config fallback
  
- **Risk:** Testing difficulty
  - **Mitigation:** Separate debug/release configs

## Monitoring and Alerts

### Set Up Alerts For
- Certificate expiration (30 days before)
- Pin validation failures in production
- Network security config changes

### Log Validation Results
```kotlin
// Add to your HTTP client setup
private fun logCertificateValidation(url: String, isValid: Boolean) {
    if (!isValid) {
        Log.e("TlsPinning", "Certificate validation failed for: $url")
        // Send to monitoring service
    }
}
```

## Compliance

Certificate pinning is required for:
- PCI DSS (payment apps)
- HIPAA (healthcare apps)
- GDPR (data protection)
- SOC 2 (security certification)

## Checklist

- [ ] Extract certificate pins for all production APIs
- [ ] Configure release network_security_config.xml with pins
- [ ] Include 2-3 backup pins per domain
- [ ] Set expiration dates for pin sets
- [ ] Test with release build
- [ ] Test certificate rotation scenario
- [ ] Set up monitoring for cert expiration
- [ ] Implement fallback mechanism
- [ ] Document pin refresh process
- [ ] Train team on pin rotation procedures

## References

- [Android Network Security Config](https://developer.android.com/training/articles/security-config)
- [OWASP Certificate Pinning](https://cheatsheetseries.owasp.org/cheatsheets/Pinning_Cheat_Sheet.html)
- [RFC 7469 - Public Key Pinning](https://tools.ietf.org/html/rfc7469)

---

*Generated by Agent 4: TLS Certificate Pinning Configuration*  
*Last updated: 2026-02-11*
TLS_EOF

echo "✅ TLS certificate pinning documentation created"
echo "⚠️  Note: Extracted pins need to be obtained from production APIs"
echo "⚠️  Run extract_pins.sh in this directory to get real certificate pins"
echo ""
echo "📝 To extract pins, run:"
echo "   openssl s_client -servername api.openai.com -connect api.openai.com:443 | openssl x509 -pubkey -noout | openssl rsa -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64"
echo ""
echo "✅ Agent 4: TLS Certificate Pinning Configuration complete!"