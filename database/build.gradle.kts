plugins {
    alias(libs.plugins.kotlin.jvm)
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
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.kotlinx.coroutines.core)
    // Innertube models for mapping helpers
    implementation(project(":innertube"))
}
