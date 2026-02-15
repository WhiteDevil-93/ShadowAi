# ShadowAi - Production Readiness Checklist

**Last Updated:** 2026-02-11  
**Status:** ⚠️ IN PROGRESS  

---

## 🔴 CRITICAL BLOCKERS (Must Complete Before Release)

### 1. Release Signing Configuration
- [ ] Generate production keystore (`keytool -genkey`)
- [ ] Update `local.properties` with real credentials
- [ ] Test release build: `./gradlew :app:assembleRelease`
- [ ] Verify APK signature: `jarsigner -verify`
- [ ] Backup keystore to secure location
- [ ] Document keystore password in password manager

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 2. Firebase Security Fix (URGENT)
- [ ] Rotate Firebase API key in console
- [ ] Remove `google-services.json` from git history
- [ ] Add `google-services.json` to `.gitignore`
- [ ] Verify key not in any commit
- [ ] Set up Firebase App Check
- [ ] Configure usage quotas and alerts

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 3. Test Suite Fixes
- [ ] Run `./gradlew test --continue` to identify failures
- [ ] Fix all failing unit tests
- [ ] Verify all tests pass: `./gradlew test`
- [ ] Check test coverage: `./gradlew koverHtmlReport`
- [ ] Achieve minimum 70% coverage on critical paths

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 4. Conversation Summarization Integration
- [ ] Wire `SummaryViewModel` into `ChatScreen.kt`
- [ ] Add `SummaryIndicator` to chat UI
- [ ] Connect auto-trigger to message flow
- [ ] Test summarization end-to-end
- [ ] Verify summary persistence works

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 5. Share Sheet Intent Handling
- [ ] Implement `handleSendIntent()` in `ComposeMainActivity.kt`
- [ ] Handle `ACTION_SEND` for text
- [ ] Handle `ACTION_SEND` for images
- [ ] Handle `ACTION_SEND_MULTIPLE` for multiple images
- [ ] Test sharing from Chrome (text)
- [ ] Test sharing from Gallery (images)

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 6. AutoLockManager Lifecycle Fix
- [ ] Implement `DefaultLifecycleObserver`
- [ ] Add `onStop()` cleanup logic
- [ ] Cancel coroutines properly
- [ ] Test with LeakCanary
- [ ] Verify no memory leaks

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 7. UserPreferences Migration
- [ ] Implement `migrateLegacyPreferences()` in `ShadowApplication.kt`
- [ ] Migrate NNAPI setting
- [ ] Migrate memory mapping setting
- [ ] Migrate auto-summarization setting
- [ ] Migrate isolated inference setting
- [ ] Test upgrade from clean install
- [ ] Verify no crashes on first launch

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 8. Gradle Configuration Cleanup
- [ ] Remove invalid JDK paths from `gradle.properties`
- [ ] Test build on clean environment
- [ ] Verify no warnings in build output
- [ ] Document required build environment

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

## 🟡 HIGH PRIORITY (Should Complete Before Release)

### 9. Missing Unit Tests
- [ ] Create `ConversationSummarizerTest.kt`
- [ ] Create `AutoLockManagerTest.kt`
- [ ] Add tests for share sheet handling
- [ ] Add tests for preferences migration
- [ ] Verify 70%+ code coverage

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 10. TODO Comment Resolution
- [ ] Fix `AdaptiveChatLayout.kt:65` - URI propagation
- [ ] Fix `CertificateErrorHandler.kt:88` - Error logging
- [ ] Fix `CertificateErrorHandler.kt:98` - Notification implementation
- [ ] Verify no remaining TODOs in production code

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 11. Logging Standardization
- [ ] Add logging to `AutoLockManager.kt`
- [ ] Verify consistent TAG usage across all files
- [ ] Ensure appropriate log levels (DEBUG, INFO, WARN, ERROR)
- [ ] Remove verbose logging from release builds

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 12. Certificate Pin Verification
- [ ] Verify OpenAI certificate pin
- [ ] Verify Anthropic certificate pin
- [ ] Verify Google Gemini certificate pin
- [ ] Verify OpenRouter certificate pin
- [ ] Verify DeepSeek certificate pin
- [ ] Verify Mistral AI certificate pin
- [ ] Verify Groq certificate pin
- [ ] Verify xAI certificate pin
- [ ] Test network connectivity to all providers

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 13. Settings Validation
- [ ] Add temperature bounds checking (0.0-2.0)
- [ ] Add top-p bounds checking (0.0-1.0)
- [ ] Add top-k bounds checking (1-200)
- [ ] Add max tokens validation
- [ ] Add user-friendly error messages
- [ ] Test invalid input handling

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 14. Feature Decision: Biometric Auth
- [ ] **Decision:** ☐ Implement Now  ☐ Defer to v1.1
- [ ] If implementing: Wire to UI screens
- [ ] If implementing: Test on multiple devices
- [ ] If deferring: Update documentation
- [ ] If deferring: Add to v1.1 roadmap

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

## 🟠 MEDIUM PRIORITY (Nice to Have)

