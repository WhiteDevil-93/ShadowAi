# Pull Request Template

## 📋 PR Type
<!-- Check all that apply -->
- [ ] Feature (new functionality)
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] UI/UX change (affects user interface or experience)
- [ ] Refactor (code improvement without behavior change)
- [ ] Documentation
- [ ] Other (please describe):

---

## 📝 Description

### What does this PR do?
<!-- Provide a clear, concise description of the changes -->

### Why is this change needed?
<!-- Explain the problem this PR solves or the feature it adds -->

### Related Issues
<!-- Link to related issues: Fixes #123, Relates to #456 -->

---

## 🎨 UI/UX Changes (Required if UI/UX checkbox is checked)

### Screenshots/Videos
<!-- Provide before/after screenshots or screen recordings -->

**Before:**
<!-- Screenshot or description of previous state -->

**After:**
<!-- Screenshot or description of new state -->

### Governance Compliance
<!-- Complete this section for ANY UI/UX change -->

#### Automatic Rejection Check
I confirm this PR does NOT contain any of the following violations:
- [ ] API key UI for local provider
- [ ] Google login inside provider config
- [ ] Credits shown in chat UI
- [ ] Multiple primary actions at rest
- [ ] No focus mode for workflows
- [ ] Keyboard overlaps input
- [ ] Provider/model/runtime mixed in one surface
- [ ] Debug terms visible by default

#### Architecture Compliance
- [ ] Information architecture follows the defined hierarchy (Account → Providers → Local Runtime → Models → Credits)
- [ ] Identity and provider authentication are clearly separated
- [ ] Local and cloud providers are visually distinct

#### Specific Compliance (Check all that apply)

**If this PR modifies provider configuration:**
- [ ] Cloud providers show: API key field, Save button, Test button, Status indicator
- [ ] Local providers show: Runtime status, Host, Port, Test connection, Installed models
- [ ] Local providers DO NOT show: API key field, OAuth, Credits, "Get API Key" links
- [ ] API keys stored in `EncryptedSharedPreferences` only

**If this PR modifies chat UI:**
- [ ] Messages are visually dominant (80% weight)
- [ ] System feedback is muted (15% weight)
- [ ] Controls are secondary (5% weight)
- [ ] Errors shown as inline pills (AssistChip), not full-width messages
- [ ] Retry actions clearly visible
- [ ] Floating chat tabs implemented (Chat/Write/Call/Image)
- [ ] Tabs auto-hide on scroll down, reappear on scroll up

**If this PR adds a workflow (Write/Call/Image/Settings):**
- [ ] Workflow uses focus mode (full-screen dedication)
- [ ] Chat list hidden during workflow
- [ ] Floating tabs hidden during workflow
- [ ] Single back/exit affordance present
- [ ] No background UI bleed-through

**If this PR modifies credits/usage:**
- [ ] Credits only shown in Settings → Usage & Credits
- [ ] Never shown in chat UI
- [ ] Never shown in provider selectors
- [ ] Clearly labeled as "Reported (API)" or "Estimated (local)"
- [ ] Read-only display

**If this PR adds/modifies input fields:**
- [ ] Input uses `Modifier.imePadding()`
- [ ] Bottom content uses `Modifier.navigationBarsPadding()`
- [ ] Top content uses `Modifier.statusBarsPadding()`
- [ ] `WindowCompat.setDecorFitsSystemWindows(window, false)` set in Activity
- [ ] Tested with keyboard visible
- [ ] No hardcoded padding for system UI

**Material 3 Compliance:**
- [ ] All colors from `MaterialTheme.colorScheme`
- [ ] All text uses `MaterialTheme.typography`
- [ ] All elevation uses Material 3 tokens
- [ ] No hardcoded `Color()` values
- [ ] No custom `fontSize` or `fontWeight` without theme

### Test Matrix
<!-- Check all configurations tested -->
- [ ] Phone portrait
- [ ] Phone landscape
- [ ] Tablet portrait
- [ ] Tablet landscape
- [ ] Keyboard visible
- [ ] 3-button navigation
- [ ] Gesture navigation

---

## 🧪 Testing

### How was this tested?
<!-- Describe your testing approach -->

### Test Coverage
- [ ] Unit tests added/updated
- [ ] UI tests added/updated
- [ ] Manual testing completed
- [ ] Regression testing completed

---

## 📚 Documentation

- [ ] Code comments added/updated
- [ ] README updated (if applicable)
- [ ] Architecture docs updated (if applicable)
- [ ] API documentation updated (if applicable)

---

## ✅ Pre-Merge Checklist

### Code Quality
- [ ] Code follows project style guidelines
- [ ] No new compiler warnings
- [ ] No new lint errors
- [ ] Build succeeds locally
- [ ] All tests pass

### Governance (UI/UX changes only)
- [ ] I have read the UI Orchestrator System Prompt
- [ ] I have read GOVERNANCE.md
- [ ] I have completed all relevant governance sections above
- [ ] I have documented any deviations with architectural justification

### Review
- [ ] Self-review completed
- [ ] Ready for team review

---

## 🔍 Reviewer Notes

### For Reviewers

**Automatic Rejection Check:**
- [ ] Verified zero automatic rejection violations

**Architecture Verification:**
- [ ] Information architecture compliance confirmed
- [ ] Identity/provider separation maintained
- [ ] Local/cloud provider distinction clear

**UX Pattern Review:**
- [ ] Relevant governance sections verified
- [ ] Screenshots demonstrate compliance
- [ ] Test matrix completed

**Material 3 Audit:**
- [ ] Color usage verified
- [ ] Typography usage verified
- [ ] Elevation usage verified

**System Integration:**
- [ ] Keyboard handling verified
- [ ] System insets handling verified
- [ ] Tested on device (if possible)

**Approval Criteria Met:**
- [ ] Zero automatic rejection violations
- [ ] All relevant checklist items verified
- [ ] Screenshots demonstrate compliance
- [ ] Test matrix completed

---

## 💬 Additional Context
<!-- Add any other context, concerns, or questions here -->

---

**Reference Documents:**
- [UI Orchestrator System Prompt](./UI_ORCHESTRATOR_SYSTEM_PROMPT.md)
- [Governance Framework](./GOVERNANCE.md)
- [Compliance Checklist](./UI_ORCHESTRATOR_COMPLIANCE_CHECKLIST.md)
- [Regression Test Matrix](./UI_REGRESSION_TEST_MATRIX.md)
