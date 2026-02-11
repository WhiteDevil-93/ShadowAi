# Local Liquid AI Model Integration Plan

## Overview

Integrate **Liquid AI LFM2.5-1.2B-Instruct** model for on-device inference using llama.cpp.

## Model Location

```text
/internal storage/liquid-main/
├── model.safetensors    (will be converted to GGUF)
├── tokenizer.json
├── special_tokens_map.json
├── chat_template.jinja
├── config.json
└── generation_config.json
```

## Model Specifications

| Property | Value |
| :--- | :--- |
| Parameters | 1.2B |
| Architecture | Lfm2ForCausalLM (custom) |
| Precision | bf16 (will quantize to q4_0/q5_0) |
| Context Length | TBD from config |
| Memory Requirement | ~1GB (quantized) |
| Inference Speed | 82+ tok/s on mobile NPU |

## Implementation Architecture

### 1. ProviderModels.kt Update

Add `LIQUID` to ProviderId enum:

```kotlin
enum class ProviderId {
    LOCAL_IMAGE, LOCAL_TEXT, OPENAI, ..., LIQUID, UNKNOWN
}
```

### 2. ProviderRepository.kt Update

Add LIQUID to default providers:

```kotlin
Provider(ProviderId.LIQUID, "Liquid AI (Local)", false, "local://liquid", ProviderAuth())
```

### 3. Create LocalLiquidEngine

```kotlin
class LocalLiquidEngine(private val context: Context) {
    companion object {
        const val MODEL_PATH = "/internal storage/liquid-main"
    }
    
    fun loadModel(): Result<Unit>
    fun generate(prompt: String, maxTokens: Int = 512): Result<String>
    fun isModelLoaded(): Boolean
    fun unload()
}
```

### 4. Create LiquidProvider

```kotlin
class LiquidProvider(
    private val engine: LocalLiquidEngine
) {
    suspend fun chat(messages: List<ChatMessage>): Result<String>
}
```

## Model Quantization (Required)

Convert model.safetensors to GGUF format:

```bash
# Install llama.cpp
pip install llama-cpp-python

# Convert using convert-hf-to-gguf.py
python3 convert-hf-to-gguf.py \
    --outfile /internal storage/liquid-main/model.gguf \
    --outtype q4_0 \
    /internal storage/liquid-main
```

## Agent Integration

```mermaid
graph TB
    User --> MainActivity
    MainActivity --> RoutingEngine
    RoutingEngine --> LocalTextProvider
    LocalTextProvider --> LiquidProvider
    LiquidProvider --> LocalLiquidEngine
    LocalLiquidEngine --> /internal storage/liquid-main/model.gguf
```

## Memory Management Strategy

| State | Action |
| :--- | :--- |
| App Start | Check if model exists |
| First Use | Load model (async) |
| Background | Keep in memory (fast resume) |
| Low Memory | Unload model |
| App Kill | Cleanup |

## Performance Targets

| Metric | Target |
| :--- | :--- |
| Time to First Token | < 3s |
| Tokens per Second | > 15 |
| Memory Usage | < 1.2GB |

## Files to Create

1. `app/src/main/java/com/shadowai/app/ai/LocalLiquidEngine.kt`
2. `app/src/main/java/com/shadowai/app/providers/LiquidProvider.kt`
3. `app/src/main/java/com/shadowai/app/functions/LiquidExecutor.kt`

## Files to Modify

1. `app/src/main/java/com/shadowai/app/providers/ProviderModels.kt` - Add LIQUID
2. `app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt` - Add to defaults

## Next Steps

1. Add LIQUID to ProviderId enum
2. Add LIQUID to default providers
3. Create LocalLiquidEngine
4. Create LiquidExecutor
5. Test with real model path
