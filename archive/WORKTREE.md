# ShadowAi Worktree — Essential Files Only

> Every file required to build and run the app.
> Excludes: tests, docs, CI/CD, IDE configs, build outputs, archives, node_modules.

---

## Root — Build Configuration

```
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
.editorconfig
.gitignore
.gitattributes
.firebaserc
.pre-commit-config.yaml
```

## buildSrc — Custom Gradle Plugin

```
buildSrc/build.gradle.kts
buildSrc/src/main/kotlin/ModuleBoundariesPlugin.kt
```

---

## app — Main Android Module

### Build & Config

```
app/build.gradle.kts
app/proguard-rules.pro
app/google-services.json.example
app/src/main/AndroidManifest.xml
app/src/debug/res/xml/network_security_config.xml
app/src/release/res/xml/network_security_config.xml
```

### Resources — Drawables

```
app/src/main/res/drawable/bg_app_radial.xml
app/src/main/res/drawable/bg_avatar_ai.xml
app/src/main/res/drawable/bg_avatar_user.xml
app/src/main/res/drawable/bg_button_icon.xml
app/src/main/res/drawable/bg_button_primary.xml
app/src/main/res/drawable/bg_card.xml
app/src/main/res/drawable/bg_chip.xml
app/src/main/res/drawable/bg_floating_tabs_container.xml
app/src/main/res/drawable/bg_input.xml
app/src/main/res/drawable/bg_status_ready.xml
app/src/main/res/drawable/bg_status_selected.xml
app/src/main/res/drawable/ic_attach.xml
app/src/main/res/drawable/ic_chevron_down.xml
app/src/main/res/drawable/ic_chevron_up.xml
app/src/main/res/drawable/ic_folder.xml
app/src/main/res/drawable/ic_key.xml
app/src/main/res/drawable/ic_menu.xml
app/src/main/res/drawable/ic_mic.xml
app/src/main/res/drawable/ic_notification.xml
app/src/main/res/drawable/ic_send.xml
app/src/main/res/drawable/ic_settings.xml
app/src/main/res/drawable/ic_storage.xml
app/src/main/res/drawable/ic_tune.xml
app/src/main/res/drawable/tab_indicator.xml
```

### Resources — Values & Colors

```
app/src/main/res/values/attrs.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/dimens.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/styles.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-night/colors.xml
app/src/main/res/color/text_input_box_stroke.xml
```

### Resources — Menus & XML Config

```
app/src/main/res/menu/drawer_menu.xml
app/src/main/res/menu/top_app_bar_menu.xml
app/src/main/res/xml/accessibility_service_config.xml
app/src/main/res/xml/network_security_config.xml
app/src/main/res/xml/network_security_config_release.xml
```

### Resources — Launcher Icons

```
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-mdpi/ic_launcher.png
app/src/main/res/mipmap-mdpi/ic_launcher_background.png
app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png
app/src/main/res/mipmap-hdpi/ic_launcher.png
app/src/main/res/mipmap-hdpi/ic_launcher_background.png
app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png
app/src/main/res/mipmap-xhdpi/ic_launcher.png
app/src/main/res/mipmap-xhdpi/ic_launcher_background.png
app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png
app/src/main/res/mipmap-xxhdpi/ic_launcher.png
app/src/main/res/mipmap-xxhdpi/ic_launcher_background.png
app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.png
app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png
```

### Kotlin Source — Core Application

```
app/src/main/java/com/shadowai/app/ComposeMainActivity.kt
app/src/main/java/com/shadowai/app/ShadowApplication.kt
```

### Kotlin Source — Accessibility

```
app/src/main/java/com/shadowai/app/accessibility/AccessibilityManager.kt
```

### Kotlin Source — Admin

```
app/src/main/java/com/shadowai/app/admin/AdminContract.kt
app/src/main/java/com/shadowai/app/admin/implementation/AdminRepository.kt
```

### Kotlin Source — Agent

