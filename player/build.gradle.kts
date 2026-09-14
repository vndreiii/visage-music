plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":innertube"))
    api(project(":jiosaavn"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.java.keyring)
    implementation(libs.jna)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.native.unixsocket)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}

tasks.test {
    useJUnitPlatform()
}
