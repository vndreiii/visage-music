package com.music.vivi.compose.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import coil3.compose.AsyncImage
import com.music.vivi.compose.theme.contrastRatio
import com.music.vivi.compose.theme.ensureContrastingOn
import com.music.vivi.compose.ui.library.tintFor
import kotlin.math.roundToInt

/** Unit-circle Material-expressive shapes, centred on the origin (so scaling by r keeps them within radius r). */
private val unitShapes: List<Path> by lazy {
    fun RoundedPolygon.path(): Path {
        val p = Path()
        var first = true
        for (c in cubics) {
            if (first) { p.moveTo(c.anchor0X, c.anchor0Y); first = false }
            p.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        }
        p.close()
        return p
    }
    listOf(
        RoundedPolygon.star(9, 1f, 0.82f, CornerRounding(0.5f)),                 // cookie
        RoundedPolygon.star(8, 1f, 0.72f, CornerRounding(0.35f)),                // flower
        RoundedPolygon.star(4, 1f, 0.55f, CornerRounding(0.6f)),                 // clover
        RoundedPolygon.star(12, 1f, 0.86f, CornerRounding(0.12f)),               // burst
        RoundedPolygon(5, 1f, rounding = CornerRounding(0.25f)),                 // pentagon
        RoundedPolygon.star(6, 1f, 0.8f, CornerRounding(0.4f)),                  // sunny-ish
        RoundedPolygon(4, 1f, rounding = CornerRounding(0.35f)),                 // squircle
        RoundedPolygon.circle(),
    ).map { it.path() }
}

private fun hsvOf(c: Color): FloatArray {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, if (mx == 0f) 0f else d / mx, mx)
}

/**
 * Flat 5-colour tonal palette from [accent]: [0] deep frame colour, [1] dark tone, [2] vivid tone,
 * [3] pale tone, [4] analogous (hue +35) tone. Solid colours only.
 */
internal fun coverPalette(accent: Color): List<Color> {
    val (h, s0, _) = hsvOf(accent).let { Triple(it[0], it[1], it[2]) }
    val s = s0.coerceIn(0.28f, 0.9f)
    return listOf(
        Color.hsv(h, (s * 0.9f).coerceAtMost(0.8f), 0.24f),
        Color.hsv(h, s, 0.56f),
        Color.hsv(h, (s * 0.85f).coerceAtMost(0.85f), 0.95f),
        Color.hsv(h, 0.16f, 0.97f),
        Color.hsv((h + 35f) % 360f, (s * 0.8f).coerceAtMost(0.8f), 0.86f),
    )
}

private fun initialsOf(label: String): String =
    label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it[0].isLetterOrDigit() }.take(2)
        .joinToString("") { it.take(1).uppercase() }

/**
 * Generated cover (variant a: full-bleed square/any-aspect cover). Flat colours only, one motif per grid cell,
 * motifs can never overlap (see [generateCoverLayout]). [label], if given, becomes a big initial in its own cell.
 * Static unless [animated] (shapes rotate / breathe inside their own cell, still no overlap).
 */
