# UI/UX Governance Framework
## DarcyAI Android Application

**Status**: Active Enforcement  
**Last Updated**: 2026-01-28  
**Authority**: UI Orchestrator System Prompt

---

## 🎯 Purpose

This document translates the UI Orchestrator System Prompt into **actionable governance rules** for developers, reviewers, and designers. Every UI change must pass these checks before merge.

---

## 📋 Pre-Merge Checklist

Before submitting any PR that touches UI/UX, the author must verify:

- [ ] I have read the UI Orchestrator System Prompt
- [ ] I have completed the relevant sections of this checklist
- [ ] I have documented any deviations with architectural justification
- [ ] I have tested on multiple screen sizes (phone, tablet, landscape)
- [ ] I have tested with keyboard visible
- [ ] I have tested with 3-button and gesture navigation

---

## 🚫 Automatic Rejection Criteria

If **ANY** of these are present, the PR is rejected immediately:

| Violation | Example | Why It Fails |
|-----------|---------|--------------|
| **API key UI for local provider** | TextField for API key in Liquid AI Local settings | Local runtimes never use API keys |
| **Google login inside provider config** | "Sign in with Google" button in OpenAI settings | Identity ≠ Provider authentication |
| **Credits shown in chat UI** | "15 credits remaining" message in conversation | Credits are Settings-only |
| **Multiple primary actions at rest** | Two FABs visible simultaneously | Violates single-action principle |
| **No focus mode for workflows** | Image generation UI shares screen with chat list | Workflows require full-screen dedication |
| **Keyboard overlaps input** | TextField covered when IME appears | Production-blocking UX failure |
| **Provider/model/runtime mixed** | Dropdown showing "OpenAI GPT-4" and "Ollama llama2" together | Violates separation of concerns |
| **Debug terms visible by default** | "KSP processing", "Hilt injection" in UI | Architecture leakage |

---

## 🏗️ Architecture Enforcement

### Information Architecture (Non-Negotiable)

```
Account (User Identity)
 └── Google login only
 └── Used for: sync, backup, personalization
 └── NEVER for provider authentication

Providers (Cloud APIs)
 ├── OpenAI, Anthropic, Gemini, etc.
 └── Require API keys or OAuth

Local Runtime
 ├── Ollama, Liquid AI Local, llama.cpp
 └── Host + port, NO API keys

Models
 └── Belong to providers/runtimes, NOT users

Usage & Credits
 └── Read-only, per-provider, Settings-only
```

**Verification Questions:**
1. Can a user confuse their Google account with a provider API key? → **FAIL**
2. Does the UI suggest models are tied to user identity? → **FAIL**
3. Are local and cloud providers visually identical? → **FAIL**

---

## 🔐 API Key Handling (Cloud Only)

### Required UX Elements

Every cloud provider configuration screen MUST have:

```kotlin
// ✅ CORRECT
Column {
    OutlinedTextField(
        value = apiKey,
        onValueChange = { /* update state */ },
        label = { Text("API Key") },
        visualTransformation = PasswordVisualTransformation()
    )
    
    Row {
        Button(onClick = { saveApiKey() }) { Text("Save") }
        Button(onClick = { testConnection() }) { Text("Test") }
    }
    
    StatusIndicator(status = connectionStatus) // Saved/Valid/Invalid
}
```

### Forbidden Patterns

```kotlin
// ❌ WRONG - Auto-save
LaunchedEffect(apiKey) {
    if (apiKey.isNotBlank()) saveApiKey() // NO!
}

// ❌ WRONG - Silent persistence
TextField(
    value = apiKey,
    onValueChange = { 
        viewModel.updateApiKey(it) // Saves immediately - NO!
    }
)

// ❌ WRONG - No status indicator
// User has no feedback about key validity
```

**Review Checkpoint:**
- [ ] Explicit Save button present
- [ ] Explicit Test button present
- [ ] Status indicator shows: Saved / Valid / Invalid
- [ ] No auto-save behavior
- [ ] Keys stored in `EncryptedSharedPreferences` only

---

## 🏠 Local Runtime Handling

### Required UX Elements

