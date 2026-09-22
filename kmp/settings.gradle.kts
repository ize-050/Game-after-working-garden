pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "LittleFarm"
include(":composeApp")
// An iOS developer should not need an Android SDK to build the iOS application.
if (providers.gradleProperty("littlefarm.iosOnly").orNull == "true") {
    project(":composeApp").buildFileName = "build-ios.gradle.kts"
} else {
    include(":androidApp")
}
