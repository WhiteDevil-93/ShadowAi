package com.shadowai.app.security

import okhttp3.CertificatePinner

/**
 * Configuration for certificate pinning to prevent MITM attacks.
 *
 * Certificate pinning ensures the app only trusts specific SSL certificates
 * for cloud AI providers, preventing connection interception even if a
 * malicious CA is installed on the device.
 *
 * ## How to update pins when certificates rotate
 * 1. Export the certificate from the endpoint:
 *    ```bash
 *    echo "api.example.com" | openssl s_client -servername api.example.com \
 *      -connect api.example.com:443 -showcerts 2>/dev/null | \
 *      openssl x509 -noout -pubkey | openssl pkey -pubin -outform der | \
 *      openssl dgst -sha256 -binary | openssl enc -base64
 *    ```
 * 2. Update the corresponding pin in the map below
 * 3. Test with a MITM proxy (Charles Proxy, Burp Suite) to verify
 *    connections fail with untrusted certificates
 *
 * ## Verification
 * Test with Charles Proxy/MITM proxy - connections should fail with
 * untrusted certs. You may need to temporarily disable pinning during
 * development by using `CertificatePinner.Builder()` without pins.
 *
 * @see okhttp3.CertificatePinner
 */
object CertificatePinningConfig {

    /**
     * Map of hostnames to their SHA256 certificate pins.
     *
     * These pins are the SHA-256 hash of the Subject Public Key Info (SPKI)
     * of the leaf certificate. They are used to validate that the app connects
     * to the expected servers and not to a MITM proxy or malicious server.
     */
    val HOST_PINS: Map<String, List<String>> = mapOf(
        // OpenAI API
        "api.openai.com" to listOf(
            "sha256/y5npFVdBuoqCSOdQa42qiUSPqwMpoei7NK0rQWGUaSU="
        ),

        // Anthropic Claude API
        "api.anthropic.com" to listOf(
            "sha256/60QDDZy98CjK1XTBTlPbInyzJzi+817KvW+usCk6r+o="
        ),

        // Google Gemini (generativelanguage.googleapis.com)
        "generativelanguage.googleapis.com" to listOf(
            "sha256/beMAm4GYDucmQKh+VCUDnjnyi6/lYbL8AGn0xzLxwdQ="
        ),

        // OpenRouter
        "openrouter.ai" to listOf(
            "sha256/2ETytvFJ0SYiiaUyT3xMrJ3Yuen/K58SNiB87YChuRg="
        ),

        // DeepSeek
        "api.deepseek.com" to listOf(
            "sha256/DR0Gpd4Pbm6uwcjbOXvXkJ+RpBGTI0Fk3zXyJFx7tIc="
        ),

        // Mistral AI
        "api.mistral.ai" to listOf(
            "sha256/WIL7Gb0Z+W0RVwIfIOJhj7MF02UNEWTAC+EaAyu9MFI="
        ),

        // xAI (X)
        "api.x.ai" to listOf(
            "sha256/goZa6+Wl5S9dLXkybh6d6cyFp6APuKnUTbOLvHjKvBs="
        ),

        // Groq
        "api.groq.com" to listOf(
            "sha256/d4+HJjLne/sZOYjO+ObMgq4Wzv3hKzBFi7hrv+Gqmt0="
        ),

        // Additional providers can be added here as needed
        // Example format:
        // "api.provider.com" to listOf("sha256/pin1", "sha256/pin2")
    )

    /**
     * Creates a configured CertificatePinner instance for use with OkHttpClient.
     *
     * The pinner will validate SSL certificates for all configured hosts.
     * If a certificate pin validation fails, the connection will be rejected
     * with a SSLPeerUnverifiedException.
     *
     * @return Configured CertificatePinner instance
     */
    fun createPinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()

        HOST_PINS.forEach { (hostname, pins) ->
            pins.forEach { pin ->
                builder.add(hostname, normalizePin(pin))
            }
        }

        return builder.build()
    }

    /**
     * Creates a CertificatePinner for a specific host only.
     *
     * Use this when you want to pin only a single provider's endpoint.
     *
     * @param hostname The hostname to pin
     * @return CertificatePinner for the specified host, or empty pinner if host not configured
     */
    fun createPinnerForHost(hostname: String): CertificatePinner {
        val pins = HOST_PINS[hostname] ?: return CertificatePinner.Builder().build()

        val builder = CertificatePinner.Builder()
        pins.forEach { pin ->
            builder.add(hostname, normalizePin(pin))
        }

        return builder.build()
    }

    /**
     * Normalizes pin format for OkHttp.
     *
     * OkHttp requires each pin to start with `sha256/` or `sha1/`. We treat raw base64
     * values as SHA-256 pins for backward compatibility with existing configuration.
     */
    private fun normalizePin(pin: String): String {
        val trimmed = pin.trim()
        return if (trimmed.startsWith("sha256/") || trimmed.startsWith("sha1/")) {
            trimmed
        } else {
            "sha256/$trimmed"
        }
    }
}
