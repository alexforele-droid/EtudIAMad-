plugins {
    id("com.android.application")
    // Pas de plugin Kotlin – on utilise Java pur
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // AUCUNE dépendance externe
}
