package com.music.vivi.compose.update

import java.io.File

/** How this copy of VIsage can be updated. Decided once at startup. */
sealed interface InstallMode {
    /** Flatpak installs are updated by flatpak; the app never prompts. */
    data object FlatpakManaged : InstallMode

    /** A running AppImage that can replace its own file. */
    data class AppImage(val file: File) : InstallMode

    /** Installed with the Windows installer (not the portable zip): updates by running the new installer. */
    data class WindowsInstaller(val exe: File) : InstallMode

    /** deb, rpm, tarball or a non-writable AppImage: tell the user, open the release page. */
    data object ReleasePageOnly : InstallMode

    companion object {
        /**
         * On Windows the portable zip carries a `portable` marker file next to `visage.exe`.
         * Without it, this copy came from the installer.
         */
        private fun windowsInstall(): InstallMode? {
            if (!com.music.vivi.nativelib.HostOs.isWindows) return null
            val exe = ProcessHandle.current().info().command().orElse(null)?.let(::File)
                ?.takeIf { it.name.equals("visage.exe", ignoreCase = true) } ?: return ReleasePageOnly
            return if (File(exe.parentFile, "portable").exists()) ReleasePageOnly else WindowsInstaller(exe)
        }

        fun detect(
            env: Map<String, String>,
            flatpakInfoExists: Boolean = File("/.flatpak-info").exists(),
            isReplaceable: (File) -> Boolean = { it.isFile && it.canWrite() && it.parentFile?.canWrite() == true },
        ): InstallMode {
            windowsInstall()?.let { return it }
            if (!env["FLATPAK_ID"].isNullOrBlank() || flatpakInfoExists) return FlatpakManaged
            val appImage = env["APPIMAGE"]?.takeIf { it.isNotBlank() }?.let(::File) ?: return ReleasePageOnly
            return if (isReplaceable(appImage)) AppImage(appImage.absoluteFile) else ReleasePageOnly
        }
    }
}
