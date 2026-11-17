plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.dudeapp.vault"
    compileSdk = 35

    /**
     * The key every P Vault release is signed with, so updates install over the old app.
     *
     * It is read from a file named by PV_KEYSTORE, falling back to the usual debug
     * keystore on this PC. Naming the file directly matters on a build server: the
     * Android plugin's own "debug" config quietly makes up a brand-new key there when it
     * does not like what it finds, which produced a different fingerprint on every run.
     */
    signingConfigs {
        create("releaseKey") {
            val keyFile = file(
                System.getenv("PV_KEYSTORE")
                    ?: "${System.getProperty("user.home")}/.android/debug.keystore",
            )
            // When there is no keystore (a plain test build), leave this config empty and
            // fall back below; failing the build here would break every test run.
            if (keyFile.exists()) {
                storeFile = keyFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.dudeapp.vault"
        minSdk = 26
        targetSdk = 35
        versionCode = 64
        versionName = "0.5.34"
    }

    buildTypes {
        release {
            // Releases are optimised (non-debuggable) builds, which run Compose far more
            // smoothly than debug builds. They are signed with the key named above.
            signingConfig = if (signingConfigs.getByName("releaseKey").storeFile != null) {
                signingConfigs.getByName("releaseKey")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Push notifications only. Analytics is not included.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    // Google sign-in for the admin panel.
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Document scanner ("Scan a document"); the scanner itself lives in Google Play services.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
