# Build Fixes Required

## Summary
The build is failing with 76 compilation errors. All Priority 1 build blockers (A1-A6) from the audit are already fixed. The current errors are:

### Error Category 1: Wrong Artifact Import (48 errors)
**Root Cause**: Files are importing `com.shadowai.artifactsystem.Artifact` instead of `com.shadowai.core.Artifact`

**Files to fix**:
1. `app/src/main/java/com/shadowai/app/agent/AgenticLoop.kt` - Line 18
2. `app/src/main/java/com/shadowai/app/db/PersistenceMappers.kt` - Line 4
3. `app/src/main/java/com/shadowai/app/ui/ChatMessage.kt` - Line 3
4. `app/src/main/java/com/shadowai/app/ui/ChatViewModel.kt` - Line 15
5. `app/src/main/java/com/shadowai/app/ui/chat/MessageContent.kt` - Line 21

**Fix**: Replace `import com.shadowai.artifactsystem.Artifact` with `import com.shadowai.core.Artifact`

### Error Category 2: Missing TaskType Enum Values (14 errors)
**File**: `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt` (lines 413-422)

**Missing enum values**:
- TEXT_TO_TEXT
- TEXT_GENERATION
- IMAGE_GENERATION
- IMAGE_TO_TEXT
- IMAGE_TO_IMAGE
- TEXT_TO_AUDIO
- TEXT_TO_SPEECH
- AUDIO_TO_TEXT
- SPEECH_TO_TEXT
- VIDEO_GENERATION
- VIDEO_TO_TEXT
- VIDEO_ANALYSIS
- AUDIO_GENERATION
- TOOL_USE

**Fix**: Add these values to the TaskType enum in `app/src/main/java/com/shadowai/app/tasks/TaskType.kt`

### Error Category 3: Method Signature Mismatch (4 errors)
**File**: `app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt` (lines 403-404)

**Issue**: `nextLocal()` and `nextCloud()` are being called with too many arguments

**Current calls**:
```kotlin
nextLocal(taskType, RoutingPolicy.LOCAL) ?: nextCloud(taskType, RoutingPolicy.CLOUD)
nextCloud(taskType, RoutingPolicy.CLOUD) ?: nextLocal(taskType, RoutingPolicy.LOCAL)
```

**Expected signature**: `suspend fun nextLocal(taskType: TaskType): ActiveProviderConfig?`

**Fix**: Remove the second argument (RoutingPolicy) from all calls

### Error Category 4: Missing apiKey Parameter (2 errors)
**File**: `app/src/main/java/com/shadowai/app/ui/settings/HotSwapScreen.kt` (lines 189, 221)

**Issue**: Code references `apiKey` parameter that doesn't exist

**Fix**: Need to investigate the HotSwapScreen implementation and either:
- Add the missing apiKey parameter to the relevant function
- Remove/replace the apiKey references

## Fix Priority

1. **CRITICAL**: Fix Artifact imports (Category 1) - This will resolve 48 errors
2. **HIGH**: Add TaskType enum values (Category 2) - This will resolve 14 errors  
3. **HIGH**: Fix method signatures (Category 3) - This will resolve 4 errors
4. **MEDIUM**: Fix apiKey parameter (Category 4) - This will resolve 2 errors

## Verification Command

After fixes:
```bash
./gradlew :app:compileDebugKotlin --no-daemon --stacktrace
```

Expected result: BUILD SUCCESSFUL
