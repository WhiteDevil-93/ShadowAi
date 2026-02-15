# ShadowAi Developer Tools

This guide provides information about the developer tools and utilities available for ShadowAi development.

## Table of Contents

1. [Development Environment Setup](#development-environment-setup)
2. [Build Tools and Scripts](#build-tools-and-scripts)
3. [Code Quality Tools](#code-quality-tools)
4. [Debugging Tools](#debugging-tools)
5. [Testing Tools](#testing-tools)
6. [Performance Analysis Tools](#performance-analysis-tools)
7. [Security Analysis Tools](#security-analysis-tools)
8. [Native Development Tools](#native-development-tools)
9. [CI/CD Tools](#cicd-tools)
10. [Custom Development Utilities](#custom-development-utilities)

## Development Environment Setup

### Required Tools

#### 1. Android Studio
- **Version**: Hedgehog (2023.1.1) or later
- **Plugins**: Kotlin, Android, Git Integration
- **SDK**: Android 14 (API 34) with NDK r25c

#### 2. Command Line Tools
```bash
# Java Development Kit
java -version  # Should be JDK 17+

# Android SDK
sdkmanager --version

# Gradle
./gradlew --version

# Git
git --version
```

#### 3. Development Dependencies
```bash
# Install NDK via SDK Manager
sdkmanager "ndk;25.2.9519653"

# Install CMake
sdkmanager "cmake;3.22.1"

# Install Android Emulator
sdkmanager "emulator"
```

### Environment Variables

```bash
# Add to ~/.bashrc or ~/.zshrc
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/ndk/25.2.9519653
```

## Build Tools and Scripts

### Gradle Build System

#### Basic Build Commands
```bash
# Clean build
./gradlew clean

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on device
./gradlew installDebug

# Run tests
./gradlew test

# Run connected tests
./gradlew connectedAndroidTest
```

#### Module-Specific Builds
```bash
# Build specific module
./gradlew :app:assembleDebug
./gradlew :core-contracts:assembleDebug
./gradlew :provider-adapters:assembleDebug

# Build all modules
./gradlew assembleDebug

# Build with specific flavor
./gradlew assembleDebug assembleRelease
```

#### Custom Build Scripts

Create `scripts/build.sh` for common operations:

```bash
#!/bin/bash
# scripts/build.sh

set -e

echo "🧹 Cleaning project..."
./gradlew clean

echo "📦 Building debug APK..."
./gradlew assembleDebug

echo "✅ Build completed successfully!"
echo "📁 APK location: app/build/outputs/apk/debug/app-debug.apk"
```

Make it executable:
```bash
chmod +x scripts/build.sh
./scripts/build.sh
```

### Build Configuration

#### Custom Gradle Properties

Add to `gradle.properties`:

```properties
# Performance optimizations
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
kotlin.incremental=true
kapt.incremental.apt=true

# Memory settings
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m

# Build optimizations
android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=true
android.nonFinalResIds=false
```

#### Build Variants

Configure build variants in `app/build.gradle.kts`:

```kotlin
android {
    flavorDimensions.add("version")
    
    productFlavors {
        create("dev") {
            dimension = "version"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            versionCode = 1000
        }
        
        create("prod") {
            dimension = "version"
            minifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

## Code Quality Tools

### Static Analysis

#### Detekt (Kotlin Linting)
```yaml
# detekt.yml
build:
  maxIssues: 0
  excludeCorrectable: false
  weights:
    complexity: 2
    formatting: 1
    comments: 1

config:
  validation: true
  warningsAsErrors: false

processors:
  active: true
  exclude:
    - 'FunctionCountProcessor'
    - 'PropertyCountProcessor'
    - 'ClassCountProcessor'
    - 'PackageCountProcessor'
    - 'KtFileCountProcessor'

console-reports:
  active: true
  exclude:
    - 'ProjectStatisticsReport'
    - 'ComplexityReport'
    - 'NotificationReport'
    - 'FindingsReport'
    - 'FileBasedFindingsReport'

output-reports:
  active: true
  exclude:
    - 'HtmlOutputReport'
    - 'TxtOutputReport'
    - 'XmlOutputReport'

comments:
  active: true
  CommentOverPrivateFunction:
    active: false
  CommentOverPrivateProperty:
    active: false
  EndOfSentenceFormat:
    active: false
  UndocumentedPublicClass:
    active: false
  UndocumentedPublicFunction:
    active: false
  UndocumentedPublicProperty:
    active: false

complexity:
  active: true
  CyclomaticComplexMethod:
    active: true
    threshold: 10
    ignorePackageStatements: false
    ignoreSimpleWhenEntries: false
    ignoreNestingFunctions: false
  LongMethod:
    active: true
    threshold: 60
  LongParameterList:
    active: true
    functionThreshold: 6
    constructorThreshold: 7
  MethodOverloading:
    active: true
    threshold: 6
  NamedArguments:
    active: false
    threshold: 3
  NestedBlockDepth:
    active: true
    threshold: 8
  TooManyFunctions:
    active: true
    thresholdInFiles: 18
    thresholdInClasses: 12
    thresholdInInterfaces: 20
    thresholdInObjects: 10
    thresholdInEnums: 10
  TooManyImports:
    active: true
    maximumImports: 20
    includeStaticImports: true

coroutines:
  active: true
  InjectDispatcher:
    active: true
    allowDispatchersParameter: false
    allowDefaultDispatcher: false
    allowDefaultDispatcherOverride: false
  RedundantSuspendModifier:
    active: true
  SleepInsteadOfDelay:
    active: true
  SuspendFunWithCoroutineScopeReceiver:
    active: true
  SuspendFunWithFlowReturnType:
    active: true

exceptions:
  active: true
  ExceptionRaisedInUnexpectedLocation:
    active: true
    methodNames: ['toString', 'hashCode', 'equals', 'finalize']
  InstanceOfCheckForException:
    active: true
    allowedExceptionTypes: ['Exception', 'Throwable']
  NotImplementedDeclaration:
    active: true
  ObjectExtendsThrowable:
    active: true
  PrintStackTrace:
    active: true
  PrintWriterWithException:
    active: true
  RethrowCaughtException:
    active: true
  ReturnFromFinally:
    active: true
  SwallowedException:
    active: true
    ignoredExceptionTypes:
      - 'InterruptedException'
      - 'MalformedURLException'
      - 'NumberFormatException'
      - 'ParseException'
    allowedExceptionNameRegex: '$^'
  ThrowingExceptionFromFinally:
    active: true
  ThrowingExceptionInMain:
    active: true
  ThrowingExceptionsWithoutMessageOrCause:
    active: true
    exemptedExceptionTypes:
      - 'CancellationException'
      - 'CompletionException'
      - 'TimeoutCancellationException'
    allowedExceptionNameRegex: '$^'
  ThrowingNewInstanceOfSameException:
    active: true
  TooGenericExceptionCaught:
    active: true
    exceptionNames:
      - 'ArrayIndexOutOfBoundsException'
      - 'Exception'
      - 'IllegalMonitorStateException'
      - 'IndexOutOfBoundsException'
      - 'NullPointerException'
      - 'RuntimeException'
      - 'Throwable'
    allowedExceptionNameRegex: '$^'
  TooGenericExceptionThrown:
    active: true
    exceptionNames:
      - 'Exception'
      - 'RuntimeException'
      - 'Throwable'
    allowedExceptionNameRegex: '$^'

naming:
  active: true
  BooleanPropertyNaming:
    active: true
    allowedPattern: '^(is|has|are)\\w+'
  ConstructorParameterNaming:
    active: true
    parameterPattern: '[a-z][A-Za-z0-9]*'
    privateParameterPattern: '[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'
  EnumNaming:
    active: true
    enumEntryPattern: '[A-Z][_a-zA-Z0-9]*'
  ForbiddenClassName:
    active: true
    forbiddenName: ['^Name$']
  FunctionMaxLength:
    active: false
    maximumFunctionNameLength: 30
  FunctionMinLength:
    active: false
    minimumFunctionNameLength: 3
  FunctionNaming:
    active: true
    functionPattern: '[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'
    ignoreAnnotated: ['Composable']
  FunctionParameterNaming:
    active: true
    parameterPattern: '[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'
  InvalidPackageDeclaration:
    active: true
    rootPackage: ''
    requirePackageDeclarationForFilesMatching: '.*'
  LambdaParameterNaming:
    active: true
    parameterPattern: '[a-z][A-Za-z0-9]*|_+'
  MatchingDeclarationName:
    active: true
    mustBeCamelCase: true
  MemberNameEqualsClassName:
    active: true
    ignoreOverridden: true
  NoNameShadowing:
    active: true
  NonBooleanPropertyPrefixedWithIs:
    active: true
  ObjectPropertyNaming:
    active: true
    constantPattern: '[A-Z][_A-Z0-9]*'
    propertyPattern: '[A-Za-z][A-Za-z0-9]*'
    privatePropertyPattern: '(_)?[A-Za-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'
  PackageNaming:
    active: true
    packagePattern: '[a-z]+(\.[a-z][A-Za-z0-9]*)*'
  TopLevelPropertyNaming:
    active: true
    constantPattern: '[A-Z][_A-Z0-9]*'
    propertyPattern: '[A-Za-z][A-Za-z0-9]*'
    privatePropertyPattern: '_?[A-Za-z][A-Za-z0-9]*'
  VariableMaxLength:
    active: false
    maximumVariableNameLength: 64
  VariableMinLength:
    active: false
    minimumVariableNameLength: 1
  VariableNaming:
    active: true
    variablePattern: '[a-z][A-Za-z0-9]*'
    privateVariablePattern: '(_)?[a-z][A-Za-z0-9]*'
    excludeClassPattern: '$^'

performance:
  active: true
  ArrayPrimitive:
    active: true
  ForEachOnRange:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
  SpreadOperator:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
  UnnecessaryPartOfStatement:
    active: true
    excludes: ['**/test/**', '**/androidTest/**']
  UnnecessaryTemporaryInstantiation:
    active: true

potential-bugs:
  active: true
  Deprecation:
    active: false
  DontDowncastCollectionTypes:
    active: true
  DuplicateCaseInWhenExpression:
    active: true
  EqualsAlwaysReturnsTrueOrFalse:
    active: true
  EqualsWithHashCodeExist:
    active: true
  ExitOutsideMain:
    active: true
  ExplicitGarbageCollection:
    active: true
    methodNames: ['gc', 'runFinalization', 'runFinalizersOnExit']
  HasPlatformType:
    active: false
  IgnoredReturnValue:
    active: true
    restrictToAnnotatedMethods: true
    returnValueAnnotations: ['*.CheckReturnValue', '*.CheckResult']
    ignoreReturnValueAnnotations: ['*.CanIgnoreReturnValue']
    ignoreFunctionCall: ['component[0-9]+']
    ignoreAnnotated: ['*.Pure', '*.SideEffect']
  ImplicitDefaultLocale:
    active: true
  ImplicitUnitReturnType:
    active: true
    allowExplicitReturnType: true
  InvalidRange:
    active: true
  IteratorHasNextCallsNextMethod:
    active: true
  IteratorNotThrowingNoSuchElementException:
    active: true
  LateinitUsage:
    active: true
    excludeAnnotatedClasses: ['*Component', '*Module']
    ignoreOnClassesPattern: 'Test|IT|TestCase'
    excludePackages: ['kotlin']
    excludeAnnotatedClasses: ['*Component', '*Module']
  MapGetWithNotNullAssertionOperator:
    active: true
  MissingPackageDeclaration:
    active: true
    allowedTargets: ['CLASS', 'INTERFACE', 'ENUM', 'ANNOTATION_CLASS', 'TYPEALIAS', 'OBJECT']
  NullableToStringCall:
    active: true
  RedundantElse:
    active: true
  RedundantHigherOrderMapUsage:
    active: true
  UnconditionalJumpStatementInLoop:
    active: true
  UnnecessaryNotNullOperator:
    active: true
  UnnecessaryNotNullCheck:
    active: true
  UnnecessarySafeCall:
    active: true
  UnreachableCode:
    active: true
  UnsafeCallOnNullableType:
    active: true
  UnsafeCast:
    active: true
  UnusedUnaryOperator:
    active: true
  UselessPostfixExpression:
    active: true
  WrongEqualsTypeParameter:
    active: true

style:
  active: true
  CollapsibleIfStatements:
    active: true
  DataClassContainsFunctions:
    active: true
    conversionFunctionPrefix: 'to'
    allowOperators: false
  DataClassShouldBeImmutable:
    active: true
  DestructuringDeclarationWithTooManyEntries:
    active: true
    maxDestructuringEntries: 6
  EqualsNullCall:
    active: true
  EqualsOnSignatureLine:
    active: true
  ExplicitItLambdaParameter:
    active: true
  ExpressionBodySyntax:
    active: true
    includeLineWrapping: true
  ForbiddenComment:
    active: true
    values: ['FIXME:', 'STOPSHIP:', 'TODO:']
    allowedAnnotations: ['kotlin.Deprecated', 'java.lang.Deprecated']
  ForbiddenImport:
    active: false
    imports: []
    forbiddenPatterns: '$^'
  ForbiddenMethodCall:
    active: false
    methods: []
  ForbiddenPublicDataClass:
    active: false
    ignorePackages: ['*.internal', '*.internal.*']
  ForbiddenVoid:
    active: false
    ignoreOverridden: false
    ignoreUsageInGenerics: false
  FunctionOnlyReturningConstant:
    active: true
    ignoreOverridableFunction: true
    ignoreAnnotatedFunction: ['*Composable']
    excludedFunctions: ['invoke']
  LoopWithTooManyJumpStatements:
    active: true
    maxJumpCount: 1
  MagicNumber:
    active: true
    ignoreNumbers: ['-1', '0', '1', '2', '10', '100', '1000']
    ignoreHashCodeFunction: true
    ignorePropertyDeclaration: false
    ignoreLocalVariableDeclaration: false
    ignoreNamedArgument: true
    ignoreConstantDeclaration: true
    ignoreCompanionObjectPropertyDeclaration: true
    ignoreAnnotation: false
    ignoreEnums: false
    ignoreRanges: false
    ignoreExtensionFunctions: true
    ignoreReceiver: false
  MaxLineLength:
    active: true
    maxLineLength: 120
    excludePackageStatements: true
    excludeImportStatements: true
    excludeCommentStatements: false
  MayBeConst:
    active: true
  ModifierOrder:
    active: true
  MultilineLambdaItParameter:
    active: true
  MultilineRawStringIndentation:
    active: true
    indentSize: 4
  NestedClassesVisibility:
    active: true
  NewLineAtEndOfFile:
    active: true
  NoTabs:
    active: false
  ObjectLiteralToLambda:
    active: true
  OptionalAbstractKeyword:
    active: true
  OptionalWhenBraces:
    active: true
  OptionalUnit:
    active: true
  OptionalValInAnnotation:
    active: true
  BracesOnWhenStatements:
    active: true
  ProtectedMemberInFinalClass:
    active: true
  RedundantExplicitType:
    active: true
  RedundantHigherOrderMapUsage:
    active: true
  RedundantVisibilityModifierRule:
    active: true
  ReturnCount:
    active: true
    maxReturnCount: 2
    excludedFunctions: 'equals'
    excludedReturnFromLambda: true
  SafeCast:
    active: true
  SerialVersionUIDInSerializableClass:
    active: true
  SpacingBetweenPackageAndImports:
    active: true
  ThrowsCount:
    active: true
    max: 2
    excludeGuardClauses: true
  TrailingWhitespace:
    active: false
  UnderscoresInNumericLiterals:
    active: true
    acceptableDecimalLength: 5
  UnnecessaryAbstractKeyword:
    active: true
    excludePrivateConstructors: true
  UnnecessaryApply:
    active: true
  UnnecessaryFilter:
    active: true
  UnnecessaryInheritance:
    active: true
  UnnecessaryInnerClass:
    active: true
  UnnecessaryLet:
    active: true
  UnnecessaryParentheses:
    active: true
    allowForUnclearPrecedence: false
  UnnecessaryPublicReceiver:
    active: true
  UnnecessarySort:
    active: true
  UnnecessaryStringTemplate:
    active: true
  UntilInsteadOfRangeTo:
    active: true
  UnusedImports:
    active: true
  UnusedPrivateClass:
    active: true
  UnusedPrivateMember:
    active: true
    allowedNames: '(_|ignored|expected|serialVersionUID)'
  UseDataClass:
    active: true
    allowVars: false
  UseEmptyCounter:
    active: true
  UseIfEmptyOrIfBlank:
    active: true
  UseRequire:
    active: true
  UseRequireNotNull:
    active: true
  UselessCallOnNotNull:
    active: true
  UtilityClassWithPublicConstructor:
    active: true
  VarCouldBeVal:
    active: true
    ignoreLateinitVar: false
  WildcardImport:
    active: true
    excludeImports: ['java.util.*', 'kotlinx.android.synthetic.*', 'androidx.biometric.*']
```

#### Lint Configuration

Add to `app/build.gradle.kts`:

```kotlin
android {
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        warningsAsErrors = true
        disable += setOf(
            "InvalidPackage",
            "OldTargetApi",
            "GoogleAppIndexingWarning"
        )
        baseline = file("lint-baseline.xml")
    }
}
```

### Code Formatting

#### Ktlint Integration

Add to `build.gradle.kts`:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
}

ktlint {
    android = true
    outputToConsole = true
    ignoreFailures = false
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporters.ReporterType.HTML)
    }
    disabledRules.add("import-ordering")
}
```

#### Custom Code Templates

Create custom file templates in Android Studio:

1. **File → Settings → Editor → File and Code Templates**
2. **Files tab → + → Create Template**

Example: Activity template

```kotlin
#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME}

#end
#parse("Kotlin Class.kt")
import android.os.Bundle
import androidx.activity.ComponentActivity

class ${NAME} : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Implement activity
    }
}
```

## Debugging Tools

### Android Studio Debugging

#### Breakpoint Configuration
1. **Line Breakpoints**: Click line numbers to add breakpoints
2. **Conditional Breakpoints**: Right-click breakpoint → Edit → Add condition
3. **Method Breakpoints**: Right-click method → Add Method Breakpoint
4. **Exception Breakpoints**: View → Breakpoints → + → Java Exception Breakpoint

#### Debug Configuration

Create custom debug configurations:

```json
{
  "configurations": [
    {
      "name": "ShadowAi Debug",
      "type": "android",
      "request": "launch",
      "project": "ShadowAi",
      "activity": "com.shadowai.app.ComposeMainActivity",
      "target": "device"
    }
  ]
}
```

#### Logcat Filtering

Create custom Logcat filters:

```bash
# Filter for ShadowAi logs
tag:ShadowAI

# Filter for specific components
tag:ShadowAI AND (TaskExecutor OR Agent OR Security)

# Filter for errors only
level:ERROR AND tag:ShadowAI
```

### Custom Debug Utilities

#### Debug Logger

Create a debug utility class:

```kotlin
object DebugLogger {
    private const val TAG = "ShadowAiDebug"
    private val isEnabled = BuildConfig.DEBUG
    
    fun d(message: String) {
        if (isEnabled) Log.d(TAG, message)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (isEnabled) Log.e(TAG, message, throwable)
    }
    
    fun w(message: String) {
        if (isEnabled) Log.w(TAG, message)
    }
    
    fun i(message: String) {
        if (isEnabled) Log.i(TAG, message)
    }
    
    fun json(tag: String, json: String) {
        if (isEnabled) {
            val formatted = JsonFormatter.format(json)
            Log.d(tag, formatted)
        }
    }
}
```

#### Performance Monitor

Create a performance monitoring utility:

```kotlin
class PerformanceMonitor {
    private val measurements = mutableMapOf<String, Long>()
    
    fun startMeasurement(name: String) {
        measurements[name] = System.currentTimeMillis()
    }
    
    fun endMeasurement(name: String): Long {
        val startTime = measurements[name] ?: return -1
        val duration = System.currentTimeMillis() - startTime
        DebugLogger.d("$name took ${duration}ms")
        measurements.remove(name)
        return duration
    }
    
    fun measure(name: String, block: () -> Unit) {
        startMeasurement(name)
        block()
        endMeasurement(name)
    }
}
```

### Memory Analysis

#### Memory Profiler Setup

1. **Open Memory Profiler**: View → Tool Windows → Memory Profiler
2. **Record Memory Usage**: Click Record button
3. **Trigger GC**: Click GC button
4. **Dump Java Heap**: Click Dump Java Heap button

#### Leak Detection

Use LeakCanary for automatic leak detection:

```kotlin
// Add to dependencies
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

// Initialize in Application class
class ShadowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            LeakCanary.install(this)
        }
    }
}
```

## Testing Tools

### Test Framework Configuration

#### Test Dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit-ktx:1.1.5")
    
    // Android Instrumentation Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}
```

#### Test Configuration

Create test configuration in `build.gradle.kts`:

```kotlin
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        
        animationsDisabled = true
        
        unitTests.all {
            jvmArgs("-Xmx1g")
            systemProperty("robolectric.logging", "stdout")
        }
    }
}
```

### Test Utilities

#### Test Data Factory

```kotlin
object TestDataFactory {
    
    fun createTestTask(
        type: TaskType = TaskType.CONVERSATION,
        input: String = "Test input",
        id: Long = 1L
    ): Task {
        return Task(
            id = id,
            type = type,
            input = input,
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun createTestUser(
        name: String = "Test User",
        email: String = "test@example.com"
    ): User {
        return User(
            name = name,
            email = email,
            preferences = UserPreferences()
        )
    }
    
    fun createTestMessage(
        content: String = "Test message",
        sender: String = "user",
        timestamp: Long = System.currentTimeMillis()
    ): Message {
        return Message(
            content = content,
            sender = sender,
            timestamp = timestamp
        )
    }
}
```

#### Test Rules

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

### UI Testing

#### Espresso Test Example

```kotlin
@RunWith(AndroidJUnit4::class)
class ChatUITest {
    
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)
    
    @Test
    fun testSendMessage() {
        // Type message
        onView(withId(R.id.message_input))
            .perform(typeText("Hello, ShadowAI!"))
        
        // Send message
        onView(withId(R.id.send_button))
            .perform(click())
        
        // Verify message appears
        onView(withId(R.id.message_list))
            .check(matches(hasDescendant(withText("Hello, ShadowAI!"))))
    }
    
    @Test
    fun testTypingIndicator() {
        // Start typing
        onView(withId(R.id.message_input))
            .perform(typeText("Typing..."))
        
        // Verify typing indicator appears
        onView(withId(R.id.typing_indicator))
            .check(matches(isDisplayed()))
    }
}
```

## Performance Analysis Tools

### Profiling Tools

#### CPU Profiler

1. **Open CPU Profiler**: View → Tool Windows → CPU Profiler
2. **Record CPU Usage**: Click Record button
3. **Analyze Methods**: Look for methods with high CPU usage
4. **Export Trace**: Export for detailed analysis

#### Memory Profiler

1. **Open Memory Profiler**: View → Tool Windows → Memory Profiler
2. **Monitor Memory Usage**: Watch memory allocation over time
3. **Force GC**: Click GC button to trigger garbage collection
4. **Dump Heap**: Click Dump Java Heap to analyze memory usage

#### Network Profiler

1. **Open Network Profiler**: View → Tool Windows → Network Profiler
2. **Monitor Network Calls**: Watch network requests and responses
3. **Analyze Response Times**: Identify slow network operations
4. **Check Data Usage**: Monitor data consumption

### Performance Testing

#### Benchmarking

Create performance benchmarks:

```kotlin
class PerformanceBenchmark {
    
    @Test
    fun benchmarkTaskExecution() {
        val task = TestDataFactory.createTestTask()
        val executor = TaskExecutor(/* dependencies */)
        
        val startTime = System.currentTimeMillis()
        
        repeat(100) {
            executor.execute(task)
        }
        
        val endTime = System.currentTimeMillis()
        val averageTime = (endTime - startTime) / 100
        
        assertTrue("Task execution too slow: ${averageTime}ms", averageTime < 100)
    }
    
    @Test
    fun benchmarkDatabaseOperations() {
        val database = ShadowDatabase.createInMemory()
        val dao = database.taskDao()
        
        val tasks = List(1000) { TestDataFactory.createTestTask(id = it.toLong()) }
        
        val startTime = System.currentTimeMillis()
        
        tasks.forEach { task ->
            dao.insert(task)
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        assertTrue("Database insertion too slow: ${totalTime}ms", totalTime < 1000)
    }
}
```

#### Memory Leak Detection

```kotlin
class MemoryLeakTest {
    
    @Test
    fun testActivityMemoryLeaks() {
        val activity = Robolectric.setupActivity(MainActivity::class.java)
        
        // Perform operations that might cause leaks
        activity.finish()
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        
        // Verify activity is properly cleaned up
        assertFalse("Activity not properly destroyed", activity.isFinishing)
    }
}
```

## Security Analysis Tools

### Static Security Analysis

#### Security Lint Rules

Add security-specific lint rules:

```kotlin
// Custom lint rule for PII logging
class PiiLoggingDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "PiiLogging",
            briefDescription = "PII data should not be logged",
            explanation = "Logging PII data can lead to security vulnerabilities",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                PiiLoggingDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("d", "e", "w", "i", "v")
    }
    
    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = context.evaluator.getContainingClass(method)
        if (containingClass?.qualifiedName != "android.util.Log") {
            return
        }
        
        val arguments = node.valueArguments
        if (arguments.size >= 2) {
            val messageArg = arguments[1]
            if (messageArg is UReferenceExpression) {
                val variableName = messageArg.identifier?.name
                if (variableName?.contains("email", ignoreCase = true) == true ||
                    variableName?.contains("password", ignoreCase = true) == true) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Potential PII data being logged: $variableName"
                    )
                }
            }
        }
    }
}
```

#### Security Testing

```kotlin
class SecurityTest {
    
    @Test
    fun testPiiMasking() {
        val processor = PiiMaskingProcessor()
        
        val input = "Contact me at john@example.com or call 555-123-4567"
        val masked = processor.maskPii(input)
        
        assertFalse("Email not masked", masked.contains("john@example.com"))
        assertFalse("Phone not masked", masked.contains("555-123-4567"))
        assertTrue("Masking failed", masked.contains("[EMAIL_REDACTED]"))
        assertTrue("Masking failed", masked.contains("[PHONE_REDACTED]"))
    }
    
    @Test
    fun testCircuitBreaker() {
        val breaker = CircuitBreaker(failureThreshold = 2)
        
        // Trigger failures
        repeat(2) {
            try {
                breaker.execute { throw RuntimeException("test") }
            } catch (_: RuntimeException) {}
        }
        
        assertEquals("Circuit should be open", CircuitBreaker.State.OPEN, breaker.currentState)
        
        // Verify circuit breaker throws exception
        assertThrows<CircuitOpenException> {
            breaker.execute { "should not execute" }
        }
    }
}
```

### Dynamic Security Analysis

#### Runtime Security Testing

```kotlin
class RuntimeSecurityTest {
    
    @Test
    fun testInputValidation() {
        val validator = InputValidator()
        
        // Test SQL injection
        val sqlInjection = "'; DROP TABLE users; --"
        assertFalse("SQL injection not detected", validator.isValidInput(sqlInjection))
        
        // Test XSS
        val xss = "<script>alert('xss')</script>"
        assertFalse("XSS not detected", validator.isValidInput(xss))
        
        // Test command injection
        val commandInjection = "; rm -rf /"
        assertFalse("Command injection not detected", validator.isValidInput(commandInjection))
    }
    
    @Test
    fun testEncryption() {
        val securityManager = SecurityManager(context)
        
        val originalData = "sensitive information"
        val encrypted = securityManager.encrypt(originalData)
        val decrypted = securityManager.decrypt(encrypted)
        
        assertEquals("Encryption/decryption failed", originalData, decrypted)
        assertNotEquals("Data not encrypted", originalData, encrypted)
    }
}
```

## Native Development Tools

### CMake Configuration

#### Advanced CMake Setup

```cmake
# CMakeLists.txt
cmake_minimum_required(VERSION 3.18.1)
project("shadowai-native")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler flags
if(ANDROID)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")
    set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -g -O0 -DDEBUG")
endif()

# Add subdirectories
add_subdirectory(src/main/cpp)

# Link libraries
find_library(log-lib log)
target_link_libraries(shadowai-native ${log-lib})

# Include directories
target_include_directories(shadowai-native PRIVATE src/main/cpp/include)

# Source files
file(GLOB_RECURSE SOURCES "src/main/cpp/*.cpp")
add_library(shadowai-native SHARED ${SOURCES})
```

### Native Debugging

#### GDB Setup

```bash
# Attach GDB to running process
adb shell gdbserver :5039 --attach $(adb shell pidof com.shadowai.app)

# Connect from host
adb forward tcp:5039 tcp:5039
gdb
(gdb) target remote :5039
(gdb) break your_function
(gdb) continue
```

#### Native Logging

```cpp
#include <android/log.h>

#define LOG_TAG "ShadowAiNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_shadowai_app_inference_NativeBridge_debugFunction(
    JNIEnv *env,
    jobject thiz) {
    
    LOGD("Debug function called");
    
    // Your native code here
    
    LOGD("Debug function completed");
}
```

### Native Testing

#### Unit Testing with Google Test

```cpp
#include <gtest/gtest.h>
#include "native_bridge.h"

class NativeBridgeTest : public ::testing::Test {
protected:
    void SetUp() override {
        // Setup test environment
    }
    
    void TearDown() override {
        // Cleanup test environment
    }
};

TEST_F(NativeBridgeTest, TestGenerateText) {
    const char* result = generate_text("Hello", 10);
    
    ASSERT_NE(result, nullptr);
    ASSERT_GT(strlen(result), 0);
    
    free((void*)result);
}

TEST_F(NativeBridgeTest, TestModelLoading) {
    bool success = load_model("/path/to/model");
    
    EXPECT_TRUE(success);
}
```

## CI/CD Tools

### GitHub Actions

#### Build and Test Workflow

```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Run lint
      run: ./gradlew lint
    
    - name: Run detekt
      run: ./gradlew detekt
    
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

  security:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Run security scan
      run: ./gradlew dependencyCheckAnalyze
    
    - name: Check for vulnerabilities
      run: ./gradlew dependencyCheckReport

  performance:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Run performance tests
      run: ./gradlew :app:connectedAndroidTest -PperformanceTest=true
```

#### Release Workflow

```yaml
# .github/workflows/release.yml
name: Release

on:
  release:
    types: [published]

jobs:
  build-release:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Sign APK
      run: |
        jarsigner -verbose -keystore ${{ secrets.KEYSTORE_FILE }} \
          -storepass ${{ secrets.KEYSTORE_PASSWORD }} \
          -keypass ${{ secrets.KEY_PASSWORD }} \
          app/build/outputs/apk/release/app-release-unsigned.apk \
          ${{ secrets.KEY_ALIAS }}
    
    - name: Upload release assets
      uses: actions/upload-release-asset@v1
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      with:
        upload_url: ${{ github.event.release.upload_url }}
        asset_path: ./app/build/outputs/apk/release/app-release.apk
        asset_name: shadowai-release.apk
        asset_content_type: application/vnd.android.package-archive
```

### Fastlane Integration

#### Fastfile Configuration

```ruby
# fastlane/Fastfile
default_platform(:android)

platform :android do
  desc "Run all tests"
  lane :test do
    gradle(
      task: "test"
    )
  end
  
  desc "Build debug APK"
  lane :debug do
    gradle(
      task: "assemble",
      build_type: "Debug"
    )
  end
  
  desc "Build release APK"
  lane :release do
    gradle(
      task: "assemble",
      build_type: "Release"
    )
  end
  
  desc "Deploy to Play Store"
  lane :deploy do
    supply(
      track: 'internal',
      apk: 'app/build/outputs/apk/release/app-release.apk'
    )
  end
end
```

## Custom Development Utilities

### Code Generation Tools

#### Template Generator

Create a script to generate common code patterns:

```bash
#!/bin/bash
# scripts/generate.sh

TEMPLATE_DIR="templates"
OUTPUT_DIR="generated"

generate_class() {
    local name=$1
    local type=$2
    
    case $type in
        "activity")
            template="ActivityTemplate.kt"
            ;;
        "fragment")
            template="FragmentTemplate.kt"
            ;;
        "viewModel")
            template="ViewModelTemplate.kt"
            ;;
        *)
            echo "Unknown type: $type"
            exit 1
            ;;
    esac
    
    if [ -f "$TEMPLATE_DIR/$template" ]; then
        sed "s/CLASS_NAME/$name/g" "$TEMPLATE_DIR/$template" > "$OUTPUT_DIR/${name}.kt"
        echo "Generated $name.kt"
    else
        echo "Template not found: $template"
        exit 1
    fi
}

