package com.music.vivi.player.login

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object ChromiumLauncher {
    private val candidates = listOf(
        "chromium",
        "chromium-browser",
        "google-chrome-stable",
        "google-chrome",
        "brave-browser",
        "microsoft-edge",
        "microsoft-edge-stable",
    )

    fun findBrowser(): String? {
        for (name in candidates) {
            val path = which(name) ?: continue
            return path
        }
        return null
    }

    fun profileDir(): Path {
        val cacheHome = System.getenv("XDG_CACHE_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: Path(System.getProperty("user.home"), ".cache").toString()
        val dir = Path(cacheHome, "vivi-desktop", "login-profile")
        if (!dir.exists()) dir.createDirectories()
        return dir
    }

    fun pickFreePort(): Int {
        java.net.ServerSocket(0).use { return it.localPort }
    }

    /**
     * Opens Chromium in app mode with an isolated profile and CDP enabled.
     * Google accepts real Chromium; JavaFX WebKit is blocked.
     */
    fun launch(browser: String, loginUrl: String, port: Int, profile: Path): Process {
        Files.createDirectories(profile)
        val cmd = listOf(
            browser,
            "--user-data-dir=${profile.toAbsolutePath()}",
            "--remote-debugging-port=$port",
            "--remote-allow-origins=*",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-sync",
            "--app=$loginUrl",
        )
        return ProcessBuilder(cmd)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private fun which(name: String): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(':')) {
            val candidate = Path(dir, name)
            if (Files.isExecutable(candidate)) return candidate.toString()
        }
        return null
    }
}
