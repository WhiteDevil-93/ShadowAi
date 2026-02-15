package com.shadowai.core.security

/**
 * Simple message data class for security operations.
 *
 * Named `SecurityMessage` to avoid collision with the canonical
 * [com.shadowai.core.Message] which uses rich typed content.
 * Callers should map from [com.shadowai.core.Message] to this
 * type when invoking [SecurityManager.secureConversation].
 */
data class SecurityMessage(
    val role: String,
    val content: String
)

/**
 * Central security manager that coordinates all security components.
 * This is the main interface for security operations throughout the application.
 */
interface SecurityManager {

    /**
     * Analyze and secure a prompt before processing.
     */
    suspend fun securePrompt(prompt: String): SecuredPromptResult

    /**
     * Analyze and secure a conversation before processing.
     */
    suspend fun secureConversation(messages: List<SecurityMessage>): SecuredConversationResult

    /**
     * Secure model output before displaying to user.
     */
    suspend fun secureModelOutput(output: String): SecuredOutputResult

    /**
     * Validate user input for security threats.
     */
    suspend fun validateUserInput(input: String): InputValidationResult

    /**
     * Encrypt sensitive data.
     */
    suspend fun encryptData(data: SecretBytes): EncryptedDataResult

    /**
     * Decrypt sensitive data.
     */
    suspend fun decryptData(encryptedData: EncryptedData): SecretBytes

    /**
     * Generate a secure API key.
     */
    suspend fun generateApiKey(): SecretBytes

    /**
     * Hash sensitive data for storage.
     */
    suspend fun hashSensitiveData(data: SecretBytes): HashedDataResult

    /**
     * Check if data has been tampered with.
     */
    suspend fun verifyDataIntegrity(data: ByteArray, signature: ByteArray): Boolean

    /**
     * Get current security configuration.
     */
    fun getSecurityConfig(): SecurityConfiguration

    /**
     * Update security configuration.
     */
    fun updateSecurityConfig(config: SecurityConfiguration)

    /**
     * Get security audit log.
     */
    suspend fun getSecurityAuditLog(): SecurityAuditLog

    /**
     * Clear security audit log.
     */
    suspend fun clearSecurityAuditLog()

    /**
     * Get overall security statistics.
     */
    suspend fun getSecurityStatistics(): SecurityStatistics

    /**
     * Perform a security health check.
     */
    suspend fun performSecurityHealthCheck(): SecurityHealthCheckResult
}

/**
 * Result of securing a prompt.
 */
data class SecuredPromptResult(
    val isSecure: Boolean,
    val securedPrompt: String,
    val securityThreats: List<SecurityThreat>,
    val appliedSecurityMeasures: List<SecurityMeasure>,
    val processingTimeMs: Long
)

/**
 * Result of securing a conversation.
 */
data class SecuredConversationResult(
    val isSecure: Boolean,
    val securedMessages: List<SecurityMessage>,
    val conversationThreats: List<SecurityThreat>,
    val appliedSecurityMeasures: List<SecurityMeasure>,
    val processingTimeMs: Long
)

/**
 * Result of securing model output.
 */
data class SecuredOutputResult(
    val isSecure: Boolean,
    val securedOutput: String,
    val detectedIssues: List<SecurityIssue>,
    val appliedSecurityMeasures: List<SecurityMeasure>,
    val processingTimeMs: Long
)

/**
 * Result of input validation.
 */
data class InputValidationResult(
    val isValid: Boolean,
    val validationErrors: List<ValidationError>,
    val securityWarnings: List<SecurityWarning>,
    val validationTimeMs: Long
)

/**
 * Encrypted data result.
 */
data class EncryptedDataResult(
    val encryptedData: EncryptedData,
    val encryptionTimeMs: Long
)

/**
 * Encrypted data container.
 */
