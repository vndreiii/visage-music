pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
        maven("https://jcenter.bintray.com/")
    }
}
rootProject.name = "visage"
include(":innertube")
include(":lyrics")
include(":jiosaavn")
include(":lastfm")
include(":spotify")
include(":shazamkit")
include(":kizzy")
include(":canvas")
include(":applecanvas")
include(":vivimusiccanvas")
include(":artistvideo")
include(":player")
include(":player-cli")
include(":nativelib")
include(":core")
include(":database")
include(":composeApp")
