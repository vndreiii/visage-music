plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    api(project(":innertube"))
    api(project(":jiosaavn"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.java.keyring)
    implementation(project(":nativelib"))
    implementation(libs.jna)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.native.unixsocket)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runMprisTest") {
    mainClass.set("com.music.vivi.player.mpris.TestMprisKt")
    classpath = sourceSets["main"].runtimeClasspath
}
