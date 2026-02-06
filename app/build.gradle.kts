plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android)
    // alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

import java.util.Properties
import java.io.FileInputStream

android {
    namespace = "com.example.taxconnect"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.taxconnect"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Optimize APK size: Only include English resources
        resourceConfigurations.add("en")

        // Optimize APK size: Only include ARM architectures (removes x86/x86_64 native libs)
        ndk {
            // abiFilters.add("armeabi-v7a") // Removing 32-bit support to reduce APK size
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val razorpayKey = localProperties.getProperty("RAZORPAY_API_KEY", "rzp_test_placeholder")
        manifestPlaceholders["RAZORPAY_API_KEY"] = razorpayKey

        val agoraAppId = localProperties.getProperty("AGORA_APP_ID", "44c3f37e8edd4d6297085630ebdb8c75")
        buildConfigField("String", "AGORA_APP_ID", "\"$agoraAppId\"")

        val cloudinaryCloudName = localProperties.getProperty("CLOUDINARY_CLOUD_NAME", "dr2oc1q4y")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")

        val cloudinaryPreset = localProperties.getProperty("CLOUDINARY_UPLOAD_PRESET", "ml_default")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryPreset\"")
    }

    signingConfigs {
        create("release") {
            val keystoreProperties = Properties()
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = if (keystoreProperties.getProperty("storeFile") != null) file(keystoreProperties.getProperty("storeFile")) else null
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    // kotlinOptions {
    //     jvmTarget = "17"
    // }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable += setOf("PropertyEscape")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // UI components
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Shimmer effect
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    
    // Lottie Animations
    implementation("com.airbnb.android:lottie:6.1.0")

    // Biometric Security
    implementation("androidx.biometric:biometric:1.1.0")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:2.5.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-perf")
    
    // Razorpay Integration
    implementation("com.razorpay:checkout:1.6.33") // Real Payment Gateway

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Agora SDK for WiFi Calling
    implementation("io.agora.rtc:full-sdk:4.3.1")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
