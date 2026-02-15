# Keep security-crypto classes (specific classes needed for encryption)
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }
-keep class androidx.security.crypto.MasterKey$Builder { *; }

# Keep Retrofit interfaces and OkHttp (required for reflection)
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-keep class retrofit2.Response { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes Exceptions

# Keep Room entities & DAOs (required for database operations)
-keep class com.shadowai.app.db.*Entity { *; }
-keep class com.shadowai.app.db.*Dao { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# ============================================================================
# JNI/Native Code Rules - Minimal required surface for llama.cpp integration
# ============================================================================

# Keep only the specific native methods that are actually called via JNI.
# Removed broad "keep class com.shadowai.app.ai.LlamaNative { *; }" rule.
# This minimizes attack surface in release builds.

# Keep only the native methods required for JNI (critical entry points)
# Use wildcard return types (***) as required by ProGuard/R8 method rule syntax
-keepclassmembers class com.shadowai.app.ai.LlamaNative {
    native <methods>;
}

# Keep minimal inner classes required for JNI callbacks and resource management
-keep class com.shadowai.app.ai.LlamaNative$GenerationCallback { *; }
-keep class com.shadowai.app.ai.LlamaNative$ModelHandle {
    long nativeHandle;
}

# Keep Gson serialization for data classes
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Hilt generated classes (minimal set required)
-keep class dagger.hilt.android.internal.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep the custom Application class and Hilt components
-keep class com.shadowai.app.ShadowApplication { *; }
-keep class com.shadowai.app.ShadowApplication_HiltComponents { *; }

# Keep all data classes (for serialization)
-keep class com.shadowai.app.models.** { *; }
# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Remove DEBUG and VERBOSE logging in release builds
# IMPORTANT: Preserve info/warn/error for crash diagnostics
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# ============================================================================
# Serialization Rules - H-12: Add ProGuard rules for serialization
# ============================================================================

# Keep Kotlin serialization classes
-keep class kotlinx.serialization.** { *; }
-keep class * implements kotlinx.serialization.Serializable { *; }
-keepclassmembers class * implements kotlinx.serialization.Serializable {
    *;
}
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# Keep annotation for kotlinx.serialization
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *;
}

# Keep Kotlin data classes for serialization
-keep class com.shadowai.app.models.** { *; }
-keep class com.shadowai.app.db.*Entity { <init>(...); }
-keepclassmembers class com.shadowai.app.db.*Entity {
    <init>(...);
    *;
}

# Keep sealed classes and their subclasses
-keep class com.shadowai.app.execution.ExecutionResult { *; }
-keep class com.shadowai.app.execution.ExecutionResult$* { *; }
-keep class com.shadowai.app.tasks.TaskState { *; }
-keep class com.shadowai.app.tasks.TaskState$* { *; }
-keep class com.shadowai.app.providers.FallbackProvider { *; }
-keep class com.shadowai.app.providers.FallbackResult { *; }
-keep class com.shadowai.app.providers.FallbackResult$* { *; }
-keep class com.shadowai.app.export.ConversationExporter$ExportResult { *; }
-keep class com.shadowai.app.ai.ModelDownloader$DownloadResult { *; }
-keep class com.shadowai.app.ai.ModelDownloader$DownloadResult$* { *; }
-keep class com.shadowai.app.ai.ModelDownloader$ModelVerificationResult { *; }
-keep class com.shadowai.app.ai.ModelDownloader$ModelVerificationResult$* { *; }

# Keep error/exception classes for serialization
-keep class com.shadowai.app.execution.ProviderQuotaException { *; }
-keep class com.shadowai.app.execution.ProviderAuthException { *; }
-keep class com.shadowai.app.agent.AgentErrors { *; }
-keep class com.shadowai.app.agent.AgentErrors$* { *; }

# Keep UI state classes that might be saved
-keep class com.shadowai.app.ui.history.ChatHistoryUiState { *; }
-keep class com.shadowai.app.ui.history.ChatHistoryUiState$* { *; }
-keep class com.shadowai.app.ui.history.ExportStatus { *; }
-keep class com.shadowai.app.ui.history.ExportStatus$* { *; }
-keep class com.shadowai.app.ui.history.ConversationSummary { *; }

# ============================================================================
# H-12: QuantizationHelper Rules
# ============================================================================

# Keep QuantizationHelper and its enum
-keep class com.shadowai.app.ai.QuantizationHelper { *; }
-keepclassmembers class com.shadowai.app.ai.QuantizationHelper {
    *;
}

# Keep QuantizationType enum (used for serialization and reflection)
-keepclassmembers enum com.shadowai.app.ai.QuantizationHelper$QuantizationType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *;
}
-keep class com.shadowai.app.ai.QuantizationHelper$QuantizationType { *; }

