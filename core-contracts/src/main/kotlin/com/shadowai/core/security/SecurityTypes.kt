package com.shadowai.core.security

/**
 * Coarse-grained security posture levels used by SecurityManager configuration.
 */
enum class SecurityLevel {
    RELAXED,
    STANDARD,
    STRICT,
    PARANOID
}

/**
 * Threat categories used by SecurityManager records.
 */
enum class ThreatType {
    INSTRUCTION_HIJACKING,
    DATA_LEAK,
    PROMPT_INJECTION,
    PRIVILEGE_ESCALATION,
    UNKNOWN
}

/**
 * Source location for a detected threat.
 */
data class ThreatLocation(
    val startIndex: Int,
    val endIndex: Int,
    val context: String
)

/**
 * PII masking strategy options for SecurityManager configuration.
 */
enum class MaskingStrategy {
    REPLACE_WITH_TYPE,
    REPLACE_WITH_SYMBOLS,
    REPLACE_WITH_PLACEHOLDER,
    PARTIAL_MASKING,
    HASH_REPLACEMENT,
    REMOVE_ENTIRELY
}

/**
 * Configurable PII types for SecurityManager configuration.
 */
enum class PiiType {
    EMAIL,
    PHONE,
    CREDIT_CARD,
    SSN,
    API_KEY,
    IP_ADDRESS,
    HIGH_ENTROPY_SECRET
}

/**
 * Sensitivity levels used for threat severity mapping.
 */
enum class SensitivityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * PII masking configuration carried by SecurityManager configuration.
 */
data class MaskingConfiguration(
    val maskingStrategy: MaskingStrategy,
    val preserveFormat: Boolean,
    val customMaskingRules: Map<PiiType, String>,
    val enabledPiiTypes: Set<PiiType>,
    val minConfidenceThreshold: Float,
    val enableRealTimeMasking: Boolean
)
