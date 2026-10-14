package com.music.vivi.player.mpris

import java.io.File

/**
 * Installs a Freedesktop desktop entry + hicolor icon so MPRIS clients
 * (QuickShell, playerctl UIs, etc.) can resolve DesktopEntry=visage.
 */
object MprisDesktopIntegration {
    const val DESKTOP_ID = "visage"
    private const val ICON_RESOURCE = "/mpris/visage.png"

    fun ensureInstalled() {
        // Packaged installs (deb/rpm/Flatpak) ship their own launcher + icons.
        if (System.getenv("FLATPAK_ID") != null ||
            File("/usr/share/applications/$DESKTOP_ID.desktop").isFile
        ) return
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
            // Remove the launcher this app generated before the rename (only if it is ours), so the
            // menu doesn't show both "VIVI Desktop" and "VIsage".
            File(apps, "vivi-desktop.desktop").takeIf { it.isFile && "Name=VIVI Desktop" in it.readText() }?.delete()
            val desktop = File(apps, "$DESKTOP_ID.desktop")
            val body = """
                |[Desktop Entry]
                |Type=Application
                |Name=VIsage
                |GenericName=Music Player
                |Comment=VIsage — music for Linux
                |Exec=${System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() } ?: "visage"}
                |Icon=$DESKTOP_ID
                |Terminal=false
                |Categories=AudioVideo;Audio;Player;
                |StartupWMClass=visage
                |StartupNotify=false
                |
            """.trimMargin()
            desktop.writeText(body)
        }.onFailure { e ->
            System.err.println("MPRIS desktop integration: ${e.message}")
        }
    }
}
