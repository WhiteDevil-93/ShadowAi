# Immediate Action Items - Phases 4-7

## Overview
This document outlines the specific gaps found during Phase 4-7 implementation review with code snippets for implementation.

---

## Priority 2: Functional Gaps

### 1. TokenCounter Implementation (Phase 5.2) ⚠️

**Status:** Not found as separate class  
**File to create:** `app/src/main/java/com/shadowai/app/ai/TokenCounter.kt`

**Implementation:**
```kotlin
package com.shadowai.app.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token counter for text input estimation.
 * Uses heuristic ~4 characters per token as fallback when proper tokenizer unavailable.
 */
@Singleton
class TokenCounter @Inject constructor() {

    companion object {
        /**
         * Heuristic: ~4 characters per token for English text.
         * This varies by text, tokenizer, and language, but provides reasonable estimates.
         */
        private const val CHARS_PER_TOKEN = 4

        /**
         * Cache token counts for performance.
         */
        private val tokenCountCache = mutableMapOf<String, Int>()
    }

    /**
     * Counts tokens in text using heuristic.
     */
    fun countTokens(text: String): Int {
        if (text.isBlank()) return 0

        // Check cache first
        return tokenCountCache.getOrPut(text) {
            (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
        }
    }

    /**
     * Counts tokens for a list of messages.
     */
    fun countTokens(messages: List<String>): Int {
        return messages.sumOf { countTokens(it) }
    }

    /**
     * Calculates total tokens including system prompt and conversation history.
     */
    fun calculateContextTokens(
        systemPrompt: String,
        conversationHistory: List<String>
    ): Int {
        return countTokens(systemPrompt) + countTokens(conversationHistory)
    }

    /**
     * Clears the token count cache.
     */
    fun clearCache() {
        tokenCountCache.clear()
    }
}
```

**Usage in ModelDescriptor:**
```kotlin
// In conversation handler:
val inputTokens = tokenCounter.countTokens(input)
val availableTokens = modelDescriptor.getAvailableGenerationTokens(inputTokens)

if (availableTokens < MIN_GENERATION_TOKENS) {
    // Apply sliding window truncation
}
```

---

### 2. ContextManager with Sliding Window (Phase 5.3) ⚠️

**Status:** Configuration exists, logic missing  
**File to create:** `app/src/main/java/com/shadowai/app/agent/ContextManager.kt`