```
app/src/main/java/com/shadowai/app/agent/AgentErrors.kt
app/src/main/java/com/shadowai/app/agent/AgentMessage.kt
app/src/main/java/com/shadowai/app/agent/AgentResult.kt
app/src/main/java/com/shadowai/app/agent/ShadowAgent.kt
app/src/main/java/com/shadowai/app/agent/TemplateVerifier.kt
app/src/main/java/com/shadowai/app/agent/VerificationEngine.kt
```

### Kotlin Source — AI & Inference

```
app/src/main/java/com/shadowai/app/ai/AIMode.kt
app/src/main/java/com/shadowai/app/ai/AuthInterceptor.kt
app/src/main/java/com/shadowai/app/ai/LlamaNative.kt
app/src/main/java/com/shadowai/app/ai/LocalBrainManager.kt
app/src/main/java/com/shadowai/app/ai/LocalInferenceManager.kt
app/src/main/java/com/shadowai/app/ai/LocalLiquidEngine.kt
app/src/main/java/com/shadowai/app/ai/MemoryConfig.kt
app/src/main/java/com/shadowai/app/ai/MemoryConstants.kt
app/src/main/java/com/shadowai/app/ai/MemoryFormatter.kt
app/src/main/java/com/shadowai/app/ai/MemoryManager.kt
app/src/main/java/com/shadowai/app/ai/MemoryPressureMonitor.kt
app/src/main/java/com/shadowai/app/ai/ModelDownloader.kt
app/src/main/java/com/shadowai/app/ai/ModelLoadingException.kt
app/src/main/java/com/shadowai/app/ai/ModelLoadingHelper.kt
app/src/main/java/com/shadowai/app/ai/ModelMigrationManager.kt
app/src/main/java/com/shadowai/app/ai/OllamaApi.kt
app/src/main/java/com/shadowai/app/ai/OpenAiApi.kt
app/src/main/java/com/shadowai/app/ai/PromptManager.kt
app/src/main/java/com/shadowai/app/ai/SafetySettingsManager.kt
```

### Kotlin Source — Authentication

```
app/src/main/java/com/shadowai/app/auth/AuthViewModel.kt
app/src/main/java/com/shadowai/app/auth/GoogleAuthManager.kt
app/src/main/java/com/shadowai/app/auth/TokenRefreshManager.kt
app/src/main/java/com/shadowai/app/auth/User.kt
app/src/main/java/com/shadowai/app/auth/UserPreferences.kt
app/src/main/java/com/shadowai/app/auth/UserRepository.kt
```

### Kotlin Source — Cache

```
app/src/main/java/com/shadowai/app/cache/DiskCache.kt
app/src/main/java/com/shadowai/app/cache/LRUCache.kt
```

### Kotlin Source — Cloud

```
app/src/main/java/com/shadowai/app/cloud/FirebaseGoogleCloudIntegration.kt
```

### Kotlin Source — Database

```
app/src/main/java/com/shadowai/app/db/DatabaseCrypto.kt
app/src/main/java/com/shadowai/app/db/EncryptedDatabaseHelper.kt
app/src/main/java/com/shadowai/app/db/PersistenceMappers.kt
app/src/main/java/com/shadowai/app/db/ShadowDatabase.kt
app/src/main/java/com/shadowai/app/db/ShadowMigrations.kt
app/src/main/java/com/shadowai/app/db/ShadowPersistence.kt
```

### Kotlin Source — Device Contracts

```
app/src/main/java/com/shadowai/app/device/AccessibilityContract.kt
app/src/main/java/com/shadowai/app/device/MediaControlContract.kt
app/src/main/java/com/shadowai/app/device/MessagingContract.kt
app/src/main/java/com/shadowai/app/device/SystemInteractionContract.kt
app/src/main/java/com/shadowai/app/device/TelephonyContract.kt
```

### Kotlin Source — Device Implementations

```
app/src/main/java/com/shadowai/app/device/implementation/AndroidAccessibility.kt
app/src/main/java/com/shadowai/app/device/implementation/AndroidMediaControl.kt
app/src/main/java/com/shadowai/app/device/implementation/AndroidMessaging.kt
app/src/main/java/com/shadowai/app/device/implementation/AndroidResourceMonitor.kt
app/src/main/java/com/shadowai/app/device/implementation/AndroidSystemInteraction.kt
app/src/main/java/com/shadowai/app/device/implementation/AndroidTelephony.kt
app/src/main/java/com/shadowai/app/device/implementation/ShadowAccessibilityService.kt
```

