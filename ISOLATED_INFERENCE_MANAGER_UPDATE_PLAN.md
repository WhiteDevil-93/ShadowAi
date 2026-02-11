# IsolatedInferenceManager Update Plan

## Current State Analysis

The `IsolatedInferenceManager.kt` file (478 lines) currently:
- Uses old AIDL interface (`IInferenceService`)
- Has manual Bundle passing for IPC
- Lacks DeathRecipient for binder death detection
- Missing reconnection logic with exponential backoff
- No health monitoring (ping)
- Uses `@Volatile` instead of proper atomic operations

## Required Changes

### 1. Update Imports
**Add**:
```kotlin
import com.shadowai.inference.IInferenceService
import com.shadowai.inference.IGenerationCallback
import com.shadowai.inference.InferenceServiceContracts
import com.shadowai.inference.InferenceServiceContracts.ErrorCodes
import com.shadowai.inference.InferenceServiceContracts.LoadModel
import com.shadowai.inference.InferenceServiceContracts.Generate
import android.os.RemoteException
import java.util.concurrent.atomic.AtomicReference
```

**Remove**:
```kotlin
// Old AIDL imports (if any from app.ai package)
```

### 2. Add DeathRecipient
**Location**: After `serviceConnection` declaration

```kotlin
private val binderDeathRecipient = IBinder.DeathRecipient {
    Log.w(TAG, "Inference service binder died")
    managerScope.launch {
        handleServiceFailure("Binder death detected", scheduleRebind = true)
    }
}
```

### 3. Update Service Connection Handling

**In `onServiceConnected()`**:
```kotlin
override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
    val remote = IInferenceService.Stub.asInterface(binder)
    if (remote == null || binder == null) {
        Log.e(TAG, "Service connected with null binder/interface")
        _connectionStatus.value = ConnectionStatus.FAILED
        connectDeferred?.completeExceptionally(IllegalStateException("Null inference binder"))
        connectDeferred = null
        return
    }

    try {
        // Link to death recipient
        binder.linkToDeath(binderDeathRecipient, 0)
        
        service = remote
        serviceBinder = binder
        isBound = true
        rebindAttempts = 0
        
        // Verify service is alive with ping
        managerScope.launch {
            try {
                remote.ping()
                val info = remote.getServiceInfo()
                nativeAvailable = info.getBoolean(InferenceServiceContracts.ServiceInfo.KEY_ALIVE, false)
                
                _connectionStatus.value = if (nativeAvailable) {
                    ConnectionStatus.OPERATIONAL
                } else {
                    ConnectionStatus.CONNECTED
                }
                
                connectDeferred?.complete(Unit)
                connectDeferred = null
                
                Log.i(TAG, \"Inference service connected and operational\")
            } catch (e: RemoteException) {
                Log.e(TAG, \"Failed to ping service\", e)
                handleServiceFailure(\"Ping failed: ${e.message}\")
            }
        }
    } catch (e: RemoteException) {
        Log.e(TAG, \"Failed to link death recipient\", e)
        handleServiceFailure(\"Death recipient link failed: ${e.message}\")
    }
}
```

**In `onServiceDisconnected()`**:
```kotlin
override fun onServiceDisconnected(name: ComponentName?) {
    Log.w(TAG, \"Inference service disconnected unexpectedly\")
    clearServiceReference(clearModels = false)
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
    handleServiceFailure(\"Service disconnected\", scheduleRebind = true)
}
```

**In `onBindingDied()`**:
```kotlin
override fun onBindingDied(name: ComponentName?) {
    Log.e(TAG, \"Inference service binding died\")
    clearServiceReference(clearModels = true)
    _connectionStatus.value = ConnectionStatus.FAILED
    handleServiceFailure(\"Binding died\", scheduleRebind = true)
}
```

### 4. Update `loadModel()` Method

