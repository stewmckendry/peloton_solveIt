import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.stewart.pelotonsolveit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.stewart.pelotonsolveit"
        minSdk = 29
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true  // add this
    }

    val localProps = Properties()
    localProps.load(file("../local.properties").inputStream())
    defaultConfig {
        buildConfigField("String", "SOLVEIT_TOKEN", "\"${localProps["SOLVEIT_TOKEN"]}\"")
        buildConfigField("String", "SOLVEIT_URL", "\"${localProps["SOLVEIT_URL"]}\"")
        buildConfigField("String", "SOLVEIT_DIALOG", "\"${localProps["SOLVEIT_DIALOG"]}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProps["OPENAI_API_KEY"]}\"")

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
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.compose.foundation.layout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(files("libs/peloton-sensor.jar"))
    implementation(files("libs/android-vad-silero-v2.0.10-release.aar"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
}