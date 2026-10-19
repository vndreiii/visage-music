package com.music.vivi.player.stream

import com.music.vivi.nativelib.HostOs
import java.io.File

/** Locate yt-dlp / youtube-dl: `VISAGE_YTDLP`, the app's own files, then PATH. */
object YtDlpBinary {
    private val candidates: List<String> by lazy {
        val names = if (HostOs.isWindows) listOf("yt-dlp.exe", "yt-dlp", "youtube-dl.exe") else listOf("yt-dlp", "yt-dlp_linux", "youtube-dl")
        val bundled = HostOs.resourcesDir()?.let { dir -> names.map { File(dir, it).path } }.orEmpty()
        listOfNotNull(com.music.vivi.nativelib.AppEnv.get("YTDLP")) + bundled + names
    }

    fun available(): Boolean = resolve() != null

    fun resolve(): String? = candidates.firstNotNullOfOrNull { which(it) }

    fun which(name: String): String? {
        if (name.contains('/') || name.contains('\\')) {
            return name.takeIf { File(it).canExecute() }
        }
        val path = System.getenv("PATH") ?: return null
        return path.split(File.pathSeparatorChar).asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }
}
