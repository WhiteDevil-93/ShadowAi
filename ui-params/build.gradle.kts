plugins {
    alias(libs.plugins.shadowai.android.library.compose)
}

android {
    namespace = "com.shadowai.uiparams"
}

dependencies {
    implementation(project(":core-contracts"))
    // Compose and coroutines provided by convention plugin
}
