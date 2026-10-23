package com.music.lyricsync

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Word timing helpers: estimating per-word times when a provider only has line times. */
object WordTiming {
    /** Characters that are sung one per unit (no spaces in the script). */
    internal fun isCjk(c: Char): Boolean {
        val code = c.code
        return code in 0x3040..0x30FF || // kana
            code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || // CJK ideographs
            code in 0xF900..0xFAFF || code in 0x0E00..0x0E7F // compat ideographs, Thai
    }

    /** Splits [text] into sung units: whitespace separated tokens, CJK characters individually. */
    fun tokenize(text: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>() // token, spaceAfter
        val cur = StringBuilder()
        fun flush(space: Boolean) {
            if (cur.isNotEmpty()) {
                out += cur.toString() to space
                cur.setLength(0)
            }
        }
        for (c in text) {
            when {
                c.isWhitespace() -> flush(true)
                isCjk(c) -> {
                    flush(false)
                    out += c.toString() to false
                }
                else -> cur.append(c)
            }
        }
        flush(false)
        return out
    }

    private fun weight(token: String): Float {
        var w = 0f
        for (c in token) w += if (c.isLetterOrDigit()) 1f else 0.3f
        return max(w, 0.6f)
    }

    /** Estimated time the line is actually sung for, given the room before the next line. */
    fun estimateSungDuration(text: String, startMs: Long, nextStartMs: Long?): Long {
        val room = ((nextStartMs ?: (startMs + 6000L)) - startMs).coerceAtLeast(300L)
        val chars = text.count { !it.isWhitespace() }
        val natural = chars * 95L + 400L
        return min((room * 0.92f).toLong().coerceAtLeast(250L), natural)
    }

    /**
     * Distributes [startMs]..[endMs] over the words of [text] proportionally to their length.
     * Words are contiguous, so the highlight sweeps smoothly.
     */
    fun estimateWords(text: String, startMs: Long, endMs: Long): List<LyricWord> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val total = (endMs - startMs).coerceAtLeast(tokens.size.toLong())
        val weights = tokens.map { weight(it.first) }
        val sum = weights.sum()
        var acc = 0f
        return tokens.mapIndexed { i, (tok, space) ->
            val s = startMs + (total * (acc / sum)).toLong()
            acc += weights[i]
            val e = if (i == tokens.lastIndex) startMs + total else startMs + (total * (acc / sum)).toLong()
            LyricWord(tok, s, max(e, s + 1), spaceAfter = space && i < tokens.lastIndex)
        }
    }
}

/**
 * Prepared, render-ready lyrics: lines with ends (and optionally estimated words), the row list
 * with instrumental gaps, and fast active-line lookups.
 */
