plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ir.abr.keyboard"
    compileSdk = 35
    defaultConfig {
        // A fresh application ID avoids signature/package conflicts with earlier debug builds.
        applicationId = "ir.abr.keyboard.v4"
        minSdk = 23
        targetSdk = 32
        versionCode = 4
        versionName = "4.0"
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
