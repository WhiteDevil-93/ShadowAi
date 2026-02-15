# ShadowAi Troubleshooting Guide

This guide helps junior developers diagnose and fix common issues in the ShadowAi project.

## Table of Contents

1. [Build Issues](#build-issues)
2. [Runtime Issues](#runtime-issues)
3. [Security Issues](#security-issues)
4. [Performance Issues](#performance-issues)
5. [Testing Issues](#testing-issues)
6. [Native Code Issues](#native-code-issues)
7. [Dependency Issues](#dependency-issues)
8. [Common Error Messages](#common-error-messages)

## Build Issues

### 1. Gradle Build Failures

#### "Could not find NDK"
**Symptoms**: Build fails with NDK-related errors
**Solutions**:
```bash
# Check NDK installation
./gradlew :app:externalNativeBuildDebug

# Install NDK via Android Studio
# File → Settings → Android SDK → SDK Tools → Check "NDK (Side by side)"

# Verify local.properties
cat local.properties
# Should contain: ndk.dir=path/to/ndk
```

#### "Failed to resolve dependencies"
**Symptoms**: Gradle cannot download dependencies
**Solutions**:
```bash
# Clean and refresh dependencies
./gradlew clean
./gradlew --refresh-dependencies

# Check internet connection and proxy settings
# Verify repositories in build.gradle.kts

# Update Gradle wrapper if needed
./gradlew wrapper --gradle-version 8.5
```

#### "CMake Error"
**Symptoms**: Native build fails with CMake errors
**Solutions**:
```bash
# Check CMakeLists.txt syntax
# Verify NDK version compatibility
# Clean native builds
./gradlew clean
./gradlew :app:externalNativeBuildDebug
```

### 2. Kotlin Compilation Errors

#### "Unresolved reference"
**Symptoms**: Kotlin cannot find classes or methods
**Solutions**:
```kotlin
// Check imports are correct
import com.shadowai.app.some.package.ClassName

// Verify module dependencies in build.gradle.kts
dependencies {
    implementation(project(":module-name"))
}

// Clean and rebuild
./gradlew clean assembleDebug
```

#### "Type mismatch"
**Symptoms**: Kotlin type system errors
**Solutions**:
```kotlin
// Check function signatures
// Use explicit types when needed
val result: String = someFunction()

// Check nullable types
val value: String? = getValue()
val safeValue: String = value ?: ""
```

### 3. Android Manifest Issues

#### "Permission denied"
**Symptoms**: App crashes or permissions not working
**Solutions**:
```xml
<!-- Check AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Runtime permissions for Android 6.0+ -->
<!-- Use ActivityCompat.requestPermissions() -->
```

## Runtime Issues

### 1. App Crashes

#### "NullPointerException"
**Symptoms**: App crashes with null pointer exceptions
**Solutions**:
```kotlin
// Use safe calls
val value = object?.property

// Use null checks
if (value != null) {
    // Safe to use value
}

// Use Elvis operator
val result = value ?: defaultValue
```

#### "ClassCastException"
**Symptoms**: Type casting fails at runtime
**Solutions**:
```kotlin
// Use safe casting
val result = value as? String

// Check type before casting
if (value is String) {
    val str = value as String
}
```

### 2. Network Issues

#### "No internet connection"
**Symptoms**: API calls fail
**Solutions**:
```kotlin
// Check network connectivity
val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val networkInfo = connectivityManager.activeNetworkInfo
if (networkInfo != null && networkInfo.isConnected) {
    // Make network call
}

// Check API endpoints
// Verify API keys are correct
// Check firewall/proxy settings
```

#### "SSL/TLS errors"
**Symptoms**: HTTPS requests fail
**Solutions**:
```xml
<!-- Check network_security_config.xml -->
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set>
            <pin digest="SHA-256">cert-hash-here</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

### 3. Database Issues

#### "Database locked"
**Symptoms**: Database operations fail
**Solutions**:
```kotlin
// Use Room with proper threading
@Dao
interface MyDao {
    @Query("SELECT * FROM table")
    suspend fun getAll(): List<Entity>
}

// Use coroutines for database operations
viewModelScope.launch {
    val data = repository.getAll()
}
```

## Security Issues

### 1. PII Masking Not Working

#### "PII detected in logs"
**Symptoms**: Sensitive data appears in logs
**Solutions**:
```kotlin
// Always mask PII before logging
val safeInput = piiMaskingProcessor.maskPii(userInput)
Log.d("TAG", "Input: $safeInput")

// Check PII masking configuration
val processor = PiiMaskingProcessor()
val containsPii = processor.containsPii(text)
```

#### "Circuit breaker not opening"
**Symptoms**: Service failures not handled
**Solutions**:
```kotlin
// Check circuit breaker configuration
val breaker = CircuitBreaker(
    failureThreshold = 5,
    successThreshold = 2,
    timeoutMs = 30000
)

// Monitor circuit state
Log.d("CircuitBreaker", "State: ${breaker.currentState}")
```

### 2. Key Management Issues

#### "Key not found"
**Symptoms**: Encryption/decryption fails
**Solutions**:
```kotlin
// Check key generation
val key = securityManager.generateHardwareBackedKey("alias")

// Verify key exists
val keyExists = keyStore.containsAlias("alias")

// Use proper key aliases
val alias = "com.shadowai.app.${BuildConfig.APPLICATION_ID}"
```

## Performance Issues

### 1. Slow Builds

#### "Build takes too long"
**Symptoms**: Long build times
**Solutions**:
```properties
# Add to gradle.properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
kotlin.incremental=true
```

#### "Memory issues during build"
**Symptoms**: OutOfMemoryError during build
**Solutions**:
```properties
# Increase Gradle memory in gradle.properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

### 2. App Performance

#### "App is slow/responsive"
**Symptoms**: Poor app performance
**Solutions**:
```kotlin
// Use coroutines for background work
viewModelScope.launch(Dispatchers.IO) {
    // Heavy work here
    withContext(Dispatchers.Main) {
        // Update UI
    }
}

// Optimize database queries
@Query("SELECT * FROM table WHERE id = :id")
suspend fun getById(id: Int): Entity

// Use pagination for large datasets
@Query("SELECT * FROM table LIMIT :limit OFFSET :offset")
suspend fun getPaginated(limit: Int, offset: Int): List<Entity>
```

#### "Memory leaks"
**Symptoms**: App memory usage grows over time
**Solutions**:
```kotlin
// Use weak references for listeners
private val listener = WeakReference<MyListener>(myListener)

// Clear references in onDestroy
override fun onDestroy() {
    super.onDestroy()
    listener.clear()
}

// Use lifecycle-aware components
class MyViewModel : ViewModel() {
    // Automatically cleaned up
}
```

## Testing Issues

### 1. Unit Test Failures

#### "Test fails with timeout"
**Symptoms**: Tests timeout or hang
**Solutions**:
```kotlin
@Test
fun `test with timeout`() = runTest(timeout = 5000) {
    // Test code here
}

// Use testDispatcher for coroutine testing
@Test
fun `test with dispatcher`() = runTest {
    val testDispatcher = TestDispatcher()
    withContext(testDispatcher) {
        // Test code
    }
}
```

#### "Mock objects not working"
**Symptoms**: Mocks don't behave as expected
**Solutions**:
```kotlin
// Use proper mock setup
val mockObject = mockk<MyClass>()
every { mockObject.method() } returns expectedResult

// Verify calls
verify { mockObject.method() }

// Clear mocks between tests
@Before
fun setup() {
    clearAllMocks()
}
```

### 2. Integration Test Issues

#### "Tests fail on device"
**Symptoms**: Tests pass locally but fail on device
**Solutions**:
```kotlin
// Use proper test annotations
@RunWith(AndroidJUnit4::class)
@LargeTest
class MyIntegrationTest {
    
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)
    
    @Test
    fun testScenario() {
        // Use Espresso for UI testing
        onView(withId(R.id.button)).perform(click())
        onView(withId(R.id.text)).check(matches(withText("Expected")))
    }
}
```

## Native Code Issues

### 1. JNI Errors

#### "UnsatisfiedLinkError"
**Symptoms**: Native library loading fails
**Solutions**:
```kotlin
// Check native library loading
try {
    System.loadLibrary("native-lib")
} catch (e: UnsatisfiedLinkError) {
    Log.e("JNI", "Native library not found", e)
}

// Verify CMakeLists.txt
# In CMakeLists.txt
add_library(native-lib SHARED src/main/cpp/native-lib.cpp)
target_link_libraries(native-lib android log)
```

#### "Native crash"
**Symptoms**: App crashes in native code
**Solutions**:
```cpp
// Add error checking in C++ code
if (input == nullptr) {
    return nullptr;
}

// Use proper memory management
JNIEnv* env = jniEnv;
jstring result = env->NewStringUTF(output.c_str());
return result;
```

### 2. NDK Build Issues

#### "Architecture mismatch"
**Symptoms**: Native library not found for device architecture
**Solutions**:
```gradle
// In app/build.gradle.kts
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
}
```

## Dependency Issues

### 1. Version Conflicts

#### "Duplicate class errors"
**Symptoms**: Multiple versions of same library
**Solutions**:
```gradle
// Check dependency tree
./gradlew app:dependencies

// Force specific versions
configurations.all {
    resolutionStrategy {
        force "androidx.core:core-ktx:1.12.0"
        force "org.jetbrains.kotlin:kotlin-stdlib:1.9.25"
    }
}
```

#### "Missing dependencies"
**Symptoms**: Classes not found at runtime
**Solutions**:
```gradle
// Check implementation vs api
dependencies {
    implementation("library")  // Only for this module
    api("library")            // Exposed to consumers
}

// Verify all modules have required dependencies
```

## Common Error Messages

### Build Errors

| Error Message | Likely Cause | Solution |
|---------------|--------------|----------|
| "Could not find com.android.tools.build:gradle:X.X.X" | Gradle plugin version mismatch | Update Gradle plugin version |
| "Failed to resolve: androidx.core:core-ktx" | Repository not configured | Add Google Maven repository |
| "CMake Error: Could not find CMAKE_CXX_COMPILER" | NDK not installed | Install NDK via SDK Manager |
| "Unresolved reference: R" | Build error | Clean and rebuild project |

### Runtime Errors

| Error Message | Likely Cause | Solution |
|---------------|--------------|----------|
| "java.lang.NullPointerException" | Null object access | Add null checks and safe calls |
| "android.permission.DENIED" | Missing runtime permission | Request permission at runtime |
| "java.net.UnknownHostException" | No internet connection | Check network connectivity |
| "android.database.sqlite.SQLiteException" | Database error | Check SQL syntax and threading |

### Security Errors

| Error Message | Likely Cause | Solution |
|---------------|--------------|----------|
| "java.security.InvalidKeyException" | Invalid encryption key | Regenerate keys properly |
| "javax.net.ssl.SSLHandshakeException" | Certificate pinning failure | Update certificate pins |
| "android.security.keystore.KeyNotYetValidException" | Key not ready | Check key validity period |

## Debugging Tools

### Android Studio Tools

1. **Logcat**: Monitor app logs
   - Filter by tag: `tag:ShadowAI`
   - Filter by level: `level:ERROR`

2. **Memory Profiler**: Monitor memory usage
   - Detect memory leaks
   - Analyze heap dumps

3. **Network Profiler**: Monitor network requests
   - Check API calls
   - Monitor response times

4. **CPU Profiler**: Monitor CPU usage
   - Identify performance bottlenecks
   - Analyze thread usage

### Command Line Tools

```bash
# View logs
adb logcat | grep ShadowAI

# Clear app data
adb shell pm clear com.shadowai.app

# Install APK
adb install app-debug.apk

# Run tests
./gradlew connectedAndroidTest
```

## Getting Help

### Internal Resources

- **Code Reviews**: Always request code review for complex changes
- **Pair Programming**: Schedule with senior developers for difficult issues
- **Documentation**: Check existing docs and wikis
- **Team Chat**: Use team communication channels

### External Resources

- **Stack Overflow**: Search for Android/Kotlin specific issues
- **GitHub Issues**: Report bugs and feature requests
- **Android Developers**: Official documentation and guides
- **Kotlin Slack**: Kotlin community support

### When to Escalate

Escalate to senior developers when:
- Issue affects multiple components
- Security vulnerability suspected
- Performance impact is significant
- Issue blocks development progress
- Root cause is unclear after reasonable investigation

---

**Remember**: Document solutions to common issues and share with the team to improve collective knowledge.