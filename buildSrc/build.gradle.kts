plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("moduleBoundaries") {
            id = "shadowai.module-boundaries"
            implementationClass = "com.shadowai.buildlogic.ModuleBoundariesPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