# Usage: ./scripts/generate.sh MyActivity activity
generate_class $1 $2
```

#### Utility Classes Generator

```kotlin
// CodeGenerator.kt
class CodeGenerator {
    
    fun generateViewModel(name: String): String {
        return """
            class ${name}ViewModel : ViewModel() {
                private val _state = MutableLiveData<${name}State>()
                val state: LiveData<${name}State> = _state
                
                fun loadData() {
                    viewModelScope.launch {
                        try {
                            val data = repository.getData()
                            _state.value = ${name}State.Success(data)
                        } catch (e: Exception) {
                            _state.value = ${name}State.Error(e.message)
                        }
                    }
                }
            }
            
            sealed class ${name}State {
                data class Success(val data: Any) : ${name}State()
                data class Error(val message: String?) : ${name}State()
                object Loading : ${name}State()
            }
        """.trimIndent()
    }
}
```

### Development Scripts

#### Setup Script

```bash
#!/bin/bash
# scripts/setup.sh

echo "🚀 Setting up ShadowAi development environment..."

# Check prerequisites
command -v java >/dev/null 2>&1 || { echo "❌ Java not found"; exit 1; }
command -v git >/dev/null 2>&1 || { echo "❌ Git not found"; exit 1; }

# Install dependencies
echo "📦 Installing dependencies..."
./gradlew dependencies

