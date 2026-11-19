plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ir.abr.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.abr.keyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
}
