package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import com.music.lyricsync.LyricsRow
import com.music.vivi.compose.prefs.LyricsTextAlign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal const val GapRowHeightDp = 64

/** Frame time in ms while [running]; idle (no frame callbacks) otherwise. */
@Composable
internal fun rememberFrameMillis(running: Boolean): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (running) while (true) withFrameNanos { t.longValue = it / 1_000_000L }
    }
    return t
}

private val shapeMorph: Morph by lazy {
    val circle = RoundedPolygon.circle(numVertices = 12).normalized()
    val cookie = RoundedPolygon.star(9, 1f, 0.8f, CornerRounding(0.5f)).normalized()
    Morph(circle, cookie)
}

/**
 * Instrumental-gap indicator. Position-dependent parts read [clock] in the draw phase; the frame
 * ticker only runs while the gap is active.
 */
@Composable
internal fun LyricsGapRow(
    gap: LyricsRow.Gap,
    active: Boolean,
    style: LyricsGapStyle,
    align: LyricsTextAlign,
    accent: Color,
    clock: State<Float>,
    sizeScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val act = animateFloatAsState(if (active) 1f else 0f, spring(0.7f, 260f), label = "gapActive")
    val running = active || act.value > 0.01f
    val frame = rememberFrameMillis(running)
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val path = remember { Path() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height((GapRowHeightDp * sizeScale).dp)
            .graphicsLayer { alpha = act.value.coerceIn(0f, 1f) },
    ) {
        val a = act.value
        if (a <= 0.003f) return@Canvas
        val s = size.height / (GapRowHeightDp * density) // compensates sizeScale
        val unit = 1f * density * s
        val cx = when (align) {
            LyricsTextAlign.LEFT -> 36f * unit
            LyricsTextAlign.CENTER -> size.width / 2f
            LyricsTextAlign.RIGHT -> size.width - 36f * unit
        }
        val cy = size.height / 2f
        val t = frame.value / 1000f
        val p = gap.progressAt(clock.value)
        val white = Color.White
        val pop = a.coerceIn(0f, 1.2f)
        when (style) {
            LyricsGapStyle.NONE -> Unit
            LyricsGapStyle.BOUNCING_DOTS -> {
                for (i in 0..2) {
                    val ph = ((t * 1.1f - i * 0.14f) % 1f + 1f) % 1f
                    val bounce = (sin(ph * PI.toFloat()).coerceAtLeast(0f)).pow(1.6f)
                    drawCircle(
                        white.copy(alpha = 0.9f),
                        radius = 5.5f * unit * pop,
                        center = Offset(cx + (i - 1) * 20f * unit, cy + 8f * unit - bounce * 18f * unit),
                    )
                }
            }
            LyricsGapStyle.FILL_DOTS -> {
                val breathe = 1f + 0.08f * sin(t * 2.6f)
                for (i in 0..2) {
                    val f = (p * 3f - i).coerceIn(0f, 1f)
                    val pulse = if (f in 0.01f..0.99f) 1f + 0.35f * sin(f * PI.toFloat()) else 1f
                    drawCircle(
                        white.copy(alpha = 0.22f + 0.78f * f),
                        radius = (5f + 2f * f) * unit * pop * breathe * pulse,
                        center = Offset(cx + (i - 1) * 22f * unit, cy),
                    )
                }
            }
            LyricsGapStyle.RING -> {
                val r = 15f * unit * pop * (1f + 0.04f * sin(t * 2.2f))
                val sw = 3.5f * unit
                drawCircle(white.copy(alpha = 0.2f), radius = r, center = Offset(cx, cy), style = Stroke(sw))
                drawArc(
                    color = white.copy(alpha = 0.95f), startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                    topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            LyricsGapStyle.BAR -> {
                val w = 150f * unit * pop
                val h = 4f * unit
                val x0 = when (align) {
                    LyricsTextAlign.LEFT -> 12f * unit
                    LyricsTextAlign.CENTER -> cx - w / 2f
                    LyricsTextAlign.RIGHT -> size.width - 12f * unit - w
                }
                drawRoundRect(white.copy(alpha = 0.2f), Offset(x0, cy - h / 2), Size(w, h), CornerRadius(h / 2))
                val fw = max(h, w * p)
                drawRoundRect(white.copy(alpha = 0.95f), Offset(x0, cy - h / 2), Size(fw, h), CornerRadius(h / 2))
                // shimmer on the leading edge
                drawCircle(accent.copy(alpha = 0.5f + 0.3f * sin(t * 4f)), radius = h * 2f, center = Offset(x0 + fw, cy))
            }
            LyricsGapStyle.EQUALIZER -> {
                val n = 5
                val bw = 5f * unit
                val gapW = 5f * unit
                val total = n * bw + (n - 1) * gapW
                val x0 = cx - total / 2f
                for (i in 0 until n) {
                    val v = 0.5f + 0.5f * sin(t * (3.2f + i * 0.9f) + i * 1.7f)
                    val h = (6f + 28f * v) * unit * pop
                    val lit = (p * n - i).coerceIn(0f, 1f)
                    drawRoundRect(
                        white.copy(alpha = 0.3f + 0.7f * lit),
                        Offset(x0 + i * (bw + gapW), cy - h / 2), Size(bw, h), CornerRadius(bw / 2),
                    )
                }
            }
            LyricsGapStyle.MORPH_SHAPE -> {
                val breathe = 0.5f + 0.5f * sin(t * 2.0f)
                val size0 = 40f * unit * pop * (1f + 0.07f * sin(t * 2.0f + 1f))
                path.rewind()
                val cubics = shapeMorph.asCubics(breathe)
                var first = true
                for (c in cubics) {
                    if (first) { path.moveTo(c.anchor0X, c.anchor0Y); first = false }
                    path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
                }
                path.close()
                translate(cx - size0 / 2f, cy - size0 / 2f) {
                    rotate(t * 24f, pivot = Offset(size0 / 2f, size0 / 2f)) {
                        scale(size0, size0, pivot = Offset.Zero) {
                            drawPath(path, white.copy(alpha = 0.22f), style = Fill)
                            clipRect(top = 1f - p, bottom = 1f) {
                                drawPath(path, white.copy(alpha = 0.95f), style = Fill)
                            }
                        }
                    }
                }
            }
            LyricsGapStyle.COUNTDOWN -> {
                val remainingMs = (gap.endMs - clock.value).coerceAtLeast(0f)
                val secs = ceil(remainingMs / 1000f).toInt().coerceAtLeast(1)
                val frac = (remainingMs / 1000f) % 1f
                val layout = measurer.measure(
                    secs.toString(),
                    TextStyle(fontSize = (30f * s).sp, fontWeight = FontWeight.Bold, color = white),
                )
                val pulse = 1f + 0.16f * (1f - frac).pow(3)
                scale(pulse * pop, pulse * pop, pivot = Offset(cx, cy)) {
                    translate(cx - layout.size.width / 2f, cy - layout.size.height / 2f) {
                        drawText(layout, color = white.copy(alpha = 0.95f))
                    }
                }
                val r = 24f * unit
                drawArc(
                    color = white.copy(alpha = 0.35f), startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                    topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r), style = Stroke(2.5f * unit, cap = StrokeCap.Round),
                )
            }
        }
    }
}
