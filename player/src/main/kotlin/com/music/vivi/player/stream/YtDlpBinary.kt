package com.music.vivi.player.stream

import java.io.File

/** Locate yt-dlp / youtube-dl on PATH (or via `VIVI_YTDLP`). */
object YtDlpBinary {
    private val candidates = listOf(
        System.getenv("VIVI_YTDLP"),
        "yt-dlp",
        "yt-dlp_linux",
        "youtube-dl",
    ).filterNotNull()

    fun available(): Boolean = resolve() != null

    fun resolve(): String? = candidates.firstOrNull { which(it) != null }?.let { which(it) }

    fun which(name: String): String? {
        if (name.contains('/')) {
            return name.takeIf { File(it).canExecute() }
        }
        val path = System.getenv("PATH") ?: return null
        return path.split(':').asSequence()
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }
}
