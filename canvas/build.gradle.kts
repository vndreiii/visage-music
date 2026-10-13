plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.encoding)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(project(":nativelib"))
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnit()
}
