# ShadowAi Adaptive Layout Components

This package provides **adaptive layout support** for ShadowAi, enabling optimal UX across phones, tablets, and foldable devices.

## Quick Start

```kotlin
import com.shadowai.app.ui.adaptive.rememberAdaptiveLayoutType
import com.shadowai.app.ui.adaptive.AdaptiveChatLayout

@Composable
fun MyScreen(viewModel: ChatViewModel, user: User?) {
    val layoutType = rememberAdaptiveLayoutType()
    
    AdaptiveChatLayout(
        layoutType = layoutType,
        viewModel = viewModel,
        user = user,
        chatMode = ChatMode.CHAT,
        onModeChange = { /* ... */ },
        onSendMessage = { /* ... */ },
        onNavigateToSettings = { /* ... */ },
        onNavigateToProviderSelection = { /* ... */ },
        onNavigateToImageGeneration = { /* ... */ },
        onSignOut = { /* ... */ }
    )
}
```

## Components

| Component | Purpose |
|-----------|---------|
| `AdaptiveLayoutType` | Enum for window size classes (Compact/Medium/Expanded) |
| `AdaptiveChatLayout` | Main adaptive layout with `when (layoutType)` switching |
| `AdaptiveScaffold` | NavigationSuiteScaffold wrapper with automatic layout selection |
| `ShadowAINavigationRail` | Navigation rail for medium screens |
| `ShadowAIExpandedDrawer` | Permanent drawer for expanded screens |
| `rememberAdaptiveLayoutType()` | Composable to get current layout type |

## Layout Types

### Compact (< 600dp) - Phones
- Single pane layout
- Modal navigation drawer
- Floating chat tabs
- Bottom input field

### Medium (600dp - 840dp) - Small tablets, foldables
- Two-pane layout (30/70 split)
- Permanent navigation rail (80dp)
- No modal drawer needed
- Chat mode icons in rail

### Expanded (>= 840dp) - Large tablets
- Three-pane layout (25/50/25 split)
- Permanent navigation drawer (240dp)
- Context sidebar on right
- Full chat mode descriptions

## Integration with ComposeMainActivity

```kotlin
@Composable
fun ShadowAiApp(
    viewModel: ChatViewModel,
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val layoutType = rememberAdaptiveLayoutType()
    
    when (authState) {
        is AuthState.Authenticated -> {
            // Automatically adapts to screen size
            AdaptiveChatLayout(
                layoutType = layoutType,
                viewModel = viewModel,
                user = authViewModel.currentUser.collectAsState().value,
                // ... callbacks
            )
        }
        // ... handle other states
    }
}
```
