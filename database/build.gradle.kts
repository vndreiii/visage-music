plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
}

sqldelight {
    databases {
        create("ViviDatabase") {
            packageName.set("com.music.vivi.database")
        }
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna)
    // Innertube models for mapping helpers
    implementation(project(":innertube"))
    implementation("net.jthink:jaudiotagger:3.0.1")
}

tasks.test { useJUnitPlatform() }
