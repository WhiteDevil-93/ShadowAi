plugins {
    alias(libs.plugins.shadowai.android.library)
    alias(libs.plugins.shadowai.hilt)
}

android {
    namespace = "com.shadowai.pipelineplanner"
}

dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":provider-adapters"))
    implementation(project(":artifact-system"))

    // Hilt provided by convention plugin
    // Coroutines provided by convention plugin
    implementation(kotlin("reflect"))

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