### Kotlin Source — Dependency Injection (Hilt)

```
app/src/main/java/com/shadowai/app/di/AdminModule.kt
app/src/main/java/com/shadowai/app/di/AiServicesModule.kt
app/src/main/java/com/shadowai/app/di/AppBindings.kt
app/src/main/java/com/shadowai/app/di/AppModule.kt
app/src/main/java/com/shadowai/app/di/ArtifactSystemModule.kt
app/src/main/java/com/shadowai/app/di/DiagnosticsModule.kt
app/src/main/java/com/shadowai/app/di/HotSwappingModule.kt
app/src/main/java/com/shadowai/app/di/PipelinePlannerModule.kt
app/src/main/java/com/shadowai/app/di/PromptModule.kt
app/src/main/java/com/shadowai/app/di/ScopeModule.kt
```

### Kotlin Source — Diagnostics

```
app/src/main/java/com/shadowai/app/diagnostics/PerformanceVerifier.kt
```

### Kotlin Source — Exceptions

```
app/src/main/java/com/shadowai/app/exceptions/AppExceptions.kt
app/src/main/java/com/shadowai/app/exceptions/ExceptionMapper.kt
```

### Kotlin Source — Execution Engine

```
app/src/main/java/com/shadowai/app/execution/CircuitBreaker.kt
app/src/main/java/com/shadowai/app/execution/CloudLlmExecutor.kt
app/src/main/java/com/shadowai/app/execution/DeviceAction.kt
app/src/main/java/com/shadowai/app/execution/DeviceActionExecutor.kt
app/src/main/java/com/shadowai/app/execution/ExecutionResult.kt
app/src/main/java/com/shadowai/app/execution/HybridAiExecutor.kt
app/src/main/java/com/shadowai/app/execution/LocalLlmExecutor.kt
app/src/main/java/com/shadowai/app/execution/ProviderQuotaException.kt
app/src/main/java/com/shadowai/app/execution/RateLimiter.kt
app/src/main/java/com/shadowai/app/execution/TaskExecutionService.kt
app/src/main/java/com/shadowai/app/execution/TaskExecutor.kt
```

### Kotlin Source — Feedback

```
app/src/main/java/com/shadowai/app/feedback/FeedbackManager.kt
```

### Kotlin Source — Functions (Image Generation)

```
app/src/main/java/com/shadowai/app/functions/FunctionExecutor.kt
app/src/main/java/com/shadowai/app/functions/ImageGenParams.kt
app/src/main/java/com/shadowai/app/functions/NovitaImageGenerator.kt
app/src/main/java/com/shadowai/app/functions/NovitaModels.kt
app/src/main/java/com/shadowai/app/functions/PixaiImageGenerator.kt
app/src/main/java/com/shadowai/app/functions/PixAiSettings.kt
```

### Kotlin Source — Lifecycle

```
app/src/main/java/com/shadowai/app/lifecycle/BackgroundTaskManager.kt
```

### Kotlin Source — Models

```
app/src/main/java/com/shadowai/app/models/ModelDescriptor.kt
```

### Kotlin Source — Network

```
app/src/main/java/com/shadowai/app/network/JsonValidator.kt
app/src/main/java/com/shadowai/app/network/NetworkMonitor.kt
```

### Kotlin Source — Notifications

```
app/src/main/java/com/shadowai/app/notifications/ShadowNotificationManager.kt
```

### Kotlin Source — Providers

