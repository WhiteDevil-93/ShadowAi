package com.shadowai.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Simple data class for testing Message type.
 */
data class Message(
    val role: String,
    val content: String
)

/**
 * Test class for SecurityManager implementation.
 */
class SecurityManagerTest {
    
    private lateinit var securityManager: DefaultSecurityManager
    
    @Before
    fun setUp() {
        securityManager = DefaultSecurityManager(
            promptInjectionDefense = PromptInjectionDefense(),
            piiMaskingProcessor = PiiMaskingProcessor()
        )
    }
    
    @Test
    fun `securePrompt - should return secure result when no threats detected`() = runBlocking {
        // Given
        val prompt = "What is the capital of France?"
        
        // When
        val result = securityManager.securePrompt(prompt)
        
        // Then
        assert(result.isSecure)
        assert(result.securedPrompt == prompt)
        assert(result.processingTimeMs >= 0)
    }
    
    @Test
    fun `secureConversation - should process multiple messages`() = runBlocking {
        // Given
        val messages = listOf(
            Message("user", "Hello"),
            Message("assistant", "Hi there"),
            Message("user", "How are you?")
        )
        
        // When
        val result = securityManager.secureConversation(messages)
        
        // Then
        assert(result.securedMessages.size == 3)
        assert(result.processingTimeMs >= 0)
    }
    
    @Test
    fun `secureModelOutput - should process output`() = runBlocking {
        // Given
        val output = "The answer is 42"
        
        // When
        val result = securityManager.secureModelOutput(output)
        
        // Then
        assert(result.securedOutput == output)
        assert(result.processingTimeMs >= 0)
    }
    
    @Test
    fun `validateUserInput - should validate input correctly`() = runBlocking {
        // Given
        val validInput = "This is a valid input"
        val emptyInput = ""
        val longInput = "A".repeat(15000)
        
        // When & Then
        val validResult = securityManager.validateUserInput(validInput)
        assert(validResult.isValid)
        
        val emptyResult = securityManager.validateUserInput(emptyInput)
        assert(!emptyResult.isValid)
        
        val longResult = securityManager.validateUserInput(longInput)
        assert(!longResult.isValid)
    }
    
    @Test
    fun `encryptData - should encrypt data successfully`() = runBlocking {
        // Given
        val data = SecretBytes(16)
        data.fill { it -> it.indices.forEach { i -> it[i] = i.toByte() } }
        
        // When
        val result = securityManager.encryptData(data)
        
        // Then
        assert(result.encryptedData.encryptedBytes.isNotEmpty())
        assert(result.encryptedData.iv.isNotEmpty())
        assert(result.encryptedData.algorithm == "XOR_ENCRYPTION")
        assert(result.encryptionTimeMs >= 0)
        data.dispose()
    }
    
    @Test
    fun `decryptData - should decrypt data successfully`() = runBlocking {
        // Given
        val data = SecretBytes(16)
        data.fill { it -> it.indices.forEach { i -> it[i] = i.toByte() } }
        
        val encryptedResult = securityManager.encryptData(data)
        
        // When
        val decryptedData = securityManager.decryptData(encryptedResult.encryptedData)
        
        // Then
        val originalBytes = data.copy()
        val decryptedBytes = decryptedData.copy()
        assert(originalBytes.contentEquals(decryptedBytes))
        
        data.dispose()
        decryptedData.dispose()
    }
    
    @Test
    fun `generateApiKey - should generate secure API key`() = runBlocking {
        // When
        val apiKey = securityManager.generateApiKey()
        
        // Then
        assert(apiKey != null)
        apiKey.dispose()
    }
    
    @Test
    fun `getSecurityConfig - should return current configuration`() {
        // When
        val config = securityManager.getSecurityConfig()
        
        // Then
        assert(config.promptInjectionDefenseEnabled)
        assert(config.piiMaskingEnabled)
        assert(config.encryptionEnabled)
        assert(config.securityLevel == SecurityLevel.STANDARD)
    }
    
    @Test
    fun `updateSecurityConfig - should update configuration`() {
        // Given
        val newConfig = SecurityConfiguration(
            promptInjectionDefenseEnabled = false,
            piiMaskingEnabled = false,
            encryptionEnabled = false,
            dataIntegrityEnabled = false,
            auditLoggingEnabled = false,
            securityLevel = SecurityLevel.STRICT,
            piiMaskingConfig = MaskingConfiguration(
                maskingStrategy = MaskingStrategy.REPLACE_WITH_SYMBOLS,
                preserveFormat = false,
                customMaskingRules = emptyMap(),
                enabledPiiTypes = emptySet(),
                minConfidenceThreshold = 0.9f,
                enableRealTimeMasking = false
            ),
            promptDefenseConfig = SecurityLevel.STRICT
        )
        
        // When
        securityManager.updateSecurityConfig(newConfig)
        
        // Then
        val updatedConfig = securityManager.getSecurityConfig()
        assert(!updatedConfig.promptInjectionDefenseEnabled)
        assert(!updatedConfig.piiMaskingEnabled)
        assert(!updatedConfig.encryptionEnabled)
        assert(updatedConfig.securityLevel == SecurityLevel.STRICT)
    }
    
    @Test
    fun `getSecurityStatistics - should return statistics`() = runBlocking {
        // When
        val statistics = securityManager.getSecurityStatistics()
        
        // Then
        assert(statistics.totalRequests >= 0)
        assert(statistics.blockedRequests >= 0)
        assert(statistics.sanitizedRequests >= 0)
        assert(statistics.encryptionOperations >= 0)
        assert(statistics.decryptionOperations >= 0)
        assert(statistics.lastUpdated > 0)
    }
    
    @Test
    fun `performSecurityHealthCheck - should return health check result`() = runBlocking {
        // When
        val healthCheck = securityManager.performSecurityHealthCheck()
        
        // Then
        assert(healthCheck.isHealthy)
        assert(healthCheck.healthScore > 0.0f)
        assert(healthCheck.componentHealth.size == 2)
        assert(healthCheck.componentHealth["PromptInjectionDefense"] != null)
        assert(healthCheck.componentHealth["PiiMaskingProcessor"] != null)
        assert(healthCheck.checkTimeMs >= 0)
    }
}