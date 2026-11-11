plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.damianogiusti.stickermaker.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

sqldelight {
    databases {
        create("StickerDatabase") {
            packageName.set("com.damianogiusti.stickermaker.data.db")
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.koin.android)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.sqldelight.sqlite.driver)
}
