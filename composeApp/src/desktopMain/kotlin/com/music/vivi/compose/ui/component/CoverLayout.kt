package com.music.vivi.compose.ui.component

import kotlin.random.Random

/**
 * Pure, deterministic layout generator for generated covers (no Compose dependency, unit-testable).
 *
 * Composition model (inspired by Bauhaus/Spotify-style flat covers): the art region is cut into 3-5 disjoint
 * rectangular cells by guillotine splits; each cell holds exactly ONE motif whose ink bounds lie fully inside
 * the cell. Cells never intersect, therefore motifs never overlap. Coordinates: region is
 * [0, aspect] x [0, 1] (height normalised to 1).
 */
internal data class CRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2f
    val cy get() = (t + b) / 2f
    fun inset(d: Float) = CRect(l + d, t + d, r - d, b - d)
    fun intersects(o: CRect) = l < o.r && o.l < r && t < o.b && o.t < b
    fun contains(o: CRect, eps: Float = 1e-4f) = o.l >= l - eps && o.t >= t - eps && o.r <= r + eps && o.b <= b + eps
}

internal enum class Motif { SHAPE, OUTLINE, RINGS, STRIPES, DOTS, QUARTER, DISC, TEXT }

internal class CoverCell(
    val rect: CRect,
    /** Fully contained ink bounds of the motif (square for round motifs, the inner rect for patterns). */
    val ink: CRect,
    val motif: Motif,
    /** Indices into the 5-colour palette (0 = gap/frame colour, 1..4 content colours). */
    val bgIdx: Int,
    val fgIdx: Int,
    val shapeIdx: Int,
    val rotation: Float,
    val count: Int,
    /** Corner for QUARTER: 0=TL 1=TR 2=BR 3=BL. */
    val corner: Int,
    val vertical: Boolean,
    val spinDir: Float,
)

internal const val COVER_SHAPE_COUNT = 8

internal fun stableSeed(seed: String): Int {
    var h = 0x811c9dc5.toInt()
    for (c in seed.lowercase()) { h = (h xor c.code) * 16777619 }
    return h
}

internal fun generateCoverLayout(seed: String, aspect: Float = 1f, reserveText: Boolean = false): List<CoverCell> {
    val rnd = Random(stableSeed(seed))
    val a = aspect.coerceIn(0.4f, 4f)
    val cells = mutableListOf(CRect(0f, 0f, a, 1f))
    val target = if (a > 2.2f) 4 + rnd.nextInt(2) else 3 + rnd.nextInt(3)
    while (cells.size < target) {
        val idx = cells.indices.maxBy { cells[it].w * cells[it].h }
        val c = cells.removeAt(idx)
        val ratio = 0.36f + rnd.nextFloat() * 0.28f
        val splitVertically = if (c.w > c.h * 1.2f) true else if (c.h > c.w * 1.2f) false else rnd.nextBoolean()
        if (splitVertically) {
            val x = c.l + c.w * ratio
            cells += CRect(c.l, c.t, x, c.b); cells += CRect(x, c.t, c.r, c.b)
        } else {
            val y = c.t + c.h * ratio
            cells += CRect(c.l, c.t, c.r, y); cells += CRect(c.l, y, c.r, c.b)
        }
    }
    cells.shuffle(rnd)
    val gap = 0.022f
    val rects = cells.map { it.inset(gap) }
    val biggest = rects.indices.maxBy { rects[it].w * rects[it].h }
    val motifs = mutableListOf(Motif.OUTLINE, Motif.RINGS, Motif.STRIPES, Motif.DOTS, Motif.QUARTER, Motif.DISC).also { it.shuffle(rnd) }
    val bgOrder = mutableListOf(1, 2, 3, 4).also { it.shuffle(rnd) }
    var mi = 0
    return rects.mapIndexed { i, rect ->
        val motif = when {
            reserveText && i == biggest -> Motif.TEXT
            i == biggest -> Motif.SHAPE
            else -> motifs[mi++ % motifs.size]
        }
        val bg = bgOrder[i % bgOrder.size]
        val fgChoices = (listOf(0, 1, 2, 3, 4) - bg)
        val fg = fgChoices[rnd.nextInt(fgChoices.size)]
        val pad = minOf(rect.w, rect.h) * 0.14f
        val inner = rect.inset(pad)
        val s = minOf(inner.w, inner.h)
        val round = CRect(inner.cx - s / 2, inner.cy - s / 2, inner.cx + s / 2, inner.cy + s / 2)
        val vertical = rect.h > rect.w
        val corner = rnd.nextInt(4)
        val ink = when (motif) {
            Motif.STRIPES, Motif.DOTS, Motif.TEXT -> inner
            Motif.QUARTER -> {
                val q = s
                when (corner) {
                    0 -> CRect(inner.l, inner.t, inner.l + q, inner.t + q)
                    1 -> CRect(inner.r - q, inner.t, inner.r, inner.t + q)
                    2 -> CRect(inner.r - q, inner.b - q, inner.r, inner.b)
                    else -> CRect(inner.l, inner.b - q, inner.l + q, inner.b)
                }
            }
            else -> round
        }
        CoverCell(
            rect = rect, ink = ink, motif = motif, bgIdx = bg, fgIdx = fg,
            shapeIdx = rnd.nextInt(COVER_SHAPE_COUNT), rotation = rnd.nextInt(8) * 45f + rnd.nextInt(3) * 7f,
            count = 3 + rnd.nextInt(3), corner = corner, vertical = vertical,
            spinDir = if (rnd.nextBoolean()) 1f else -1f,
        )
    }
}
