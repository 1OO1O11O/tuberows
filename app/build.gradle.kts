plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes -PversionCode=<run number> so every build installs over the last one,
// and -PversionName=<tag without the v> for releases.
val buildNumber = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val versionLabel = (project.findProperty("versionName") as String?) ?: "dev.$buildNumber"
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "tv.tuberows.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.tuberows.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = versionLabel
    }

    signingConfigs {
        create("release") {
            val ks = keystoreFile
            if (ks != null) {
                storeFile = file(ks)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystoreFile != null) "release" else "debug")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.tvprovider:tvprovider:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