```
app/src/main/java/com/shadowai/app/providers/ActiveProviderConfig.kt
app/src/main/java/com/shadowai/app/providers/ActiveProviderManager.kt
app/src/main/java/com/shadowai/app/providers/LiquidProvider.kt
app/src/main/java/com/shadowai/app/providers/ProviderConfigurationService.kt
app/src/main/java/com/shadowai/app/providers/ProviderCrudRepository.kt
app/src/main/java/com/shadowai/app/providers/ProviderModelCatalog.kt
app/src/main/java/com/shadowai/app/providers/ProviderModelDiscovery.kt
app/src/main/java/com/shadowai/app/providers/ProviderModelRepository.kt
app/src/main/java/com/shadowai/app/providers/ProviderModels.kt
app/src/main/java/com/shadowai/app/providers/ProviderNetworkTester.kt
app/src/main/java/com/shadowai/app/providers/ProviderPlugin.kt
app/src/main/java/com/shadowai/app/providers/ProviderRepository.kt
app/src/main/java/com/shadowai/app/providers/ProviderSecretRepository.kt
app/src/main/java/com/shadowai/app/providers/ProviderSelector.kt
```

### Kotlin Source — Routing

```
app/src/main/java/com/shadowai/app/routing/ExecutionSource.kt
app/src/main/java/com/shadowai/app/routing/RoutingEngine.kt
app/src/main/java/com/shadowai/app/routing/RoutingPolicy.kt
```

### Kotlin Source — Security

```
app/src/main/java/com/shadowai/app/security/BiometricKeyManager.kt
app/src/main/java/com/shadowai/app/security/PiiMaskingProcessor.kt
app/src/main/java/com/shadowai/app/security/PromptInjectionDefense.kt
app/src/main/java/com/shadowai/app/security/SecretBytes.kt
app/src/main/java/com/shadowai/app/security/SecureDataStore.kt
app/src/main/java/com/shadowai/app/security/SecureStoreMigration.kt
app/src/main/java/com/shadowai/app/security/SecureString.kt
app/src/main/java/com/shadowai/app/security/SecurityManager.kt
app/src/main/java/com/shadowai/app/security/TeeKeyManager.kt
```

### Kotlin Source — Tasks

```
app/src/main/java/com/shadowai/app/tasks/Plan.kt
app/src/main/java/com/shadowai/app/tasks/PlanParser.kt
app/src/main/java/com/shadowai/app/tasks/TaskDefinitions.kt
```

### Kotlin Source — Tools

```
app/src/main/java/com/shadowai/app/tools/ToolDescriptor.kt
app/src/main/java/com/shadowai/app/tools/ToolRegistry.kt
```

### Kotlin Source — UI / Auth

```
app/src/main/java/com/shadowai/app/ui/auth/LoginScreen.kt
```

### Kotlin Source — UI / Chat

```
app/src/main/java/com/shadowai/app/ui/ChatMessage.kt
app/src/main/java/com/shadowai/app/ui/ChatViewModel.kt
app/src/main/java/com/shadowai/app/ui/chat/ChatInputField.kt
app/src/main/java/com/shadowai/app/ui/chat/ChatMessageItem.kt
app/src/main/java/com/shadowai/app/ui/chat/ChatScreen.kt
app/src/main/java/com/shadowai/app/ui/chat/FloatingChatTabs.kt
app/src/main/java/com/shadowai/app/ui/chat/MessageContent.kt
app/src/main/java/com/shadowai/app/ui/chat/NavigationDrawerContent.kt
```

### Kotlin Source — UI / Components

```
app/src/main/java/com/shadowai/app/ui/components/EmptyState.kt
app/src/main/java/com/shadowai/app/ui/components/LoadingState.kt
app/src/main/java/com/shadowai/app/ui/components/TaskProgress.kt
app/src/main/java/com/shadowai/app/ui/components/TypingIndicator.kt
```

### Kotlin Source — UI / Dialogs

```
app/src/main/java/com/shadowai/app/ui/dialogs/ProviderSetupWizard.kt
```

### Kotlin Source — UI / Navigation

```
app/src/main/java/com/shadowai/app/ui/navigation/ShadowAINavGraph.kt
```

### Kotlin Source — UI / Providers

```
app/src/main/java/com/shadowai/app/ui/providers/CloudProviderConfig.kt
app/src/main/java/com/shadowai/app/ui/providers/LocalRuntimeConfig.kt
app/src/main/java/com/shadowai/app/ui/providers/PixAiConfig.kt
app/src/main/java/com/shadowai/app/ui/providers/ProviderConfigScreen.kt
app/src/main/java/com/shadowai/app/ui/providers/ProviderConfigViewModel.kt
app/src/main/java/com/shadowai/app/ui/providers/ProviderSelectionScreen.kt
app/src/main/java/com/shadowai/app/ui/providers/ProviderSelectionViewModel.kt
```

