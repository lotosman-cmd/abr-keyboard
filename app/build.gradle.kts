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
        versionCode = 1
        versionName = "1.0"
    }
}
