plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreFile = rootProject.file("keystore/dateimanager.jks")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val signingReady = keystoreFile.exists() && !keystorePassword.isNullOrBlank()

android {
    namespace = "de.protech.dateimanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.protech.dateimanager"
        minSdk = 26
        targetSdk = 35
        // versionCode zaehlt mit jeder GitHub-Actions-Laufnummer hoch, damit
        // sich ein neues Build ueber eine bestehende Installation legen laesst.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0"
    }

    if (signingReady) {
        signingConfigs.create("shared") {
            storeFile = keystoreFile
            storePassword = keystorePassword
            keyAlias = System.getenv("KEY_ALIAS") ?: "dateimanager"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        debug {
            if (signingReady) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            if (signingReady) signingConfig = signingConfigs.getByName("shared")
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
