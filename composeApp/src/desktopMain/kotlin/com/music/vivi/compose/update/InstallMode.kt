package com.music.vivi.compose.update

import java.io.File

/** How this copy of VIsage can be updated. Decided once at startup. */
sealed interface InstallMode {
    /** Flatpak installs are updated by flatpak; the app never prompts. */
    data object FlatpakManaged : InstallMode

    /** A running AppImage that can replace its own file. */
    data class AppImage(val file: File) : InstallMode

    /** deb, rpm, tarball or a non-writable AppImage: tell the user, open the release page. */
    data object ReleasePageOnly : InstallMode

    companion object {
        fun detect(
            env: Map<String, String>,
            flatpakInfoExists: Boolean = File("/.flatpak-info").exists(),
            isReplaceable: (File) -> Boolean = { it.isFile && it.canWrite() && it.parentFile?.canWrite() == true },
        ): InstallMode {
            if (!env["FLATPAK_ID"].isNullOrBlank() || flatpakInfoExists) return FlatpakManaged
            val appImage = env["APPIMAGE"]?.takeIf { it.isNotBlank() }?.let(::File) ?: return ReleasePageOnly
            return if (isReplaceable(appImage)) AppImage(appImage.absoluteFile) else ReleasePageOnly
        }
    }
}
