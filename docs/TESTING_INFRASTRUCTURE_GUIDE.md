# ShadowAi Testing Infrastructure Guide

This guide explains the testing infrastructure and best practices for ShadowAi, designed to help junior developers write effective tests.

## Table of Contents

1. [Testing Overview](#testing-overview)
2. [Test Types and Structure](#test-types-and-structure)
3. [Unit Testing](#unit-testing)
4. [Integration Testing](#integration-testing)
5. [Security Testing](#security-testing)
6. [Performance Testing](#performance-testing)
7. [Test Utilities and Helpers](#test-utilities-and-helpers)
8. [Mocking Strategies](#mocking-strategies)
9. [Test Data Management](#test-data-management)
10. [CI/CD Testing](#cicd-testing)

## Testing Overview

ShadowAi follows a comprehensive testing strategy with multiple layers:

```
┌─────────────────────────────────────────────────────────┐
│                    Test Pyramid                         │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                UI Tests (Espresso)                  │ │
│  │  - End-to-end user flows                            │ │
│  │  - Integration with real components                 │ │
│  └─────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              Integration Tests                      │ │
│  │  - Component integration                            │ │
│  │  - Database operations                              │ │
│  │  - Network calls                                    │ │
│  └─────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                Unit Tests                           │ │
│  │  - Individual class testing                         │ │
│  │  - Logic validation                                 │ │
│  │  - Fast execution                                   │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### Testing Principles

1. **Test-Driven Development (TDD)**: Write tests before implementation when possible
2. **Fast Feedback**: Unit tests should run in milliseconds
3. **Isolation**: Each test should be independent
4. **Coverage**: Aim for 90%+ coverage on critical components
5. **Realistic Data**: Use realistic test data and scenarios

## Test Types and Structure

### Unit Tests (`app/src/test/`)

Unit tests focus on individual classes and methods:

```kotlin
// Location: app/src/test/java/com/shadowai/app/agent/ShadowAgentTest.kt
class ShadowAgentTest {
    
    @Test
    fun `processInput should return security error for unsafe input`() = runTest {
        // Test implementation
    }
    
    @Test
    fun `processInput should execute simple tasks correctly`() = runTest {
        // Test implementation
    }
}
```

### Integration Tests (`app/src/androidTest/`)

Integration tests verify component interactions:

```kotlin
// Location: app/src/androidTest/java/com/shadowai/app/AgentIntegrationTest.kt
@RunWith(AndroidJUnit4::class)
@LargeTest
class AgentIntegrationTest {
    
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)
    
    @Test
    fun `agent should handle complete user flow`() {
        // Test complete user interaction
    }
}
```

### Security Tests

Security tests validate security measures:

```kotlin
// Location: app/src/test/java/com/shadowai/app/security/PiiMaskingTest.kt
class PiiMaskingTest {
    
    @Test
    fun `should detect and mask email addresses`() {
        // Test PII detection and masking
    }
    
    @Test
    fun `should detect and mask credit card numbers`() {
        // Test credit card detection
    }
}
```

## Unit Testing

### Basic Unit Test Structure

```kotlin
class ExampleUnitTest {
    
    // Test dependencies
    private lateinit var sut: SystemUnderTest
    private lateinit var mockDependency: MockDependency
    
    @Before
    fun setup() {
        mockDependency = mockk()
        sut = SystemUnderTest(mockDependency)
    }
    
    @Test
    fun `test description`() = runTest {
        // Arrange
        val input = "test input"
        every { mockDependency.method() } returns expectedResult
        
        // Act
        val result = sut.method(input)
        
        // Assert
        assertEquals(expectedResult, result)
        verify { mockDependency.method() }
    }
}
```

### Testing Coroutines

```kotlin
class CoroutineTest {
    
    @Test
    fun `suspend function should work correctly`() = runTest {
        // Test suspend functions
        val result = suspendFunction()
        assertEquals(expected, result)
    }
    
    @Test
    fun `coroutine with delay should be testable`() = runTest {
        val testDispatcher = TestDispatcher()
        
        // Advance time in tests
        advanceTimeBy(1000)
        
        // Verify delayed operations
        verify { mock.delayedOperation() }
    }
}
```

### Testing Error Handling

```kotlin
class ErrorHandlingTest {
    
    @Test
    fun `should handle network errors gracefully`() = runTest {
        // Setup mock to throw exception
        every { mock.networkCall() } throws IOException("Network error")
        
        // Test error handling
        val result = sut.performNetworkOperation()
        
        // Verify error handling
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException)
    }
    
    @Test
    fun `should retry on transient errors`() = runTest {
        // Setup mock to fail then succeed
        every { mock.operation() } throws IOException("Temporary error") andThen "success"
        
        val result = sut.operationWithRetry()
        
        assertEquals("success", result)
        verify(exactly = 2) { mock.operation() }
    }
}
```

## Integration Testing

### Database Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {
    
    private lateinit var database: ShadowDatabase
    private lateinit var dao: TestDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShadowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.testDao()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun `should save and retrieve entity correctly`() = runTest {
        val entity = TestEntity(id = 1, name = "Test")
        
        // Save entity
        dao.insert(entity)
        
        // Retrieve entity
        val retrieved = dao.getById(1)
        
        assertEquals(entity, retrieved)
    }
}
```

### Network Integration Tests

```kotlin
class NetworkIntegrationTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(ApiService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    @Test
    fun `should handle successful API response`() = runTest {
        // Setup mock response
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"data\":\"test\"}"))
        
        // Make API call
        val response = apiService.getData()
        
        // Verify response
        assertTrue(response.isSuccessful)
        assertNotNull(response.body())
    }
}
```

## Security Testing

### PII Masking Tests

```kotlin
class PiiMaskingTest {
    
    private lateinit var piiProcessor: PiiMaskingProcessor
    
    @Before
    fun setup() {
        piiProcessor = PiiMaskingProcessor()
    }
    
    @Test
    fun `should detect email addresses`() {
        val input = "Contact me at john@example.com"
        val result = piiProcessor.detectPii(input)
        
        assertTrue(result.containsPii)
        assertEquals(1, result.piiMatches.size)
        assertEquals(PiiType.EMAIL, result.piiMatches.first().type)
    }
    
    @Test
    fun `should mask email addresses`() {
        val input = "Contact me at john@example.com"
        val result = piiProcessor.maskPii(input)
        
        assertEquals("Contact me at [EMAIL_REDACTED]", result)
        assertFalse(result.contains("john@example.com"))
    }
    
    @Test
    fun `should detect credit card numbers`() {
        val input = "My card is 4532-1234-5678-9012"
        val result = piiProcessor.detectPii(input)
        
        assertTrue(result.containsPii)
        assertEquals(PiiType.CREDIT_CARD, result.piiMatches.first().type)
    }
}
```

### Circuit Breaker Tests

```kotlin
class CircuitBreakerTest {
    
    private lateinit var circuitBreaker: CircuitBreaker
    
    @Before
    fun setup() {
        circuitBreaker = CircuitBreaker(
            failureThreshold = 3,
            successThreshold = 2,
            timeoutMs = 1000
        )
    }
    
    @Test
    fun `should open after threshold failures`() = runTest {
        // Trigger failures
        repeat(3) {
            try {
                circuitBreaker.execute { throw RuntimeException("test") }
            } catch (_: RuntimeException) {}
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.currentState)
    }
    
    @Test
    fun `should close after successful calls`() = runTest {
        // Open circuit
        repeat(3) {
            try {
                circuitBreaker.execute { throw RuntimeException("test") }
            } catch (_: RuntimeException) {}
        }
        
        // Wait for timeout
        delay(1100)
        
        // Successful calls
        repeat(2) {
            circuitBreaker.execute { "success" }
        }
        
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.currentState)
    }
}
```

### Security Manager Tests

```kotlin
class SecurityManagerTest {
    
    @Test
    fun `should encrypt and decrypt data correctly`() {
        val securityManager = SecurityManager(context)
        val originalData = "sensitive data"
        
        val encrypted = securityManager.encrypt(originalData)
        val decrypted = securityManager.decrypt(encrypted)
        
        assertEquals(originalData, decrypted)
        assertNotEquals(originalData, encrypted)
    }
    
    @Test
    fun `should generate hardware-backed keys`() {
        val securityManager = SecurityManager(context)
        val key = securityManager.generateHardwareBackedKey("test-key")
        
        assertNotNull(key)
        assertTrue(key.algorithm == "AES")
    }
}
```

## Performance Testing

### Memory Usage Tests

```kotlin
class MemoryUsageTest {
    
    @Test
    fun `should not leak memory in long-running operations`() {
        val initialMemory = Runtime.getRuntime().freeMemory()
        
        // Run memory-intensive operation
        repeat(1000) {
            val largeObject = createLargeObject()
            processObject(largeObject)
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        
        val finalMemory = Runtime.getRuntime().freeMemory()
        
        // Memory should be similar to initial
        val memoryDifference = abs(initialMemory - finalMemory)
        assertTrue("Memory leak detected: $memoryDifference bytes", 
                  memoryDifference < 1024 * 1024) // 1MB tolerance
    }
}
```

### Performance Benchmark Tests

```kotlin
class PerformanceBenchmarkTest {
    
    @Test
    fun `task execution should complete within time limit`() = runTest {
        val startTime = System.currentTimeMillis()
        
        val result = taskExecutor.execute(testTask)
        
        val executionTime = System.currentTimeMillis() - startTime
        
        assertTrue("Task took too long: ${executionTime}ms", 
                  executionTime < 5000) // 5 seconds limit
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `database operations should be fast`() = runTest {
        val entities = createTestEntities(1000)
        
        val startTime = System.currentTimeMillis()
        
        entities.forEach { entity ->
            dao.insert(entity)
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        assertTrue("Database insertion too slow: ${executionTime}ms", 
                  executionTime < 1000) // 1 second for 1000 entities
    }
}
```

## Test Utilities and Helpers

### Test Data Factories

```kotlin
object TestDataFactory {
    
    fun createTestTask(
        type: TaskType = TaskType.CONVERSATION,
        input: String = "test input",
        id: Long = 1L
    ): Task {
        return Task(
            id = id,
            type = type,
            input = input,
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun createTestEntity(
        id: Long = 1L,
        name: String = "Test Entity"
    ): TestEntity {
        return TestEntity(id = id, name = name)
    }
    
    fun createLargeObject(): LargeObject {
        return LargeObject(
            data = List(10000) { "data-$it" },
            metadata = Map(1000) { "key-$it" to "value-$it" }
        )
    }
}
```

### Test Rule for Database

```kotlin
class DatabaseTestRule : TestWatcher() {
    
    private lateinit var database: ShadowDatabase
    
    override fun starting(description: Description) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShadowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
    
    override fun finished(description: Description) {
        database.close()
    }
    
    fun getDatabase(): ShadowDatabase = database
}
```

### Test Rule for Coroutines

```kotlin
class CoroutineTestRule : TestWatcher() {
    
    private val testDispatcher = TestDispatcher()
    
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
    
    fun advanceTime(timeMs: Long) {
        testDispatcher.scheduler.advanceTimeBy(timeMs)
    }
}
```

## Mocking Strategies

### MockK Usage

```kotlin
class MockingExampleTest {
    
    private lateinit var mockDependency: MockDependency
    private lateinit var spyDependency: SpyDependency
    private lateinit var sut: SystemUnderTest
    
    @Before
    fun setup() {
        // Create mocks
        mockDependency = mockk()
        spyDependency = spyk(RealDependency())
        
        // Setup mock behavior
        every { mockDependency.getValue() } returns "mocked value"
        every { mockDependency.process(any()) } returns Result.success("processed")
        
        // Setup spy behavior
        every { spyDependency.expensiveOperation() } answers { 
            callOriginal() // Call real implementation
        }
        
        sut = SystemUnderTest(mockDependency, spyDependency)
    }
    
    @Test
    fun `should use mocked dependencies`() {
        val result = sut.processData("input")
        
        assertEquals("processed", result)
        verify { mockDependency.process("input") }
    }
}
```

### Mocking Android Components

```kotlin
class AndroidComponentTest {
    
    @Test
    fun `should handle context operations`() {
        val context = mockk<Context>(relaxed = true)
        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        
        val manager = PreferenceManager(context)
        
        // Test operations
        manager.saveValue("key", "value")
        
        verify { sharedPreferences.edit() }
    }
}
```

## Test Data Management

### Test Fixtures

```kotlin
class TestFixtures {
    
    companion object {
        val VALID_EMAILS = listOf(
            "user@example.com",
            "test.email@domain.co.uk",
            "user+tag@example.org"
        )
        
        val INVALID_EMAILS = listOf(
            "invalid-email",
            "@domain.com",
            "user@",
            "user space@example.com"
        )
        
        val CREDIT_CARD_NUMBERS = listOf(
            "4532-1234-5678-9012",
            "4532123456789012",
            "4532 1234 5678 9012"
        )
        
        val VALID_TASKS = listOf(
            TestDataFactory.createTestTask(TaskType.CONVERSATION, "Hello"),
            TestDataFactory.createTestTask(TaskType.TELEPHONY, "Call John"),
            TestDataFactory.createTestTask(TaskType.MESSAGING, "Send message")
        )
    }
}
```

### Test Data Cleanup

```kotlin
class TestDataCleanupTest {
    
    @After
    fun cleanup() {
        // Clear all test data
        clearDatabase()
        clearSharedPreferences()
        clearFiles()
        
        // Reset mocks
        clearAllMocks()
        
        // Reset static state
        resetStaticState()
    }
    
    private fun clearDatabase() {
        // Clear test database
    }
    
    private fun clearSharedPreferences() {
        // Clear test preferences
    }
    
    private fun clearFiles() {
        // Clear test files
    }
}
```

## CI/CD Testing

### Test Configuration

```gradle
// app/build.gradle.kts
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        
        animationsDisabled = true
        
        // JVM arguments for tests
        unitTests.all {
            jvmArgs("-Xmx1g")
            systemProperty("robolectric.logging", "stdout")
        }
    }
}
```

### Test Coverage

```gradle
// app/build.gradle.kts
plugins {
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.8"
}

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    
    classDirectories.setFrom(
        fileTree("$buildDir/intermediates/javac/debug/classes") {
            exclude("com/shadowai/app/debug/**")
        }
    )
    
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(files("$buildDir/outputs/code_coverage/debugAndroidTest/connected/*coverage.ec"))
}
```

### Continuous Testing

```yaml
# .github/workflows/test.yml
name: Test
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Run unit tests
        run: ./gradlew testDebugUnitTest
      
      - name: Run instrumented tests
        run: ./gradlew connectedAndroidTest
      
      - name: Generate test coverage report
        run: ./gradlew jacocoTestReport
      
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          file: ./app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
```

## Testing Best Practices

### 1. Test Naming

```kotlin
// GOOD: Descriptive test names
@Test
fun `should return error for invalid email format`() {
    // Test implementation
}

// BAD: Vague test names
@Test
fun test1() {
    // Test implementation
}
```

### 2. Test Structure

```kotlin
@Test
fun `test description`() = runTest {
    // Arrange - Setup test data and mocks
    val input = "test input"
    val expectedOutput = "expected result"
    
    // Act - Execute the method under test
    val result = sut.method(input)
    
    // Assert - Verify the result
    assertEquals(expectedOutput, result)
}
```

### 3. Test Isolation

```kotlin
class IsolatedTest {
    
    @Before
    fun setup() {
        // Reset state before each test
        clearDatabase()
        resetMocks()
        clearPreferences()
    }
    
    @Test
    fun `test 1`() {
        // Test implementation
    }
    
    @Test
    fun `test 2`() {
        // Test implementation - should not be affected by test 1
    }
}
```

### 4. Test Data

```kotlin
class TestDataTest {
    
    @Test
    fun `should handle edge cases`() {
        // Test with null values
        assertNull(sut.process(null))
        
        // Test with empty values
        assertEquals("", sut.process(""))
        
        // Test with boundary values
        assertEquals("boundary", sut.process("boundary"))
    }
}
```

### 5. Error Testing

```kotlin
class ErrorTestingTest {
    
    @Test
    fun `should handle exceptions gracefully`() = runTest {
        // Setup mock to throw exception
        every { mock.operation() } throws IOException("Network error")
        
        // Test error handling
        val result = sut.operation()
        
        // Verify error handling
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException)
    }
}
```

## Common Testing Patterns

### Testing LiveData

```kotlin
class LiveDataTest {
    
    @Test
    fun `should emit correct values`() {
        val liveData = MutableLiveData<String>()
        val observer = mockk<Observer<String>>(relaxed = true)
        
        liveData.observeForever(observer)
        
        liveData.value = "test"
        
        verify { observer.onChanged("test") }
    }
}
```

### Testing Coroutines with Flow

```kotlin
class FlowTest {
    
    @Test
    fun `should emit correct flow values`() = runTest {
        val flow = flowOf("value1", "value2", "value3")
        val collector = mockk<suspend (String) -> Unit>(relaxed = true)
        
        flow.collect { value ->
            collector(value)
        }
        
        verify {
            collector("value1")
            collector("value2")
            collector("value3")
        }
    }
}
```

### Testing Room Database

```kotlin
class RoomTest {
    
    @Test
    fun `should insert and query data correctly`() = runTest {
        val entity = TestEntity(id = 1, name = "Test")
        
        // Insert
        dao.insert(entity)
        
        // Query
        val result = dao.getById(1)
        
        assertEquals(entity, result)
    }
}
```

## Test Maintenance

### Regular Test Review

1. **Remove obsolete tests**: Delete tests for removed functionality
2. **Update test data**: Keep test data current and relevant
3. **Fix flaky tests**: Identify and fix non-deterministic tests
4. **Improve test coverage**: Add tests for uncovered critical paths

### Test Performance

1. **Fast unit tests**: Should complete in milliseconds
2. **Parallel execution**: Run tests in parallel when possible
3. **Resource cleanup**: Clean up resources after tests
4. **Mock external dependencies**: Avoid real network/database calls in unit tests

### Test Documentation

1. **Clear test descriptions**: Use descriptive test method names
2. **Document test scenarios**: Explain what each test validates
3. **Update documentation**: Keep test documentation current
4. **Share best practices**: Document and share testing patterns

---

**Remember**: Tests are code too! They should be well-written, maintainable, and provide value to the development process.