Local provider screens MUST show:

```kotlin
// ✅ CORRECT
Column {
    Text("Runtime Status: ${if (connected) "Connected" else "Not Running"}")
    
    OutlinedTextField(
        value = host,
        onValueChange = { /* update */ },
        label = { Text("Host") }
    )
    
    OutlinedTextField(
        value = port,
        onValueChange = { /* update */ },
        label = { Text("Port") }
    )
    
    Button(onClick = { testConnection() }) { Text("Test Connection") }
    
    LazyColumn {
        items(installedModels) { model ->
            ModelCard(model)
        }
    }
}
```

### Forbidden Elements for Local Providers

```kotlin
// ❌ WRONG - API key field
OutlinedTextField(label = { Text("API Key") }) // NO!

// ❌ WRONG - OAuth button
Button(onClick = { signInWithProvider() }) { Text("Sign In") } // NO!

// ❌ WRONG - Credit usage
Text("Credits remaining: $credits") // NO!

// ❌ WRONG - "Get API Key" link
TextButton(onClick = { openBrowser() }) { Text("Get API Key") } // NO!
```

**Review Checkpoint:**
- [ ] No API key field
- [ ] No OAuth/sign-in buttons
- [ ] No credit/usage display
- [ ] Shows runtime status (Connected/Not Running)
- [ ] Shows host + port configuration
- [ ] Shows installed models list

---

## 💰 Credits & Usage Tracking

### Location Rule

Credits MUST only appear in: `Settings → Usage & Credits`

### Required Display Format

```kotlin
// ✅ CORRECT
@Composable
fun ProviderUsageCard(provider: Provider) {
    Card {
        Column {
            Text(provider.name, style = MaterialTheme.typography.titleMedium)
            
            Row {
                Text("Used:")
                Text(provider.usedAmount)
            }
            
            Row {
                Text("Remaining:")
                Text(provider.remainingCredits)
            }
            
            Text(
                "Last updated: ${provider.lastUpdated}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Clearly label data source
            Text(
                if (provider.isCloudReported) "Reported by API" else "Estimated (local calculation)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
```

### Forbidden Locations

```kotlin
// ❌ WRONG - In chat UI
ChatMessage(
    text = "Response generated (5 credits used)" // NO!
)

// ❌ WRONG - In provider selector
DropdownMenuItem(
    text = { Text("OpenAI (15 credits left)") } // NO!
)

// ❌ WRONG - As toast/snackbar during chat
Snackbar { Text("Low credits warning") } // NO!
```

**Review Checkpoint:**
- [ ] Credits only in Settings → Usage & Credits
- [ ] Never shown in chat UI
- [ ] Never shown in provider selectors
- [ ] Clearly labeled as "Reported" or "Estimated"
- [ ] Read-only display (no inline top-up)

---

## 💬 Chat UI - Dominance & Simplicity

### Visual Hierarchy (Mandatory)

```
1. Messages (dominant) ← 80% visual weight
2. Inline system feedback (muted) ← 15% visual weight
3. Controls (secondary) ← 5% visual weight
```

### Error Presentation

```kotlin
// ✅ CORRECT - Inline error pill
@Composable
fun ErrorPill(error: String, onRetry: () -> Unit) {
    AssistChip(
        onClick = { /* expand details */ },
        label = { Text(error, maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Error, null) },
        trailingIcon = { 
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, "Retry")
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    )
}

// ❌ WRONG - Full-width chat message
ChatMessage(
    text = "Error: API rate limit exceeded. Click here to retry.",
    isError = true // NO! Not a chat message
)

// ❌ WRONG - Dialog for non-critical error
AlertDialog(
    title = { Text("Minor Error") }, // NO! Use inline pill
    text = { Text("Request timed out") }
)
```

**Review Checkpoint:**
- [ ] Errors shown as inline pills (AssistChip)
- [ ] Pills attached to relevant message
- [ ] Retry action clearly visible
- [ ] Details hidden behind expansion
- [ ] No full-width error messages in chat
- [ ] Critical errors use dialog, transient errors use pills

---

## 🎨 Floating Chat Tabs (Required)