**Implementation:**
```kotlin
package com.shadowai.app.agent

import com.shadowai.app.ai.TokenCounter
import com.shadowai.core.ModelDescriptor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages conversation context with sliding window truncation.
 * Ensures context stays within model's token budget.
 */
@Singleton
class ContextManager @Inject constructor(
    private val tokenCounter: TokenCounter
) {

    companion object {
        /**
         * Minimum tokens needed for generation (conservative estimate).
         */
        private const val MIN_GENERATION_TOKENS = 256

        /**
         * Default sliding window size (number of exchanges to keep).
         */
        private const val DEFAULT_SLIDING_WINDOW_SIZE = 5

        /**
         * Default context threshold (percentage of maxContext before truncation).
         */
        private const val DEFAULT_CONTEXT_THRESHOLD = 0.9f
    }

    data class ConversationExchange(
        val role: String,  // "user", "assistant", "system"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ContextTruncationResult(
        val exchanges: List<ConversationExchange>,
        val systemPrompt: String,
        val wasTruncated: Boolean,
        val originalTokens: Int,
        val truncatedTokens: Int
    )

    /**
     * Manages conversation context with sliding window.
     *
     * @param exchanges List of conversation exchanges (user/assistant)
     * @param systemPrompt System prompt to preserve
     * @param modelDescriptor Model context limits
     * @param windowSize Number of recent exchanges to keep (default: 5)
     * @param threshold Context usage threshold (default: 0.9 = 90%)
     */
    fun manageContext(
        exchanges: List<ConversationExchange>,
        systemPrompt: String,
        modelDescriptor: ModelDescriptor,
        windowSize: Int = DEFAULT_SLIDING_WINDOW_SIZE,
        threshold: Float = DEFAULT_CONTEXT_THRESHOLD
    ): ContextTruncationResult {
        val systemPromptTokens = tokenCounter.countTokens(systemPrompt)
        val exchangeTokens = tokenCounter.countTokens(exchanges.map { it.content })
        val totalTokens = systemPromptTokens + exchangeTokens

        val maxTokens = (modelDescriptor.maxContext * threshold).toInt()
        val requiredForGeneration = MIN_GENERATION_TOKENS
        val availableForExchanges = maxTokens - systemPromptTokens - requiredForGeneration

        // If context fits, no truncation needed
        if (exchangeTokens <= availableForExchanges) {
            return ContextTruncationResult(
                exchanges = exchanges,
                systemPrompt = systemPrompt,
                wasTruncated = false,
                originalTokens = totalTokens,
                truncatedTokens = totalTokens
            )
        }

        // Apply sliding window: keep last N exchanges
        val truncatedExchanges = exchanges.takeLast(windowSize * 2)  // *2 for user/assistant pairs
        val truncatedTokens = systemPromptTokens + tokenCounter.countTokens(truncatedExchanges.map { it.content })

        return ContextTruncationResult(
            exchanges = truncatedExchanges,
            systemPrompt = systemPrompt,
            wasTruncated = true,
            originalTokens = totalTokens,
            truncatedTokens = truncatedTokens
        )
    }

    /**
     * Formats conversation exchanges for model input.
     */
    fun formatExchangesForModel(exchanges: List<ConversationExchange>): String {
        return exchanges.joinToString("\n") { exchange ->
            "${exchange.role}: ${exchange.content}"
        }
    }

    /**
     * Reconstructs full context prompt (system + exchanges).
     */
    fun buildFullPrompt(
        systemPrompt: String,
        exchanges: List<ConversationExchange>
    ): String {
        val formattedExchanges = formatExchangesForModel(exchanges)
        return if (formattedExchanges.isNotBlank()) {
            "$systemPrompt\n\n$formattedExchanges"
        } else {
            systemPrompt
        }
    }
}
```

**Usage in AgenticLoop:**
```kotlin
// In AgenticLoop.execute():
val contextResult = contextManager.manageContext(
    exchanges = conversationExchanges,
    systemPrompt = systemPrompt,
    modelDescriptor = modelDescriptor,
    windowSize = config.slidingWindowSize,
    threshold = config.contextThreshold
)

if (contextResult.wasTruncated) {
    Log.i(TAG, "Context truncated from ${contextResult.originalTokens} to ${contextResult.truncatedTokens} tokens")
}

val fullPrompt = contextManager.buildFullPrompt(
    systemPrompt = contextResult.systemPrompt,
    exchanges = contextResult.exchanges
)
```

---

## Priority 3: Verification Tasks

### 3. Test Coverage Verification (Phase 4.3)

**Steps:**
1. Navigate to project root:
   ```bash
   cd /mnt/c/Users/anon3/Downloads/ShadowAi
   ```

2. Run coverage:
   ```bash
   ./gradlew jacocoTestReport
   ```

3. Check coverage reports:
   - Open `build/reports/jacoco/test/html/index.html`
   - Look for `core-contracts/src/main/kotlin/com/shadowai/core/security/PiiMaskingProcessor.kt`
   - Verify: Line coverage ≥ 90%, Branch coverage ≥ 85%

4. If coverage is below thresholds:
   - Add test cases for uncovered branches
   - Focus on edge cases and error paths
   - Re-run coverage check

**Example test to add (if needed):**
```kotlin
@Test
fun `handles very long text without overflow`() {
    val longText = "a".repeat(1000000)  // 1MB text
    val result = processor.maskPii(longText)
    assertNotNull("Should handle large text", result)
}
```

---

### 4. TLS Certificate Pinning Verification (Phase 4.4)

**Steps:**
1. Review network security config:
   ```bash
   cat app/src/main/res/xml/network_security_config_release.xml
   ```

