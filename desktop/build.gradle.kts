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
    modules("javafx.controls", "javafx.web")
}

dependencies {
    implementation(project(":innertube"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.javafx)
    implementation(libs.java.keyring)
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("com.music.vivi.desktop.MainKt")
}

tasks.named<JavaExec>("run") {
    // Prefer a real display; fail clearly if headless.
    environment("DISPLAY", System.getenv("DISPLAY") ?: ":0")
}
