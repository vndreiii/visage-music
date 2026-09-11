pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}
rootProject.name = "vivi-desktop"
include(":innertube")
include(":lyrics")
include(":desktop")