### Implementation

```kotlin
// ✅ CORRECT
@Composable
fun ChatScreen() {
    val scrollState = rememberLazyListState()
    val showTabs by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex == 0 || 
            scrollState.isScrollingUp()
        }
    }
    
    Scaffold {
        Box {
            LazyColumn(state = scrollState) {
                // Chat messages
            }
            
            AnimatedVisibility(
                visible = showTabs,
                enter = slideInVertically(),
                exit = slideOutVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == ChatMode.CHAT,
                        onClick = { mode = ChatMode.CHAT },
                        label = { Text("Chat") }
                    )
                    FilterChip(
                        selected = mode == ChatMode.WRITE,
                        onClick = { mode = ChatMode.WRITE },
                        label = { Text("Write") }
                    )
                    FilterChip(
                        selected = mode == ChatMode.CALL,
                        onClick = { mode = ChatMode.CALL },
                        label = { Text("Call") }
                    )
                    FilterChip(
                        selected = mode == ChatMode.IMAGE,
                        onClick = { mode = ChatMode.IMAGE },
                        label = { Text("Image") }
                    )
                }
            }
        }
    }
}

// ❌ WRONG - In TopAppBar
TopAppBar(
    title = { 
        Row {
            Tab(text = { Text("Chat") }) // NO! Not in app bar
            Tab(text = { Text("Write") })
        }
    }
)

// ❌ WRONG - Bottom navigation
BottomNavigation {
    BottomNavigationItem(label = { Text("Chat") }) // NO! These are modes, not destinations
}
```

**Review Checkpoint:**
- [ ] Tabs float above chat content
- [ ] Auto-hide on scroll down
- [ ] Reappear on scroll up or tap
- [ ] Use FilterChip or AssistChip styling
- [ ] NOT in TopAppBar
- [ ] NOT in BottomNavigation

---

## 🎯 Focus Mode - Single-Window Dedication

### When Focus Mode Applies

- Write workflow
- Call workflow
- Image generation
- Model management
- Advanced settings

### Required Behavior

```kotlin
// ✅ CORRECT - Full-screen dedication
@Composable
fun ImageGenerationScreen(onExit: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Generation") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.Close, "Exit")
                    }
                }
            )
        }
    ) {
        // ONLY image generation UI
        // NO chat list
        // NO floating tabs
        // NO global controls
    }
}

// ❌ WRONG - Split attention
Row {
    ChatList(modifier = Modifier.weight(0.3f)) // NO! Hidden in focus mode
    ImageGenerationUI(modifier = Modifier.weight(0.7f))
}

// ❌ WRONG - Background UI visible
Box {
    ChatScreen() // NO! Completely hidden
    ImageGenerationOverlay() // Should be full-screen, not overlay
}
```

**Review Checkpoint:**
- [ ] Workflow occupies entire screen
- [ ] Chat list hidden
- [ ] Floating tabs hidden
- [ ] Global controls hidden
- [ ] Single back/exit affordance visible
- [ ] No split attention
- [ ] No background UI bleed-through

---

## ⌨️ Keyboard & System Insets (Production-Blocking)

### Mandatory Modifiers

```kotlin
// ✅ CORRECT - Input field with IME padding
@Composable
fun ChatInputField() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding() // MANDATORY
            .navigationBarsPadding() // MANDATORY
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ✅ CORRECT - Full screen with system bars
@Composable
fun ChatScreen() {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(), // MANDATORY
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding() // MANDATORY
            )
        }
    ) {
        // Content
    }
}

// ❌ WRONG - No IME padding
OutlinedTextField(
    modifier = Modifier.fillMaxWidth() // NO! Missing imePadding()
)

// ❌ WRONG - Hardcoded padding
Box(
    modifier = Modifier.padding(bottom = 48.dp) // NO! Use navigationBarsPadding()
)
```

### Required Activity Setup

```kotlin
// ✅ CORRECT - In onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    WindowCompat.setDecorFitsSystemWindows(window, false) // MANDATORY
    
    setContent {
        ShadowAITheme {
            // UI
        }
    }
}
```

