plugins {
    alias(libs.plugins.shadowai.android.library)
    alias(libs.plugins.shadowai.hilt)
}

android {
    namespace = "com.shadowai.provideradapters"
}

dependencies {
    implementation(project(":core-contracts"))
    implementation(project(":model-catalog"))

    // Security
    implementation(libs.androidx.security.crypto)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Hilt provided by convention plugin
    // Coroutines provided by convention plugin

    // Test dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
