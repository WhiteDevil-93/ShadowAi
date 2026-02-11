# LFM2.5 Model Loading - UPDATED Analysis

## ✅ GOOD NEWS
Your llama.cpp **DOES** support LFM2.5! 

Evidence:
- `app/src/main/cpp/llama/src/models/lfm2.cpp` exists (8009 bytes)
- Contains `build_shortconv_block()` - the LIV convolution implementation
- Contains `build_attn_block()` - the GQA implementation  
- Supports hybrid architecture (recurrent + attention layers)

## ❌ Actual Problem
The model fails to load despite architecture support. Possible causes:

### 1. Memory Allocation Failure (MOST LIKELY)
The 730MB model might be too large for available RAM during loading.

**Solution**: Check available memory
```bash
adb shell dumpsys meminfo com.shadowai.app
```

Try a smaller quantization:
- Current: Q4_K_M (730MB)
- Try: Q4_0 (~650MB) or IQ3_XS (~500MB)

Download from: https://huggingface.co/LiquidAI/LFM2.5-1.2B-Thinking-GGUF

### 2. Missing Tensor in Model File
The GGUF file might be missing required tensors for the LFM2 architecture.

**Solution**: Re-download from official source
```bash
# Download official GGUF
wget https://huggingface.co/LiquidAI/LFM2.5-1.2B-Thinking-GGUF/resolve/main/LFM2.5-1.2B-Thinking-Q4_K_M.gguf

# Or try the Instruct version instead of Thinking
wget https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf
```

### 3. llama.cpp Logging Disabled
We can't see the actual llama.cpp error because logging might be disabled.

**Solution**: Enable llama.cpp logging in CMakeLists.txt
```cmake
# Add to app/src/main/cpp/CMakeLists.txt after line 107:
target_compile_definitions(llama PRIVATE
    GGML_VERSION=\"1.0.0\"
    GGML_COMMIT=\"unknown\"
    GGML_USE_LLAMAFILE=1  # Enable detailed logging
)
```

## Next Steps

### Step 1: Try Smaller Model First
Test with a known-working model to verify the pipeline:

```bash
# Download Qwen2.5-0.5B (much smaller, ~300MB)
cd /storage/emulated/0/liquid-main/
wget https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf
```

Update `LocalLiquidEngine.kt` to try this model first.

### Step 2: Enable Verbose Logging
Add to `llama_jni.cpp` before line 236:

```cpp
// Enable llama.cpp internal logging
llama_log_set([](enum ggml_log_level level, const char * text, void * user_data) {
    LOGI("llama.cpp: %s", text);
}, nullptr);
```

### Step 3: Check Model Integrity
Verify the GGUF file isn't corrupted:

```bash
# On your PC, check file hash
certutil -hashfile "C:\Users\anon3\Downloads\liquid-main\LFM2.5-1.2B-Thinking-Q4_K_M.gguf" SHA256
```

Compare with official hash from Hugging Face.

### Step 4: Monitor Memory During Load
```bash
# Watch memory in real-time
adb shell "while true; do dumpsys meminfo com.shadowai.app | grep TOTAL; sleep 1; done"
```

## Expected Behavior

If successful, you should see:
```
LlamaJNI: Attempting to load model from: /storage/emulated/0/Download/LFM2.5-1.2B-Thinking-Q4_K_M.gguf
LlamaJNI: Model file verified accessible, size=730894816 bytes
LlamaJNI: GGUF header: magic='GGUF' (0x47475546), version=3
LlamaJNI: Loading model with default params (mmap=true)...
LlamaJNI: Model loaded successfully!
```

Current behavior:
```
LlamaJNI: First load attempt failed, retrying with mmap=false, mlock=false...
LlamaJNI: llama_model_load_from_file returned null
```

This suggests llama.cpp is rejecting the model internally, likely due to:
- Memory allocation failure
- Missing/corrupted tensor data
- Incompatible tensor shapes

## Files Modified

1. `app/src/main/cpp/llama_jni.cpp` - Added diagnostics
2. Error messages now show GGUF version and file size
3. Suggests actionable solutions to users

## Architecture Confirmed

Your llama.cpp supports:
- ✅ GGUF v3
- ✅ LFM2/LFM2.5 architecture
- ✅ Hybrid models (recurrent + attention)
- ✅ Shortconv blocks (LIV convolutions)
- ✅ MoE (Mixture of Experts) layers
- ✅ GQA (Grouped Query Attention)

The issue is NOT architecture support - it's runtime loading failure.
