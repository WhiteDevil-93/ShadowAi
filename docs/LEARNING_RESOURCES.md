# ShadowAi Learning Resources

This guide provides learning resources and tutorials for junior developers to understand and contribute to ShadowAi effectively.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Kotlin and Android Fundamentals](#kotlin-and-android-fundamentals)
3. [Architecture Patterns](#architecture-patterns)
4. [Security Best Practices](#security-best-practices)
5. [Testing Strategies](#testing-strategies)
6. [Performance Optimization](#performance-optimization)
7. [Native Code Development](#native-code-development)
8. [AI and Machine Learning](#ai-and-machine-learning)
9. [Development Tools](#development-tools)
10. [ShadowAi Specific](#shadowai-specific)

## Getting Started

### For Complete Beginners

If you're new to Android development, start here:

#### 1. Android Development Basics
- **[Android Developer Fundamentals](https://developer.android.com/courses/fundamentals-training/toc-v2)**
  - Complete course for Android beginners
  - Covers activities, fragments, UI, data storage
  - Hands-on exercises and projects

- **[Kotlin Bootcamp for Programmers](https://developer.android.com/courses/kotlin-bootcamp/overview)**
  - Learn Kotlin syntax and concepts
  - Object-oriented programming with Kotlin
  - Functional programming features

#### 2. ShadowAi Project Familiarization
- **[ShadowAi Architecture Guide](AGENTS.md)**
  - Understand the multi-agent system
  - Learn about module structure
  - Get familiar with key components

- **[ShadowAi Setup Guide](JUNIOR_TEAM_SETUP.md)**
  - Step-by-step development environment setup
  - Build and run instructions
  - Common development tasks

### For Experienced Developers

If you have Android/Kotlin experience, focus on these areas:

#### 1. Advanced Kotlin
- **[Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)**
  - Asynchronous programming in Kotlin
  - Structured concurrency
  - Error handling and cancellation

- **[Kotlin Flow Documentation](https://kotlinlang.org/docs/flow.html)**
  - Reactive programming with Flow
  - Cold streams and operators
  - StateFlow and SharedFlow

#### 2. Android Architecture
- **[Guide to App Architecture](https://developer.android.com/topic/architecture)**
  - Clean architecture principles
  - Repository pattern
  - Data binding and LiveData

## Kotlin and Android Fundamentals

### Essential Kotlin Concepts

#### 1. Coroutines and Concurrency
```kotlin
// Basic coroutine usage
suspend fun fetchData(): Data {
    return withContext(Dispatchers.IO) {
        apiService.getData()
    }
}

// Coroutine scope management
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launch {
            try {
                val data = repository.fetchData()
                _data.value = data
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
```

#### 2. Extension Functions
```kotlin
// Add functionality to existing classes
fun String.isValidEmail(): Boolean {
    return Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
```

#### 3. Sealed Classes
```kotlin
// Type-safe state management
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

### Android-Specific Patterns

#### 1. ViewModel and LiveData
```kotlin
class ChatViewModel : ViewModel() {
    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val message = Message(text, System.currentTimeMillis())
                repository.sendMessage(message)
                _messages.value = repository.getMessages()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

#### 2. Repository Pattern
```kotlin
class MessageRepository {
    private val localDataSource: MessageDao
    private val remoteDataSource: MessageApiService
    
    suspend fun getMessages(): List<Message> {
        return localDataSource.getMessages()
    }
    
    suspend fun sendMessage(message: Message) {
        remoteDataSource.sendMessage(message)
        localDataSource.insert(message)
    }
}
```

## Architecture Patterns

### Clean Architecture

ShadowAi follows Clean Architecture principles:

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                   │
│  - Activities, Fragments, Composables                   │
│  - ViewModels, UI State                                 │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                    Domain Layer                         │
│  - Use Cases, Interactors                               │
│  - Entities, Business Logic                             │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                    Data Layer                           │
│  - Repositories, Data Sources                           │
│  - API Services, Database                               │
└─────────────────────────────────────────────────────────┘
```

### Dependency Injection with Hilt

```kotlin
// Module for providing dependencies
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShadowDatabase {
        return Room.databaseBuilder(
            context,
            ShadowDatabase::class.java,
            "shadowai.db"
        ).build()
    }
    
    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// Injecting dependencies
@HiltAndroidApp
class ShadowApplication : Application()

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var viewModel: MainViewModel
}
```

### Repository Pattern

```kotlin
// Repository interface
interface TaskRepository {
    suspend fun getTasks(): List<Task>
    suspend fun saveTask(task: Task)
    suspend fun deleteTask(taskId: Long)
}

// Repository implementation
class TaskRepositoryImpl @Inject constructor(
    private val localDataSource: TaskDao,
    private val remoteDataSource: TaskApiService
) : TaskRepository {
    
    override suspend fun getTasks(): List<Task> {
        return localDataSource.getAllTasks()
    }
    
    override suspend fun saveTask(task: Task) {
        localDataSource.insert(task)
        remoteDataSource.syncTask(task)
    }
    
    override suspend fun deleteTask(taskId: Long) {
        localDataSource.delete(taskId)
    }
}
```

## Security Best Practices

### PII Handling

```kotlin
// Always mask PII before processing
class PiiSafeProcessor {
    
    fun processInput(input: String): String {
        // Detect and mask PII
        val maskedInput = piiMaskingProcessor.maskPii(input)
        
        // Log safely
        Log.d("Processor", "Processing input: ${maskedInput.take(50)}...")
        
        return maskedInput
    }
}

// Never log sensitive data
class UnsafeExample {
    fun processInput(input: String) {
        // DON'T DO THIS
        Log.d("Processor", "Processing input: $input")
        
        // DO THIS INSTEAD
        Log.d("Processor", "Processing input: ${input.take(10)}...")
    }
}
```

### Secure Key Management

```kotlin
// Use Android Keystore for key management
class SecureKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun generateKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
```

### Circuit Breaker Pattern

```kotlin
// Protect against cascading failures
class CircuitBreaker @Inject constructor() {
    
    suspend fun <T> execute(block: suspend () -> T): T {
        return when (currentState) {
            State.CLOSED -> executeClosed(block)
            State.OPEN -> throw CircuitOpenException("Circuit is open")
            State.HALF_OPEN -> executeHalfOpen(block)
        }
    }
    
    private suspend fun <T> executeClosed(block: suspend () -> T): T {
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
}
```

## Testing Strategies

### Unit Testing with MockK

```kotlin
class TaskExecutorTest {
    
    @Test
    fun `should execute task successfully`() = runTest {
        // Arrange
        val mockRepository = mockk<TaskRepository>()
        val mockSecurityManager = mockk<SecurityManager>()
        
        every { mockRepository.getTasks() } returns listOf(task)
        every { mockSecurityManager.isInputSafe(any()) } returns true
        
        val executor = TaskExecutor(mockRepository, mockSecurityManager)
        
        // Act
        val result = executor.execute(task)
        
        // Assert
        assertTrue(result.isSuccess)
        verify { mockRepository.getTasks() }
    }
}
```

### Integration Testing

```kotlin
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {
    
    @get:Rule
    val databaseRule = DatabaseTestRule()
    
    @Test
    fun `should save and retrieve task correctly`() = runTest {
        val database = databaseRule.getDatabase()
        val dao = database.taskDao()
        
        val task = Task(id = 1, content = "Test task")
        
        // Save task
        dao.insert(task)
        
        // Retrieve task
        val retrievedTask = dao.getById(1)
        
        assertEquals(task, retrievedTask)
    }
}
```

### Testing Coroutines

```kotlin
class CoroutineTest {
    
    @Test
    fun `should handle coroutine cancellation`() = runTest {
        val job = launch {
            delay(1000)
            fail("Should have been cancelled")
        }
        
        job.cancel()
        advanceTimeBy(1000)
        
        assertFalse(job.isActive)
    }
    
    @Test
    fun `should handle coroutine exceptions`() = runTest {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            assertEquals("Test exception", exception.message)
        }
        
        val job = launch(exceptionHandler) {
            throw RuntimeException("Test exception")
        }
        
        job.join()
    }
}
```

## Performance Optimization

### Memory Management

```kotlin
// Prevent memory leaks
class MemorySafeActivity : AppCompatActivity() {
    
    private var listener: MyListener? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use weak references for listeners
        listener = WeakReference<MyListener>(object : MyListener {
            override fun onDataChanged(data: String) {
                // Handle data change
            }
        }).get()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clear references
        listener = null
    }
}

// Use object pooling for expensive objects
class ObjectPool<T>(private val factory: () -> T) {
    private val pool = mutableListOf<T>()
    
    fun acquire(): T {
        return if (pool.isNotEmpty()) {
            pool.removeAt(pool.size - 1)
        } else {
            factory()
        }
    }
    
    fun release(obj: T) {
        pool.add(obj)
    }
}
```

### Database Optimization

```kotlin
// Use pagination for large datasets
@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessages(limit: Int, offset: Int): List<Message>
    
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}

// Use transactions for multiple operations
@Dao
interface TaskDao {
    
    @Transaction
    suspend fun insertWithRelations(task: Task, relations: List<Relation>) {
        val taskId = insert(task)
        relations.forEach { relation ->
            insertRelation(relation.copy(taskId = taskId))
        }
    }
}
```

### Network Optimization

```kotlin
// Use caching for network requests
class CachedApiService {
    
    private val cache = LruCache<String, ApiResponse>(100)
    
    suspend fun getData(key: String): ApiResponse {
        return cache.get(key) ?: run {
            val response = apiService.getData(key)
            cache.put(key, response)
            response
        }
    }
}

// Use compression for large payloads
val client = OkHttpClient.Builder()
    .addInterceptor(GzipRequestInterceptor())
    .build()
```

## Native Code Development

### JNI Basics

```cpp
// C++ implementation
extern "C" JNIEXPORT jstring JNICALL
Java_com_shadowai_app_inference_NativeBridge_generateText(
    JNIEnv *env,
    jobject thiz,
    jstring prompt,
    jint maxTokens) {
    
    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    
    // Process the prompt
    std::string result = processPrompt(promptStr, maxTokens);
    
    env->ReleaseStringUTFChars(prompt, promptStr);
    
    return env->NewStringUTF(result.c_str());
}
```

```kotlin
// Kotlin wrapper
class NativeBridge {
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
    
    external fun generateText(prompt: String, maxTokens: Int): String
}
```

### CMake Configuration

```cmake
# CMakeLists.txt
cmake_minimum_required(VERSION 3.18.1)
project("native-lib")

# Add your source files
add_library(native-lib SHARED
    src/main/cpp/native-lib.cpp
    src/main/cpp/llama_jni.cpp)

# Link libraries
find_library(log-lib log)
target_link_libraries(native-lib ${log-lib})

# Set compiler flags
target_compile_options(native-lib PRIVATE -O3 -DNDEBUG)
```

### Error Handling in Native Code

```cpp
// Always check for errors
extern "C" JNIEXPORT jboolean JNICALL
Java_com_shadowai_app_inference_NativeBridge_initModel(
    JNIEnv *env,
    jobject thiz,
    jstring modelPath) {
    
    try {
        const char *path = env->GetStringUTFChars(modelPath, nullptr);
        
        if (!loadModel(path)) {
            env->ReleaseStringUTFChars(modelPath, path);
            return false;
        }
        
        env->ReleaseStringUTFChars(modelPath, path);
        return true;
        
    } catch (const std::exception &e) {
        // Log error
        __android_log_print(ANDROID_LOG_ERROR, "NativeBridge", "Error: %s", e.what());
        return false;
    }
}
```

## AI and Machine Learning

### Understanding LLMs

#### 1. Basic Concepts
- **Prompt Engineering**: How to craft effective prompts
- **Tokenization**: Understanding how text is processed
- **Context Windows**: Managing conversation history
- **Temperature and Sampling**: Controlling output randomness

#### 2. Model Types
- **GGUF Models**: Quantized models for mobile
- **LoRA Adapters**: Fine-tuning without full retraining
- **Multi-modal Models**: Text + image processing

### Prompt Engineering

```kotlin
class PromptManager {
    
    fun createConversationPrompt(userInput: String, history: List<Message>): String {
        val prompt = StringBuilder()
        
        prompt.append("You are a helpful AI assistant.\n\n")
        prompt.append("Conversation history:\n")
        
        history.takeLast(10).forEach { message ->
            prompt.append("${message.sender}: ${message.content}\n")
        }
        
        prompt.append("User: $userInput\n")
        prompt.append("Assistant:")
        
        return prompt.toString()
    }
    
    fun createTaskPrompt(task: Task): String {
        return """
            You are an AI task executor.
            
            Task: ${task.description}
            Type: ${task.type}
            
            Please provide a step-by-step plan to complete this task.
            Return your response in JSON format with the following structure:
            {
                "steps": [
                    {"action": "description", "priority": 1}
                ],
                "estimatedTime": "5 minutes"
            }
        """.trimIndent()
    }
}
```

### Context Management

```kotlin
class ContextManager {
    
    private val maxTokens = 4096
    private val tokenCounter = TokenCounter()
    
    fun manageContext(history: List<Message>, newInput: String): List<Message> {
        val context = mutableListOf<Message>()
        var totalTokens = tokenCounter.countTokens(newInput)
        
        // Add messages from most recent to oldest
        for (message in history.reversed()) {
            val messageTokens = tokenCounter.countTokens(message.content)
            
            if (totalTokens + messageTokens > maxTokens * 0.8) {
                break // Leave room for response
            }
            
            context.add(0, message) // Add to beginning to maintain order
            totalTokens += messageTokens
        }
        
        return context
    }
}
```

## Development Tools

### Android Studio Tips

#### 1. Code Templates
- **Live Templates**: Create custom code snippets
- **File Templates**: Generate boilerplate code
- **Postfix Templates**: Transform expressions quickly

#### 2. Debugging Tools
- **Layout Inspector**: Analyze UI layouts
- **Memory Profiler**: Monitor memory usage
- **Network Profiler**: Analyze network requests
- **CPU Profiler**: Identify performance bottlenecks

#### 3. Productivity Features
- **Code Folding**: Hide/show code sections
- **Parameter Info**: View method signatures
- **Quick Documentation**: View documentation inline
- **Refactoring Tools**: Rename, extract, inline

### Command Line Tools

```bash
# Build and run
./gradlew assembleDebug
./gradlew installDebug

# Testing
./gradlew test
./gradlew connectedAndroidTest

# Code quality
./gradlew lint
./gradlew detekt

# Performance
./gradlew assembleDebug --profile
./gradlew buildHealth

# Dependency analysis
./gradlew app:dependencies
./gradlew app:dependencyInsight --dependency=kotlin
```

### Git Best Practices

```bash
# Commit message format
git commit -m "feat(module): description of change"
git commit -m "fix(module): description of fix"
git commit -m "docs(module): description of documentation change"

# Branch naming
git checkout -b feature/user-authentication
git checkout -b bugfix/login-crash
git checkout -b hotfix/security-patch

# Rebase workflow
git pull --rebase origin main
git rebase -i HEAD~3  # Interactive rebase

# Stashing changes
git stash
git stash pop
git stash list
```

## ShadowAi Specific

### Understanding the Codebase

#### 1. Module Dependencies
```
app/ (Main application)
├── core-contracts/ (Shared interfaces)
├── model-catalog/ (Model management)
├── provider-adapters/ (Cloud providers)
├── inference_process/ (Native inference)
└── pipeline-planner/ (Task planning)
```

#### 2. Key Classes and Their Responsibilities
- **ShadowAgent**: Main agent for task execution
- **SupervisorAgent**: High-level task orchestration
- **TaskExecutor**: Routes tasks to providers
- **SecurityManager**: Handles encryption and security
- **PiiMaskingProcessor**: Masks sensitive data

#### 3. Configuration Files
- **local.properties**: Local development configuration
- **build.gradle.kts**: Build configuration
- **AndroidManifest.xml**: App permissions and components
- **network_security_config.xml**: Network security settings

### ShadowAi Development Workflow

#### 1. Adding a New Feature
1. **Create a branch**: `git checkout -b feature/your-feature`
2. **Write tests first**: Follow TDD principles
3. **Implement the feature**: Keep changes focused
4. **Update documentation**: Add KDoc and README updates
5. **Run tests**: Ensure all tests pass
6. **Code review**: Submit for review before merging

#### 2. Debugging Issues
1. **Check logs**: Use Logcat to identify issues
2. **Reproduce locally**: Create minimal reproduction
3. **Add logging**: Add debug logs to understand flow
4. **Use debugger**: Set breakpoints and step through code
5. **Check dependencies**: Verify external dependencies
6. **Test on device**: Verify on physical device

#### 3. Performance Optimization
1. **Profile the app**: Use Android Studio profilers
2. **Identify bottlenecks**: Find slow operations
3. **Optimize algorithms**: Improve time complexity
4. **Reduce memory usage**: Fix memory leaks
5. **Cache appropriately**: Add caching for expensive operations
6. **Test improvements**: Verify performance gains

### ShadowAi Best Practices

#### 1. Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and method names
- Keep methods short and focused
- Use proper access modifiers
- Add comprehensive KDoc comments

#### 2. Security
- Always use PII masking for user input
- Never log sensitive information
- Use circuit breakers for external calls
- Validate all user input
- Use secure key management

#### 3. Testing
- Write unit tests for all new code
- Use integration tests for component interaction
- Test edge cases and error conditions
- Mock external dependencies
- Maintain high test coverage

#### 4. Performance
- Use coroutines for asynchronous operations
- Implement proper memory management
- Optimize database queries
- Use pagination for large datasets
- Cache expensive operations

## Additional Resources

### Online Courses and Tutorials

#### Kotlin and Android
- **[Kotlin Documentation](https://kotlinlang.org/docs/)**
- **[Android Developers](https://developer.android.com/)**
- **[Udacity Android Nanodegree](https://www.udacity.com/course/android-developer-nanodegree-by-google--nd801)**
- **[Ray Wenderlich Android Tutorials](https://www.raywenderlich.com/android)**

#### Architecture and Patterns
- **[Clean Architecture by Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)**
- **[Android Architecture Blueprints](https://github.com/android/architecture-samples)**
- **[Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)**

#### Security
- **[OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)**
- **[Android Security Best Practices](https://developer.android.com/topic/security/best-practices)**
- **[Secure Coding Guidelines](https://cwe.mitre.org/)**

#### Testing
- **[Android Testing Guide](https://developer.android.com/training/testing)**
- **[MockK Documentation](https://mockk.io/)**
- **[Kotest Documentation](https://kotest.io/)**

### Books

#### Kotlin and Android Development
- **"Kotlin in Action"** by Dmitry Jemerov and Svetlana Isakova
- **"Android Development with Kotlin"** by Marcin Moskala and Igor Wojda
- **"Clean Architecture"** by Robert C. Martin

#### Software Architecture
- **"Design Patterns: Elements of Reusable Object-Oriented Software"** by Gang of Four
- **"Clean Code"** by Robert C. Martin
- **"The Pragmatic Programmer"** by Andrew Hunt and David Thomas

#### Security
- **"Security Patterns in Practice"** by Edward J. Naughton and Robert J. Muller
- **"Threat Modeling"** by Adam Shostack

### Communities and Forums

#### Online Communities
- **[Stack Overflow](https://stackoverflow.com/questions/tagged/android+kotlin)**
- **[Kotlin Slack](https://slack.kotlinlang.org/)**
- **[Android Developers Community](https://developer.android.com/community)**
- **[Reddit r/androiddev](https://www.reddit.com/r/androiddev/)**

#### ShadowAi Specific
- **[ShadowAi GitHub Issues](https://github.com/your-organization/ShadowAi/issues)**
- **[ShadowAi Wiki](https://github.com/your-organization/ShadowAi/wiki)**
- **[ShadowAi Discussions](https://github.com/your-organization/ShadowAi/discussions)**

### Practice Projects

#### Beginner Projects
1. **Simple Calculator**: Practice basic Android UI and Kotlin
2. **To-Do List**: Learn data persistence and RecyclerView
3. **Weather App**: Practice API integration and networking
4. **Note Taking App**: Learn Room database and content providers

#### Intermediate Projects
1. **Chat Application**: Real-time communication and WebSocket
2. **E-commerce App**: Complex UI, payment integration, and state management
3. **Photo Gallery**: Image processing and media handling
4. **Task Manager**: Advanced database operations and background tasks

#### Advanced Projects
1. **Social Media App**: Complex state management and real-time updates
2. **Video Streaming App**: Media playback and streaming protocols
3. **AI-Powered App**: Machine learning integration and model inference
4. **Cross-Platform App**: Kotlin Multiplatform development

---

**Remember**: Learning is a continuous process. Don't hesitate to ask questions, seek help, and practice regularly. The ShadowAi codebase is a great learning resource - explore it, understand it, and contribute to it!