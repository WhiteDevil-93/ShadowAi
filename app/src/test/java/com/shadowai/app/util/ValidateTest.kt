package com.shadowai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for Validate utility.
 */
class ValidateTest {

    @Test
    fun `notBlank should pass for non-blank string`() {
        val result = Validate.notBlank("hello", "field")
        assertEquals("hello", result)
    }

    @Test
    fun `notBlank should throw for blank string`() {
        try {
            Validate.notBlank("   ", "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must not be blank", e.message)
        }
    }

    @Test
    fun `notBlank should throw for null string`() {
        try {
            Validate.notBlank(null, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must not be blank", e.message)
        }
    }

    @Test
    fun `notNull should pass for non-null value`() {
        val result = Validate.notNull("value", "field")
        assertEquals("value", result)
    }

    @Test
    fun `notNull should throw for null value`() {
        try {
            Validate.notNull<String>(null, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must not be null", e.message)
        }
    }

    @Test
    fun `maxLength should pass for short string`() {
        val result = Validate.maxLength("hello", 10, "field")
        assertEquals("hello", result)
    }

    @Test
    fun `maxLength should throw for long string`() {
        try {
            Validate.maxLength("hello world", 5, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must be at most 5 characters (was 11)", e.message)
        }
    }

    @Test
    fun `minLength should pass for long enough string`() {
        val result = Validate.minLength("hello", 3, "field")
        assertEquals("hello", result)
    }

    @Test
    fun `minLength should throw for short string`() {
        try {
            Validate.minLength("hi", 5, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must be at least 5 characters (was 2)", e.message)
        }
    }

    @Test
    fun `lengthBetween should pass for valid length`() {
        val result = Validate.lengthBetween("hello", 3, 10, "field")
        assertEquals("hello", result)
    }

    @Test
    fun `positive should pass for positive number`() {
        val result = Validate.positive(5, "field")
        assertEquals(5, result)
    }

    @Test
    fun `positive should throw for zero`() {
        try {
            Validate.positive(0, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must be positive (was 0)", e.message)
        }
    }

    @Test
    fun `nonNegative should pass for zero`() {
        val result = Validate.nonNegative(0, "field")
        assertEquals(0, result)
    }

    @Test
    fun `nonNegative should throw for negative`() {
        try {
            Validate.nonNegative(-1, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must not be negative (was -1)", e.message)
        }
    }

    @Test
    fun `inRange should pass for value in range`() {
        val result = Validate.inRange(5, 1..10, "field")
        assertEquals(5, result)
    }

    @Test
    fun `inRange should throw for value out of range`() {
        try {
            Validate.inRange(15, 1..10, "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field must be in range 1..10 (was 15)", e.message)
        }
    }

    @Test
    fun `email should pass for valid email`() {
        val result = Validate.email("user@example.com")
        assertEquals("user@example.com", result)
    }

    @Test
    fun `email should throw for invalid email`() {
        try {
            Validate.email("not-an-email")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("email must be a valid email address", e.message)
        }
    }

    @Test
    fun `phoneNumber should pass for valid phone`() {
        val validPhones = listOf(
            "+1234567890",
            "123-456-7890",
            "(123) 456-7890",
            "+1 (234) 567-8901"
        )
        
        for (phone in validPhones) {
            val result = Validate.phoneNumber(phone)
            assertEquals(phone, result)
        }
    }

    @Test
    fun `phoneNumber should throw for invalid phone`() {
        try {
            Validate.phoneNumber("abc")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("phone must be a valid phone number", e.message)
        }
    }

    @Test
    fun `url should pass for valid URL`() {
        val result = Validate.url("https://example.com/path?query=1")
        assertEquals("https://example.com/path?query=1", result)
    }

    @Test
    fun `url should throw for invalid URL`() {
        try {
            Validate.url("not-a-url")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("url must be a valid URL", e.message)
        }
    }

    @Test
    fun `packageName should pass for valid package`() {
        val result = Validate.packageName("com.example.app")
        assertEquals("com.example.app", result)
    }

    @Test
    fun `packageName should throw for invalid package`() {
        try {
            Validate.packageName("Invalid_Package")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("package must be a valid Android package name", e.message)
        }
    }

    @Test
    fun `noSpecialChars should pass for safe string`() {
        val result = Validate.noSpecialChars("hello world 123", "field")
        assertEquals("hello world 123", result)
    }

    @Test
    fun `noSpecialChars should throw for dangerous chars`() {
        try {
            Validate.noSpecialChars("hello<script>", "field")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("field contains prohibited characters: <>", e.message)
        }
    }

    @Test
    fun `notEmpty collection should pass for non-empty list`() {
        val result = Validate.notEmpty(listOf(1, 2, 3), "items")
        assertEquals(3, result.size)
    }

    @Test
    fun `notEmpty collection should throw for empty list`() {
        try {
            Validate.notEmpty(emptyList<Int>(), "items")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("items must not be empty", e.message)
        }
    }

    @Test
    fun `matches should pass for matching pattern`() {
        val pattern = Regex("^[A-Z]{3}$")
        val result = Validate.matches("ABC", pattern, "code", "3-letter code")
        assertEquals("ABC", result)
    }

    @Test
    fun `matches should throw for non-matching`() {
        val pattern = Regex("^[A-Z]{3}$")
        try {
            Validate.matches("abcd", pattern, "code", "3-letter code")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("code must match 3-letter code", e.message)
        }
    }

    @Test
    fun `isTrue should pass for true condition`() {
        Validate.isTrue(1 == 1) { "Should be equal" }
    }

    @Test
    fun `isTrue should throw for false condition`() {
        try {
            Validate.isTrue(1 == 2) { "Values must be equal" }
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("Values must be equal", e.message)
        }
    }

    @Test
    fun `validated extension should wrap value`() {
        val validated = "test".validated()
        assertEquals("test", validated.value)
    }
}
