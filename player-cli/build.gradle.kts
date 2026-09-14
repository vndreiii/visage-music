plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":player"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("com.music.vivi.player.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    environment("LC_NUMERIC", "C")
}
