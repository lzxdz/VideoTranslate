plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.videotranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.videotranslate.offline"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-offline-whisper"
    }
}

dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

kotlin { jvmToolchain(17) }