# Keep ModelInfo data class (used for JSON serialization)
-keep class com.shadowai.app.ai.QuantizationHelper$ModelInfo { *; }
-keepclassmembers class com.shadowai.app.ai.QuantizationHelper$ModelInfo {
    <init>(...);
    *;
}

# ============================================================================
# H-12: ConversationSummarizer Rules
# ============================================================================

# Keep ConversationSummarizer class (DI-injected Singleton)
-keep class com.shadowai.app.ai.ConversationSummarizer { *; }
-keepclassmembers class com.shadowai.app.ai.ConversationSummarizer {
    <init>(...);
    *;
}

# Keep SummaryMetadata (@Serializable data class)
-keep class com.shadowai.app.ai.ConversationSummarizer$SummaryMetadata { *; }
-keepclassmembers class com.shadowai.app.ai.ConversationSummarizer$SummaryMetadata {
    <init>(...);
    *;
}

# Keep StoredSummary data class
-keep class com.shadowai.app.ai.ConversationSummarizer$StoredSummary { *; }
-keepclassmembers class com.shadowai.app.ai.ConversationSummarizer$StoredSummary {
    <init>(...);
    *;
}

# Keep SummarizationResult data class
-keep class com.shadowai.app.ai.ConversationSummarizer$SummarizationResult { *; }
-keepclassmembers class com.shadowai.app.ai.ConversationSummarizer$SummarizationResult {
    <init>(...);
    *;
}

# Keep Config data class
-keep class com.shadowai.app.ai.ConversationSummarizer$Config { *; }
-keepclassmembers class com.shadowai.app.ai.ConversationSummarizer$Config {
    <init>(...);
    *;
}

# ============================================================================
# H-12: ProviderAdapter Rules
# ============================================================================

# Keep ProviderAdapter interface and its implementations
-keep interface com.shadowai.provideradapters.ProviderAdapter { *; }
-keep interface com.shadowai.provideradapters.ProviderAdapter$* { *; }

# Keep ProviderAdapterConfig data class (used for serialization)
-keep class com.shadowai.provideradapters.ProviderAdapterConfig { *; }
-keepclassmembers class com.shadowai.provideradapters.ProviderAdapterConfig {
    <init>(...);
    *;
}

# Keep InferenceResult sealed class and its implementations
-keep class com.shadowai.provideradapters.InferenceResult { *; }
-keep class com.shadowai.provideradapters.InferenceResult$* { *; }
-keepclassmembers class com.shadowai.provideradapters.InferenceResult$* {
    <init>(...);
    *;
}

# Keep all provider adapter implementations (for DI)
-keep class com.shadowai.provideradapters.*Adapter { *; }
-keep class com.shadowai.provideradapters.*Adapter$* { *; }
-keepclassmembers class com.shadowai.provideradapters.*Adapter {
    <init>(...);
    *;
}

# ============================================================================
# H-12: DI-Injected Classes Rules
# ============================================================================

# Prevent obfuscation of all Hilt-injected classes
-keep @javax.inject.Singleton class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.qualifiers.ApplicationContext class * { *; }

# Keep all @Inject constructors
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject *;
}

# Keep all @Singleton and @Inject annotated classes
-keep @javax.inject.Singleton class * {
    <init>(...);
    *;
}

# Keep Hilt modules
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclassmembers @dagger.Module class * {
    @dagger.Provides *;
    @dagger.Binds *;
}

# Keep core-contracts classes (referenced by ProviderAdapter)
-keep class com.shadowai.core.** { *; }
-keepclassmembers class com.shadowai.core.** {
    <init>(...);
    *;
}

# Keep Artifact sealed class and subclasses (core serialization)
-keep class com.shadowai.core.Artifact { *; }
-keep class com.shadowai.core.Artifact$* { *; }
-keepclassmembers class com.shadowai.core.Artifact$* {
    <init>(...);
    *;
}

# ============================================================================
# GSON Serialization Rules
# ============================================================================

# Keep GSON specific classes
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }

# Keep classes with @SerializedName annotation
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep GSON type adapter factories
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# ============================================================================
# ViewModel State
# ============================================================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ============================================================================
# Firebase & Play Services
# ============================================================================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================================
# FIX: Missing ProGuard Rules (Hilt Workers, Credential Manager, SQLCipher, Tink)
# ============================================================================

# WorkManager Workers (Hilt-injected)
-keep class com.shadowai.app.download.ModelDownloadWorker { *; }
-keepclassmembers class com.shadowai.app.download.ModelDownloadWorker {
    @dagger.assisted.AssistedInject <init>(...);
}

# Credential Manager & Google Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Room query POJOs (used by DAO return types, not annotated with @Entity)
-keep class com.shadowai.app.db.RatingCount { *; }

# Tink (Google Crypto)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
