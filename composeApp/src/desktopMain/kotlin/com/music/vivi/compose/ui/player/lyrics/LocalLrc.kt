package com.music.vivi.compose.ui.player.lyrics

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Finds the `.lrc` file that belongs to a local song. Lyrics usually sit next to the audio, but the name
 * rarely matches byte for byte (`Song.LRC`, `01 - Song.lrc`, `Artist - Song.lrc`, a `Lyrics` subfolder).
 */
internal object LocalLrc {
    private val lyricFolders = setOf("lyrics", "lrc", "lyric", "lrcs")

    /** Best candidate, or null. Order: the path the index stored, exact name, loosely matching name, then the same inside a lyrics folder. */
    fun find(audio: File, storedPath: String?, title: String?): File? {
        storedPath?.let(::File)?.takeIf { it.isFile }?.let { return it }
        val dir = audio.parentFile ?: return null
        val stem = audio.nameWithoutExtension
        val dirs = listOf(dir) +
            listOfNotNull(dir, dir.parentFile).flatMap { base ->
                base.listFiles { f -> f.isDirectory && f.name.lowercase() in lyricFolders }?.toList().orEmpty()
            }
        for (d in dirs) {
            val lrcs = d.listFiles { f -> f.isFile && f.extension.equals("lrc", ignoreCase = true) }?.toList().orEmpty()
            if (lrcs.isEmpty()) continue
            lrcs.firstOrNull { it.nameWithoutExtension == stem }?.let { return it }
            lrcs.firstOrNull { it.nameWithoutExtension.equals(stem, ignoreCase = true) }?.let { return it }
            val want = normalize(stem)
            if (want.isNotEmpty()) lrcs.firstOrNull { normalize(it.nameWithoutExtension) == want }?.let { return it }
            val t = normalize(title.orEmpty())
            if (t.length >= 3) {
                lrcs.firstOrNull { normalize(it.nameWithoutExtension) == t }?.let { return it }
                // "Artist - Title.lrc" next to "01 Title.flac".
                lrcs.firstOrNull { normalize(it.nameWithoutExtension).endsWith(t) }?.let { return it }
            }
        }
        return null
    }

    /** Lower-case letters and digits only, with a leading track number ("01 - ", "1. ") removed. */
    fun normalize(name: String): String =
        name.replace(Regex("^\\s*\\d{1,3}\\s*[-._)]*\\s+"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }

    /** LRC text: UTF-8 (with or without BOM), falling back to Windows-1252 for old western files. */
    fun read(file: File): String? = runCatching {
        val bytes = file.readBytes()
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val buf = ByteBuffer.wrap(bytes, start, bytes.size - start)
        try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(buf).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, start, bytes.size - start, charset("windows-1252"))
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
