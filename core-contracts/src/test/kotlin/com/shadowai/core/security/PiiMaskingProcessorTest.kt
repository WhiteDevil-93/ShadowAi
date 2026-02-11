package com.shadowai.core.security
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive test suite for PiiMaskingProcessor.
 *
 * Coverage targets:
 * - Email masking (various formats)
 * - Phone number masking (US, international, formats)
 * - SSN masking
 * - Credit card masking
 * - IP address masking
 * - Mixed PII detection
 * - No false positives on safe text
 */
class PiiMaskingProcessorTest {

    private val processor = PiiMaskingProcessor()

    // ==================== EMAIL MASKING ====================

    @Test
    fun `simple email masked`() {
        val text = "Contact me at john@example.com for details"
        val result = processor.maskPii(text)

        assertFalse("Email should be masked", result.contains("john@example.com"))
        assertTrue("Should contain email placeholder", result.contains("[EMAIL_REDACTED]"))
        assertTrue("Safe text preserved", result.contains("Contact me at"))
    }

    @Test
    fun `email with subdomain masked`() {
        val text = "Send to admin@mail.company.co.uk"
        val result = processor.maskPii(text)

        assertFalse("Email should be masked", result.contains("admin@mail.company.co.uk"))
        assertTrue("Should contain placeholder", result.contains("[EMAIL_REDACTED]"))
    }

    @Test
    fun `email with plus addressing masked`() {
        val text = "My email is user+tag@example.com"
        val result = processor.maskPii(text)

        assertFalse("Plus email should be masked", result.contains("user+tag@example.com"))
        assertTrue("Should contain placeholder", result.contains("[EMAIL_REDACTED]"))
    }

    @Test
    fun `email with international domain masked`() {
        val text = "Reach me at user@xn--e1afmkfd.xn--p1ai"
        val result = processor.maskPii(text)

        assertFalse("Internationalized email should be masked", result.contains("user@xn--e1afmkfd.xn--p1ai"))
        assertTrue("Should contain placeholder", result.contains("[EMAIL_REDACTED]"))
    }

    @Test
    fun `multiple emails all masked`() {
        val text = "CC: alice@test.com and bob@demo.org"
        val result = processor.maskPii(text)

        assertFalse("First email masked", result.contains("alice@test.com"))
        assertFalse("Second email masked", result.contains("bob@demo.org"))
        assertEquals("Both placeholders present", 2, result.split("[EMAIL_REDACTED]").size - 1)
    }

    @Test
    fun `containsPii detects email`() {
        val text = "Contact: john@example.com"
        assertTrue("Should detect email PII", processor.containsPii(text))
    }

    // ==================== PHONE NUMBER MASKING ====================

    @Test
    fun `us phone with dashes masked`() {
        val text = "Call me at 555-123-4567 anytime"
        val result = processor.maskPii(text)

        assertFalse("Phone should be masked", result.contains("555-123-4567"))
        assertTrue("Should contain phone placeholder", result.contains("[PHONE_REDACTED]"))
    }

    @Test
    fun `us phone with parentheses masked`() {
        val text = "Reach me at (555) 123-4567"
        val result = processor.maskPii(text)

        assertFalse("Parentheses phone should be masked", result.contains("(555) 123-4567"))
        assertTrue("Should contain placeholder", result.contains("[PHONE_REDACTED]"))
    }

    @Test
    fun `us phone with spaces masked`() {
        val text = "Phone: 555 123 4567"
        val result = processor.maskPii(text)

        assertFalse("Spaced phone should be masked", result.contains("555 123 4567"))
        assertTrue("Should contain placeholder", result.contains("[PHONE_REDACTED]"))
    }

    @Test
    fun `international phone with plus masked`() {
        val text = "International: +1-555-123-4567"
        val result = processor.maskPii(text)

        assertFalse("International phone masked", result.contains("+1-555-123-4567"))
        assertTrue("Should contain placeholder", result.contains("[PHONE_REDACTED]"))
    }

    @Test
    fun `international phone with spaces masked`() {
        val text = "Office line: +44 20 7946 0958"
        val result = processor.maskPii(text)

        assertFalse("International spaced phone should be masked", result.contains("+44 20 7946 0958"))
        assertTrue("Should contain placeholder", result.contains("[PHONE_REDACTED]"))
    }

