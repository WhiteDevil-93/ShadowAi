package com.shadowai.app.ai

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.util.regex.Pattern

/**
 * OkHttp interceptor for adding authentication headers.
 * 
 * Provides comprehensive sanitization to prevent:
 * - CRLF injection attacks (HTTP response splitting)
 * - Header injection via control characters
 * - Excessive header length attacks
 */
class AuthInterceptor(
    private val authHeaderProvider: () -> String?
) : Interceptor {
    companion object {
        private const val TAG = "AuthInterceptor"
        private const val MAX_HEADER_LENGTH = 500
        
        // Pattern for invalid header characters (control chars except HTAB)
        private val INVALID_HEADER_CHARS = Pattern.compile("[\\x00-\\x08\\x0A-\\x1F\\x7F]")
        
        // Pattern for detecting potential CRLF injection attempts
        private val CRLF_PATTERN = Pattern.compile("[\r\n]")
        
        // Valid authorization header prefix patterns
        private val VALID_PREFIXES = listOf("Bearer ", "Basic ", "ApiKey ", "Token ")
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        
        // Get auth header from provider
        val authHeader = authHeaderProvider()
        
        // If no auth header, proceed without modification
        if (authHeader.isNullOrBlank()) {
            return chain.proceed(original)
        }
        
        // Sanitize the auth header
        val sanitizedAuth = sanitizeAuthHeader(authHeader)
        
        // If sanitization failed or resulted in empty string, proceed without auth
        if (sanitizedAuth.isNullOrBlank()) {
            Log.w(TAG, "Auth header sanitization failed - proceeding without authentication")
            return chain.proceed(original)
        }
        
        // Build request with sanitized auth header
        val request = original.newBuilder()
            .header("Authorization", sanitizedAuth)
            .method(original.method, original.body)
            .build()
        
        return chain.proceed(request)
    }
    
    /**
     * Sanitize authorization header to prevent CRLF injection.
     * 
     * Removes:
     * - Carriage return (\r) and line feed (\n) characters
     * - Other control characters that could be used for injection
     * - Excessively long headers
     * 
     * @param rawHeader The raw authorization header from provider
     * @return Sanitized header, or null if sanitization failed
     */
    private fun sanitizeAuthHeader(rawHeader: String): String? {
        // Check for null/empty
        if (rawHeader.isNullOrBlank()) {
            return null
        }
        
        // Length validation - prevent memory exhaustion
        if (rawHeader.length > MAX_HEADER_LENGTH) {
            Log.w(TAG, "Auth header too long: ${rawHeader.length} chars, max is $MAX_HEADER_LENGTH")
            return null
        }
        
        // Detect CRLF injection attempts
        if (CRLF_PATTERN.matcher(rawHeader).find()) {
            Log.w(TAG, "CRLF characters detected in auth header - possible injection attempt")
            return null
        }
        
        // Remove control characters
        val cleanHeader = INVALID_HEADER_CHARS.matcher(rawHeader).replaceAll("")
        
        // Validate header format - must start with valid prefix
        val hasValidPrefix = VALID_PREFIXES.any { cleanHeader.startsWith(it) }
        
        if (!hasValidPrefix && !cleanHeader.startsWith("Bearer") && 
            !cleanHeader.startsWith("Basic") && !cleanHeader.startsWith("ApiKey") &&
            !cleanHeader.startsWith("Token")) {
            // Allow through but log warning - might be custom auth
            Log.d(TAG, "Auth header doesn't match known prefix patterns")
        }
        
        // Final validation
        return if (cleanHeader.isNotBlank() && cleanHeader.length <= MAX_HEADER_LENGTH) {
            cleanHeader
        } else {
            null
        }
    }
}
