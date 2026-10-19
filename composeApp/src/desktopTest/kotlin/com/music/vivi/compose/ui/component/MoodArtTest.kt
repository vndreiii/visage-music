package com.music.vivi.compose.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoodArtTest {
    private val aspects = listOf(0.7f, 1f, 1.2f, 1.6f, 2.4f, 3.5f)

    private val expected = mapOf(
        "Chill" to MoodTheme.CALM, "Christmas" to MoodTheme.SEASON, "Commute" to MoodTheme.PARTY,
        "Energize" to MoodTheme.PARTY, "Feel good" to MoodTheme.POP, "Focus" to MoodTheme.CALM,
        "Gaming" to MoodTheme.PIXEL, "Party" to MoodTheme.PARTY, "Romance" to MoodTheme.HEART,
        "Sad" to MoodTheme.HEART, "Sleep" to MoodTheme.CALM, "Workout" to MoodTheme.PARTY,
        "African" to MoodTheme.LATIN, "Arabic" to MoodTheme.ORNAMENT, "Autumn" to MoodTheme.SEASON,
        "Blues" to MoodTheme.JAZZ, "Bollywood & Indian" to MoodTheme.ORNAMENT, "Classical" to MoodTheme.CLASSICAL,
        "Country & Americana" to MoodTheme.FOLK, "Dance & electronic" to MoodTheme.ELECTRONIC,
        "Decades" to MoodTheme.DECADES, "Family" to MoodTheme.FAMILY, "Folk & acoustic" to MoodTheme.FOLK,
        "Hip-hop" to MoodTheme.HIPHOP, "Indie & alternative" to MoodTheme.FOLK, "J-Pop" to MoodTheme.POP,
        "Jazz" to MoodTheme.JAZZ, "K-Pop" to MoodTheme.POP, "Latin" to MoodTheme.LATIN,
        "Mandopop & cantopop" to MoodTheme.ORNAMENT, "Metal" to MoodTheme.ROCK, "Pop" to MoodTheme.POP,
        "R&B & soul" to MoodTheme.JAZZ, "Reggae & caribbean" to MoodTheme.LATIN, "Rock" to MoodTheme.ROCK,
        "Soundtracks & musicals" to MoodTheme.CLASSICAL,
    )

    @Test
    fun keywordMappingResolvesAllListedTitles() {
        for ((title, theme) in expected) assertEquals(theme, MoodTheme.forTitle(title), title)
        // unknown titles still resolve deterministically
        assertEquals(MoodTheme.forTitle("Zzzxq"), MoodTheme.forTitle("zzzxq"))
    }

    @Test
    fun fullBleedNeverOverlapsAndCoversTheCard() {
        for (theme in MoodTheme.entries) for (aspect in aspects) for (i in 0 until 60) {
            val cells = generateMoodLayout("seed-$i", aspect, theme)
            val region = CRect(-BLEED, -BLEED, aspect + BLEED, 1f + BLEED)
            assertTrue(cells.size >= 3, "cell count")
            for (c in cells) {
                assertTrue(region.contains(c.rect), "cell outside region $theme seed-$i")
                assertTrue(c.rect.contains(c.ink), "ink outside cell $theme seed-$i")
                assertTrue(c.ink.w > 0.01f && c.ink.h > 0.01f, "degenerate ink")
            }
            for (a in cells.indices) for (b in a + 1 until cells.size) {
                assertFalse(cells[a].rect.intersects(cells[b].rect), "cells overlap $theme seed-$i")
                assertFalse(cells[a].ink.intersects(cells[b].ink), "ink overlaps $theme seed-$i")
            }
            // tiling: cell areas (before the uniform gap inset) sum to the region area -> no holes in the card
            val gap = 0.014f
            val total = cells.sumOf { ((it.rect.w + 2 * gap) * (it.rect.h + 2 * gap)).toDouble() }
            assertEquals(region.w * region.h.toDouble(), total, 1e-3, "tiling incomplete $theme seed-$i")
        }
    }

    @Test
    fun deterministicAndVariedBySeed() {
        val a = generateMoodLayout("Rock", 1.3f, MoodTheme.ROCK).map { it.rect to it.motif }
        assertEquals(a, generateMoodLayout("rock", 1.3f, MoodTheme.ROCK).map { it.rect to it.motif })
        assertTrue(a != generateMoodLayout("Metal", 1.3f, MoodTheme.ROCK).map { it.rect to it.motif })
    }
}
