plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.arrow.core)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.test { useJUnit() }
