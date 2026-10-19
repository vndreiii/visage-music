import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

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
                val cefOs = if (isWindowsHost) "windows" else "linux"
                runtimeOnly("me.friwi:jcef-natives-$cefOs-$cefArch:jcef-17e805a+cef-152.0.6+g708dc14+chromium-152.0.7977.83")
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
                // Direct input into NavHost's seekable predictive back (touchpad back swipe).
                implementation("androidx.navigationevent:navigationevent:1.0.2")
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
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

val visageVersion: String = (System.getenv("VISAGE_VERSION") ?: "1.0.0").removePrefix("v")

// Stage the optional native helper where Compose copies "linux"/"windows" resources from.
// On Windows, `scripts/package-windows.ps1` also puts libmpv-2.dll and yt-dlp.exe in build/windows-deps.
val nativeResources = layout.buildDirectory.dir("native-resources")
val stageNativeLib by tasks.registering(Copy::class) {
    if (isWindowsHost) {
        val lib = rootProject.file("native/target/release/visage_native.dll")
        val deps = layout.buildDirectory.dir("windows-deps")
        if (lib.isFile) from(lib)
        from(deps)
        into(nativeResources.map { it.dir("windows") })
    } else {
        val lib = rootProject.file("native/target/release/libvisage_native.so")
        onlyIf { lib.isFile }
        from(lib)
        into(nativeResources.map { it.dir("linux") })
    }
}

// Linux-only JVM flags: the X11 toolkit package does not exist in the Windows JDK.
val x11Opens = if (isWindowsHost) emptyList() else listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")

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
            if (isWindowsHost) targetFormats(TargetFormat.Msi, TargetFormat.Exe) else targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "visage"
            packageVersion = visageVersion
            description = "YouTube Music player for desktop"
            copyright = "© VIsage contributors"
            vendor = "VIsage"
            // Rust helper (libvisage_native.so) lands in the app's resources dir when it has been built
            // (CI does; locally `cargo build --release --manifest-path native/Cargo.toml` first).
            appResourcesRootDir.set(nativeResources)
            linux {
                packageName = "visage"
                appCategory = "AudioVideo"
                menuGroup = "Audio & Video"
                iconFile.set(rootProject.file("packaging/icons/visage-512.png"))
                debMaintainer = "Alex <alex@milfs.party>"
                debPackageVersion = visageVersion
                rpmLicenseType = "GPL-3.0-only"
                rpmPackageVersion = visageVersion
                shortcut = true
            }
            windows {
                // Only set on Windows: on Linux it would rename the app image folder.
                if (isWindowsHost) packageName = "VIsage"
                menuGroup = "VIsage"
                iconFile.set(rootProject.file("packaging/icons/visage.ico"))
                // Per-user install: no administrator prompt, and the updater can run the installer silently.
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Fixed id so each new installer upgrades the old one instead of installing beside it.
                upgradeUuid = "6f1c7a52-3b8e-4f0d-9a47-5d2b8c1e7a90"
            }
        }
        jvmArgs += listOf(
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "-Dawt.appClassName=visage",
            "-Dvisage.version=$visageVersion",
        ) + x11Opens
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
                "-Dawt.appClassName=visage",
            )
            jvmArgs(x11Opens)
        }
    }
}

tasks.withType<Test>().configureEach {
    environment("DISABLE_LSFGVK", "1")
    jvmArgs("--add-opens=java.desktop/sun.awt=ALL-UNNAMED")
    jvmArgs(x11Opens)
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(stageNativeLib) }
tasks.withType<AbstractJPackageTask>().configureEach {
    dependsOn(stageNativeLib)
    // Custom launcher template (adds StartupWMClass so docks match the window to its icon).
    freeArgs.addAll("--resource-dir", rootProject.file("packaging/jpackage").absolutePath)
}

// jpackage's stock launcher segfaults at startup on current glibc (2.44); replace it in the
// app-image with a shell script that starts the bundled JVM from lib/app/visage.cfg.
// (Used by the tar.gz, AppImage and Flatpak; deb/rpm keep jpackage's own launcher.)
val toolchainHome: String = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}.get().metadata.installationPath.asFile.absolutePath
tasks.matching { !isWindowsHost && (it.name == "createDistributable" || it.name == "createReleaseDistributable") }.configureEach {
    doLast {
        val launcher = rootProject.file("packaging/launcher/visage")
        layout.buildDirectory.dir("compose/binaries").get().asFile.walkTopDown()
            .filter { it.isFile && it.name == "visage" && it.parentFile.name == "bin" }
            .forEach { target ->
                launcher.copyTo(target, overwrite = true)
                target.setExecutable(true, false)
                // jpackage strips bin/java from the runtime; the script needs it (same JDK as the runtime's libjli).
                val java = File(toolchainHome, "bin/java")
                val dest = File(target.parentFile.parentFile, "lib/runtime/bin/java")
                dest.parentFile.mkdirs()
                java.copyTo(dest, overwrite = true)
                dest.setExecutable(true, false)
            }
    }
}
