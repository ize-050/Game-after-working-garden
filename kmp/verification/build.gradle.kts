plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Compile the shared UI and test the real game sources without requiring either mobile SDK.
// Headless UI rendering below also consumes the real shared UI and resources.
kotlin {
    jvm()
    sourceSets {
        commonMain {
            kotlin.srcDir("../composeApp/src/commonMain/kotlin")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir("../composeApp/src/commonTest/kotlin")
            dependencies { implementation(kotlin("test")) }
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "com.littlefarm.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = provider { layout.projectDirectory.dir("../composeApp/src/commonMain/composeResources") }
    )
}

tasks.register<JavaExec>("renderScreens") {
    group = "verification"
    description = "Render actual shared Compose screens without an iOS Simulator."
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider, "jvmProcessResources")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.littlefarm.preview.RenderScreensKt")
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "512m"
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}

tasks.register<JavaExec>("smokeUi") {
    group = "verification"
    description = "Exercise actual Compose semantics and assert the resulting game saves."
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider, "jvmProcessResources")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.littlefarm.preview.SmokeUiKt")
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("playDesktop") {
    group = "verification"
    description = "Play the shared game in an isolated Mac QA window; not an iOS app."
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider, "jvmProcessResources")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.littlefarm.preview.PlayDesktopKt")
    systemProperty("java.awt.headless", "false")
    maxHeapSize = "512m"
    args(layout.projectDirectory.dir("../.tooling/desktop-preview").asFile.absolutePath)
}
