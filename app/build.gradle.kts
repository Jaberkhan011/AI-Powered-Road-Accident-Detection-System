plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.crashguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.crashguard"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        mlModelBinding = true
    }

    // Support for 16 KB page sizes (Android 15+)
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.coordinatorlayout)

    // TFLite dependencies - Updated to 2.16.1 to support newer model ops (FULLY_CONNECTED v12)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")


//        implementation 'org.tensorflow:tensorflow-lite:2.14.0'
//        implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'


    // Play Services for Location
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation(libs.tensorflow.lite.select.tf.ops)
    implementation(libs.play.services.tflite.java)

    // JLibrosa for audio feature extraction
    // Exclude old junit and hamcrest to fix duplicate class errors

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}