**Replace Bundle-based IPC with AIDL**:
```kotlin
override suspend fun loadModel(
    modelPath: String,
    config: LocalGenerationConfig
): LocalModelHandle? = withContext(Dispatchers.IO) {
    val service = requireService()
    val normalizedPath = normalizePath(modelPath)
    
    // Check if already loaded
    modelIdsByPath[normalizedPath]?.let { existingId ->
        if (isModelIdLoaded(existingId)) {
            Log.i(TAG, \"Model already loaded: $normalizedPath\")
            return@withContext RemoteModelHandle(existingId, normalizedPath)
        }
    }
    
    try {
        // Open file descriptor for cross-process access
        val file = File(normalizedPath)
        if (!file.exists()) {
            Log.e(TAG, \"Model file not found: $normalizedPath\")
            return@withContext null
        }
        
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        
        try {
            // Build config bundle using contracts
            val configBundle = Bundle().apply {
                putInt(LoadModel.KEY_CONTEXT_SIZE, config.nCtx)
                putInt(LoadModel.KEY_GPU_LAYERS, config.gpuLayers)
                putInt(LoadModel.KEY_THREADS, config.nThreads)
                putBoolean(LoadModel.KEY_USE_MMAP, config.useMmap)
                putBoolean(LoadModel.KEY_USE_MLOCK, config.useMlock)
            }
            
            // Call AIDL method
            val result = service.loadModel(pfd, configBundle)
            
            if (!result.getBoolean(LoadModel.KEY_SUCCESS, false)) {
                val errorCode = result.getInt(\"errorCode\", ErrorCodes.ERROR_UNKNOWN)
                val errorMsg = result.getString(LoadModel.KEY_ERROR, \"Unknown error\")
                Log.e(TAG, \"Failed to load model: [$errorCode] $errorMsg\")
                return@withContext null
            }
            
            val handle = result.getLong(LoadModel.KEY_HANDLE)
            val modelId = \"model_$handle\"
            
            // Store mappings
            modelIdsByPath[normalizedPath] = modelId
            modelPathsById[modelId] = normalizedPath
            
            Log.i(TAG, \"Model loaded successfully: $modelId from $normalizedPath\")
            RemoteModelHandle(modelId, normalizedPath)
            
        } finally {
            pfd.close()
        }
    } catch (e: RemoteException) {
        Log.e(TAG, \"Remote exception loading model\", e)
        handleServiceFailure(\"Load model failed: ${e.message}\")
        null
    } catch (e: IOException) {
        Log.e(TAG, \"IO exception opening model file\", e)
        null
    }
}
```

### 5. Update `generate()` Method

```kotlin
override suspend fun generate(
    modelId: String,
    prompt: String,
    config: LocalGenerationConfig
): String = withContext(Dispatchers.IO) {
    val service = requireService()
    
    if (!isModelIdLoaded(modelId)) {
        throw IllegalStateException(\"Model not loaded: $modelId\")
    }
    
    try {
        // Extract handle from modelId
        val handle = modelId.removePrefix(\"model_\").toLongOrNull()
            ?: throw IllegalArgumentException(\"Invalid model ID: $modelId\")
        
        // Build request bundle
        val request = Bundle().apply {
            putLong(Generate.KEY_HANDLE, handle)
            putString(Generate.KEY_PROMPT, prompt)
            putInt(Generate.KEY_MAX_TOKENS, config.maxTokens)
            putFloat(Generate.KEY_TEMPERATURE, config.temp)
            putFloat(Generate.KEY_TOP_P, config.topP)
            putInt(Generate.KEY_TOP_K, config.topK)
            putFloat(Generate.KEY_REPEAT_PENALTY, config.repeatPenalty)
        }
        
        // Call AIDL method
        val result = service.generate(request)
        
        if (!result.getBoolean(Generate.KEY_SUCCESS, false)) {
            val errorCode = result.getInt(\"errorCode\", ErrorCodes.ERROR_UNKNOWN)
            val errorMsg = result.getString(Generate.KEY_ERROR, \"Unknown error\")
            throw RuntimeException(\"Generation failed: [$errorCode] $errorMsg\")
        }
        
        result.getString(Generate.KEY_TEXT, \"\")
        
    } catch (e: RemoteException) {
        Log.e(TAG, \"Remote exception during generation\", e)
        handleServiceFailure(\"Generation failed: ${e.message}\")
        throw RuntimeException(\"Generation failed due to service error\", e)
    }
}
```

