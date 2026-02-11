plugins {
    alias(libs.plugins.shadowai.android.library.compose)
    alias(libs.plugins.shadowai.hilt)
}

android {
    namespace = "com.shadowai.diagnostics"
}

dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":pipeline-planner"))
    // Compose and coroutines provided by convention plugin
}
