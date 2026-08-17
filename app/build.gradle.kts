plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.arora.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arora.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // ML Kit for offline high-speed OCR & On-Device Neural Translation
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.translate)

    // Shizuku (Wireless ADB) to bypass FLAG_SECURE
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Networking for Gemini Multimodal API
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Preferences & Storage
    implementation(libs.androidx.datastore.preferences)

    // Media3 ExoPlayer (Hardware Accelerated Streaming & Background Audio)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Async Image Loader for Jetpack Compose
    implementation(libs.coil.compose)
}