2. Expected content for production:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <network-security-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors>
               <certificates src="system" />
           </trust-anchors>
       </base-config>
       
       <domain-config>
           <domain includeSubdomains="true">api.openai.com</domain>
           <pin-set>
               <pin digest="SHA-256">ACTUAL_CERTIFICATE_PIN_HERE</pin>
               <!-- Add backup pins here -->
           </pin-set>
       </domain-config>
       
       <!-- Add other API domains with pins -->
   </network-security-config>
   ```

3. Extract certificate pins:
   ```bash
   # Using openssl to get certificate
   openssl s_client -servername api.openai.com -connect api.openai.com:443 \
     | openssl x509 -pubkey -noout \
     | openssl rsa -pubin -outform der \
     | openssl dgst -sha256 -binary \
     | openssl enc -base64
   ```

4. **IMPORTANT:** Add backup pins to prevent breakage during certificate rotation

---

## Priority 4: Documentation Updates

### 5. Update README with Architecture Details

**Add to README.md:**
```markdown
## Security Architecture

### PII Masking
- PII detection and masking via `PiiMaskingProcessor`
- Automatic masking for cloud provider requests
- Context-aware API key detection
- Secure memory handling with `SecretBytes`

### Thread Safety
- Coroutines with proper dispatchers
- Mutex protection for shared state
- Atomic operations for counters and flags
- ConcurrentHashMap for concurrent access

### Memory Management
- LRU model unloading on memory pressure
- Database persistence for model metadata
- Automatic cache reduction under pressure
- Context window management with sliding window truncation

```

---

## Integration Checklist

- [ ] Create `TokenCounter.kt` class
- [ ] Create `ContextManager.kt` class
- [ ] Integrate TokenCounter in AgenticLoop
- [ ] Integrate ContextManager in AgenticLoop
- [ ] Run test coverage verification
- [ ] Verify TLS certificate pins
- [ ] Update UnitTestHelper.kt with context management tests
- [ ] Update README.md with architecture documentation
- [ ] Commit changes with descriptive message

---

## Testing Plan

### Test TokenCounter
```kotlin
@Test
fun `counts tokens using heuristic`() {
    val tokenCounter = TokenCounter()
    val text = "The quick brown fox jumps over the lazy dog"
    val tokens = tokenCounter.countTokens(text)
    // 43 chars / 4 = ~10 tokens
    assertTrue(tokens > 0)
}

@Test
fun `handles empty text`() {
    val tokenCounter = TokenCounter()
    assertEquals(0, tokenCounter.countTokens(""))
}

@Test
fun `caches token counts`() {
    val tokenCounter = TokenCounter()
    val text = "Sample text"
    val count1 = tokenCounter.countTokens(text)
    val count2 = tokenCounter.countTokens(text)
    assertEquals(count1, count2)
}
```

### Test ContextManager
```kotlin
@Test
fun `preserves context when within limits`() {
    val modelDescriptor = ModelDescriptor(
        id = "test",
        name = "Test",
        providerId = ProviderId.LOCAL_TEXT,
        capabilities = emptySet(),
        maxContext = 4096
    )
    
    val exchanges = listOf(
        ContextManager.ConversationExchange("user", "Hello"),
        ContextManager.ConversationExchange("assistant", "Hi there")
    )
    
    val result = contextManager.manageContext(
        exchanges = exchanges,
        systemPrompt = "You are helpful.",
        modelDescriptor = modelDescriptor
    )
    
    assertFalse(result.wasTruncated)
    assertEquals(2, result.exchanges.size)
}

@Test
fun `applies sliding window when over threshold`() {
    val modelDescriptor = ModelDescriptor(
        id = "test",
        name = "Test",
        providerId = ProviderId.LOCAL_TEXT,
        capabilities = emptySet(),
        maxContext = 100  // Small context for testing
    )
    
    val exchanges = (1..20).map { 
        ContextManager.ConversationExchange("user", "Message $it")
    }
    
    val result = contextManager.manageContext(
        exchanges = exchanges,
        systemPrompt = "System",
        modelDescriptor = modelDescriptor,
        windowSize = 3
    )
    
    assertTrue(result.wasTruncated)
    assertTrue(result.exchanges.size <= 6)  // 3*2
}
```

---

*Generated: 2026-02-11 02:35 UTC+2*