**Review Checkpoint:**
- [ ] `WindowCompat.setDecorFitsSystemWindows(window, false)` in Activity
- [ ] Input fields use `Modifier.imePadding()`
- [ ] Bottom content uses `Modifier.navigationBarsPadding()`
- [ ] Top content uses `Modifier.statusBarsPadding()`
- [ ] Full screen uses `Modifier.systemBarsPadding()`
- [ ] No hardcoded padding for system UI
- [ ] Tested with keyboard visible
- [ ] Tested with 3-button and gesture navigation

---

## 🎨 Material 3 Compliance (Mandatory)

### Color Usage

```kotlin
// ✅ CORRECT
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
)

// ❌ WRONG
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF6200EE) // NO! Use theme colors
    )
)
```

### Typography

```kotlin
// ✅ CORRECT
Text(
    text = "Headline",
    style = MaterialTheme.typography.headlineMedium
)

// ❌ WRONG
Text(
    text = "Headline",
    fontSize = 24.sp, // NO! Use typography scale
    fontWeight = FontWeight.Bold
)
```

### Elevation

```kotlin
// ✅ CORRECT
Card(
    elevation = CardDefaults.cardElevation(
        defaultElevation = 2.dp // Use Material 3 tokens
    )
)

// ❌ WRONG
Card(
    modifier = Modifier.shadow(elevation = 8.dp) // NO! Use CardDefaults
)
```

**Review Checkpoint:**
- [ ] All colors from `MaterialTheme.colorScheme`
- [ ] All text uses `MaterialTheme.typography`
- [ ] All elevation uses Material 3 tokens
- [ ] No hardcoded `Color()` values
- [ ] No custom `fontSize` or `fontWeight`
- [ ] No arbitrary `shadow()` modifiers

---

## 📝 Code Review Process

### For Authors

1. **Self-Review**: Complete relevant checklist sections
2. **Screenshot**: Provide before/after screenshots for UI changes
3. **Test Matrix**: Document tested configurations:
   - [ ] Phone portrait
   - [ ] Phone landscape
   - [ ] Tablet portrait
   - [ ] Tablet landscape
   - [ ] Keyboard visible
   - [ ] 3-button navigation
   - [ ] Gesture navigation

### For Reviewers

1. **Automatic Rejection Check**: Scan for violations in "Automatic Rejection Criteria"
2. **Architecture Verification**: Confirm information architecture compliance
3. **UX Pattern Review**: Verify against relevant governance sections
4. **Material 3 Audit**: Check color/typography/elevation usage
5. **System Integration**: Verify keyboard and insets handling

### Approval Criteria

A PR is approved when:
- [ ] Zero automatic rejection violations
- [ ] All relevant checklist items verified
- [ ] Screenshots demonstrate compliance
- [ ] Test matrix completed
- [ ] Reviewer has verified on device (if possible)

---

## 🔄 Governance Updates

This document is living and may be updated. Changes require:

1. **Proposal**: Document proposed change with rationale
2. **Review**: Team discussion and consensus
3. **Update**: Modify this document
4. **Communication**: Announce changes to team
5. **Enforcement**: Apply to all new PRs immediately

---

## 📚 Reference Documents

- **UI Orchestrator System Prompt**: `/UI_ORCHESTRATOR_SYSTEM_PROMPT.md` (authoritative source)
- **Compliance Checklist**: `/UI_ORCHESTRATOR_COMPLIANCE_CHECKLIST.md`
- **Regression Test Matrix**: `/UI_REGRESSION_TEST_MATRIX.md`
- **Material 3 Guidelines**: https://m3.material.io/

---

## ✅ Success Definition

The app must feel:
- **Calm** - No visual chaos or competing elements
- **Intentional** - Every UI decision has clear purpose
- **Native** - Feels like a first-party Android app
- **Trustworthy** - Professional, secure, reliable
- **Professional** - Production-grade, not experimental

The app must NOT feel like:
- A developer console
- An operator dashboard
- An experimental tool
- A debug interface

---

**Enforcement Authority**: This document supersedes individual design decisions. When in doubt, this document wins.
