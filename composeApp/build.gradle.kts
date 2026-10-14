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
                implementation(kotlin("test"))
                implementation(libs.junit)
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation("com.github.jagrosh:DiscordIPC:master-SNAPSHOT")
                implementation("me.friwi:jcefmaven:152.0.6")
                val cefArch = when (System.getProperty("os.arch")) {
                    "amd64", "x86_64" -> "amd64"
                    "aarch64" -> "arm64"
                    else -> error("Unsupported embedded browser architecture")
                }
                runtimeOnly("me.friwi:jcef-natives-linux-$cefArch:jcef-17e805a+cef-152.0.6+g708dc14+chromium-152.0.7977.83")
                implementation(project(":core"))
                implementation(project(":nativelib"))
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
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            // Keyring/D-Bus and Discord load JDK classes dynamically; jlink's
            // inferred module set misses jdk.security.auth and java.management.
            includeAllModules = true
            packageName = "visage"
            description = "VIsage"
            copyright = ""
            vendor = "visage"
            linux {
                packageName = "visage"
                appCategory = "AudioVideo"
            }
        }
        jvmArgs += listOf(
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
            "-Dawt.appClassName=visage",
        )
    }
}

afterEvaluate {
    tasks.findByName("run")?.let { task ->
        (task as? JavaExec)?.apply {
            environment("LC_NUMERIC", "C")
            environment("DISABLE_LSFGVK", "1")
            jvmArgs(
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
                "-Dawt.appClassName=visage",
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    environment("DISABLE_LSFGVK", "1")
    jvmArgs("--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
}
