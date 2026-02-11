# UI/UX Quick Reference Card
**DarcyAI Android - Developer Cheat Sheet**

---

## 🚨 Instant Rejection Checklist

Before committing UI code, verify NONE of these exist:

```
[ ] API key field in local provider screen
[ ] Google login in provider config
[ ] Credits displayed in chat
[ ] Multiple FABs/primary actions visible
[ ] Workflow without focus mode
[ ] Input field missing imePadding()
[ ] Mixed provider/model/runtime in one list
[ ] Debug terms (e.g., "Hilt", "KSP") visible to user
```

If ANY box is checked → **STOP. Fix before commit.**

---

## 🏗️ Information Architecture

```
Account          → Google login (sync/backup ONLY)
Providers (Cloud)→ OpenAI, Anthropic (need API keys)
Local Runtime    → Ollama, llama.cpp (need host/port)
Models           → Belong to providers, NOT users
Credits          → Settings only, NEVER in chat
```

---

## 🔐 Provider Configuration

### Cloud Provider (OpenAI, Anthropic, etc.)
```kotlin
OutlinedTextField(
    label = { Text("API Key") },
    visualTransformation = PasswordVisualTransformation()
)
Button(onClick = { save() }) { Text("Save") }
Button(onClick = { test() }) { Text("Test") }
StatusIndicator() // Saved/Valid/Invalid
```

### Local Runtime (Ollama, llama.cpp)
```kotlin
Text("Status: ${if (connected) "Connected" else "Not Running"}")
OutlinedTextField(label = { Text("Host") })
OutlinedTextField(label = { Text("Port") })
Button(onClick = { test() }) { Text("Test Connection") }
LazyColumn { items(models) { ModelCard(it) } }
// NO API KEY FIELD!
```

---

## 💬 Chat UI Rules

### Visual Hierarchy
```
Messages:        80% visual weight (dominant)
System feedback: 15% visual weight (muted)
Controls:         5% visual weight (secondary)
```

### Error Display
```kotlin
// ✅ Transient errors
AssistChip(
    label = { Text(error) },
    leadingIcon = { Icon(Icons.Default.Error, null) },
    trailingIcon = { IconButton(onClick = retry) { Icon(Icons.Default.Refresh, null) } }
)

// ✅ Critical errors
AlertDialog(
    title = { Text("Error") },
    text = { Text(error) },
    confirmButton = { Button(onClick = fix) { Text("Fix") } }
)

// ❌ NEVER as chat message
ChatMessage(text = "Error: ...") // NO!
```

---

## 🏷️ Floating Chat Tabs

```kotlin
// ✅ CORRECT - Floating above content
AnimatedVisibility(visible = showTabs) {
    Row {
        FilterChip(selected = mode == CHAT, onClick = { mode = CHAT }, label = { Text("Chat") })
        FilterChip(selected = mode == WRITE, onClick = { mode = WRITE }, label = { Text("Write") })
        FilterChip(selected = mode == CALL, onClick = { mode = CALL }, label = { Text("Call") })
        FilterChip(selected = mode == IMAGE, onClick = { mode = IMAGE }, label = { Text("Image") })
    }
}

// ❌ WRONG - In TopAppBar or BottomNavigation
TopAppBar(title = { Row { Tab(...) } }) // NO!
BottomNavigation { BottomNavigationItem(...) } // NO!
```

---

## 🎯 Focus Mode

```kotlin
// ✅ CORRECT - Full-screen dedication
@Composable
fun WorkflowScreen(onExit: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflow") },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.Close, null) } }
            )
        }
    ) {
        // ONLY workflow UI - NO chat list, NO tabs, NO global controls
    }
}

// ❌ WRONG - Split screen
Row {
    ChatList(Modifier.weight(0.3f)) // NO!
    WorkflowUI(Modifier.weight(0.7f))
}
```

---

## ⌨️ Keyboard & Insets (MANDATORY)

```kotlin
// In Activity onCreate()
WindowCompat.setDecorFitsSystemWindows(window, false)

// Input fields
Box(
    modifier = Modifier
        .imePadding()              // MANDATORY
        .navigationBarsPadding()   // MANDATORY
) {
    OutlinedTextField(...)
}

// Full screen
Scaffold(
    modifier = Modifier.systemBarsPadding() // MANDATORY
) { ... }

// Top content
TopAppBar(modifier = Modifier.statusBarsPadding()) // MANDATORY
```

---

## 🎨 Material 3 Compliance

```kotlin
// ✅ Colors
MaterialTheme.colorScheme.primary
MaterialTheme.colorScheme.onPrimary
MaterialTheme.colorScheme.error

// ✅ Typography
MaterialTheme.typography.headlineMedium
MaterialTheme.typography.bodyLarge

// ✅ Elevation
CardDefaults.cardElevation(defaultElevation = 2.dp)

// ❌ NEVER
Color(0xFF6200EE)           // NO! Use theme
fontSize = 24.sp            // NO! Use typography
Modifier.shadow(8.dp)       // NO! Use CardDefaults
```

---

## 💰 Credits (Settings Only!)

```kotlin
// ✅ CORRECT - In Settings → Usage & Credits
@Composable
fun UsageScreen() {
    LazyColumn {
        items(providers) { provider ->
            Card {
                Text(provider.name)
                Text("Used: ${provider.used}")
                Text("Remaining: ${provider.remaining}")
                Text("Last updated: ${provider.lastUpdated}")
                Text(if (provider.isReported) "Reported by API" else "Estimated")
            }
        }
    }
}

// ❌ WRONG - In chat
ChatMessage(text = "Response (5 credits used)") // NO!
Snackbar { Text("Low credits") } // NO!
```

---

## 📝 Test Matrix (Before PR)

```
[ ] Phone portrait
[ ] Phone landscape
[ ] Tablet portrait
[ ] Tablet landscape
[ ] Keyboard visible
[ ] 3-button navigation
[ ] Gesture navigation
```

---

## 📚 Reference Docs

- **System Prompt**: `/UI_ORCHESTRATOR_SYSTEM_PROMPT.md`
- **Governance**: `/GOVERNANCE.md`
- **Checklist**: `/UI_ORCHESTRATOR_COMPLIANCE_CHECKLIST.md`
- **PR Template**: `/.github/PULL_REQUEST_TEMPLATE.md`

---

## 💡 When in Doubt

1. Read the System Prompt
2. Check GOVERNANCE.md
3. Ask: "Does this feel like a developer console?" → If yes, STOP
4. Ask: "Would a non-technical user understand this?" → If no, SIMPLIFY

---

**Remember**: Governance wins. Always.
