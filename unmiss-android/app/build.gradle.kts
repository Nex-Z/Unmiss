plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.unmiss.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/unmiss-release.jks")
            storePassword = System.getenv("UNMISS_KEYSTORE_PASSWORD")
                ?: error("UNMISS_KEYSTORE_PASSWORD is required")
            keyAlias = "unmiss"
            keyPassword = System.getenv("UNMISS_KEY_PASSWORD")
                ?: error("UNMISS_KEY_PASSWORD is required")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.unmiss.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("compat") {
            initWith(getByName("release"))
            applicationIdSuffix = ".compat"
            versionNameSuffix = "-compat"
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    // AndroidLiquidGlass / Backdrop. 1.0.6 remains compatible with compileSdk 36.
    implementation(files("libs/backdrop-1.0.6.aar"))
    implementation(files("libs/shapes-android-1.2.0.aar"))
}
