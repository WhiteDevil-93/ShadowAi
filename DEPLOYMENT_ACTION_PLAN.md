# ShadowAi - Deployment Action Plan

**Date:** 2026-02-11  
**Status:** ⚠️ PRE-PRODUCTION  
**Target:** Production-Ready Release  

---

## Quick Reference

**Current State:** 8 Critical Blockers, 6 High Priority Issues  
**Estimated Time to Production:** 7-10 engineering days  
**Recommended Approach:** Phased resolution with validation gates  

---

## PHASE 1: CRITICAL BLOCKERS (Days 1-4)

### Day 1: Security & Build Infrastructure

#### Morning: Firebase Security (URGENT)
```bash
# 1. Rotate Firebase API Key
# - Go to: https://console.firebase.google.com/project/shadowai-4663b/settings/general
# - Regenerate API key
# - Update local google-services.json (DO NOT COMMIT)

# 2. Remove from Git History
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch app/google-services.json" \
  --prune-empty --tag-name-filter cat -- --all

# 3. Add to .gitignore
echo "app/google-services.json" >> .gitignore
git add .gitignore
git commit -m "security: Remove Firebase config from version control"
```

#### Afternoon: Release Signing Configuration
```bash
# 1. Generate Production Keystore
keytool -genkey -v -keystore shadowai-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias shadowai-release

# 2. Update local.properties (NEVER COMMIT THIS)
# Replace YOUR_* placeholders with actual values

# 3. Test Release Build
./gradlew :app:assembleRelease

# 4. Verify APK
ls -lh app/build/outputs/apk/release/
```

**Deliverables:**
- [ ] Firebase API key rotated
- [ ] `google-services.json` removed from git history
- [ ] Production keystore generated and backed up
- [ ] Release APK builds successfully

---

### Day 2: Test Suite & Core Integrations

#### Morning: Fix Test Failures
```bash
# 1. Identify Failing Tests
./gradlew test --continue > test_results.txt

# 2. Fix Each Failure
# - Review test_results.txt
# - Fix failing tests one by one
# - Re-run: ./gradlew test

# 3. Verify All Pass
./gradlew test --console=plain
```

#### Afternoon: Conversation Summarization Integration
```kotlin
// File: app/src/main/java/com/shadowai/app/ui/chat/ChatScreen.kt

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    summaryViewModel: SummaryViewModel = hiltViewModel() // ADD THIS
) {
    val summaryState by summaryViewModel.summaryState.collectAsState()
    
    // Add to LazyColumn
    item {
        if (summaryState.hasSummary) {
            SummaryIndicator(
                messageCount = summaryState.messageCount,
                summary = summaryState.summary,
                onViewDetails = { /* Show summary dialog */ },
                onRestore = { summaryViewModel.restoreMessages() }
            )
        }
    }
}

// File: app/src/main/java/com/shadowai/app/ui/chat/ChatViewModel.kt

suspend fun sendMessage(text: String) {
    // ... existing code ...
    
    // ADD: Check if summarization needed
    if (conversationSummarizer.shouldSummarize(messages)) {
        val result = conversationSummarizer.summarize(messages)
        summaryViewModel.updateSummary(result)
    }
}
```

**Deliverables:**
- [ ] All unit tests passing
- [ ] Summarization wired to ChatScreen
- [ ] Auto-trigger working
- [ ] End-to-end test complete

---

### Day 3: Intent Handling & Lifecycle Fixes

#### Morning: Share Sheet Implementation
```kotlin
// File: app/src/main/java/com/shadowai/app/ComposeMainActivity.kt

class ComposeMainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle incoming intents
        handleIncomingIntent(intent)
        
        setContent {
            ShadowAiTheme {
                MainNavigation(
                    sharedText = sharedTextState.value,
                    sharedImageUri = sharedImageUriState.value
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }
    
    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                when {
                    intent.type == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        sharedTextState.value = text
                    }
                    intent.type?.startsWith("image/") == true -> {
                        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        sharedImageUriState.value = imageUri
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                // Handle multiple images
            }
        }
    }
}
```

#### Afternoon: AutoLockManager Lifecycle Fix
```kotlin
// File: app/src/main/java/com/shadowai/app/security/AutoLockManager.kt

@Singleton
class AutoLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope // Inject application scope
) : DefaultLifecycleObserver {
    
    private var monitoringJob: Job? = null
    
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    override fun onStop(owner: LifecycleOwner) {
        stopMonitoring()
    }
    
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    fun startMonitoring() {
        monitoringJob = scope.launch {
            // Monitoring logic
        }
    }
}
```

**Deliverables:**
- [ ] Share sheet handling implemented
- [ ] Test sharing text from Chrome
- [ ] Test sharing images from Gallery
- [ ] AutoLockManager lifecycle fixed
- [ ] Memory leak verified resolved

---

### Day 4: Preferences Migration & Gradle Cleanup

