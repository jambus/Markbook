plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.markbook.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.markbook.android"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
