package com.music.vivi.player.mpris

import java.io.File

/**
 * Installs a Freedesktop desktop entry + hicolor icon so MPRIS clients
 * (QuickShell, playerctl UIs, etc.) can resolve DesktopEntry=vivi-desktop.
 */
object MprisDesktopIntegration {
    const val DESKTOP_ID = "vivi-desktop"
    private const val ICON_RESOURCE = "/mpris/vivi-desktop.png"

    fun ensureInstalled() {
        runCatching {
            val home = System.getProperty("user.home")
            val dataHome = System.getenv("XDG_DATA_HOME")
                ?.takeIf { it.isNotBlank() }
                ?: "$home/.local/share"

            val bytes = MprisDesktopIntegration::class.java
                .getResourceAsStream(ICON_RESOURCE)
                ?.use { it.readBytes() }
            if (bytes != null) {
                for (size in listOf("512x512", "256x256", "128x128", "64x64", "48x48", "32x32")) {
                    val dir = File(dataHome, "icons/hicolor/$size/apps")
                    dir.mkdirs()
                    File(dir, "$DESKTOP_ID.png").writeBytes(bytes)
                }
                // Also a generic apps path some shells check
                val pixmaps = File(dataHome, "icons")
                pixmaps.mkdirs()
                File(pixmaps, "$DESKTOP_ID.png").writeBytes(bytes)
            }

            val apps = File(dataHome, "applications")
            apps.mkdirs()
            val desktop = File(apps, "$DESKTOP_ID.desktop")
            val body = """
                |[Desktop Entry]
                |Type=Application
                |Name=VIVI Desktop
                |GenericName=Music Player
                |Comment=VIVI Music for Linux
                |Exec=vivi-desktop
                |Icon=$DESKTOP_ID
                |Terminal=false
                |Categories=AudioVideo;Audio;Player;
                |StartupWMClass=com.music.vivi.alex
                |StartupNotify=false
                |
            """.trimMargin()
            desktop.writeText(body)
        }.onFailure { e ->
            System.err.println("MPRIS desktop integration: ${e.message}")
        }
    }
}
