plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.damianogiusti.stickermaker.imaging"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The TFLite model is memory-mapped from the APK, so it must stay uncompressed.
    androidResources { noCompress += "tflite" }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tensorflow.lite)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
