plugins {
    alias(libs.plugins.shadowai.android.library)
    id("kotlin-parcelize")
}

android {
    namespace = "com.shadowai.core"
}

dependencies {
    // Required for @Inject/@Singleton annotations used by shared security components.
    implementation("javax.inject:javax.inject:1")
}

// Standard coroutines are provided by the convention plugin
