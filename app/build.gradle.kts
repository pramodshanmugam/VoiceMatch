plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {

    namespace = "com.example.voicematchapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voicematchapp"
        minSdk = 21
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 24
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation(libs.tensorflow.lite.support.api)
    implementation(libs.tensorflow.lite.support.api)
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation("androidx.compose.runtime:runtime-livedata:1.4.0")
    // https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite
    runtimeOnly("org.tensorflow:tensorflow-lite:2.16.1")
    implementation ("org.tensorflow:tensorflow-lite:2.10.0")
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.0")
    // https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite-support
    runtimeOnly("org.tensorflow:tensorflow-lite-support:0.4.3")

    // https://mvnrepository.com/artifact/be.tarsos.dsp/core
    implementation ("com.google.android.material:material:1.9.0")

    implementation("be.tarsos.dsp:core:2.5")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.2")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.2")
    implementation("com.squareup.okhttp3:okhttp:4.9.2")  // For HTTP requests
    implementation("androidx.compose.material3:material3:1.0.0-alpha01") // For Material 3 UI
    implementation("androidx.compose.ui:ui-tooling-preview:1.0.1")
    implementation("androidx.compose.ui:ui-tooling:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
