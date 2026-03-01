plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

import java.util.Properties
import java.io.FileInputStream

android {
    namespace = "com.example.taxconnect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.taxconnect"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val razorpayKey = localProperties.getProperty("RAZORPAY_API_KEY", "rzp_test_placeholder")
        manifestPlaceholders["RAZORPAY_API_KEY"] = razorpayKey
        buildConfigField("String", "RAZORPAY_API_KEY", "\"$razorpayKey\"")

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
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // UI components
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // WorkManager for Offline Queuing
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Shimmer Effect
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Lottie Animations
    implementation("com.airbnb.android:lottie:6.0.0")

    // Biometric Security
    implementation("androidx.biometric:biometric:1.1.0")
    
    // Encrypted Shared Preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:2.5.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    // Razorpay Integration
    implementation("com.razorpay:checkout:1.6.33")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Agora SDK for WiFi Calling
    implementation("io.agora.rtc:full-sdk:4.3.1")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")

    // Testing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
