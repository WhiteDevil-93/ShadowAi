package com.shadowai.app.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Input validation utilities for defensive programming.
 * 
 * Provides validation functions that throw descriptive exceptions
 * when conditions are not met, helping catch bugs early.
 * 
 * Usage:
 * ```kotlin
 * fun processMessage(message: String, userId: String) {
 *     Validate.notBlank(message, "message")
 *     Validate.notBlank(userId, "userId")
 *     Validate.maxLength(message, 10000, "message")
 *     
 *     // Process validated input...
 * }
 * ```
 */
object Validate {

    /**
     * Validates that a string is not null or blank.
     * 
     * @throws IllegalArgumentException if the value is null or blank
     */
    @OptIn(ExperimentalContracts::class)
    fun notBlank(value: String?, fieldName: String): String {
        contract {
            returns() implies (value != null)
        }
        require(!value.isNullOrBlank()) {
            "$fieldName must not be blank"
        }
        return value
    }

    /**
     * Validates that a string is not null or empty.
     */
    @OptIn(ExperimentalContracts::class)
    fun notEmpty(value: String?, fieldName: String): String {
        contract {
            returns() implies (value != null)
        }
        require(!value.isNullOrEmpty()) {
            "$fieldName must not be empty"
        }
        return value
    }

    /**
     * Validates that a value is not null.
     */
    @OptIn(ExperimentalContracts::class)
    fun <T : Any> notNull(value: T?, fieldName: String): T {
        contract {
            returns() implies (value != null)
        }
        requireNotNull(value) {
            "$fieldName must not be null"
        }
        return value
    }

    /**
     * Validates that a string length is within a maximum.
     */
    fun maxLength(value: String, maxLength: Int, fieldName: String): String {
        require(value.length <= maxLength) {
            "$fieldName must be at most $maxLength characters (was ${value.length})"
        }
        return value
    }

    /**
     * Validates that a string length is at least a minimum.
     */
    fun minLength(value: String, minLength: Int, fieldName: String): String {
        require(value.length >= minLength) {
            "$fieldName must be at least $minLength characters (was ${value.length})"
        }
        return value
    }

    /**
     * Validates that a string length is within a range.
     */
    fun lengthBetween(value: String, minLength: Int, maxLength: Int, fieldName: String): String {
        require(value.length in minLength..maxLength) {
            "$fieldName must be between $minLength and $maxLength characters (was ${value.length})"
        }
        return value
    }

    /**
     * Validates that a collection is not empty.
     */
    fun <T> notEmpty(collection: Collection<T>?, fieldName: String): Collection<T> {
        require(!collection.isNullOrEmpty()) {
            "$fieldName must not be empty"
        }
        return collection
    }

    /**
     * Validates that a number is positive.
     */
    fun positive(value: Int, fieldName: String): Int {
        require(value > 0) {
            "$fieldName must be positive (was $value)"
        }
        return value
    }

    /**
     * Validates that a number is non-negative.
     */
    fun nonNegative(value: Int, fieldName: String): Int {
        require(value >= 0) {
            "$fieldName must not be negative (was $value)"
        }
        return value
    }

    /**
     * Validates that a number is within a range.
     */
    fun inRange(value: Int, range: IntRange, fieldName: String): Int {
        require(value in range) {
            "$fieldName must be in range $range (was $value)"
        }
        return value
    }

    /**
     * Validates that a double is within a range.
     */
    fun inRange(value: Double, min: Double, max: Double, fieldName: String): Double {
        require(value in min..max) {
            "$fieldName must be between $min and $max (was $value)"
        }
        return value
    }

    /**
     * Validates that a string matches a regex pattern.
     */
    fun matches(value: String, pattern: Regex, fieldName: String, patternName: String = "pattern"): String {
        require(pattern.matches(value)) {
            "$fieldName must match $patternName"
        }
        return value
    }

    /**
     * Validates an email address format.
     */
    fun email(value: String, fieldName: String = "email"): String {
        val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        require(emailPattern.matches(value)) {
            "$fieldName must be a valid email address"
        }
        return value
    }

    /**
     * Validates a phone number format (basic international).
     */
    fun phoneNumber(value: String, fieldName: String = "phone"): String {
        val phonePattern = Regex("^\\+?[0-9\\s\\-().]{7,20}$")
        require(phonePattern.matches(value)) {
            "$fieldName must be a valid phone number"
        }
        return value
    }

    /**
     * Validates a URL format.
     */
    fun url(value: String, fieldName: String = "url"): String {
        val urlPattern = Regex("^https?://[^\\s/$.?#].[^\\s]*$")
        require(urlPattern.matches(value)) {
            "$fieldName must be a valid URL"
        }
        return value
    }

    /**
     * Validates an Android package name format.
     */
    fun packageName(value: String, fieldName: String = "package"): String {
        val packagePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        require(packagePattern.matches(value)) {
            "$fieldName must be a valid Android package name"
        }
        return value
    }

    /**
     * Validates that a string doesn't contain dangerous characters.
     * Useful for preventing injection attacks.
     */
    fun noSpecialChars(value: String, fieldName: String, allowedChars: String = ""): String {
        val dangerousChars = setOf('<', '>', '"', '\'', '\\', '/', '|', '&', ';', '`') - allowedChars.toSet()
        val foundDangerous = value.filter { it in dangerousChars }
        require(foundDangerous.isEmpty()) {
            "$fieldName contains prohibited characters: $foundDangerous"
        }
        return value
    }

    /**
     * Validates that a condition is true.
     */
    fun isTrue(condition: Boolean, message: () -> String) {
        require(condition, message)
    }

    /**
     * Validates that a condition is false.
     */
    fun isFalse(condition: Boolean, message: () -> String) {
        require(!condition, message)
    }
}

/**
 * Validated wrapper that ensures a value has been validated.
 */
@JvmInline
value class Validated<T>(val value: T)

/**
 * Extension to mark a value as validated.
 */
fun <T> T.validated(): Validated<T> = Validated(this)
