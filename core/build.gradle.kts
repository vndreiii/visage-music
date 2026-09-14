plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":player"))
    api(project(":innertube"))
    api(project(":jiosaavn"))
    api(project(":lyrics"))
    api(project(":database"))
    api(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