#### Morning: UserPreferences Migration
```kotlin
// File: app/src/main/java/com/shadowai/app/ShadowApplication.kt

override fun onCreate() {
    super.onCreate()
    
    lifecycleScope.launch {
        migrateLegacyPreferences()
    }
}

private suspend fun migrateLegacyPreferences() {
    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    
    // Migrate NNAPI setting
    if (!prefs.contains("nnapi_delegation_enabled")) {
        userPreferences.saveNnapiDelegationEnabled(
            DeviceCapabilities.hasNpuSupport()
        )
    }
    
    // Migrate memory mapping
    if (!prefs.contains("memory_mapping_enabled")) {
        userPreferences.saveMemoryMappingEnabled(true)
    }
    
    // Migrate auto-summarization
    if (!prefs.contains("auto_summarization_enabled")) {
        userPreferences.saveAutoSummarizationEnabled(true)
    }
    
    // Migrate isolated inference
    if (!prefs.contains("isolated_inference_enabled")) {
        userPreferences.saveIsolatedInferenceEnabled(
            BuildConfig.USE_ISOLATED_INFERENCE_ENGINE
        )
    }
    
    Log.d(TAG, "Legacy preferences migration complete")
}
```

#### Afternoon: Gradle Configuration Cleanup
```properties
# File: gradle.properties

android.useAndroidX=true
kotlin.code.style=official

# Gradle JVM args - increase memory for release builds
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g

# Performance optimizations
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.configureondemand=true
org.gradle.caching=true
ksp.incremental=false

# REMOVE THIS LINE (invalid paths):
# org.gradle.java.installations.paths=/home/anon3/.jdks/jdk-17,/home/anon3/.jdks/jdk-21
```

**Deliverables:**
- [ ] Migration logic implemented
- [ ] Test upgrade from clean install
- [ ] Verify no crashes on first launch
- [ ] Gradle warnings resolved
- [ ] Clean build on fresh checkout

---

## PHASE 2: HIGH PRIORITY (Days 5-7)

### Day 5: Testing & Code Quality

#### Tasks:
1. **Add Missing Unit Tests**
   ```kotlin
   // ConversationSummarizerTest.kt
   @Test
   fun `summarize should return structured summary`() {
       // Test implementation
   }
   
   // AutoLockManagerTest.kt
   @Test
   fun `lifecycle observer should clean up coroutines`() {
       // Test implementation
   }
   ```

2. **Resolve TODO Comments**
   - `AdaptiveChatLayout.kt:65` - Implement URI propagation
   - `CertificateErrorHandler.kt:88` - Add error logging
   - `CertificateErrorHandler.kt:98` - Implement notifications

3. **Standardize Logging**
   - Add logging to `AutoLockManager`
   - Ensure consistent TAG usage
   - Verify log levels appropriate

**Deliverables:**
- [ ] 70%+ code coverage achieved
- [ ] All TODO comments resolved
- [ ] Logging standardized
- [ ] Code quality metrics green

---

### Day 6: Feature Decisions

#### Morning: Biometric Auth Decision
**Options:**
1. **Implement Now** - Protect sensitive screens (2-3 days)
2. **Defer to v1.1** - Document as planned feature

**If Implementing:**
```kotlin
// Protect conversation history
@Composable
fun HistoryScreen() {
    BiometricGuard(
        onAuthenticated = {
            // Show history
        },
        onAuthenticationFailed = {
            // Show error
        }
    )
}
```

#### Afternoon: Export Formats Decision
**Options:**
1. **Implement Now** - PDF/Markdown export (3-4 days)
2. **Remove from Docs** - Defer to future release

**If Removing:**
- Update `README.md` line 72
- Update `CHANGELOG.md` line 43
- Add to roadmap for v1.1

**Deliverables:**
- [ ] Biometric auth decision made
- [ ] Export formats decision made
- [ ] Documentation updated accordingly

---

### Day 7: Certificate Verification & Validation

#### Tasks:
1. **Verify Certificate Pins**
   ```bash
   # Test each provider
   openssl s_client -connect api.openai.com:443 -showcerts
   openssl s_client -connect api.anthropic.com:443 -showcerts
   # ... etc for all providers
   
   # Extract and compare pins
   ```

2. **Add Settings Validation**
   ```kotlin
   // GenerationSettingsViewModel.kt
   fun setTemperature(value: Float) {
       require(value in 0.0f..2.0f) { 
           "Temperature must be 0.0-2.0" 
       }
       _temperature.value = value
   }
   
   fun setTopP(value: Float) {
       require(value in 0.0f..1.0f) { 
           "Top P must be 0.0-1.0" 
       }
       _topP.value = value
   }
   ```

**Deliverables:**
- [ ] All certificate pins verified current
- [ ] Settings validation implemented
- [ ] Input bounds tested
- [ ] Error messages user-friendly

---

## PHASE 3: FINAL VALIDATION (Days 8-9)

### Day 8: Release Build Testing

