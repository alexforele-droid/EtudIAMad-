plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // ✅ Le namespace est ICI (hors defaultConfig)
    namespace = "com.etudamada.etudiamad"
    compileSdk = 34

    defaultConfig {
        // ✅ Le applicationId est ICI (à l'intérieur)
        applicationId = "com.etudamada.etudiamad"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 👇 OBLIGATOIRE pour ton ZTE 32 bits
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}
