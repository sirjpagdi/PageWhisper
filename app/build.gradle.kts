plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pagewhisper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pagewhisper.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
        // Most phones are arm64. Remove this line to also build for x86 emulators (a bigger APK).
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES", "/META-INF/LICENSE*", "/META-INF/NOTICE*") } }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // Speech engine: download the sherpa-onnx Android AAR from
    // https://github.com/k2-fsa/sherpa-onnx/releases and put it in app/libs/ (see app/libs/README.txt).
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.commons.compress)
    // Reads the text of PDF books (Apache-2.0). EPUB needs nothing extra: it is a zip of web pages, read with jsoup.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Runs the overnight preparation of speech while the phone charges.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(libs.junit)
}