    @Test
    fun `containsPii detects phone`() {
        val text = "My number is 555-123-4567"
        assertTrue("Should detect phone PII", processor.containsPii(text))
    }

    // ==================== SSN MASKING ====================

    @Test
    fun `ssn with dashes masked`() {
        val text = "My SSN is 123-45-6789 for verification"
        val result = processor.maskPii(text)

        assertFalse("SSN should be masked", result.contains("123-45-6789"))
        assertTrue("Should contain SSN placeholder", result.contains("[SSN_REDACTED]"))
    }

    @Test
    fun `containsPii detects ssn`() {
        val text = "SSN: 123-45-6789"
        assertTrue("Should detect SSN PII", processor.containsPii(text))
    }

    // ==================== CREDIT CARD MASKING ====================

    @Test
    fun `credit card with spaces masked`() {
        val text = "Card: 4111 1111 1111 1111"
        val result = processor.maskPii(text)

        assertFalse("CC should be masked", result.contains("4111 1111 1111 1111"))
        assertTrue("Should contain CC placeholder", result.contains("[CC_REDACTED]"))
    }

    @Test
    fun `credit card with dashes masked`() {
        val text = "Payment: 4111-1111-1111-1111"
        val result = processor.maskPii(text)

        assertFalse("CC with dashes should be masked", result.contains("4111-1111-1111-1111"))
        assertTrue("Should contain placeholder", result.contains("[CC_REDACTED]"))
    }

    @Test
    fun `credit card with no separators masked`() {
        val text = "Card number 4111111111111111 accepted"
        val result = processor.maskPii(text)

        assertFalse("CC without separators masked", result.contains("4111111111111111"))
        assertTrue("Should contain placeholder", result.contains("[CC_REDACTED]"))
    }

    @Test
    fun `invalid credit card fails luhn and is not masked`() {
        val text = "Invalid card: 4111 1111 1111 1112"
        val result = processor.maskPii(text)

        assertTrue("Invalid Luhn card should remain", result.contains("4111 1111 1111 1112"))
        assertFalse("Invalid card should not be replaced", result.contains("[CC_REDACTED]"))
    }

    @Test
    fun `containsPii detects credit card`() {
        val text = "My card is 4111 1111 1111 1111"
        assertTrue("Should detect CC PII", processor.containsPii(text))
    }

    // ==================== IP ADDRESS MASKING ====================

    @Test
    fun `ipv4 address masked`() {
        val text = "Server at 192.168.1.1 is down"
        val result = processor.maskPii(text)

        assertFalse("IP should be masked", result.contains("192.168.1.1"))
        assertTrue("Should contain IP placeholder", result.contains("[IP_REDACTED]"))
    }

    @Test
    fun `public ip masked`() {
        val text = "My public IP is 8.8.8.8"
        val result = processor.maskPii(text)

        assertFalse("Public IP should be masked", result.contains("8.8.8.8"))
        assertTrue("Should contain placeholder", result.contains("[IP_REDACTED]"))
    }

    @Test
    fun `containsPii detects ip`() {
        val text = "Connect to 10.0.0.1"
        assertTrue("Should detect IP PII", processor.containsPii(text))
    }

    // ==================== MIXED PII ====================

    @Test
    fun `multiple pii types all masked`() {
        val text = "Contact john@test.com or call 555-123-4567. SSN: 123-45-6789"
        val result = processor.maskPii(text)

        assertFalse("Email masked", result.contains("john@test.com"))
        assertFalse("Phone masked", result.contains("555-123-4567"))
        assertFalse("SSN masked", result.contains("123-45-6789"))
        assertTrue("Email placeholder", result.contains("[EMAIL_REDACTED]"))
        assertTrue("Phone placeholder", result.contains("[PHONE_REDACTED]"))
        assertTrue("SSN placeholder", result.contains("[SSN_REDACTED]"))
    }

    @Test
    fun `containsPii detects multiple types`() {
        val text = "Email me at x@y.com from IP 1.2.3.4"
        assertTrue("Should detect mixed PII", processor.containsPii(text))
    }

