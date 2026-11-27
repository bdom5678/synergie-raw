plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sophonie.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sophonie.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.2"
    }

    // Clé de signature fournie par la compilation GitHub (jamais stockée dans le dépôt).
    val cle = System.getenv("SOPHONIE_KEYSTORE")
    signingConfigs {
        if (cle != null) {
            create("sophonie") {
                storeFile = file(cle)
                storePassword = System.getenv("SOPHONIE_KEYSTORE_PASS")
                keyAlias = "sophonie"
                keyPassword = System.getenv("SOPHONIE_KEYSTORE_PASS")
            }
        }
    }

    flavorDimensions += "acces"
    productFlavors {
        // Tous les accès, y compris la lecture des SMS et du journal d'appels.
        create("complet") { dimension = "acces" }
        // Sans lecture des SMS ni du journal d'appels : moins souvent bloquée par Play Protect.
        create("essentiel") { dimension = "acces" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (cle != null) signingConfig = signingConfigs.getByName("sophonie")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/*.kotlin_module", "META-INF/versions/**",
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // SDK officiel Claude (Java, utilisable en Kotlin)
    implementation("com.anthropic:anthropic-java:3.3.0")
}
