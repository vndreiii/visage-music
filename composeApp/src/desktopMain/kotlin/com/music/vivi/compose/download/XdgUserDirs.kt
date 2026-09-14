package com.music.vivi.compose.download

import java.io.File

/**
 * Resolve the user Downloads folder (locale-aware on Linux via xdg-user-dir).
 *
 * Order: `xdg-user-dir DOWNLOAD` → `$XDG_DOWNLOAD_DIR` → `~/Downloads`.
 */
object XdgUserDirs {
    fun downloads(): File {
        xdgUserDir("DOWNLOAD")?.let { return it }
        System.getenv("XDG_DOWNLOAD_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let { return File(it).absoluteFile }
        return File(System.getProperty("user.home"), "Downloads").absoluteFile
    }

    private fun xdgUserDir(name: String): File? = runCatching {
        val proc = ProcessBuilder("xdg-user-dir", name)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() != 0 || out.isBlank()) return null
        File(out).takeIf { it.isAbsolute }?.absoluteFile
    }.getOrNull()
}
