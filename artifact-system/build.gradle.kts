plugins {
    alias(libs.plugins.shadowai.android.library)
    alias(libs.plugins.shadowai.hilt)
}

android {
    namespace = "com.shadowai.artifactsystem"
}

dependencies {
    implementation(project(":core-contracts"))

    // Hilt provided by convention plugin
    // Coroutines provided by convention plugin
}
