# ShadowAi AI Code Assistant Guidelines

This document defines standards for Claude Code and other IDE AI agents working on the ShadowAi codebase.

## Code Formatting

### Kotlin Style Guide

All Kotlin code **must** follow the official Kotlin style guide and be formatted with **ktlint**.

#### Key Rules
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: No hard limit (ktlint default is off)
- **Trailing Commas**: Allowed and encouraged in multi-line constructs
- **Semicolons**: Not used (Kotlin convention)
- **Naming**: camelCase for variables/functions, PascalCase for classes
- **Imports**: Alphabetically sorted, no wildcard imports

#### ktlint Formatting
Before committing or submitting PRs, run formatting:
```bash
# Format all Kotlin files in-place
./gradlew ktlintFormat

# Format specific files
ktlint --format app/src/main/java/com/shadowai/app/**/*.kt

# Validate without changes
ktlint app/src/main/java/com/shadowai/app/**/*.kt
```

### Code Style Conventions

#### Class and Function Formatting
```kotlin
// ✅ CORRECT
class ProviderRepository @Inject constructor(
    private val securityManager: SecurityManager,
    private val database: ShadowDatabase
) {
    suspend fun getProvider(id: ProviderId): Result<Provider> = withContext(Dispatchers.IO) {
        try {
            val provider = database.providerDao().getById(id.value)
            Result.success(provider)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ✅ CORRECT - Trailing comma in multi-line
val result = when (routingDecision.selectedSource) {
    ExecutionSource.LOCAL -> ModelId("local-primary")
    ExecutionSource.CLOUD -> ModelId("cloud-primary")
    else -> ModelId("default-fallback")
}

// ❌ AVOID
class ProviderRepository @Inject constructor(private val securityManager: SecurityManager, private val database: ShadowDatabase)
```

#### Compose UI Formatting
```kotlin
// ✅ CORRECT
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ChatTopAppBar(onNavigateBack = {}) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            items(uiState.messages) { message ->
                ChatMessageItem(message = message)
            }
        }
    }
}
```

#### Comments and Documentation
```kotlin
// ✅ CORRECT - KDoc format
/**
 * Manages AI provider configurations and API key storage.
 *
 * This class provides secure storage for provider credentials using
 * AES-256-GCM encryption and handles provider discovery.
 *
 * @property securityManager The security manager for encryption operations
 * @see ProviderPlugin
 */
class ProviderRepository @Inject constructor(
    private val securityManager: SecurityManager
) { }
```

## Architecture & Code Quality

### Zero-Trust Security Model
- Every component is treated as a potential compromise point
- All API keys must use `SecretBytes` pattern (no immutable strings)
- Use `SecurityManager` for all encryption operations
- Never hardcode secrets in code or config files

### Dependency Injection
- **Always** use Hilt DI (`@Inject`, `@Module`, `@Provides`)
- Manual instantiation is **forbidden** for managed components
- All ViewModels must be created with `hiltViewModel()`

### Coroutines & Structured Concurrency
- **No blocking calls** on the main thread
- Use `withContext(Dispatchers.IO)` for I/O operations
- All networking code must be `suspend` functions
- Flow/StateFlow for reactive patterns, never LiveData

### Error Handling
```kotlin
// ✅ CORRECT - Using Result or sealed class
suspend fun fetchData(): Result<Data> {
    return try {
        val data = api.getData()
        Result.success(data)
    } catch (e: Exception) {
        Log.e("TAG", "Failed to fetch data", e)
        Result.failure(e)
    }
}

// ✅ CORRECT - Circuit breaker for external calls
val response = circuitBreaker.execute {
    provider.generate(prompt)
}
```

### Code Review Checklist
Before submitting code, verify:

- [ ] All Kotlin files are formatted with ktlint
- [ ] No warnings from lint checks
- [ ] KDoc comments for all public APIs
- [ ] No hardcoded secrets or API keys
- [ ] All coroutine code uses proper context
- [ ] Circuit breaker used for external calls
- [ ] Unit tests added for new business logic
- [ ] No mock implementations or TODOs left as fallbacks

## IDE Settings

### VSCode Extensions Required
- **kotlin-formatter** (cstef.kotlin-formatter)
- **Kotlin Language** (fwcd.kotlin-language)
- **GitHub Copilot** (GitHub.copilot)

### Auto-Formatting
Files are automatically formatted on save using ktlint integration. The project `.editorconfig` and `.vscode/settings.json` define all formatting rules.

## Git Workflow

### Pre-commit Hook
The project uses pre-commit hooks to enforce code quality:

```bash
# Install pre-commit hook
pre-commit install

# Run manually before commit
pre-commit run --all-files
```

### Branch Naming
- `feature/` - New features (e.g., `feature/new-provider-adapter`)
- `fix/` - Bug fixes (e.g., `fix/circuit-breaker-timeout`)
- `refactor/` - Code improvements (e.g., `refactor/security-manager`)
- `chore/` - Maintenance tasks (e.g., `chore/upgrade-dependencies`)

### Commit Messages
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Example:**
```
feat(providers): add OpenAICompatibleAdapter

Implement adapter for OpenAI-compatible APIs including:
- Chat completions endpoint
- Streaming response support
- Model listing

Relates-to: #123
```

## Resources

- **Project Architecture**: See [AGENTS.md](AGENTS.md) and [plans/todo.md](plans/todo.md)
- **Kotlin Style Guide**: https://kotlinlang.org/docs/coding-conventions.html
- **ktlint**: https://pinterest.github.io/ktlint/
- **Android Development**: https://developer.android.com/
- **Jetpack Compose**: https://developer.android.com/jetpack/compose

## Questions?

Refer to:
1. `AGENTS.md` - Project structure and module layout
2. Existing code in similar modules - patterns are established
3. Architecture decision records in `plans/`