### Kotlin Source — UI / Settings

```
app/src/main/java/com/shadowai/app/ui/settings/AppearanceSettingsScreen.kt
app/src/main/java/com/shadowai/app/ui/settings/DiagnosticsScreen.kt
app/src/main/java/com/shadowai/app/ui/settings/DiagnosticsViewModel.kt
app/src/main/java/com/shadowai/app/ui/settings/GenerationSettingsScreen.kt
app/src/main/java/com/shadowai/app/ui/settings/HotSwapScreen.kt
app/src/main/java/com/shadowai/app/ui/settings/HotSwapViewModel.kt
app/src/main/java/com/shadowai/app/ui/settings/SettingsScreen.kt
app/src/main/java/com/shadowai/app/ui/settings/SettingsViewModel.kt
app/src/main/java/com/shadowai/app/ui/settings/UsageCreditsScreen.kt
app/src/main/java/com/shadowai/app/ui/settings/UsageCreditsViewModel.kt
```

### Kotlin Source — UI / Theme

```
app/src/main/java/com/shadowai/app/ui/theme/Color.kt
app/src/main/java/com/shadowai/app/ui/theme/Dimens.kt
app/src/main/java/com/shadowai/app/ui/theme/Theme.kt
app/src/main/java/com/shadowai/app/ui/theme/Type.kt
```

### Kotlin Source — UI / Workflows

```
app/src/main/java/com/shadowai/app/ui/workflows/ImageGenerationScreen.kt
```

### Kotlin Source — Utilities

```
app/src/main/java/com/shadowai/app/util/ErrorDialogHelper.kt
app/src/main/java/com/shadowai/app/util/ErrorHandler.kt
app/src/main/java/com/shadowai/app/util/PermissionHelper.kt
app/src/main/java/com/shadowai/app/util/ProgressDialogHelper.kt
app/src/main/java/com/shadowai/app/util/RetryPolicy.kt
app/src/main/java/com/shadowai/app/util/Validate.kt
app/src/main/java/com/shadowai/app/utils/ErrorFormatter.kt
```

### Kotlin Source — Voice

```
app/src/main/java/com/shadowai/app/voice/HotwordModel.kt
app/src/main/java/com/shadowai/app/voice/VoiceManager.kt
app/src/main/java/com/shadowai/app/voice/WakeWordDetector.kt
```

### Native C++ — JNI Bridge

```
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/llama_jni.cpp
```

### Native C++ — llama.cpp Library (~340 files)

