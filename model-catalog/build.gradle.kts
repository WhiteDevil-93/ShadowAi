plugins {
    alias(libs.plugins.shadowai.android.library)
    alias(libs.plugins.shadowai.hilt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.shadowai.modelcatalog"
}

dependencies {
    implementation(project(":core-contracts"))

    implementation(libs.gson)
    implementation(kotlin("reflect"))
    // Coroutines provided by convention plugin
}
