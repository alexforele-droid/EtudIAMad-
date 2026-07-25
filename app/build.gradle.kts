plugins {
    id("com.android.application")
    // Pas de plugin Kotlin
}

android {
    namespace = "com.etudamada.etudiamad"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.etudamada.etudiamad"
        minSdk = 26
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // On utilise Java 8 pour éviter les problèmes avec les anciens téléphones
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // AUCUNE dépendance externe
}