```
app/src/main/cpp/llama/include/
    llama.h
    llama-cpp.h

app/src/main/cpp/llama/src/
    CMakeLists.txt
    llama.cpp                    # Core implementation
    llama-adapter.cpp/.h
    llama-arch.cpp/.h
    llama-batch.cpp/.h
    llama-chat.cpp/.h
    llama-context.cpp/.h
    llama-cparams.cpp/.h
    llama-grammar.cpp/.h
    llama-graph.cpp/.h
    llama-hparams.cpp/.h
    llama-impl.cpp/.h
    llama-io.cpp/.h
    llama-kv-cache.cpp/.h
    llama-kv-cache-iswa.cpp/.h
    llama-kv-cells.h
    llama-memory.cpp/.h
    llama-memory-hybrid.cpp/.h
    llama-memory-recurrent.cpp/.h
    llama-mmap.cpp/.h
    llama-model.cpp/.h
    llama-model-loader.cpp/.h
    llama-model-saver.cpp/.h
    llama-quant.cpp/.h
    llama-sampling.cpp/.h
    llama-vocab.cpp/.h
    unicode.cpp/.h
    unicode-data.cpp/.h
    models/                      # Architecture-specific model files

app/src/main/cpp/llama/ggml/
    CMakeLists.txt
    cmake/                       # CMake build scripts

    include/                     # Public headers
        ggml.h
        ggml-alloc.h
        ggml-backend.h
        ggml-blas.h
        ggml-cann.h
        ggml-cpp.h
        ggml-cpu.h
        ggml-cuda.h
        ggml-hexagon.h
        ggml-metal.h
        ggml-opencl.h
        ggml-opt.h
        ggml-rpc.h
        ggml-sycl.h
        ggml-vulkan.h
        ggml-webgpu.h
        ggml-zdnn.h
        ggml-zendnn.h
        gguf.h

    src/                         # Core tensor operations
        ggml.cpp
        ggml-alloc.cpp
        ggml-backend.cpp
        ggml-opt.cpp
        ggml-threading.cpp
        gguf.cpp
        ggml-cpu/                # CPU backend (ARM NEON, x86 AVX, etc.)
        ggml-cuda/               # CUDA GPU backend
        ggml-metal/              # Apple Metal backend
        ggml-vulkan/             # Vulkan GPU backend
        ggml-opencl/             # OpenCL backend
        ggml-blas/               # BLAS backend
        ggml-rpc/                # RPC backend
        ggml-sycl/               # Intel SYCL backend
        ggml-cann/               # Ascend CANN backend
        ggml-hexagon/            # Qualcomm Hexagon backend
        ggml-hip/                # AMD HIP backend
        ggml-musa/               # Moore Threads MUSA backend
        ggml-webgpu/             # WebGPU backend
        ggml-zdnn/               # IBM zDNN backend
        ggml-zendnn/             # AMD ZenDNN backend
```

---

## core-contracts — Foundation Interfaces

```
core-contracts/build.gradle.kts
core-contracts/src/main/kotlin/com/shadowai/core/Capability.kt
core-contracts/src/main/kotlin/com/shadowai/core/Modality.kt
core-contracts/src/main/kotlin/com/shadowai/core/ModelDescriptor.kt
core-contracts/src/main/kotlin/com/shadowai/core/ProviderExecutor.kt
core-contracts/src/main/kotlin/com/shadowai/core/ProviderId.kt
core-contracts/src/main/kotlin/com/shadowai/core/Transform.kt
```

## model-catalog — Model Discovery & Registry

```
model-catalog/build.gradle.kts
model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelCatalogRepository.kt
model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelDiscovery.kt
model-catalog/src/main/kotlin/com/shadowai/modelcatalog/ModelRegistry.kt
```

## provider-adapters — Provider Implementations

```
provider-adapters/build.gradle.kts
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/AdapterCircuitBreaker.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/AdapterHealthChecker.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/AdapterMetrics.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/FluxAdapter.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/LocalLlamaAdapter.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/OpenAICompatibleAdapter.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderAdapter.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ProviderAdapterFactory.kt
provider-adapters/src/main/kotlin/com/shadowai/provideradapters/ReplicateAdapter.kt
```

## artifact-system — I/O Normalization

```
artifact-system/build.gradle.kts
artifact-system/src/main/kotlin/com/shadowai/artifactsystem/Artifact.kt
artifact-system/src/main/kotlin/com/shadowai/artifactsystem/ArtifactCache.kt
artifact-system/src/main/kotlin/com/shadowai/artifactsystem/ArtifactConverter.kt
artifact-system/src/main/kotlin/com/shadowai/artifactsystem/ArtifactStore.kt
artifact-system/src/main/kotlin/com/shadowai/artifactsystem/ArtifactValidator.kt
```

## pipeline-planner — Graph Transformations

```
pipeline-planner/build.gradle.kts
pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelineBuilder.kt
pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelineExecutor.kt
pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelineGraph.kt
pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelinePlan.kt
pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/PipelinePlanner.kt
pipeline-planner/src/main/kotlin/com/shadowai/pipelineplanner/TransformationRules.kt
```

## ui-params — Parameter Management

```
ui-params/build.gradle.kts
ui-params/src/main/kotlin/com/shadowai/uiparams/ModelParameter.kt
ui-params/src/main/kotlin/com/shadowai/uiparams/ParameterDiffer.kt
ui-params/src/main/kotlin/com/shadowai/uiparams/ParameterHistory.kt
ui-params/src/main/kotlin/com/shadowai/uiparams/ParameterRenderer.kt
ui-params/src/main/kotlin/com/shadowai/uiparams/ParameterSerializer.kt
ui-params/src/main/kotlin/com/shadowai/uiparams/ParameterValidator.kt
ui-params/src/main/kotlin/com/shadowai/uiparams/ParameterViewState.kt
```

