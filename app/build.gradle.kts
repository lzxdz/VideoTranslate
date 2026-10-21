plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.videotranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.videotranslate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
}

kotlin { jvmToolchain(17) }