# Setup git hooks
echo "🪝 Setting up git hooks..."
cp scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# Create local.properties if needed
if [ ! -f local.properties ]; then
    echo "📝 Creating local.properties..."
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo "ndk.dir=$ANDROID_HOME/ndk/25.2.9519653" >> local.properties
fi

echo "✅ Setup complete!"
echo "💡 Run './gradlew assembleDebug' to build the project"
```

#### Pre-commit Hook

```bash
#!/bin/bash
# scripts/pre-commit

echo "🔍 Running pre-commit checks..."

# Run lint
echo "Linting code..."
./gradlew lint
if [ $? -ne 0 ]; then
    echo "❌ Lint failed"
    exit 1
fi

# Run detekt
echo "Running detekt..."
./gradlew detekt
if [ $? -ne 0 ]; then
    echo "❌ Detekt failed"
    exit 1
fi

# Run tests
echo "Running tests..."
./gradlew test
if [ $? -ne 0 ]; then
    echo "❌ Tests failed"
    exit 1
fi

echo "✅ Pre-commit checks passed!"
```

### Development Dashboard

#### Custom Dashboard

Create a development dashboard using a simple HTML file:

```html
<!DOCTYPE html>
<html>
<head>
    <title>ShadowAi Development Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .card { border: 1px solid #ccc; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .status { display: inline-block; padding: 2px 8px; border-radius: 3px; color: white; }
        .success { background-color: #28a745; }
        .error { background-color: #dc3545; }
        .warning { background-color: #ffc107; color: black; }
    </style>
</head>
<body>
    <h1>ShadowAi Development Dashboard</h1>
    
    <div class="card">
        <h3>Build Status</h3>
        <div id="build-status" class="status">Unknown</div>
    </div>
    
    <div class="card">
        <h3>Test Results</h3>
        <div id="test-results">Loading...</div>
    </div>
    
    <div class="card">
        <h3>Code Quality</h3>
        <div id="code-quality">Loading...</div>
    </div>
    
    <script>
        // Fetch build status
        fetch('/api/build-status')
            .then(response => response.json())
            .then(data => {
                const statusEl = document.getElementById('build-status');
                statusEl.textContent = data.status;
                statusEl.className = 'status ' + (data.success ? 'success' : 'error');
            });
    </script>
</body>
</html>
```

---

**Remember**: These tools are designed to improve your development experience and code quality. Use them consistently to maintain high standards in the ShadowAi project.