package com.music.vivi.compose.ui.component

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure, deterministic "full-bleed" art generator for mood / genre cards (no Compose dependency, unit-testable).
 *
 * Builds on the cell model of [generateCoverLayout]: the region is cut into disjoint rectangular cells by
 * guillotine splits (or a regular grid for pattern themes) and every cell holds exactly one motif whose ink
 * bounds lie fully inside the cell, so motifs can never overlap. Unlike covers, the tiled region is *larger*
 * than the card ([BLEED] on every side) so cells at the edges are partially cropped by the card and the art
 * fills the entire background. Coordinates: card is [0, aspect] x [0, 1] (height normalised to 1).
 */
internal enum class MoodTheme(
    val keywords: List<String>,
    val motifs: List<MoodMotif>,
    /** Indices into the card shape set (see MoodCard.kt). */
    val shapes: IntArray,
    /** Base cell count for a square card; scaled by sqrt(aspect). */
    val density: Int,
    val grid: Boolean = false,
    /** Probability that a cell gets a rounded tonal tile behind its motif. */
    val tileChance: Float = 0.25f,
) {
    ORNAMENT(
        listOf("arabic", "bollywood", "indian", "mandopop", "cantopop"),
        listOf(MoodMotif.SHAPE, MoodMotif.SHAPE, MoodMotif.SCALLOP, MoodMotif.RINGS, MoodMotif.DOTS),
        intArrayOf(1, 3, 5, 12, 13), 9, grid = true, tileChance = 0.5f,
    ),
    ROCK(
        listOf("rock", "metal"),
        listOf(MoodMotif.ZIGZAG, MoodMotif.SHAPE, MoodMotif.STRIPES, MoodMotif.DIAG, MoodMotif.SHAPE),
        intArrayOf(8, 9, 8, 13, 10), 7, tileChance = 0.3f,
    ),
    JAZZ(
        listOf("jazz", "blues", "soul", "r&b"),
        listOf(MoodMotif.HALF, MoodMotif.ARCS, MoodMotif.BARS, MoodMotif.QUARTER, MoodMotif.DISC, MoodMotif.PILL),
        intArrayOf(7, 6, 11), 6, tileChance = 0.35f,
    ),
    ELECTRONIC(
        listOf("dance", "electro"),
        listOf(MoodMotif.BARS, MoodMotif.DOTS, MoodMotif.GRID, MoodMotif.RINGS, MoodMotif.BLOCK),
        intArrayOf(6, 7, 10), 9, grid = true, tileChance = 0.4f,
    ),
    HIPHOP(
        listOf("hip", "rap"),
        listOf(MoodMotif.BLOCK, MoodMotif.DISC, MoodMotif.QUARTER, MoodMotif.STRIPES, MoodMotif.BLOCK),
        intArrayOf(6, 7, 4, 10), 5, tileChance = 0.2f,
    ),
    CLASSICAL(
        listOf("classical", "sound", "musical"),
        listOf(MoodMotif.ARCS, MoodMotif.RINGS, MoodMotif.ARCS, MoodMotif.HALF, MoodMotif.STRIPES),
        intArrayOf(7, 6), 5, tileChance = 0.15f,
    ),
    FOLK(
        listOf("folk", "country", "indie", "acoustic", "alternative"),
        listOf(MoodMotif.SHAPE, MoodMotif.SHAPE, MoodMotif.SHAPE, MoodMotif.PILL, MoodMotif.DISC),
        intArrayOf(0, 1, 2, 11, 5), 6, tileChance = 0.2f,
    ),
    LATIN(
        listOf("latin", "reggae", "caribbean", "african", "afro"),
        listOf(MoodMotif.SUN, MoodMotif.STRIPES, MoodMotif.HALF, MoodMotif.SHAPE, MoodMotif.SUN, MoodMotif.DIAG),
        intArrayOf(3, 5, 9), 7, tileChance = 0.3f,
    ),
    POP(
        listOf("pop", "feel"),
        listOf(MoodMotif.SHAPE, MoodMotif.SHAPE, MoodMotif.SHAPE, MoodMotif.DISC, MoodMotif.DOTS),
        intArrayOf(0, 1, 2, 5, 3), 6, tileChance = 0.25f,
    ),
    CALM(
        listOf("chill", "sleep", "focus"),
        listOf(MoodMotif.DISC, MoodMotif.PILL, MoodMotif.DISC, MoodMotif.HALF, MoodMotif.PILL),
        intArrayOf(7, 7, 0), 3, tileChance = 0.1f,
    ),
    PARTY(
        listOf("party", "energize", "workout", "commute"),
        listOf(MoodMotif.SHAPE, MoodMotif.DIAG, MoodMotif.ZIGZAG, MoodMotif.SHAPE, MoodMotif.STRIPES, MoodMotif.SHAPE),
        intArrayOf(3, 9, 8, 3, 13), 9, tileChance = 0.3f,
    ),
    HEART(
        listOf("romance", "sad"),
        listOf(MoodMotif.SHAPE, MoodMotif.SHAPE, MoodMotif.ARCS, MoodMotif.DISC, MoodMotif.HALF),
        intArrayOf(2, 2, 11, 7), 5, tileChance = 0.2f,
    ),
    SEASON(
        listOf("christmas", "autumn"),
        listOf(MoodMotif.SHAPE, MoodMotif.DOTS, MoodMotif.SHAPE, MoodMotif.DISC, MoodMotif.SHAPE),
        intArrayOf(13, 9, 11, 1, 8), 8, tileChance = 0.25f,
    ),
    PIXEL(
        listOf("gaming", "game"),
        listOf(MoodMotif.GRID, MoodMotif.BLOCK, MoodMotif.GRID, MoodMotif.BLOCK, MoodMotif.DOTS),
        intArrayOf(10, 6), 10, grid = true, tileChance = 0.45f,
    ),
    FAMILY(
        listOf("family"),
        listOf(MoodMotif.SHAPE, MoodMotif.DISC, MoodMotif.SHAPE, MoodMotif.PILL, MoodMotif.SHAPE),
        intArrayOf(0, 0, 1, 7), 5, tileChance = 0.3f,
    ),
    DECADES(
        listOf("decade"),
        listOf(MoodMotif.RINGS, MoodMotif.STRIPES, MoodMotif.RINGS, MoodMotif.BARS, MoodMotif.QUARTER),
        intArrayOf(7, 6), 6, tileChance = 0.3f,
    ),
    ;

    companion object {
        /** Keyword match on the title; falls back to a seeded theme so unknown titles still get variety. */
        fun forTitle(title: String): MoodTheme {
            val lower = title.lowercase()
            val tokens = lower.split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
            for (t in entries) {
                if (t.keywords.any { kw -> tokens.any { it.startsWith(kw) } || (!kw.all { c -> c.isLetter() } && lower.contains(kw)) }) return t
            }
            return entries[Math.floorMod(stableSeed(lower), entries.size)]
        }
    }
}