### 15. Feature Decision: Export Formats
- [ ] **Decision:** ☐ Implement Now  ☐ Defer to v1.1
- [ ] If implementing: PDF export
- [ ] If implementing: Markdown export
- [ ] If deferring: Remove from README.md
- [ ] If deferring: Remove from CHANGELOG.md

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 16. ProGuard Testing
- [ ] Build release APK
- [ ] Install on test device
- [ ] Test all features in release build
- [ ] Verify no crashes from obfuscation
- [ ] Check mapping.txt for proper obfuscation
- [ ] Verify APK size reduction

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 17. Crash Reporting Verification
- [ ] Verify Crashlytics initialization
- [ ] Force test crash in debug build
- [ ] Verify crash appears in Firebase console
- [ ] Set up crash alerts
- [ ] Configure crash-free rate monitoring

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 18. Voice Activation Decision
- [ ] **Decision:** ☐ Complete Now  ☐ Defer to v1.1
- [ ] If completing: Wire to chat UI
- [ ] If completing: Implement hotword training
- [ ] If deferring: Update documentation

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

## 🔵 VALIDATION & TESTING

### 19. Release Build Testing
- [ ] Test on Pixel device (stock Android)
- [ ] Test on Samsung device (OneUI)
- [ ] Test on OnePlus device (OxygenOS)
- [ ] Test local LLM inference
- [ ] Test cloud provider connections
- [ ] Test conversation summarization
- [ ] Test share sheet functionality
- [ ] Test settings persistence
- [ ] Test widget functionality
- [ ] Test voice recognition
- [ ] Test rotation handling
- [ ] Run LeakCanary for memory leaks

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 20. Performance Profiling
- [ ] Profile CPU usage during inference
- [ ] Profile memory usage over time
- [ ] Profile network requests
- [ ] Verify no ANRs (blocking main thread)
- [ ] Measure cold start time (target < 3s)
- [ ] Measure APK size (target < 50MB)

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 21. Security Audit
- [ ] Verify no API keys in code
- [ ] Verify all secrets in EncryptedSharedPreferences
- [ ] Verify TLS pinning working
- [ ] Verify PII masking functional
- [ ] Verify all permissions justified
- [ ] Verify network security config enforced
- [ ] Run security scanner (e.g., MobSF)

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

## 📋 DISTRIBUTION PREPARATION

### 22. Release Notes
- [ ] Write user-facing release notes
- [ ] List key features
- [ ] List known limitations
- [ ] Prepare screenshots for Play Store
- [ ] Prepare feature graphic
- [ ] Write app description

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 23. Play Store Listing
- [ ] Create Play Console account (if needed)
- [ ] Set up app listing
- [ ] Upload screenshots
- [ ] Upload feature graphic
- [ ] Write app description
- [ ] Set content rating
- [ ] Configure pricing & distribution

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

### 24. Internal Testing
- [ ] Upload APK to internal testing track
- [ ] Add internal testers (10 users)
- [ ] Monitor crash reports (24 hours)
- [ ] Collect feedback
- [ ] Fix critical issues
- [ ] Verify crash-free rate > 99%

**Owner:** _____________  
**Due Date:** _____________  
**Status:** ❌ NOT STARTED

---

## 📊 PROGRESS TRACKING

### Overall Progress
- **Critical Blockers:** 0/8 complete (0%)
- **High Priority:** 0/6 complete (0%)
- **Medium Priority:** 0/4 complete (0%)
- **Validation:** 0/3 complete (0%)
- **Distribution:** 0/3 complete (0%)

**Total:** 0/24 complete (0%)

---

### Phase Completion

#### Phase 1: Critical Blockers (Days 1-4)
- [ ] All 8 critical blockers resolved
- [ ] Release APK builds successfully
- [ ] All unit tests passing
- [ ] No exposed secrets

**Status:** ❌ NOT STARTED  
**Target Date:** _____________

---

#### Phase 2: High Priority (Days 5-7)
- [ ] All 6 high-priority issues resolved
- [ ] 70%+ code coverage achieved
- [ ] Feature decisions documented
- [ ] Certificate pins verified

**Status:** ❌ NOT STARTED  
**Target Date:** _____________

---

#### Phase 3: Validation (Days 8-9)
- [ ] Release build tested on 3+ devices
- [ ] No crashes in 1-hour test session
- [ ] Performance acceptable
- [ ] Security audit passed

**Status:** ❌ NOT STARTED  
**Target Date:** _____________

---

#### Phase 4: Distribution (Day 10)
- [ ] Internal testing successful
- [ ] No critical bugs reported
- [ ] Crash-free rate > 99%
- [ ] Ready for public beta

**Status:** ❌ NOT STARTED  
**Target Date:** _____________

---

## 🎯 DEFINITION OF DONE

### Critical Blockers
- [x] Issue identified and documented
- [ ] Solution implemented
- [ ] Unit tests added/updated
- [ ] Manual testing completed
- [ ] Code reviewed
- [ ] Merged to main branch

### High Priority
- [x] Issue identified and documented
- [ ] Solution implemented or decision documented
- [ ] Testing completed
- [ ] Documentation updated

### Medium Priority
- [x] Issue identified
- [ ] Decision made (implement vs defer)
- [ ] If implementing: completed and tested
- [ ] If deferring: documented in roadmap

---

## 📝 NOTES & BLOCKERS

### Current Blockers
_Document any blockers preventing progress_

1. _______________________________________________
2. _______________________________________________
3. _______________________________________________

### Decisions Needed
_Document decisions required from stakeholders_

1. _______________________________________________
2. _______________________________________________
3. _______________________________________________

### Risks & Concerns
_Document any risks or concerns_

1. _______________________________________________
2. _______________________________________________
3. _______________________________________________

---

## 📞 CONTACTS

**Engineering Lead:** _____________  
**QA Lead:** _____________  
**Product Manager:** _____________  
**Release Manager:** _____________  

---

**Last Updated:** 2026-02-11  
**Next Review:** _____________  
**Status:** ⚠️ IN PROGRESS