#### Morning: Build & Install
```bash
# 1. Clean build
./gradlew clean

# 2. Build release APK
./gradlew :app:assembleRelease

# 3. Install on test devices
adb install app/build/outputs/apk/release/app-release.apk

# 4. Test on multiple devices
# - Pixel (stock Android)
# - Samsung (OneUI)
# - OnePlus (OxygenOS)
```

#### Afternoon: Feature Testing Checklist
- [ ] Local LLM inference works
- [ ] Cloud provider connections work
- [ ] Conversation summarization triggers
- [ ] Share sheet accepts text/images
- [ ] Settings persist correctly
- [ ] Biometric auth (if implemented)
- [ ] Voice recognition works
- [ ] Widget displays correctly
- [ ] No crashes on rotation
- [ ] No memory leaks (LeakCanary)

---

### Day 9: Performance & Security Audit

#### Morning: Performance Profiling
```bash
# 1. Profile with Android Studio Profiler
# - CPU usage during inference
# - Memory usage over time
# - Network requests

# 2. Check for ANRs
# - Ensure no blocking operations on main thread

# 3. Verify ProGuard effectiveness
# - Check APK size reduction
# - Verify obfuscation in mapping.txt
```

#### Afternoon: Security Review
- [ ] No API keys in code
- [ ] All secrets in EncryptedSharedPreferences
- [ ] TLS pinning working
- [ ] PII masking functional
- [ ] Permissions justified
- [ ] Network security config enforced

**Deliverables:**
- [ ] Release APK fully tested
- [ ] Performance acceptable
- [ ] Security audit passed
- [ ] No critical issues found

---

## PHASE 4: DISTRIBUTION (Day 10)

### Morning: Final Preparation
```bash
# 1. Generate release notes
cat > release_notes.txt << EOF
ShadowAi v1.0.0 - Initial Release

Features:
- Local LLM inference via llama.cpp
- Multi-provider cloud AI support
- Conversation summarization
- Security-first architecture
- Material Design 3 UI

Known Limitations:
- [List any deferred features]
EOF

# 2. Build final release APK
./gradlew clean :app:assembleRelease

# 3. Sign APK (if not auto-signed)
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
```

### Afternoon: Upload to Play Console
1. **Internal Testing Track**
   - Upload APK to Google Play Console
   - Add internal testers
   - Monitor crash reports

2. **Gradual Rollout Plan**
   - Day 1: Internal testing (10 users)
   - Day 3: Closed alpha (50 users)
   - Day 7: Open beta (500 users)
   - Day 14: Production (gradual rollout)

**Deliverables:**
- [ ] Release APK uploaded
- [ ] Internal testing initiated
- [ ] Crash reporting verified
- [ ] Rollout plan documented

---

## Validation Gates

Each phase must pass validation before proceeding:

### Phase 1 Gate
- [ ] All critical blockers resolved
- [ ] Release APK builds successfully
- [ ] All unit tests passing
- [ ] No exposed secrets

### Phase 2 Gate
- [ ] 70%+ code coverage
- [ ] All high-priority issues resolved
- [ ] Feature decisions documented
- [ ] Certificate pins verified

### Phase 3 Gate
- [ ] Release build tested on 3+ devices
- [ ] No crashes in 1-hour test session
- [ ] Performance acceptable
- [ ] Security audit passed

### Phase 4 Gate
- [ ] Internal testing successful
- [ ] No critical bugs reported
- [ ] Crash-free rate > 99%
- [ ] Ready for public beta

---

## Emergency Rollback Plan

If critical issues discovered post-release:

1. **Immediate Actions**
   ```bash
   # Halt rollout in Play Console
   # Investigate crash reports
   # Identify root cause
   ```

2. **Hotfix Process**
   - Create hotfix branch
   - Fix critical issue
   - Test thoroughly
   - Release patch version

3. **Communication**
   - Notify users of issue
   - Provide timeline for fix
   - Update Play Store listing

---

## Success Criteria

**Production-Ready When:**
- [ ] All critical blockers resolved (8/8)
- [ ] All high-priority issues resolved (6/6)
- [ ] 70%+ code coverage
- [ ] Release build tested on 3+ devices
- [ ] Security audit passed
- [ ] No crashes in 1-hour test session
- [ ] Crash-free rate > 99% in internal testing
- [ ] Performance acceptable (< 3s cold start)
- [ ] All features documented accurately

---

## Resources

**Documentation:**
- [DEPLOYMENT_READINESS_REPORT.md](DEPLOYMENT_READINESS_REPORT.md)
- [CODE_AUDIT_REPORT.md](docs/CODE_AUDIT_REPORT.md)
- [README.md](README.md)

**Tools:**
- Android Studio Profiler
- LeakCanary (memory leaks)
- Firebase Crashlytics
- Google Play Console

**Support:**
- [Android Developer Documentation](https://developer.android.com/)
- [Firebase Documentation](https://firebase.google.com/docs)

---

**Last Updated:** 2026-02-11  
**Status:** ⚠️ IN PROGRESS  
**Next Review:** After Phase 1 completion
