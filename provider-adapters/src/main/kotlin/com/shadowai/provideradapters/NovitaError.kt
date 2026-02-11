package com.shadowai.provideradapters

/**
 * Structured Novita API error response wrapper.
 */
data class NovitaApiErrorWrapper(
    val error: NovitaApiError?,
    val status: String? = null,
    val code: Int? = null,
    val message: String? = null
)

/**
 * Individual Novita API error details.
 */
data class NovitaApiError(
    val message: String?,
    val code: String?,
    val type: String? = null,
    val param: String? = null
)

/**
 * Fallback simple error format for parsing.
 */
data class NovitaSimpleError(
    val message: String?,
    val code: String?,
    val error: String?
)
