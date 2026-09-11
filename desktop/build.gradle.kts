plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.javafx)
    application
}

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.6"
    modules("javafx.controls")
}

dependencies {
    implementation(project(":innertube"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.javafx)
    implementation(libs.java.keyring)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)
}

application {
    mainClass.set("com.music.vivi.desktop.MainKt")
}

tasks.named<JavaExec>("run") {
    // Prefer a real display; fail clearly if headless.
    environment("DISPLAY", System.getenv("DISPLAY") ?: ":0")
}
