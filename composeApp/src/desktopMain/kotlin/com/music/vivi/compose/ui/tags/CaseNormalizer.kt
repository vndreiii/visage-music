package com.music.vivi.compose.ui.tags

/** "normalise case": fixes ALL CAPS / all lower values; mixed-case values are left alone. */
object CaseNormalizer {
    private val small = setOf("a", "an", "and", "as", "at", "but", "by", "for", "in", "nor", "of", "on", "or", "the", "to", "vs", "vs.", "feat.", "ft.")

    fun needsFixing(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.length >= 3 && (letters.all { it.isUpperCase() } || letters.all { it.isLowerCase() })
    }

    fun titleCase(s: String): String {
        if (!needsFixing(s)) return s
        val words = s.split(' ')
        return words.mapIndexed { i, w ->
            val lower = w.lowercase()
            when {
                w.isEmpty() -> w
                i > 0 && i < words.lastIndex && lower in small -> lower
                else -> capitalizeWord(lower)
            }
        }.joinToString(" ")
    }

    private fun capitalizeWord(w: String): String {
        val i = w.indexOfFirst { it.isLetter() }
        if (i < 0) return w
        // keep "(feat" / quotes: capitalise the first letter, and each part after - or /
        val sb = StringBuilder(w)
        sb.setCharAt(i, w[i].uppercaseChar())
        for (j in i + 1 until w.length) if ((w[j - 1] == '-' || w[j - 1] == '/' || w[j - 1] == '(') && w[j].isLetter()) sb.setCharAt(j, w[j].uppercaseChar())
        return sb.toString()
    }

    /** Fields of [tags] whose value would change; returned as a minimal edit map. */
    fun fix(tags: Tags, fields: Collection<TagField> = listOf(TagField.Title, TagField.Artist, TagField.Album, TagField.AlbumArtist, TagField.Genre, TagField.Composer)): Tags =
        fields.mapNotNull { f ->
            val v = tags.value(f)
            val n = titleCase(v)
            if (v.isNotBlank() && n != v) f to n else null
        }.toMap()
}
