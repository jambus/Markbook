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
        versionCode = 7
        versionName = "0.4.0"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    testImplementation("junit:junit:4.13.2")
}
