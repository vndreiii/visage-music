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
include(":core")
include(":database")
include(":composeApp")
include(":desktop")