## ui-composition — View Composition

```
ui-composition/build.gradle.kts
ui-composition/src/main/kotlin/com/shadowai/uicomposition/AudioTransformationView.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/ChatTransformationView.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/EditTransformationView.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/ImageTransformationView.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/TransformationExecutor.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/TransformationView.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/TransformationViewHost.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/TransformationViewState.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/VideoTransformationView.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/ViewCompositionRules.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/ViewFactory.kt
ui-composition/src/main/kotlin/com/shadowai/uicomposition/ViewParameterSchemas.kt
```

## diagnostics — Error Handling & Reporting

```
diagnostics/build.gradle.kts
diagnostics/src/main/kotlin/com/shadowai/diagnostics/DebugOverlay.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/DiagnosticsLogger.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/ErrorAnalytics.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/ErrorCollector.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/ErrorContext.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/ErrorRecovery.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/ErrorReportScreen.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/ErrorSeverity.kt
diagnostics/src/main/kotlin/com/shadowai/diagnostics/PipelineError.kt
```

## hot-swapping — Runtime Provider Config

```
hot-swapping/build.gradle.kts
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderBackupManager.kt
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderConfigLoader.kt
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderConfigMigrator.kt
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderConfigModels.kt
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderConfigValidator.kt
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderHotSwapManager.kt
hot-swapping/src/main/kotlin/com/shadowai/hotswapping/ProviderRegistry.kt
```

## ui-validator — Lint Detectors

```
ui-validator/build.gradle.kts
ui-validator/src/main/kotlin/com/shadowai/uivalidator/annotations/Material3Compliant.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/AccessibilitySemanticsDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/ErrorPresentationDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/ExecutionContextDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/KeyboardAwarenessDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/Material3ColorRoleDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/Material3ElevationDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/Material3MotionDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/Material3TypographyDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/ModeSeparationDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/MotionRulesDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/ResponsiveLayoutDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/SystemInsetsDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/UiLintReporter.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/UiValidatorIssueRegistry.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/VisualHierarchyDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/lint/WindowSizeClassDetector.kt
ui-validator/src/main/kotlin/com/shadowai/uivalidator/processor/Material3ComplianceProcessor.kt
```

## backend — Ktor JVM Server (Image Generation)

```
backend/build.gradle.kts
backend/src/main/kotlin/com/shadowai/backend/Application.kt
backend/src/main/kotlin/com/shadowai/backend/models.kt
backend/src/main/kotlin/com/shadowai/backend/NovitaService.kt
backend/src/main/kotlin/com/shadowai/backend/PixAiService.kt
backend/src/main/kotlin/com/shadowai/backend/WebhookVerifier.kt
backend/src/main/kotlin/com/shadowai/backend/cache/TaskCache.kt
```

---

## File Counts

| Module | Kotlin | Native (C/C++) | Resources | Build Config | Total |
|--------|--------|----------------|-----------|--------------|-------|
| app | 145 | ~342 | 52 | 3 | ~542 |
| core-contracts | 6 | — | — | 1 | 7 |
| model-catalog | 3 | — | — | 1 | 4 |
| provider-adapters | 9 | — | — | 1 | 10 |
| artifact-system | 5 | — | — | 1 | 6 |
| pipeline-planner | 6 | — | — | 1 | 7 |
| ui-params | 7 | — | — | 1 | 8 |
| ui-composition | 12 | — | — | 1 | 13 |
| diagnostics | 9 | — | — | 1 | 10 |
| hot-swapping | 7 | — | — | 1 | 8 |
| ui-validator | 18 | — | — | 1 | 19 |
| backend | 6 | — | — | 1 | 7 |
| buildSrc | 1 | — | — | 1 | 2 |
| root config | — | — | — | 12 | 12 |
| **Total** | **234** | **~342** | **52** | **27** | **~655** |