internal enum class MoodMotif { SHAPE, DISC, RINGS, STRIPES, DIAG, BARS, DOTS, GRID, BLOCK, QUARTER, HALF, ARCS, ZIGZAG, PILL, SCALLOP, SUN }

internal class MoodCell(
    val rect: CRect,
    val ink: CRect,
    val motif: MoodMotif,
    /** Indices into the card palette's three ink colours (0..2). */
    val colorIdx: Int,
    val color2Idx: Int,
    val shapeIdx: Int,
    val rotation: Float,
    val count: Int,
    val corner: Int,
    val vertical: Boolean,
    val spinDir: Float,
    /** Draw a rounded tonal tile behind the motif (the tile is the cell rect, still disjoint). */
    val tile: Boolean,
)

internal const val BLEED = 0.1f
internal const val MOOD_SHAPE_COUNT = 14

/** Round motifs need a square ink; pattern motifs use the whole inner rect. */
private fun isRound(m: MoodMotif) = when (m) {
    MoodMotif.SHAPE, MoodMotif.DISC, MoodMotif.RINGS, MoodMotif.QUARTER, MoodMotif.HALF, MoodMotif.ARCS, MoodMotif.SUN, MoodMotif.BLOCK -> true
    else -> false
}

internal fun generateMoodLayout(seed: String, aspect: Float, theme: MoodTheme): List<MoodCell> {
    val rnd = Random(stableSeed(seed) xor (theme.ordinal * 7919))
    val a = aspect.coerceIn(0.5f, 4f)
    val region = CRect(-BLEED, -BLEED, a + BLEED, 1f + BLEED)
    val target = (theme.density * sqrt(a) + rnd.nextInt(3) - 1).roundToInt().coerceIn(3, 40)
    val cells = mutableListOf<CRect>()
    if (theme.grid) {
        val cols = maxOf(2, sqrt(target * region.w / region.h).roundToInt())
        val rows = maxOf(2, (target.toFloat() / cols).roundToInt())
        val cw = region.w / cols; val ch = region.h / rows
        for (i in 0 until cols) for (j in 0 until rows) cells += CRect(region.l + i * cw, region.t + j * ch, region.l + (i + 1) * cw, region.t + (j + 1) * ch)
    } else {
        cells += region
        while (cells.size < target) {
            val idx = cells.indices.maxBy { cells[it].w * cells[it].h * (0.8f + rnd.nextFloat() * 0.4f) }
            val c = cells.removeAt(idx)
            val ratio = 0.36f + rnd.nextFloat() * 0.28f
            val vertical = if (c.w > c.h * 1.15f) true else if (c.h > c.w * 1.15f) false else rnd.nextBoolean()
            if (vertical) { val x = c.l + c.w * ratio; cells += CRect(c.l, c.t, x, c.b); cells += CRect(x, c.t, c.r, c.b) }
            else { val y = c.t + c.h * ratio; cells += CRect(c.l, c.t, c.r, y); cells += CRect(c.l, y, c.r, c.b) }
        }
    }
    cells.shuffle(rnd)
    val gap = 0.014f
    return cells.mapIndexed { i, cell ->
        val rect = cell.inset(gap)
        val motif = theme.motifs[rnd.nextInt(theme.motifs.size)]
        val tile = rnd.nextFloat() < theme.tileChance
        val pad = minOf(rect.w, rect.h) * (if (tile) 0.2f else 0.1f)
        val inner = rect.inset(pad)
        val s = minOf(inner.w, inner.h)
        val ink = if (isRound(motif)) CRect(inner.cx - s / 2, inner.cy - s / 2, inner.cx + s / 2, inner.cy + s / 2) else inner
        val c1 = rnd.nextInt(3)
        MoodCell(
            rect = rect, ink = ink, motif = motif,
            colorIdx = c1, color2Idx = (c1 + 1 + rnd.nextInt(2)) % 3,
            shapeIdx = theme.shapes[rnd.nextInt(theme.shapes.size)].coerceIn(0, MOOD_SHAPE_COUNT - 1),
            rotation = rnd.nextInt(8) * 45f + rnd.nextInt(3) * 9f,
            count = 3 + rnd.nextInt(3), corner = rnd.nextInt(4),
            vertical = rect.h > rect.w, spinDir = if ((i + rnd.nextInt(2)) % 2 == 0) 1f else -1f,
            tile = tile,
        )
    }
}
