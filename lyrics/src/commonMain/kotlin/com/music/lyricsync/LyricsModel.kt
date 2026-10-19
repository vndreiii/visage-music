package com.music.lyricsync

/**
 * One sung unit (a word, or a syllable when [spaceAfter] is false) with its own timing.
 * Times are absolute playback milliseconds.
 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    /** False for intra-word syllables: the next unit is glued to this one without a space. */
    val spaceAfter: Boolean = true,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    /** 0..1 progress of [positionMs] through this unit. */
    fun progressAt(positionMs: Float): Float =
        ((positionMs - startMs) / durationMs).coerceIn(0f, 1f)
}

/** Where a line's [LyricsLine.words] came from. */
enum class WordSource { NONE, PROVIDER, ESTIMATED }

data class LyricsLine(
    /** Start time; null for unsynced (plain) lyrics. */
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    /** End of the sung part of the line (real when word-synced, estimated otherwise). */
    val endMs: Long? = null,
    val wordSource: WordSource = if (words.isEmpty()) WordSource.NONE else WordSource.PROVIDER,
    val isBackground: Boolean = false,
    val agent: String? = null,
    val translation: String? = null,
)

data class ParsedLyrics(
    val lines: List<LyricsLine>,
    val isSynced: Boolean,
    val hasProviderWordSync: Boolean,
) {
    companion object {
        val Empty = ParsedLyrics(emptyList(), isSynced = false, hasProviderWordSync = false)
    }
}

/** A list row: either a lyric line (index into [LyricsTimeline.lines]) or an instrumental gap. */
sealed interface LyricsRow {
    val startMs: Long

    data class Line(val index: Int, override val startMs: Long) : LyricsRow

    data class Gap(override val startMs: Long, val endMs: Long) : LyricsRow {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)
        fun progressAt(positionMs: Float): Float = ((positionMs - startMs) / durationMs).coerceIn(0f, 1f)
    }
}

enum class LinePhase { Past, Active, Future }
