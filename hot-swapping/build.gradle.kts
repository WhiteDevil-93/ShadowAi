plugins {
    alias(libs.plugins.shadowai.android.library)
    alias(libs.plugins.shadowai.hilt)
}

android {
    namespace = "com.shadowai.hotswapping"
}

dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":provider-adapters"))

    implementation(libs.gson)
    implementation(libs.snakeyaml)
    // Hilt and Coroutines provided by convention plugin
}
