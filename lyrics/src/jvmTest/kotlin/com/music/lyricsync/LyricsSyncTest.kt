package com.music.lyricsync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsSyncTest {

    @Test
    fun plainLyricsAreUnsynced() {
        val p = LyricsParser.parse("Hello there\nGeneral Kenobi\n")
        assertFalse(p.isSynced)
        assertEquals(2, p.lines.size)
        assertNull(p.lines[0].timeMs)
    }

    @Test
    fun lineLrcWithMetadataAndMultiTimestamps() {
        val raw = """
            [ar:Someone]
            [ti:Song]
            [00:01.50]First
            [00:10.00][00:30.5]Chorus
            [00:20.123]Second
        """.trimIndent()
        val p = LyricsParser.parse(raw)
        assertTrue(p.isSynced)
        assertFalse(p.hasProviderWordSync)
        assertEquals(listOf(1500L, 10000L, 20123L, 30500L), p.lines.map { it.timeMs })
        assertEquals(listOf("First", "Chorus", "Second", "Chorus"), p.lines.map { it.text })
    }

    @Test
    fun enhancedLrcWordsAndSyllables() {
        val raw = "[00:12.00]<00:12.00>Hel<00:12.30>lo <00:12.60>world<00:13.00>"
        val line = LyricsParser.parse(raw).lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(3, line.words.size)
        assertEquals(listOf("Hel", "lo", "world"), line.words.map { it.text })
        assertFalse(line.words[0].spaceAfter)
        assertTrue(line.words[1].spaceAfter)
        assertEquals(12_300L, line.words[0].endMs)
        assertEquals(13_000L, line.words[2].endMs) // trailing tag closes the last word
        assertEquals(13_000L, line.endMs)
    }

    @Test
    fun enhancedLrcLastWordEndComesFromNextLine() {
        val raw = "[00:01.000]<00:01.000>a <00:01.500>b\n[00:02.000]<00:02.000>c"
        val p = LyricsParser.parse(raw)
        assertTrue(p.hasProviderWordSync)
        assertEquals(2_000L, p.lines[0].words.last().endMs)
        // last line: capped at start + 2500
        assertEquals(4_500L, p.lines[1].words.last().endMs)
    }

    @Test
    fun paxsenixWordRowsAttachToPreviousLine() {
        val raw = """
            [00:07.02]{agent:v1}Walk into
            <Walk:7.022:7.291|into:7.291:7.657>
            [00:08.00]{bg}ooh
            <ooh:8.0:8.5>
        """.trimIndent()
        val p = LyricsParser.parse(raw)
        assertEquals(2, p.lines.size)
        val main = p.lines[0]
        assertEquals("Walk into", main.text)
        assertEquals("v1", main.agent)
        assertEquals(listOf("Walk", "into"), main.words.map { it.text })
        assertEquals(7022L, main.words[0].startMs)
        assertEquals(7657L, main.endMs)
        assertTrue(p.lines[1].isBackground)
        assertEquals(8500L, p.lines[1].words.single().endMs)
    }

    @Test
    fun paxsenixWordWithColonInText() {
        val w = LyricsParser.parsePaxsenixRow("<a:b:1.0:2.0>")
        assertNotNull(w)
        assertEquals("a:b", w.single().text)
        assertEquals(1000L, w.single().startMs)
    }

    @Test
    fun enhancedLineIsNotMistakenForPaxRow() {
        assertNull(LyricsParser.parsePaxsenixRow("<00:12.00>Hi <00:13.00>"))
    }

    @Test
    fun sameTimestampSecondLineBecomesTranslation() {
        val raw = "[00:05.00]Hola\n[00:05.00]Hello\n[00:09.00]Adios\n[00:09.00]Bye"
        val p = LyricsParser.parse(raw)
        assertEquals(2, p.lines.size)
        assertEquals("Hello", p.lines[0].translation)
        assertEquals("Bye", p.lines[1].translation)
    }

    @Test
    fun rawTtmlWordSpans() {
        val ttml = """<?xml version="1.0"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="0:01.000" end="0:03.000" ttm:agent="v1"><span begin="0:01.000" end="0:01.500">Hello</span> <span begin="0:01.500" end="0:02.000">there</span></p>
              </div></body>
            </tt>""".trimIndent()
        val p = LyricsParser.parse(ttml)
        assertTrue(p.hasProviderWordSync)
        val line = p.lines.single()
        assertEquals("Hello there", line.text)
        assertEquals(1_500L, line.words[1].startMs)
        assertEquals(2_000L, line.endMs)
    }

    @Test
    fun estimatedWordsAreContiguousAndCoverLine() {
        val words = WordTiming.estimateWords("The quick brown fox", 1000, 3000)
        assertEquals(4, words.size)
        assertEquals(1000L, words.first().startMs)
        assertEquals(3000L, words.last().endMs)
        for (i in 1 until words.size) assertEquals(words[i - 1].endMs, words[i].startMs)
        // longer words get more time
        assertTrue(words[1].durationMs > words[0].durationMs)
    }

    @Test
    fun cjkIsSplitPerCharacter() {
        val t = WordTiming.tokenize("你好 world")
        assertEquals(listOf("你", "好", "world"), t.map { it.first })
        assertFalse(t[0].second)
    }

    @Test
    fun timelineEstimatesOnlyWhenAsked() {
        val parsed = LyricsParser.parse("[00:01.00]Hello big world\n[00:05.00]Next")
        val on = LyricsTimeline.build(parsed, estimateWords = true)
        assertEquals(WordSource.ESTIMATED, on.lines[0].wordSource)
        assertEquals(3, on.lines[0].words.size)
        assertTrue(on.lines[0].endMs!! <= 5000L)
        val off = LyricsTimeline.build(parsed, estimateWords = false)
        assertTrue(off.lines[0].words.isEmpty())
        assertNotNull(off.lines[0].endMs)
    }

    @Test
    fun timelineKeepsProviderWords() {
        val parsed = LyricsParser.parse("[00:01.000]<00:01.000>a <00:01.500>b<00:02.000>")
        val t = LyricsTimeline.build(parsed, estimateWords = true)
        assertEquals(WordSource.PROVIDER, t.lines[0].wordSource)
    }

    @Test
    fun primaryIndexAndGaps() {
        val raw = "[00:02.00]one\n[00:04.00]two\n[00:30.00]three"
        val t = LyricsTimeline.build(LyricsParser.parse(raw))
        assertEquals(-1, t.primaryIndex(500))
        assertEquals(0, t.primaryIndex(2000))
        assertEquals(1, t.primaryIndex(29_999))
        assertEquals(2, t.primaryIndex(31_000))
        // intro gap too short (2s) -> none; long gap before "three" -> one Gap row
        val gaps = t.rows.filterIsInstance<LyricsRow.Gap>()
        assertEquals(1, gaps.size)
        assertTrue(gaps[0].startMs in 4_000L..8_000L)
        assertEquals(30_000L, gaps[0].endMs)
        val gapRow = t.rows.indexOf(gaps[0])
        assertEquals(gapRow, t.activeRowIndex(20_000))
        assertEquals(t.rowOfLine(1), t.activeRowIndex(4_100))
        assertEquals(t.rowOfLine(2), gapRow + 1)
    }

    @Test
    fun introGapWhenFirstLineIsLate() {
        val t = LyricsTimeline.build(LyricsParser.parse("[00:20.00]late\n[00:23.00]x"))
        val g = t.rows.first()
        assertTrue(g is LyricsRow.Gap)
        assertEquals(0.5f, (g as LyricsRow.Gap).progressAt(10_000f), 0.001f)
    }

    @Test
    fun backgroundLineDoesNotStealPrimary() {
        val raw = "[00:02.00]{agent:v1}main\n<main:2.0:3.0>\n[00:02.20]{bg}echo\n<echo:2.2:3.0>\n[00:05.00]next"
        val t = LyricsTimeline.build(LyricsParser.parse(raw))
        assertEquals(0, t.primaryIndex(2_500))
        assertEquals(LinePhase.Active, t.phase(1, 2_500))
        assertEquals(LinePhase.Past, t.phase(1, 4_000))
        val noBg = LyricsTimeline.build(LyricsParser.parse(raw), showBackground = false)
        assertEquals(2, noBg.lines.size)
    }

    @Test
    fun wordProgress() {
        val w = LyricWord("x", 1000, 2000)
        assertEquals(0f, w.progressAt(500f))
        assertEquals(0.25f, w.progressAt(1250f), 1e-4f)
        assertEquals(1f, w.progressAt(5000f))
    }

    @Test
    fun interpolatorExtrapolatesSnapsAndBlends() {
        val ms = 1_000_000L
        val ip = PositionInterpolator()
        ip.report(1000, 0)
        ip.setPlaying(true, 0)
        assertEquals(1050f, ip.estimate(50 * ms), 0.5f)
        // small error: continuous at the moment of report (no step)
        val before = ip.estimate(100 * ms)
        ip.report(1130, 100 * ms)
        assertEquals(before, ip.estimate(100 * ms), 0.5f)
        // ...and converges towards the reported timeline
        assertEquals(1130f + 1000f, ip.estimate(1100 * ms), 1.5f)
        // large error (seek): snap
        ip.report(60_000, 1200 * ms)
        assertEquals(60_000f, ip.estimate(1200 * ms), 0.5f)
        // paused: frozen
        ip.setPlaying(false, 1300 * ms)
        val frozen = ip.estimate(1300 * ms)
        assertEquals(frozen, ip.estimate(5000 * ms), 0.5f)
    }

    @Test
    fun interpolatorHoldsDuringAStall() {
        val ms = 1_000_000L
        val ip = PositionInterpolator(maxExtrapolateMs = 600f)
        ip.report(1000, 0)
        ip.setPlaying(true, 0)
        assertEquals(1400f, ip.estimate(400 * ms), 0.5f)
        // no report for 5s (buffering): never more than 600ms past the last known position
        assertEquals(1600f, ip.estimate(5000 * ms), 0.5f)
    }

    @Test
    fun lrcOffsetTagShiftsTimestamps() {
        val parsed = LyricsParser.parse("[offset:500]\n[00:10.00]hello\n[00:20.00]world")
        assertEquals(listOf(9500L, 19500L), parsed.lines.map { it.timeMs })
        val negative = LyricsParser.parse("[offset:-250]\n[00:10.00]hello\n[00:20.00]world")
        assertEquals(listOf(10250L, 20250L), negative.lines.map { it.timeMs })
    }
}