@Composable
fun GeneratedCover(
    seed: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    animated: Boolean = false,
    accent: Color? = null,
) {
    val accent = accent ?: remember(seed) { tintFor(seed, 0.7f) }
    val initials = remember(label) { label?.let(::initialsOf).orEmpty() }
    BoxWithConstraints(modifier.clip(androidx.compose.ui.graphics.RectangleShape)) {
        val aspect = if (constraints.hasBoundedWidth && constraints.hasBoundedHeight && constraints.maxHeight > 0)
            ((constraints.maxWidth.toFloat() / constraints.maxHeight) * 100).roundToInt() / 100f else 1f
        val cells = remember(seed, aspect, initials.isNotEmpty()) { generateCoverLayout(seed, aspect, initials.isNotEmpty()) }
        val palette = remember(accent) { coverPalette(accent) }
        var spin = 0f
        var breathe = 1f
        if (animated) {
            val t = rememberInfiniteTransition(label = "shapeArt")
            val sp by t.animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "spin")
            val br by t.animateFloat(0.93f, 1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
            spin = sp; breathe = br
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(palette[0])
            val u = size.height
            cells.forEach { drawCell(it, palette, u, spin, breathe) }
        }
        if (initials.isNotEmpty()) {
            cells.firstOrNull { it.motif == Motif.TEXT }?.let { c ->
                val d = LocalDensity.current
                val hPx = constraints.maxHeight.toFloat()
                val wPx = c.ink.w * hPx
                val hh = c.ink.h * hPx
                val bg = palette[c.bgIdx]
                Box(
                    Modifier.offset { IntOffset((c.ink.l * hPx).roundToInt(), (c.ink.t * hPx).roundToInt()) }
                        .size(with(d) { wPx.toDp() }, with(d) { hh.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initials, color = ensureContrastingOn(bg, palette[c.fgIdx], 3f), fontWeight = FontWeight.Black,
                        fontSize = (minOf(wPx, hh) * (if (initials.length > 1) 0.5f else 0.72f) / d.density).sp,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCell(c: CoverCell, pal: List<Color>, u: Float, spin: Float, breathe: Float) {
    val r = c.rect
    val bg = pal[c.bgIdx]
    drawRoundRect(bg, Offset(r.l * u, r.t * u), Size(r.w * u, r.h * u), CornerRadius(0.05f * u))
    var fg = pal[c.fgIdx]
    if (contrastRatio(bg, fg) < 1.7f) fg = ensureContrastingOn(bg, fg, 1.7f)
    val k = c.ink
    val cx = k.cx * u; val cy = k.cy * u; val rad = k.w * u / 2f
    when (c.motif) {
        Motif.SHAPE, Motif.OUTLINE -> {
            val sc = rad * (if (c.motif == Motif.OUTLINE) 0.9f else 1f) * breathe
            withTransform({ translate(cx, cy); rotate(c.rotation + spin * c.spinDir, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
                if (c.motif == Motif.SHAPE) drawPath(unitShapes[c.shapeIdx], fg)
                else drawPath(unitShapes[c.shapeIdx], fg, style = Stroke(width = 0.16f, join = StrokeJoin.Round))
            }
        }
        Motif.DISC -> drawCircle(fg, rad * breathe, Offset(cx, cy))
        Motif.RINGS -> {
            val sw = rad * 0.16f
            drawCircle(fg, (rad - sw / 2) * breathe, Offset(cx, cy), style = Stroke(sw))
            drawCircle(fg, rad * 0.58f * breathe, Offset(cx, cy), style = Stroke(sw))
            drawCircle(fg, rad * 0.26f * breathe, Offset(cx, cy))
        }
        Motif.STRIPES -> {
            val n = c.count
            val len = if (c.vertical) k.h * u else k.w * u
            val span = if (c.vertical) k.w * u else k.h * u
            val th = span / (2 * n - 1)
            for (i in 0 until n) {
                val l = if (i % 2 == 0) len else len * 0.68f
                val off = i * 2 * th
                if (c.vertical) drawRoundRect(fg, Offset(k.l * u + off, k.t * u), Size(th, l), CornerRadius(th / 2))
                else drawRoundRect(fg, Offset(k.l * u, k.t * u + off), Size(l, th), CornerRadius(th / 2))
            }
        }
        Motif.DOTS -> {
            val cols = c.count + 1
            val pitch = k.w * u / cols
            val rows = maxOf(2, (k.h * u / pitch).toInt())
            val gw = pitch * cols; val gh = pitch * rows
            val ox = k.l * u + (k.w * u - gw) / 2; val oy = k.t * u + (k.h * u - gh) / 2
            for (i in 0 until cols) for (j in 0 until rows)
                drawCircle(fg, pitch * 0.3f, Offset(ox + pitch * (i + 0.5f), oy + pitch * (j + 0.5f)))
        }
        Motif.QUARTER -> {
            val q = k.w * u
            val (px, py, start) = when (c.corner) {
                0 -> Triple(k.l * u, k.t * u, 0f)
                1 -> Triple(k.r * u, k.t * u, 90f)
                2 -> Triple(k.r * u, k.b * u, 180f)
                else -> Triple(k.l * u, k.b * u, 270f)
            }
            drawArc(fg, start, 90f, true, Offset(px - q, py - q), Size(2 * q, 2 * q))
        }
        Motif.TEXT -> Unit
    }
}

/**
 * One-liner cover: loads [model] (URL string, File, ...) and fades it in; when [model] is null/blank or
 * loading fails, fades in a [GeneratedCover] seeded by [seed] so the same item always looks the same.
 */
@Composable
fun CoverImage(
    model: Any?,
    seed: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    label: String? = null,
    animated: Boolean = false,
) {
    val usable = model != null && (model as? String)?.isBlank() != true
    var failed by remember(model) { mutableStateOf(false) }
    var loaded by remember(model) { mutableStateOf(false) }
    val imgAlpha by animateFloatAsState(if (loaded) 1f else 0f, tween(350), label = "coverImgFade")
    val artAlpha by animateFloatAsState(if (!usable || failed) 1f else 0f, tween(350), label = "coverArtFade")
    Box(modifier) {
        if (!usable || failed) {
            GeneratedCover(seed, Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha }, label, animated)
        }
        if (usable && !failed) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imgAlpha },
            )
        }
    }
}