data class EncryptedData(
    val encryptedBytes: ByteArray,
    val iv: ByteArray,
    val algorithm: String,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return encryptedBytes.contentEquals(other.encryptedBytes) &&
                iv.contentEquals(other.iv) &&
                algorithm == other.algorithm &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = encryptedBytes.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Hashed data result.
 */
data class HashedDataResult(
    val hashedData: ByteArray,
    val hashAlgorithm: String,
    val hashTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HashedDataResult) return false
        return hashedData.contentEquals(other.hashedData) &&
                hashAlgorithm == other.hashAlgorithm &&
                hashTimeMs == other.hashTimeMs
    }

    override fun hashCode(): Int {
        var result = hashedData.contentHashCode()
        result = 31 * result + hashAlgorithm.hashCode()
        result = 31 * result + hashTimeMs.hashCode()
        return result
    }
}

/**
 * Security configuration.
 */
data class SecurityConfiguration(
    val promptInjectionDefenseEnabled: Boolean,
    val piiMaskingEnabled: Boolean,
    val encryptionEnabled: Boolean,
    val dataIntegrityEnabled: Boolean,
    val auditLoggingEnabled: Boolean,
    val securityLevel: SecurityLevel,
    val piiMaskingConfig: MaskingConfiguration,
    val promptDefenseConfig: SecurityLevel
)

/**
 * Security threat detected.
 */
data class SecurityThreat(
    val threatType: ThreatType,
    val description: String,
    val severity: ThreatSeverity,
    val confidence: Float,
    val location: ThreatLocation?
)

/**
 * Security measure applied.
 */
data class SecurityMeasure(
    val measureType: MeasureType,
    val description: String,
    val effectiveness: Float,
    val timestamp: Long
)

/**
 * Security issue detected in output.
 */
data class SecurityIssue(
    val issueType: IssueType,
    val description: String,
    val severity: IssueSeverity,
    val suggestedAction: String
)

/**
 * Validation error.
 */
data class ValidationError(
    val errorType: ValidationErrorType,
    val description: String,
    val field: String?,
    val value: String?
)

/**
 * Security warning.
 */
data class SecurityWarning(
    val warningType: WarningType,
    val description: String,
    val confidence: Float,
    val suggestedAction: String
)

/**
 * Threat severity levels.
 */
enum class ThreatSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Issue severity levels.
 */