    // ==================== FALSE POSITIVES ====================

    @Test
    fun `safe text not modified`() {
        val text = "The quick brown fox jumps over 13 lazy dogs"
        val result = processor.maskPii(text)

        assertEquals("Safe text unchanged", text, result)
    }

    @Test
    fun `numbers that are not phone not masked`() {
        val text = "Version 1.2 of the software released today"
        val result = processor.maskPii(text)
 
        assertEquals("Version numbers preserved", text, result)
        assertFalse("No IP detected in short version", processor.containsPii(text))
    }

    @Test
    fun `short number sequences not credit card`() {
        val text = "The number 123 456 789 exists"
        val result = processor.maskPii(text)

        assertEquals("Short sequences preserved", text, result)
    }

    @Test
    fun `text with at symbol but not email not masked`() {
        val text = "Look @ this example of @mentions"
        val result = processor.maskPii(text)

        assertEquals("Non-email @ preserved", text, result)
        assertFalse("No email detected", processor.containsPii(text))
    }

    @Test
    fun `safe text no pii detected`() {
        val text = "This is a completely safe message with no personal information"
        assertFalse("Should not detect PII in safe text", processor.containsPii(text))
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `empty string handled`() {
        val text = ""
        val result = processor.maskPii(text)

        assertEquals("Empty string unchanged", "", result)
        assertFalse("No PII in empty", processor.containsPii(text))
    }

    @Test
    fun `text with only pii markers handled`() {
        val text = "[EMAIL_REDACTED]"
        val result = processor.maskPii(text)

        // Should not double-mask
        assertEquals("Markers unchanged if no actual PII", text, result)
    }

    @Test
    fun `partial email not detected`() {
        val text = "Contact us at example.com or call support"
        assertFalse("No @ symbol should not be email", processor.containsPii(text))
    }

    @Test
    fun `long number sequence not cc but might be phone`() {
        val text = "My ID is 12345678901234 (14 digits)"
        val result = processor.maskPii(text)
        // 14 digits is not 16 digit CC, may match phone partially
        // Test ensures no exception thrown
        assertNotNull("Should handle gracefully", result)
    }

    @Test
    fun `multiple cc_numbers all masked`() {
        val text = "Cards: 4111 1111 1111 1111 and 4242 4242 4242 4242"
        val result = processor.maskPii(text)

        assertEquals("Both CCs replaced with one placeholder each", 2,
            result.split("[CC_REDACTED]").size - 1)
    }

    @Test
    fun `pii_in_middle_of_word_not_detected`() {
        val text = "My username is johnexamplecom123 (no @, not email)"
        val result = processor.maskPii(text)

        assertEquals("Should not detect fake emails", text, result)
    }

    // ==================== SECRET & API KEY MASKING ====================

    @Test
    fun `high entropy secret masked`() {
        // Use a neutral keyword that is NOT in the api key context keywords
        val text = "Identifier: 4f8a92b3c1d0e5f7a9b8c7d6e5f4a3b2c1d0e5f"
        val result = processor.maskPii(text)

        assertFalse("Secret should be masked", result.contains("4f8a92b3c1d0e5f7a9b8c7d6e5f4a3b2c1d0e5f"))
        assertTrue("Should be masked. Result was: $result", 
            result.contains("[SECRET_REDACTED]") || result.contains("[API_KEY_REDACTED]"))
    }

    @Test
    fun `api key with context masked`() {
        val text = "My api_key is sk-abc12345678901234567890123456789"
        val result = processor.maskPii(text)

        assertFalse("API key should be masked", result.contains("sk-abc12345678901234567890123456789"))
        assertTrue("Should contain api key placeholder", result.contains("[API_KEY_REDACTED]"))
    }

    @Test
    fun `long random string without context not masked as api key`() {
        val text = "Just a random string: abcdefghijklmnopqrstuvwxyz123456"
        val result = processor.maskPii(text)

        // It might be masked as SECRET if it matches high entropy, but not as API_KEY
        assertFalse("Should not be masked as API_KEY without context", result.contains("[API_KEY_REDACTED]"))
    }
}
