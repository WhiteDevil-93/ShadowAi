# ShadowAi Junior Developer Setup Guide

This guide provides step-by-step instructions for junior developers to set up the ShadowAi development environment and understand the codebase structure.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Development Environment Setup](#development-environment-setup)
3. [Codebase Overview](#codebase-overview)
4. [Building and Running](#building-and-running)
5. [Common Development Tasks](#common-development-tasks)
6. [Troubleshooting](#troubleshooting)
7. [Learning Resources](#learning-resources)

## Prerequisites

### Required Software

- **Android Studio Hedgehog (2023.1.1)** or later
- **JDK 17** or later
- **Android SDK** (API 24+)
- **NDK r25c** or later (for native builds)
- **Git** (for version control)

### System Requirements

- Windows 10/11, macOS 10.15+, or Linux
- 8GB RAM minimum (16GB recommended)
- 10GB free disk space

## Development Environment Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-organization/ShadowAi.git
cd ShadowAi
```

### 2. Install Dependencies

#### Android Studio Setup

1. Open Android Studio
2. Import the project: `File → New → Import Project`
3. Select the ShadowAi directory
4. Wait for Gradle sync to complete

#### NDK Setup (for native builds)

1. Open Android Studio
2. Go to `File → Settings → Android SDK → SDK Tools`
3. Check "NDK (Side by side)" and install
4. Verify NDK path in `local.properties`:
   ```
   ndk.dir=C\:\\Users\\yourname\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653
   ```

### 3. Configure Development Environment

#### Create local.properties (if not exists)

```properties
# Android SDK path
sdk.dir=C\:\\Users\\yourname\\AppData\\Local\\Android\\Sdk

# NDK path
ndk.dir=C\:\\Users\\yourname\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653

# Optional: GCP configuration
GCP_PROJECT_ID=your-project-id
GCP_REGION=us-central1
USE_ISOLATED_INFERENCE_ENGINE=true
```

### 4. Initial Build

```bash
# Clean build
./gradlew clean assembleDebug

# Run tests
./gradlew test

# Check lint
./gradlew lint
```

## Codebase Overview

### Module Structure

```
ShadowAi/
├── app/                          # Main Android application
│   ├── src/main/java/com/shadowai/app/
│   │   ├── agent/               # AI Agent system
│   │   ├── ai/                  # AI inference & models
│   │   ├── security/            # Security components
│   │   ├── execution/           # Task execution
│   │   ├── providers/           # Provider management
│   │   ├── ui/                  # User interface
│   │   └── db/                  # Database layer
│   └── build.gradle.kts
├── core-contracts/              # Shared contracts & security
├── model-catalog/               # Model discovery & management
├── provider-adapters/           # Cloud provider adapters
├── inference_process/           # Isolated inference service
└── plans/                       # Project documentation
```

### Key Architecture Components

#### 1. Security Layer
- **PiiMaskingProcessor**: Masks sensitive data before sending to cloud
- **SecurityManager**: Handles encryption and key management
- **CircuitBreaker**: Prevents cascading failures

#### 2. Agent System
- **ShadowAgent**: Main agent for task execution
- **SupervisorAgent**: High-level task orchestration
- **AgenticLoop**: Iterative execution with context management

#### 3. Task Execution
- **TaskExecutor**: Routes tasks to appropriate providers
- **HybridAiExecutor**: Handles local and cloud execution
- **DeviceActionExecutor**: System interactions

## Building and Running

### 1. Build the Application

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

### 2. Run on Device/Emulator

```bash
# Install debug APK
./gradlew installDebug

# Run tests on device
./gradlew connectedAndroidTest
```

### 3. Run Backend Service (Optional)

```bash
cd backend
./gradlew run
```

## Common Development Tasks

### Adding a New Feature

1. **Create a new branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Follow the module structure**:
   - UI changes → `app/src/main/java/com/shadowai/app/ui/`
   - Security changes → `core-contracts/src/main/kotlin/com/shadowai/core/security/`
   - Provider changes → `provider-adapters/src/main/kotlin/`

3. **Write tests**:
   - Unit tests → `app/src/test/java/`
   - Instrumentation tests → `app/src/androidTest/java/`

4. **Update documentation**:
   - Add KDoc comments to public methods
   - Update relevant README files

5. **Commit and push**:
   ```bash
   git add .
   git commit -m "feat(module): description of change"
   git push origin feature/your-feature-name
   ```

### Debugging Tips

#### Common Issues

1. **Build Failures**:
   ```bash
   # Clean and rebuild
   ./gradlew clean assembleDebug
   
   # Invalidate caches
   # In Android Studio: File → Invalidate Caches / Restart
   ```

2. **Native Build Issues**:
   ```bash
   # Check NDK version
   ./gradlew :app:externalNativeBuildDebug
   
   # Clean native builds
   ./gradlew clean
   ```

3. **Dependency Issues**:
   ```bash
   # Update dependencies
   ./gradlew dependencies --update-locks
   
   # Check for conflicts
   ./gradlew dependencyInsight --dependency=kotlin
   ```

#### Debugging Tools

1. **Logcat**: Use Android Studio's Logcat window
2. **Breakpoints**: Set breakpoints in Kotlin/Java code
3. **Memory Profiler**: Monitor memory usage
4. **Network Profiler**: Monitor network requests

### Testing Guidelines

#### Unit Tests

```kotlin
@Test
fun `test description`() = runTest {
    // Arrange
    val sut = SystemUnderTest()
    
    // Act
    val result = sut.method()
    
    // Assert
    assertTrue(result)
}
```

#### Test Naming Convention

- Use descriptive test names
- Follow `Given_When_Then` pattern
- Test one thing per test

#### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests="com.shadowai.app.SomeTestClass"

# Run with coverage
./gradlew jacocoTestReport
```

## Troubleshooting

### Common Build Errors

#### 1. "Could not find NDK"
**Solution**: Install NDK via Android Studio SDK Manager

#### 2. "Failed to resolve dependencies"
**Solution**: 
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

#### 3. "CMake Error"
**Solution**: Check CMakeLists.txt syntax and NDK version compatibility

#### 4. "Permission denied"
**Solution**: Ensure proper file permissions and AndroidManifest.xml permissions

### Performance Issues

#### Slow Builds
- Use `./gradlew assembleDebug` instead of full build
- Enable Gradle daemon in `gradle.properties`
- Use build cache

#### Memory Issues
- Increase Android Studio memory in `studio.vmoptions`
- Use `./gradlew clean` periodically
- Monitor memory usage in Android Studio

### Security Issues

#### PII Masking Not Working
- Check `PiiMaskingProcessor` configuration
- Verify patterns in test cases
- Ensure masking is enabled in settings

#### Circuit Breaker Not Opening
- Check failure threshold configuration
- Verify exception types are being caught
- Monitor logs for circuit state changes

## Learning Resources

### Kotlin & Android

- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Android Developer Guide](https://developer.android.com/guide)
- [Jetpack Compose Tutorial](https://developer.android.com/jetpack/compose/tutorial)

### Architecture Patterns

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [MVVM Pattern](https://developer.android.com/topic/architecture)
- [Dependency Injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)

### ShadowAi Specific

- [AGENTS.md](../AGENTS.md) - Project architecture guide
- [WORKTREE.md](../WORKTREE.md) - Development workflow
- [plans/todo.md](../plans/todo.md) - Project roadmap

### Security Best Practices

- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Secure Coding Guidelines](https://cwe.mitre.org/)

### Testing

- [Android Testing Guide](https://developer.android.com/training/testing)
- [Kotlin Test Documentation](https://kotest.io/)
- [MockK Documentation](https://mockk.io/)

## Getting Help

### Internal Resources

- **Code Reviews**: All changes require code review
- **Pair Programming**: Schedule with senior developers
- **Documentation**: Check existing docs first
- **Wiki**: Internal knowledge base

### External Resources

- **Stack Overflow**: Use tags `android`, `kotlin`, `shadowai`
- **GitHub Issues**: Report bugs and feature requests
- **Android Developers Community**: Google Groups and forums

### Emergency Contacts

- **Senior Developer**: [Contact Info]
- **Team Lead**: [Contact Info]
- **On-call**: [Contact Info]

---

**Note**: This guide is a living document. Please suggest improvements via pull requests.