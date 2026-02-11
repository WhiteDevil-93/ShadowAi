pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-plugin")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ShadowAi-Android-16"
include(":app")
include(":backend")
include(":core-contracts")
include(":model-catalog")
include(":provider-adapters")
include(":artifact-system")
include(":pipeline-planner")
include(":ui-params")
include(":ui-composition")
include(":diagnostics")
include(":hot-swapping")
include(":ui-validator")
include(":inference_process")
