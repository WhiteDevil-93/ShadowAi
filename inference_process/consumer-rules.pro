# consumer-rules.pro - ProGuard rules for inference_process module

# Keep the service class
-keep public class com.shadowai.inference.InferenceService {
    public <methods>;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Don't warn about missing native library
-dontwarn com.shadowai.inference.**