class LyricsTimeline private constructor(
    val lines: List<LyricsLine>,
    val rows: List<LyricsRow>,
    val isSynced: Boolean,
    private val mainIndices: IntArray,
    private val lineToRow: IntArray,
) {
    val hasWordSync: Boolean get() = lines.any { it.words.isNotEmpty() }

    /** Index into [lines] of the current non-background line, or -1 before the first line. */
    fun primaryIndex(positionMs: Long): Int {
        if (!isSynced || mainIndices.isEmpty()) return -1
        var lo = 0
        var hi = mainIndices.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val t = lines[mainIndices[mid]].timeMs ?: 0L
            if (t <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (ans < 0) -1 else mainIndices[ans]
    }

    /** Row to keep at the scroll anchor: the gap while in an instrumental break, else the primary line. */
    fun activeRowIndex(positionMs: Long): Int {
        if (!isSynced) return -1
        for (i in rows.indices) {
            val r = rows[i]
            if (r is LyricsRow.Gap && positionMs >= r.startMs && positionMs < r.endMs) return i
        }
        val p = primaryIndex(positionMs)
        return if (p < 0) -1 else lineToRow[p]
    }

    fun rowOfLine(lineIndex: Int): Int = lineToRow.getOrElse(lineIndex) { -1 }

    fun phase(lineIndex: Int, positionMs: Long, primary: Int = primaryIndex(positionMs)): LinePhase {
        val line = lines[lineIndex]
        if (!isSynced) return LinePhase.Future
        if (line.isBackground) {
            val s = line.timeMs ?: return LinePhase.Future
            val e = (line.endMs ?: s) + 700L
            return when {
                positionMs < s -> LinePhase.Future
                positionMs < e -> LinePhase.Active
                else -> LinePhase.Past
            }
        }
        return when {
            lineIndex == primary -> LinePhase.Active
            lineIndex < primary -> LinePhase.Past
            else -> LinePhase.Future
        }
    }

    companion object {
        val Empty = LyricsTimeline(emptyList(), emptyList(), false, IntArray(0), IntArray(0))

        const val DEFAULT_MIN_GAP_MS = 5_000L

        /**
         * @param estimateWords fill missing word timings from the line duration
         * @param showBackground keep background-vocal lines
         */
        fun build(
            parsed: ParsedLyrics,
            estimateWords: Boolean = true,
            minGapMs: Long = DEFAULT_MIN_GAP_MS,
            showBackground: Boolean = true,
        ): LyricsTimeline {
            val src = if (showBackground) parsed.lines else parsed.lines.filter { !it.isBackground }
            if (src.isEmpty()) return Empty
            if (!parsed.isSynced) {
                val rows = src.mapIndexed { i, _ -> LyricsRow.Line(i, 0L) }
                return LyricsTimeline(src, rows, false, IntArray(0), IntArray(src.size) { it })
            }

            val lines = src.mapIndexed { i, line ->
                val start = line.timeMs ?: 0L
                val nextMain = src.drop(i + 1).firstOrNull { !it.isBackground && (it.timeMs ?: 0L) > start }?.timeMs
                when {
                    line.words.isNotEmpty() -> line.copy(endMs = line.endMs ?: line.words.last().endMs)
                    else -> {
                        val dur = WordTiming.estimateSungDuration(line.text, start, nextMain)
                        val end = start + dur
                        if (estimateWords) {
                            line.copy(
                                words = WordTiming.estimateWords(line.text, start, end),
                                endMs = end,
                                wordSource = WordSource.ESTIMATED,
                            )
                        } else {
                            line.copy(endMs = end)
                        }
                    }
                }
            }

            val main = lines.indices.filter { !lines[it].isBackground }.toIntArray()
            val rows = ArrayList<LyricsRow>()
            val lineToRow = IntArray(lines.size)
            var prevMainEnd = 0L
            var seenMain = false
            for (i in lines.indices) {
                val l = lines[i]
                val start = l.timeMs ?: 0L
                if (!l.isBackground) {
                    val gap = start - prevMainEnd
                    if (gap >= minGapMs && (seenMain || start >= minGapMs)) {
                        rows += LyricsRow.Gap(prevMainEnd, start)
                    }
                    seenMain = true
                    prevMainEnd = max(prevMainEnd, l.endMs ?: start)
                }
                lineToRow[i] = rows.size
                rows += LyricsRow.Line(i, start)
            }
            return LyricsTimeline(lines, rows, true, main, lineToRow)
        }
    }
}

/**
 * Smooths the coarse (~100ms) player position into a per-frame value:
 * extrapolates with the wall clock while playing and blends corrections instead of stepping.
 */
class PositionInterpolator(
    private val snapThresholdMs: Float = 450f,
    private val correctionTauMs: Float = 140f,
    /**
     * Longest the clock runs ahead of the last report. Reports come every ~100-200ms while audio
     * plays, so silence longer than this means a stall (buffering): hold instead of drifting ahead.
     */
    private val maxExtrapolateMs: Float = Float.MAX_VALUE,
) {
    private var anchorPos = 0f
    private var anchorNanos = 0L
    private var correction = 0f
    private var playing = false
    private var initialised = false

    fun setPlaying(isPlaying: Boolean, nowNanos: Long) {
        if (playing == isPlaying) return
        val current = estimate(nowNanos)
        anchorPos = current
        anchorNanos = nowNanos
        correction = 0f
        playing = isPlaying
    }

    fun report(positionMs: Long, nowNanos: Long) {
        val reported = positionMs.toFloat()
        if (!initialised) {
            initialised = true
            anchorPos = reported; anchorNanos = nowNanos; correction = 0f
            return
        }
        val predicted = estimate(nowNanos)
        val err = reported - predicted
        anchorPos = reported
        anchorNanos = nowNanos
        correction = if (kotlin.math.abs(err) > snapThresholdMs) 0f else -err
    }

    fun estimate(nowNanos: Long): Float {
        val elapsedMs = ((nowNanos - anchorNanos) / 1_000_000f).coerceAtLeast(0f)
        val decay = exp(-elapsedMs / correctionTauMs)
        return anchorPos + (if (playing) elapsedMs.coerceAtMost(maxExtrapolateMs) else 0f) + correction * decay
    }
}
