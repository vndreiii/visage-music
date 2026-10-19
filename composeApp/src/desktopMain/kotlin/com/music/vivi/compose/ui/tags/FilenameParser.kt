package com.music.vivi.compose.ui.tags

import java.io.File

/** What a file name / folder layout says about a track. */
data class ParsedName(
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val trackNo: Int? = null,
) {
    fun toTags(): Tags = buildMap {
        artist?.let { put(TagField.Artist, it) }
        title?.let { put(TagField.Title, it) }
        album?.let { put(TagField.Album, it) }
        trackNo?.let { put(TagField.TrackNo, it.toString()) }
    }
}

object FilenameParser {
    private val leadingNumber = Regex("""^\s*(?:(\d{1,3})\s*[-._)\]]\s*|(\d{1,3})\s+(?=\D))""")
    private val separators = Regex("""\s+[-–—]\s+|\s*_-_\s*""")
    private val junkSuffix = Regex("""\s*[\[(](?:official\s*(?:music\s*)?(?:video|audio)|lyrics?|hd|hq|audio|visuali[sz]er|explicit)[\])]\s*$""", RegexOption.IGNORE_CASE)
    private val genericFolders = setOf(
        "music", "downloads", "download", "songs", "audio", "mp3", "mp3s", "flac", "media", "library", "my music",
        "desktop", "documents", "home", "tmp", "temp", "new folder", "unknown", "various artists", "various", "singles", "misc",
    )

    private fun tidy(s: String): String =
        s.replace('_', ' ').replace(Regex("""\s+"""), " ").trim().trim('-', ' ')

    fun isGenericFolder(name: String?): Boolean =
        name.isNullOrBlank() || name.lowercase() in genericFolders || name.length <= 1

    /**
     * "01 - Artist - Title.mp3", "Artist - Title", "01. Title" (artist from the folder), plus
     * `Artist/Album/Title.mp3` folder structure for artist/album.
     */
    fun parse(path: String): ParsedName {
        val f = File(path)
        var stem = f.nameWithoutExtension
        var trackNo: Int? = null
        leadingNumber.find(stem)?.let { m ->
            trackNo = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
            stem = stem.removeRange(m.range)
        }
        stem = tidy(stem).replace(junkSuffix, "").trim()
        val parts = stem.split(separators).map(::tidy).filter { it.isNotEmpty() }
        val parent = f.parentFile?.name
        val grand = f.parentFile?.parentFile?.name
        var artist: String? = null
        var title: String? = null
        when {
            parts.size >= 3 -> { artist = parts[0]; title = parts.drop(1).joinToString(" - ") }
            parts.size == 2 -> { artist = parts[0]; title = parts[1] }
            parts.size == 1 -> title = parts[0]
        }
        val album = parent?.takeUnless(::isGenericFolder)?.let(::tidy)
        if (artist == null && grand != null && !isGenericFolder(grand) && album != null) artist = tidy(grand)
        return ParsedName(artist, title?.takeIf { it.isNotBlank() }, album, trackNo)
    }

    // ── patterns: %artist% - %title% ───────────────────────────────────────────────────────

    private val placeholders: Map<String, TagField> = mapOf(
        "artist" to TagField.Artist, "title" to TagField.Title, "album" to TagField.Album,
        "albumartist" to TagField.AlbumArtist, "year" to TagField.Year, "genre" to TagField.Genre,
        "track" to TagField.TrackNo, "disc" to TagField.DiscNo, "composer" to TagField.Composer,
    )
    private val token = Regex("""%([a-z]+)%""")

    /** Fields named by a pattern, in order (unknown placeholders are ignored). */
    fun fieldsIn(pattern: String): List<TagField> =
        token.findAll(pattern).mapNotNull { placeholders[it.groupValues[1]] }.toList()

    /** Extracts tags from a file name stem by [pattern] ("%track% - %artist% - %title%"); null when it doesn't match. */
    fun parsePattern(pattern: String, stem: String): Tags? {
        val sb = StringBuilder("^")
        val order = ArrayList<TagField>()
        var last = 0
        for (m in token.findAll(pattern)) {
            val field = placeholders[m.groupValues[1]] ?: continue
            sb.append(Regex.escape(pattern.substring(last, m.range.first)))
            sb.append(if (field.numeric) "(\\d{1,4})" else "(.+?)")
            order += field
            last = m.range.last + 1
        }
        if (order.isEmpty()) return null
        sb.append(Regex.escape(pattern.substring(last))).append('$')
        val match = Regex(sb.toString(), RegexOption.IGNORE_CASE).matchEntire(stem.trim()) ?: return null
        return order.mapIndexedNotNull { i, f ->
            val v = tidy(match.groupValues[i + 1])
            if (v.isEmpty()) null else f to v
        }.toMap()
    }

    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

    fun sanitizeFileName(s: String): String =
        s.replace(illegal, "_").replace(Regex("""\s+"""), " ").trim().trim('.').ifEmpty { "_" }

    /** Renders a rename [pattern] for [tags]; null when a referenced field is empty (nothing sensible to name it). */
    fun format(pattern: String, tags: Tags): String? {
        var missing = false
        val out = token.replace(pattern) { m ->
            val f = placeholders[m.groupValues[1]] ?: return@replace m.value
            val v = tags.value(f).trim()
            if (v.isEmpty()) missing = true
            when (f) {
                TagField.TrackNo, TagField.DiscNo -> v.toIntOrNull()?.let { "%02d".format(it) } ?: v
                else -> v
            }
        }
        return if (missing) null else sanitizeFileName(out)
    }
}
