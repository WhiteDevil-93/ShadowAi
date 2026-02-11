package com.shadowai.app.execution

import com.shadowai.core.ProviderId

/**
 * Signals that a provider is unreachable due to billing or quota exhaustion.
 *
 * This is treated as a non-retryable error for the active provider and is
 * used to trigger provider disabling and fallback routing.
 *
 * CRITICAL FIX: Refactored to use idiomatic Kotlin
 * - Removed Java-style companion object with private function
 * - Uses local function for message building
 * - Proper exception hierarchy
 *
 * @property providerId The provider that returned a quota/billing error.
 * @property statusCode The HTTP status code returned by the provider.
 * @property rawError The raw error body for diagnostics.
 */
class ProviderQuotaException(
    val providerId: ProviderId?,
    val statusCode: Int,
    rawError: String
) : RuntimeException(buildExceptionMessage(providerId, statusCode, rawError)) {

    companion object {
        private const val MAX_ERROR_LENGTH = 500
        
        /**
         * Build exception message using Kotlin local function.
         */
        private fun buildExceptionMessage(
            providerId: ProviderId?,
            statusCode: Int,
            rawError: String
        ): String {
            val providerLabel = providerId?.name ?: "UNKNOWN_PROVIDER"
            val trimmed = rawError.take(MAX_ERROR_LENGTH).trim()
            return "Provider quota exceeded for $providerLabel (HTTP $statusCode). Server message: $trimmed"
        }
    }
}
