// Keep the Guest build independent of Firebase SDKs until a real project is configured.
buildscript {
    if (providers.gradleProperty("littlefarm.firebase").orNull == "true") {
        check(file("google-services.json").isFile) {
            "Firebase is enabled but androidApp/google-services.json is missing. See androidApp/FIREBASE-SETUP.md."
        }
        repositories { google(); mavenCentral() }
        dependencies { classpath("com.google.gms:google-services:4.5.0") }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val firebaseEnabled = providers.gradleProperty("littlefarm.firebase").orNull == "true"
val appleEnabled = firebaseEnabled && providers.gradleProperty("littlefarm.appleSignIn").orNull == "true"
if (firebaseEnabled) {
    check(file("google-services.json").isFile) {
        "Firebase is enabled but androidApp/google-services.json is missing. See androidApp/FIREBASE-SETUP.md."
    }
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.littlefarm.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.littlefarm.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("boolean", "APPLE_SIGN_IN_ENABLED", appleEnabled.toString())
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].java.srcDir(if (firebaseEnabled) "src/firebase/kotlin" else "src/offline/kotlin")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    if (firebaseEnabled) {
        implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-firestore")
        implementation("androidx.credentials:credentials:1.6.0")
        implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
        implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    }
}
