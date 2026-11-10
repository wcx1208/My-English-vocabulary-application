plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wcx.mywords"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wcx.mywords"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // 音频以未压缩形式打入 APK，才能用 AssetManager.openFd 直接播放
    androidResources { noCompress += listOf("mp3") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
}
