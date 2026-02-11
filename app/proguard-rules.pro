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
-keepclassmembers class com.shadowai.app.ai.LlamaNative {
    native nativeLoadModel(java.lang.String,int,int);
    native nativeFreeModel(long);
    native nativeGetString(long);
    native nativeFreeString(long);
    native nativeGenerate(long,java.lang.String,int,int,int,float);
    native nativeGenerateStream(long,java.lang.String,int,int,int,float,com.shadowai.app.ai.LlamaNative$GenerationCallback);
    native nativeCancel(long);
    native nativeGetSystemInfo();
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

# Firebase & Play Services
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