### 6. Add Health Monitoring

**New method**:
```kotlin
suspend fun pingService(): Boolean = withContext(Dispatchers.IO) {
    val service = service ?: return@withContext false
    
    try {
        service.ping()
        true
    } catch (e: RemoteException) {
        Log.w(TAG, \"Ping failed\", e)
        false
    }
}

// Start periodic health check
private fun startHealthMonitoring() {
    managerScope.launch {
        while (true) {
            delay(InferenceServiceContracts.Config.PING_INTERVAL_MS)
            
            if (_connectionStatus.value == ConnectionStatus.OPERATIONAL) {
                if (!pingService()) {
                    Log.w(TAG, \"Health check failed\")
                    handleServiceFailure(\"Health check failed\", scheduleRebind = true)
                }
            }
        }
    }
}
```

### 7. Update `clearServiceReference()`

```kotlin
private fun clearServiceReference(clearModels: Boolean) {
    serviceBinder?.let { binder ->
        try {
            binder.unlinkToDeath(binderDeathRecipient, 0)
        } catch (e: Exception) {
            Log.w(TAG, \"Failed to unlink death recipient\", e)
        }
    }
    
    service = null
    serviceBinder = null
    isBound = false
    nativeAvailable = false
    
    if (clearModels) {
        modelIdsByPath.clear()
        modelPathsById.clear()
    }
}
```

### 8. Update `scheduleRebind()` with Exponential Backoff

```kotlin
private fun scheduleRebind() {
    if (!rebinding.compareAndSet(false, true)) {
        Log.d(TAG, \"Rebind already in progress\")
        return
    }
    
    managerScope.launch {
        try {
            if (rebindAttempts >= MAX_REBIND_ATTEMPTS) {
                Log.e(TAG, \"Max rebind attempts reached, giving up\")
                _connectionStatus.value = ConnectionStatus.FAILED
                rebinding.set(false)
                return@launch
            }
            
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s
            val delayMs = REBIND_BASE_DELAY_MS * (1 shl rebindAttempts)
            Log.i(TAG, \"Scheduling rebind attempt ${rebindAttempts + 1}/$MAX_REBIND_ATTEMPTS in ${delayMs}ms\")
            
            delay(delayMs)
            
            rebindAttempts++
            
            try {
                bindService()
                Log.i(TAG, \"Rebind successful\")
                rebindAttempts = 0
            } catch (e: Exception) {
                Log.e(TAG, \"Rebind attempt failed\", e)
                scheduleRebind() // Try again
            }
        } finally {
            rebinding.set(false)
        }
    }
}
```

## Implementation Steps

1. ✅ Update imports
2. ✅ Add DeathRecipient
3. ✅ Update service connection callbacks
4. ✅ Update loadModel() to use AIDL
5. ✅ Update generate() to use AIDL
6. ✅ Add health monitoring
7. ✅ Update clearServiceReference()
8. ✅ Update scheduleRebind() with exponential backoff
9. ⏳ Add streaming generation support
10. ⏳ Add comprehensive error handling
11. ⏳ Add unit tests

## Testing Checklist

- [ ] Service binding succeeds
- [ ] Model loading works
- [ ] Generation produces output
- [ ] Service death triggers rebind
- [ ] Exponential backoff works correctly
- [ ] Health monitoring detects failures
- [ ] Multiple rebind attempts succeed
- [ ] Max rebind attempts respected
- [ ] Memory leaks checked
- [ ] Thread safety verified

## Notes

- All `@Volatile` fields should eventually be replaced with `AtomicReference`
- Consider adding metrics for rebind success/failure rates
- Add telemetry for service health
- Consider circuit breaker pattern for repeated failures
