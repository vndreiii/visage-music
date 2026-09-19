plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")
    sourceSets {
        val desktopTest by getting {
            dependencies {
    implementation("club.minnced:java-discord-rpc:2.0.2")
                implementation(kotlin("test"))
                implementation(libs.junit)
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            }
        }
        val desktopMain by getting {
            dependencies {
    implementation("club.minnced:java-discord-rpc:2.0.2")
                implementation(project(":core"))
                implementation(project(":lyrics"))
                implementation(project(":canvas"))
                implementation(project(":applecanvas"))
                implementation(project(":vivimusiccanvas"))
                implementation(compose.desktop.currentOs)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.runtime)
                implementation(compose.ui)
                // M3 Expressive morphing loader (official LoadingIndicator is internal on CMP desktop)
                implementation("androidx.graphics:graphics-shapes-desktop:1.1.0")
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.navigation.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.okhttp)
                implementation(libs.material.kolor)
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport.native.unixsocket)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.music.vivi.alex"
        nativeDistributions {
            packageName = "com.music.vivi.alex"
            description = "VIVI Desktop"
            copyright = ""
            vendor = "vivi"
            linux {
                packageName = "vivi-desktop"
                appCategory = "AudioVideo"
            }
        }
        jvmArgs += listOf(
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
            "-Dawt.appClassName=com.music.vivi.alex",
        )
    }
}

afterEvaluate {
    tasks.findByName("run")?.let { task ->
        (task as? JavaExec)?.apply {
            environment("LC_NUMERIC", "C")
            jvmArgs(
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
                "-Dawt.appClassName=com.music.vivi.alex",
            )
        }
    }
}