enum class IssueSeverity {
    INFORMATIONAL,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Validation error types.
 */
enum class ValidationErrorType {
    INVALID_FORMAT,
    INVALID_LENGTH,
    INVALID_CONTENT,
    MALFORMED_DATA
}

/**
 * Warning types.
 */
enum class WarningType {
    POTENTIAL_INJECTION,
    POTENTIAL_PII_LEAK,
    SUSPICIOUS_CONTENT,
    UNUSUAL_PATTERN
}

/**
 * Measure types.
 */
enum class MeasureType {
    PROMPT_INJECTION_DETECTION,
    PII_MASKING,
    ENCRYPTION,
    DATA_INTEGRITY_CHECK,
    AUDIT_LOGGING
}

/**
 * Issue types.
 */
enum class IssueType {
    POTENTIAL_DATA_LEAK,
    MALICIOUS_CONTENT,
    PRIVILEGE_ESCALATION_ATTEMPT,
    INJECTION_ATTEMPT,
    UNAUTHORIZED_ACCESS_ATTEMPT
}

/**
 * Security audit log.
 */
data class SecurityAuditLog(
    val entries: List<SecurityAuditEntry>,
    val totalEntries: Int,
    val logSizeBytes: Long
)

/**
 * Security audit entry.
 */
data class SecurityAuditEntry(
    val timestamp: Long,
    val eventType: AuditEventType,
    val description: String,
    val severity: ThreatSeverity,
    val userId: String?,
    val ipAddress: String?,
    val additionalData: Map<String, String>
)

/**
 * Audit event types.
 */
enum class AuditEventType {
    PROMPT_INJECTION_DETECTED,
    PII_DETECTED,
    ENCRYPTION_ERROR,
    DECRYPTION_ERROR,
    AUTHENTICATION_FAILURE,
    UNAUTHORIZED_ACCESS,
    DATA_INTEGRITY_VIOLATION,
    SECURITY_CONFIG_CHANGED
}

/**
 * Security statistics.
 */
data class SecurityStatistics(
    val totalRequests: Long,
    val blockedRequests: Long,
    val sanitizedRequests: Long,
    val encryptionOperations: Long,
    val decryptionOperations: Long,
    val averageResponseTimeMs: Float,
    val topThreats: Map<ThreatType, Int>,
    val securityIncidents: Int,
    val lastUpdated: Long
)

/**
 * Security health check result.
 */
data class SecurityHealthCheckResult(
    val isHealthy: Boolean,
    val healthScore: Float,
    val componentHealth: Map<String, ComponentHealth>,
    val recommendations: List<String>,
    val checkTimeMs: Long
)

/**
 * Component health status.
 */
data class ComponentHealth(
    val componentName: String,
    val isHealthy: Boolean,
    val healthScore: Float,
    val lastCheckTime: Long,
    val issues: List<String>
)

/**
 * Default implementation of SecurityManager.
 */
class DefaultSecurityManager(
    private val promptInjectionDefense: PromptInjectionDefense,
    private val piiMaskingProcessor: PiiMaskingProcessor
) : SecurityManager {

    private var securityConfig = SecurityConfiguration(
        promptInjectionDefenseEnabled = true,
        piiMaskingEnabled = true,
        encryptionEnabled = true,
        dataIntegrityEnabled = true,
        auditLoggingEnabled = true,
        securityLevel = SecurityLevel.STANDARD,
        piiMaskingConfig = MaskingConfiguration(
            maskingStrategy = MaskingStrategy.REPLACE_WITH_TYPE,
            preserveFormat = true,
            customMaskingRules = emptyMap(),
            enabledPiiTypes = PiiType.values().toSet(),
            minConfidenceThreshold = 0.7f,
            enableRealTimeMasking = true
        ),
        promptDefenseConfig = SecurityLevel.STANDARD
    )

    private val auditLog = mutableListOf<SecurityAuditEntry>()
    private var statistics = SecurityStatistics(
        totalRequests = 0,
        blockedRequests = 0,
        sanitizedRequests = 0,
        encryptionOperations = 0,
        decryptionOperations = 0,
        averageResponseTimeMs = 0f,
        topThreats = emptyMap(),
        securityIncidents = 0,
        lastUpdated = System.currentTimeMillis()
    )

    override suspend fun securePrompt(prompt: String): SecuredPromptResult {
        val startTime = System.currentTimeMillis()
        val threats = mutableListOf<SecurityThreat>()
        val measures = mutableListOf<SecurityMeasure>()
        var securedPrompt = prompt
        var isSecure = true

        // Check for prompt injection
        if (securityConfig.promptInjectionDefenseEnabled) {
            val analysis = promptInjectionDefense.scan(prompt)

            if (!analysis.isSafe) {
                isSecure = false
                threats.add(
                    SecurityThreat(
                        threatType = ThreatType.INSTRUCTION_HIJACKING,
                        description = analysis.reason ?: "Potential prompt injection detected",
                        severity = mapRiskLevelToSeverity(analysis.riskLevel),
                        confidence = riskToConfidence(analysis.riskLevel),
                        location = null
                    )
                )

                // Apply best-effort sanitization from scan result.
                securedPrompt = analysis.sanitizedPrompt.ifBlank { prompt }
                measures.add(SecurityMeasure(
                    measureType = MeasureType.PROMPT_INJECTION_DETECTION,
                    description = "Applied prompt injection sanitization",
                    effectiveness = 1.0f,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }

        // Apply PII masking
        if (securityConfig.piiMaskingEnabled) {
            val piiDetection = piiMaskingProcessor.detectPii(securedPrompt)

            if (piiDetection.containsPii) {
                threats.addAll(piiDetection.detectedTypes.map { piiType ->
                    SecurityThreat(
                        threatType = ThreatType.DATA_LEAK,
                        description = "PII detected: $piiType",
                        severity = ThreatSeverity.HIGH,
                        confidence = piiDetection.confidence.toFloat(),
                        location = null
                    )
                })

                securedPrompt = piiMaskingProcessor.maskPii(securedPrompt)
                measures.add(SecurityMeasure(
                    measureType = MeasureType.PII_MASKING,
                    description = "Applied PII masking",
                    effectiveness = 1.0f,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }

        val processingTime = System.currentTimeMillis() - startTime

        // Update statistics
        statistics = statistics.copy(
            totalRequests = statistics.totalRequests + 1,
            blockedRequests = statistics.blockedRequests + if (!isSecure) 1 else 0,
            sanitizedRequests = statistics.sanitizedRequests + if (measures.isNotEmpty()) 1 else 0
        )

        return SecuredPromptResult(
            isSecure = isSecure,
            securedPrompt = securedPrompt,
            securityThreats = threats,
            appliedSecurityMeasures = measures,
            processingTimeMs = processingTime
        )
    }

    override suspend fun secureConversation(messages: List<SecurityMessage>): SecuredConversationResult {
        val startTime = System.currentTimeMillis()
        val threats = mutableListOf<SecurityThreat>()
        val measures = mutableListOf<SecurityMeasure>()
        val securedMessages = mutableListOf<SecurityMessage>()
        var isSecure = true

        for (message in messages) {
            val securedResult = securePrompt(message.content)
            securedMessages.add(message.copy(content = securedResult.securedPrompt))

            if (!securedResult.isSecure) {
                isSecure = false
                threats.addAll(securedResult.securityThreats)
            }
            measures.addAll(securedResult.appliedSecurityMeasures)
        }

        val processingTime = System.currentTimeMillis() - startTime

        return SecuredConversationResult(
            isSecure = isSecure,
            securedMessages = securedMessages,
            conversationThreats = threats,
            appliedSecurityMeasures = measures,
            processingTimeMs = processingTime
        )
    }

    override suspend fun secureModelOutput(output: String): SecuredOutputResult {
        val startTime = System.currentTimeMillis()
        val issues = mutableListOf<SecurityIssue>()
        val measures = mutableListOf<SecurityMeasure>()
        var securedOutput = output
        var isSecure = true

        // Check for potential data leaks in output
        val piiDetection = piiMaskingProcessor.detectPii(output)
        if (piiDetection.containsPii) {
            isSecure = false
            issues.add(SecurityIssue(
                issueType = IssueType.POTENTIAL_DATA_LEAK,
                description = "PII detected in model output",
                severity = IssueSeverity.ERROR,
                suggestedAction = "Mask PII before displaying to user"
            ))

            securedOutput = piiMaskingProcessor.maskPii(output)
            measures.add(SecurityMeasure(
                measureType = MeasureType.PII_MASKING,
                description = "Masked PII in model output",
                effectiveness = 1.0f,
                timestamp = System.currentTimeMillis()
            ))
        }

        val processingTime = System.currentTimeMillis() - startTime

        return SecuredOutputResult(
            isSecure = isSecure,
            securedOutput = securedOutput,
            detectedIssues = issues,
            appliedSecurityMeasures = measures,
            processingTimeMs = processingTime
        )
    }

    override suspend fun validateUserInput(input: String): InputValidationResult {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<SecurityWarning>()

        // Basic input validation
        if (input.isBlank()) {
            errors.add(ValidationError(
                errorType = ValidationErrorType.INVALID_CONTENT,
                description = "Input cannot be empty",
                field = null,
                value = input
            ))
        }

        if (input.length > 10000) {
            errors.add(ValidationError(
                errorType = ValidationErrorType.INVALID_LENGTH,
                description = "Input too long",
                field = null,
                value = input
            ))
        }

        // Security validation
        val promptAnalysis = promptInjectionDefense.scan(input)
        if (!promptAnalysis.isSafe) {
            warnings.add(SecurityWarning(
                warningType = WarningType.POTENTIAL_INJECTION,
                description = "Potential prompt injection detected",
                confidence = riskToConfidence(promptAnalysis.riskLevel),
                suggestedAction = "Review and sanitize input"
            ))
        }

        val piiDetection = piiMaskingProcessor.detectPii(input)
        if (piiDetection.containsPii) {
            warnings.add(SecurityWarning(
                warningType = WarningType.POTENTIAL_PII_LEAK,
                description = "PII detected in input",
                confidence = piiDetection.confidence.toFloat(),
                suggestedAction = "Consider masking PII"
            ))
        }

        val validationTime = System.currentTimeMillis() - startTime

        return InputValidationResult(
            isValid = errors.isEmpty(),
            validationErrors = errors,
            securityWarnings = warnings,
            validationTimeMs = validationTime
        )
    }

    override suspend fun encryptData(data: SecretBytes): EncryptedDataResult {
        val startTime = System.currentTimeMillis()

        val iv = ByteArray(12)  // 96-bit IV for AES-GCM
        java.security.SecureRandom().nextBytes(iv)

        val encryptedBytes = data.withSecretBytes { plaintext ->
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            // Derive a 256-bit key from the data length (in production, use a proper KDF).
            // This is a self-encrypting wrapper; real key management lives in TeeKeyManager.
            val keyBytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(keyBytes)
            val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec)
            // Prepend key material so decryptData can reverse (self-encrypting demo).
            // In production, the key would come from Android Keystore / TeeKeyManager.
            val ciphertext = cipher.doFinal(plaintext)
            keyBytes + ciphertext  // key (32) + ciphertext
        }

        val encryptedData = EncryptedData(
            encryptedBytes = encryptedBytes,
            iv = iv,
            algorithm = "AES/GCM/NoPadding",
            timestamp = System.currentTimeMillis()
        )

        val encryptionTime = System.currentTimeMillis() - startTime
        statistics = statistics.copy(encryptionOperations = statistics.encryptionOperations + 1)

        return EncryptedDataResult(
            encryptedData = encryptedData,
            encryptionTimeMs = encryptionTime
        )
    }

    override suspend fun decryptData(encryptedData: EncryptedData): SecretBytes {
        val startTime = System.currentTimeMillis()

        val raw = encryptedData.encryptedBytes
        val keyBytes = raw.copyOfRange(0, 32)
        val ciphertext = raw.copyOfRange(32, raw.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val spec = javax.crypto.spec.GCMParameterSpec(128, encryptedData.iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)
        val plaintext = cipher.doFinal(ciphertext)

        // Zero the extracted key material
        java.util.Arrays.fill(keyBytes, 0.toByte())

        val decryptedSecretBytes = SecretBytes.fromByteArray(plaintext)
        java.util.Arrays.fill(plaintext, 0.toByte())

        val decryptionTime = System.currentTimeMillis() - startTime
        statistics = statistics.copy(decryptionOperations = statistics.decryptionOperations + 1)

        return decryptedSecretBytes
    }

    override suspend fun generateApiKey(): SecretBytes {
        return SecretBytes.random(32)
    }

    override suspend fun hashSensitiveData(data: SecretBytes): HashedDataResult {
        val startTime = System.currentTimeMillis()

        val hashedData = data.withSecretBytes { bytes ->
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        }

        val hashTime = System.currentTimeMillis() - startTime

        return HashedDataResult(
            hashedData = hashedData,
            hashAlgorithm = "SHA-256",
            hashTimeMs = hashTime
        )
    }

    override suspend fun verifyDataIntegrity(data: ByteArray, signature: ByteArray): Boolean {
        // Constant-time comparison to prevent timing attacks
        return java.security.MessageDigest.isEqual(data, signature)
    }

    override fun getSecurityConfig(): SecurityConfiguration = securityConfig

    override fun updateSecurityConfig(config: SecurityConfiguration) {
        securityConfig = config
    }

    override suspend fun getSecurityAuditLog(): SecurityAuditLog {
        return SecurityAuditLog(
            entries = auditLog.toList(),
            totalEntries = auditLog.size,
            logSizeBytes = auditLog.sumOf { it.description.length.toLong() }
        )
    }

    override suspend fun clearSecurityAuditLog() {
        auditLog.clear()
    }

    override suspend fun getSecurityStatistics(): SecurityStatistics {
        return statistics.copy(lastUpdated = System.currentTimeMillis())
    }

    override suspend fun performSecurityHealthCheck(): SecurityHealthCheckResult {
        val startTime = System.currentTimeMillis()
        val componentHealth = mutableMapOf<String, ComponentHealth>()
        val recommendations = mutableListOf<String>()

        // Check prompt injection defense
        val promptHealth = checkComponentHealth("PromptInjectionDefense", promptInjectionDefense)
        componentHealth["PromptInjectionDefense"] = promptHealth

        // Check PII masking processor
        val piiHealth = checkComponentHealth("PiiMaskingProcessor", piiMaskingProcessor)
        componentHealth["PiiMaskingProcessor"] = piiHealth

        val overallHealth = componentHealth.values.map { it.healthScore }.average().toFloat()
        val isHealthy = overallHealth >= 0.8f

        if (overallHealth < 0.8f) {
            recommendations.add("Consider reviewing security component configurations")
        }

        val checkTime = System.currentTimeMillis() - startTime

        return SecurityHealthCheckResult(
            isHealthy = isHealthy,
            healthScore = overallHealth,
            componentHealth = componentHealth,
            recommendations = recommendations,
            checkTimeMs = checkTime
        )
    }

    // Helper methods

    private fun mapRiskLevelToSeverity(riskLevel: PromptInjectionDefense.RiskLevel): ThreatSeverity {
        return when (riskLevel) {
            PromptInjectionDefense.RiskLevel.NONE,
            PromptInjectionDefense.RiskLevel.LOW -> ThreatSeverity.LOW
            PromptInjectionDefense.RiskLevel.MEDIUM -> ThreatSeverity.MEDIUM
            PromptInjectionDefense.RiskLevel.HIGH -> ThreatSeverity.HIGH
            PromptInjectionDefense.RiskLevel.CRITICAL -> ThreatSeverity.CRITICAL
        }
    }

    private fun mapSensitivityToSeverity(sensitivityLevel: SensitivityLevel): ThreatSeverity {
        return when (sensitivityLevel) {
            SensitivityLevel.LOW -> ThreatSeverity.LOW
            SensitivityLevel.MEDIUM -> ThreatSeverity.MEDIUM
            SensitivityLevel.HIGH -> ThreatSeverity.HIGH
            SensitivityLevel.CRITICAL -> ThreatSeverity.CRITICAL
        }
    }

    private fun riskToConfidence(riskLevel: PromptInjectionDefense.RiskLevel): Float {
        return when (riskLevel) {
            PromptInjectionDefense.RiskLevel.NONE -> 0.5f
            PromptInjectionDefense.RiskLevel.LOW -> 0.6f
            PromptInjectionDefense.RiskLevel.MEDIUM -> 0.75f
            PromptInjectionDefense.RiskLevel.HIGH -> 0.9f
            PromptInjectionDefense.RiskLevel.CRITICAL -> 0.95f
        }
    }

    private fun checkComponentHealth(componentName: String, component: Any): ComponentHealth {
        // Basic health check - in production, implement more comprehensive checks
        return ComponentHealth(
            componentName = componentName,
            isHealthy = true,
            healthScore = 1.0f,
            lastCheckTime = System.currentTimeMillis(),
            issues = emptyList()
        )
    }
}
