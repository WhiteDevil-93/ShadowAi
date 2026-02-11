plugins {
    alias(libs.plugins.shadowai.android.library.compose)
}

android {
    namespace = "com.shadowai.uicomposition"
}

dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":ui-params"))
    implementation(project(":pipeline-planner"))
    implementation(project(":artifact-system"))

    implementation(libs.coil.compose)
    // Compose and coroutines provided by convention plugin
}
