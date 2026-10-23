package com.music.lyricsync

import com.music.vivi.betterlyrics.TTMLParser
import kotlin.math.roundToLong

/**
 * Parses every lyrics text format the providers emit into [ParsedLyrics]:
 *  - plain text
 *  - line-synced LRC (`[mm:ss.xx]text`, repeated timestamps, metadata tags ignored)
 *  - enhanced LRC with inline word/syllable tags (`[..]<mm:ss.xx>Hel<mm:ss.xx>lo`) -- Musixmatch richsync, YouLy+
 *  - Paxsenix / BetterLyrics word rows (`<word:startSec:endSec|word:startSec:endSec>` after the line)
 *  - raw TTML (Apple style `<span begin end>` words)
 * No network, no Compose: unit-testable.
 */
object LyricsParser {
    private val lrcTag = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTag = Regex("""^\[[A-Za-z#][^\]]*:[^\]]*]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val agentMarker = Regex("""\{agent:([^}]*)}""")
    private val bgMarker = Regex("""\{bg}""")

    fun parse(raw: String): ParsedLyrics {
        val text = raw.removePrefix("﻿")
        if (text.isBlank()) return ParsedLyrics.Empty
        val head = text.trimStart()
        if (head.startsWith("<?xml") || head.startsWith("<tt")) {
            val fromTtml = parseTtml(text)
            if (fromTtml.lines.isNotEmpty()) return fromTtml
        }

        val out = ArrayList<LyricsLine>()
        // Lines still waiting for a Paxsenix word row (only the most recent main/bg line can receive one).
        var last: LyricsLine? = null
        var lastIdx = -1
        var providerWords = false

        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
                val words = parsePaxsenixRow(trimmed)
                if (words != null) {
                    val l = last
                    if (l != null && l.words.isEmpty()) {
                        val fixed = l.copy(
                            words = words,
                            endMs = words.last().endMs,
                            wordSource = WordSource.PROVIDER,
                        )
                        out[lastIdx] = fixed
                        last = fixed
                        providerWords = true
                    }
                    continue
                }
            }

            val parsed = parseLine(trimmed) ?: continue
            for (line in parsed) {
                out += line
                if (line.words.isNotEmpty()) providerWords = true
            }
            lastIdx = out.size - 1
            last = out.lastOrNull()
        }

        if (out.isEmpty()) return ParsedLyrics.Empty
        val synced = out.any { it.timeMs != null }
        if (!synced) return ParsedLyrics(out, isSynced = false, hasProviderWordSync = false)

        val timed = out.filter { it.timeMs != null }
        val sorted = timed.sortedBy { it.timeMs } // stable
        val withTranslations = attachTranslations(sorted)
        val finished = applyOffsetTag(finishEnds(withTranslations), lrcOffsetMs(text))
        return ParsedLyrics(finished, isSynced = true, hasProviderWordSync = providerWords)
    }

    private val offsetTag = Regex("""(?im)^\s*\[offset:\s*([+-]?\d+)\s*]""")

    /** The LRC `[offset:ms]` header: positive means the lyrics show earlier (timestamps shift back). */
    internal fun lrcOffsetMs(text: String): Long =
        offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    internal fun applyOffsetTag(lines: List<LyricsLine>, offsetMs: Long): List<LyricsLine> {
        if (offsetMs == 0L) return lines
        return lines.map { l ->
            l.copy(
                timeMs = l.timeMs?.let { (it - offsetMs).coerceAtLeast(0L) },
                endMs = l.endMs?.let { (it - offsetMs).coerceAtLeast(0L) },
                words = l.words.map { w -> w.copy(startMs = (w.startMs - offsetMs).coerceAtLeast(0L), endMs = (w.endMs - offsetMs).coerceAtLeast(0L)) },
            )
        }
    }

    // ---- single line ----

    private fun parseLine(line: String): List<LyricsLine>? {
        var rest = line
        val times = ArrayList<Long>()
        while (true) {
            val m = lrcTag.find(rest)
            if (m != null) {
                times += lrcMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                rest = rest.substring(m.value.length).trimStart()
                continue
            }
            if (times.isEmpty() && metaTag.containsMatchIn(rest)) return null
            break
        }
        val isBg = bgMarker.containsMatchIn(rest)
        val agent = agentMarker.find(rest)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val body = rest.replace(agentMarker, "").replace(bgMarker, "").trim()

        if (times.isEmpty()) {
            if (line.startsWith("[")) return null
            if (body.isEmpty()) return null
            return listOf(LyricsLine(null, body, isBackground = isBg, agent = agent))
        }

        val first = times.first()
        var words: List<LyricWord> = emptyList()
        var plain = body
        var lineEnd: Long? = null
        if (wordTag.containsMatchIn(body)) {
            val e = parseEnhanced(body, first)
            words = e.words
            plain = e.text
            lineEnd = e.words.lastOrNull()?.endMs?.takeIf { it > 0 }
        }
        if (plain.isEmpty()) return null
        return times.map { t ->
            val shift = t - first
            LyricsLine(
                timeMs = t,
                text = plain,
                words = if (shift == 0L) words else words.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) },
                endMs = lineEnd?.plus(shift),
                wordSource = if (words.isEmpty()) WordSource.NONE else WordSource.PROVIDER,
                isBackground = isBg,
                agent = agent,
            )
        }
    }

    private class Enhanced(val text: String, val words: List<LyricWord>)

    /**
     * Enhanced LRC body: text with `<mm:ss.xx>` tags before each word/syllable.
     * The final word's end is the time of a trailing tag when present, else [UNRESOLVED_END]
     * (resolved by [finishEnds] from the next line).
     */
    private fun parseEnhanced(body: String, lineStart: Long): Enhanced {
        val matches = wordTag.findAll(body).toList()
        data class Raw(var text: String, val start: Long, var space: Boolean)
        val raws = ArrayList<Raw>()
        var trailingEnd: Long? = null

        // text before the first tag
        val lead = body.substring(0, matches.first().range.first)
        if (lead.isNotBlank()) raws += Raw(lead.trim(), lineStart, lead.last().isWhitespace())

        for ((i, m) in matches.withIndex()) {
            val t = lrcMillis(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val segEnd = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            val seg = body.substring(m.range.last + 1, segEnd)
            if (seg.isBlank()) {
                if (seg.isNotEmpty() && raws.isNotEmpty()) raws.last().space = true
                if (i == matches.lastIndex) trailingEnd = t
                continue
            }
            if (seg.first().isWhitespace() && raws.isNotEmpty()) raws.last().space = true
            raws += Raw(seg.trim(), t, seg.last().isWhitespace())
        }
        if (raws.isEmpty()) return Enhanced(body.replace(wordTag, "").trim(), emptyList())
        raws.last().space = false

        val words = raws.mapIndexed { i, r ->
            val end = when {
                i + 1 < raws.size -> raws[i + 1].start
                trailingEnd != null -> trailingEnd
                else -> UNRESOLVED_END
            }
            LyricWord(r.text, r.start, end, spaceAfter = r.space)
        }
        val sb = StringBuilder()
        words.forEachIndexed { i, w ->
            sb.append(w.text)
            if (w.spaceAfter && i < words.lastIndex) sb.append(' ')
        }
        return Enhanced(sb.toString(), words)
    }

    /** `<word:start:end|word:start:end>` with times in seconds. Returns null when it is not that format. */
    internal fun parsePaxsenixRow(row: String): List<LyricWord>? {
        val inner = row.removeSurrounding("<", ">")
        if (inner.isEmpty()) return null
        val words = ArrayList<LyricWord>()
        for (seg in inner.split('|')) {
            val c2 = seg.lastIndexOf(':')
            if (c2 <= 0) return null
            val c1 = seg.lastIndexOf(':', c2 - 1)
            if (c1 <= 0) return null
            val end = seg.substring(c2 + 1).toDoubleOrNull() ?: return null
            val start = seg.substring(c1 + 1, c2).toDoubleOrNull() ?: return null
            val text = seg.substring(0, c1).trim()
            if (text.isEmpty()) continue
            words += LyricWord(text, (start * 1000).roundToLong(), (end * 1000).roundToLong())
        }
        if (words.isEmpty()) return null
        return words.mapIndexed { i, w -> if (i == words.lastIndex) w.copy(spaceAfter = false) else w }
    }

    // ---- TTML ----

    fun parseTtml(ttml: String): ParsedLyrics {
        val parsed = TTMLParser.parseTTML(ttml)
        if (parsed.isEmpty()) return ParsedLyrics.Empty
        val out = ArrayList<LyricsLine>()
        fun conv(p: TTMLParser.ParsedLine, bg: Boolean) {
            val words = p.words.map {
                LyricWord(it.text, (it.startTime * 1000).roundToLong(), (it.endTime * 1000).roundToLong())
            }.mapIndexed { i, w -> if (i == p.words.lastIndex) w.copy(spaceAfter = false) else w }
            out += LyricsLine(
                timeMs = (p.startTime * 1000).roundToLong(),
                text = p.text,
                words = words,
                endMs = words.lastOrNull()?.endMs,
                wordSource = if (words.isEmpty()) WordSource.NONE else WordSource.PROVIDER,
                isBackground = bg,
                agent = p.agent,
            )
            p.backgroundLines.forEach { conv(it, true) }
        }
        parsed.forEach { conv(it, false) }
        val finished = finishEnds(out.sortedBy { it.timeMs })
        return ParsedLyrics(finished, isSynced = true, hasProviderWordSync = finished.any { it.words.isNotEmpty() })
    }

    // ---- post-processing ----

    /**
     * Second consecutive non-background line with the same timestamp and different text is the
     * translation / romanisation of the first (a common LRC convention).
     */
    internal fun attachTranslations(sorted: List<LyricsLine>): List<LyricsLine> {
        val out = ArrayList<LyricsLine>()
        var lastMainIdx = -1
        for (line in sorted) {
            val prev = out.getOrNull(lastMainIdx)
            val isTranslation = !line.isBackground && prev != null && prev.timeMs == line.timeMs &&
                prev.translation == null && line.words.isEmpty() && line.text != prev.text &&
                out.lastIndex == lastMainIdx
            if (isTranslation) {
                out[lastMainIdx] = prev!!.copy(translation = line.text)
                continue
            }
            out += line
            if (!line.isBackground) lastMainIdx = out.lastIndex
        }
        return out
    }

    /** Resolve [UNRESOLVED_END] word ends and the line end for word-synced lines. */
    internal fun finishEnds(lines: List<LyricsLine>): List<LyricsLine> {
        return lines.mapIndexed { i, line ->
            if (line.words.isEmpty() || line.words.last().endMs != UNRESOLVED_END) return@mapIndexed line
            val start = line.words.last().startMs
            val next = lines.drop(i + 1).firstOrNull { !it.isBackground && (it.timeMs ?: 0L) > start }?.timeMs
            val limit = start + 150L
            val cap = start + 2500L
            val end = (next ?: cap).coerceIn(limit, cap).coerceAtLeast(limit)
            val fixed = line.words.dropLast(1) + line.words.last().copy(endMs = end)
            line.copy(words = fixed, endMs = end)
        }
    }

    private fun lrcMillis(min: String, sec: String, frac: String): Long {
        val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + ms
    }

    internal const val UNRESOLVED_END = -1L
}
