package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.lyricsync.LinePhase
import com.music.lyricsync.LyricsLine
import com.music.vivi.compose.prefs.LyricsTextAlign
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** One measured sung unit (word / syllable, or the whole wrapped line when [LineLayout.wholeLine]). */
internal class UnitBox(
    val layout: TextLayoutResult,
    val startMs: Long,
    val endMs: Long,
    var x: Float,
    var y: Float,
    val w: Float,
    val h: Float,
) {
    fun progressAt(posMs: Float): Float =
        if (endMs <= startMs) (if (posMs >= startMs) 1f else 0f) else ((posMs - startMs) / (endMs - startMs)).coerceIn(0f, 1f)
}

internal class LineLayout(
    val units: List<UnitBox>,
    val width: Int,
    val height: Int,
    val wholeLine: Boolean,
    val translation: TextLayoutResult?,
    val translationOffset: Offset,
)

private class LayoutHolder {
    var key: Long = Long.MIN_VALUE
    var layout: LineLayout? = null
}

internal fun buildLineLayout(
    measurer: TextMeasurer,
    line: LyricsLine,
    cfg: LyricsConfig,
    sizeScale: Float,
    isBackground: Boolean,
    maxWidth: Int,
    density: Density,
): LineLayout {
    val fs = cfg.fontSizeSp * sizeScale * (if (isBackground) 0.7f else 1f)
    val align = when (cfg.align) {
        LyricsTextAlign.LEFT -> TextAlign.Start
        LyricsTextAlign.CENTER -> TextAlign.Center
        LyricsTextAlign.RIGHT -> TextAlign.End
    }
    val style = TextStyle(
        fontSize = fs.sp,
        lineHeight = (fs * 1.2f).sp,
        fontWeight = cfg.weight.weight,
        fontFamily = cfg.font.family(),
        fontStyle = if (isBackground) FontStyle.Italic else FontStyle.Normal,
        textAlign = align,
    )
    val alignFactor = when (cfg.align) {
        LyricsTextAlign.LEFT -> 0f
        LyricsTextAlign.CENTER -> 0.5f
        LyricsTextAlign.RIGHT -> 1f
    }
    val words = line.words
    val units = ArrayList<UnitBox>()
    var height = 0f
    val whole = !cfg.wordSync || words.isEmpty()

    if (whole) {
        val r = measurer.measure(cfg.textCase.apply(line.text), style, constraints = Constraints(maxWidth = maxWidth))
        val ux = (maxWidth - r.size.width).coerceAtLeast(0) * alignFactor
        units += UnitBox(r, line.timeMs ?: 0L, line.endMs ?: 0L, ux, 0f, r.size.width.toFloat(), r.size.height.toFloat())
        height = r.size.height.toFloat()
    } else {
        val nowrap = Constraints()
        val measured = words.map {
            measurer.measure(cfg.textCase.apply(it.text), style, softWrap = false, maxLines = 1, overflow = TextOverflow.Visible, constraints = nowrap)
        }
        val spaceW = (measurer.measure("n n", style, softWrap = false).size.width - measurer.measure("nn", style, softWrap = false).size.width)
            .toFloat().coerceAtLeast(fs * 0.2f)
        // clusters = syllables glued together; they never wrap apart
        val clusters = ArrayList<MutableList<Int>>()
        var cur = ArrayList<Int>()
        words.forEachIndexed { i, w ->
            cur.add(i)
            if (w.spaceAfter || i == words.lastIndex) { clusters += cur; cur = ArrayList() }
        }
        val rows = ArrayList<MutableList<MutableList<Int>>>()
        var row = ArrayList<MutableList<Int>>()
        var rowW = 0f
        fun cw(c: List<Int>) = c.sumOf { measured[it].size.width }.toFloat()
        for (c in clusters) {
            val w = cw(c)
            if (row.isNotEmpty() && rowW + spaceW + w > maxWidth) { rows += row; row = ArrayList(); rowW = 0f }
            rowW += (if (row.isEmpty()) 0f else spaceW) + w
            row.add(c)
        }
        if (row.isNotEmpty()) rows += row
        val tmp = arrayOfNulls<UnitBox>(words.size)
        var y = 0f
        for (r in rows) {
            val total = r.sumOf { cw(it).toDouble() }.toFloat() + spaceW * (r.size - 1)
            var x = (maxWidth - total).coerceAtLeast(0f) * alignFactor
            val rowH = r.flatten().maxOf { measured[it].size.height }.toFloat()
            for ((ci, c) in r.withIndex()) {
                for (i in c) {
                    val m = measured[i]
                    tmp[i] = UnitBox(m, words[i].startMs, words[i].endMs, x, y, m.size.width.toFloat(), m.size.height.toFloat())
                    x += m.size.width
                }
                if (ci < r.lastIndex) x += spaceW
            }
            y += rowH
        }
        tmp.forEach { units += it!! }
        height = y
    }

    var translation: TextLayoutResult? = null
    var tOff = Offset.Zero
    val tr = line.translation
    if (cfg.showTranslation && !tr.isNullOrBlank() && !isBackground) {
        val ts = style.copy(fontSize = (fs * 0.56f).sp, lineHeight = (fs * 0.7f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        val t = measurer.measure(tr, ts, constraints = Constraints(maxWidth = maxWidth))
        translation = t
        tOff = Offset((maxWidth - t.size.width).coerceAtLeast(0) * alignFactor, height + fs * 0.18f * density.density)
        height = tOff.y + t.size.height
    }
    return LineLayout(units, maxWidth, height.toInt() + 1, whole, translation, tOff)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }

/**
 * One lyric line. Position-dependent drawing reads [clock] only in the draw phase, and only when
 * the line is active, so a ticking clock never recomposes anything.
 */
@Composable
internal fun LyricLineView(
    line: LyricsLine,
    phase: LinePhase,
    distance: Int,
    visible: Boolean,
    plain: Boolean,
    cfg: LyricsConfig,
    accent: Color,
    clock: State<Float>,
    sizeScale: Float = 1f,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spec = cfg.spec
    val strength = cfg.animStrength
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val holder = remember(line, cfg.layoutKey, sizeScale) { LayoutHolder() }

    val active = phase == LinePhase.Active
    val activation = animateFloatAsState(if (active) 1f else 0f, spec.spring<Float>(), label = "lyricActive")
    val lineAlpha = animateFloatAsState(
        targetValue = when {
            !visible -> 0f
            plain -> 0.9f
            active -> 1f
            else -> (cfg.inactiveBrightness * Math.pow(0.66, (distance - 1).coerceAtLeast(0).toDouble()).toFloat()).coerceIn(0.05f, 1f)
        },
        animationSpec = spring(1f, 260f),
        label = "lyricAlpha",
    )
    val blurTarget = if (plain || active || !visible || distance > 5) 0f else {
        cfg.blur * 5f * spec.blurFactor * (distance.coerceIn(1, 3) / 3f) * sizeScale
    }
    val blur = animateFloatAsState(blurTarget, spring(1f, 200f), label = "lyricBlur")
    val slideTarget = if (active || plain || spec.slideIn == 0f) 0f else when {
        cfg.align == LyricsTextAlign.LEFT -> -spec.slideIn
        cfg.align == LyricsTextAlign.RIGHT -> spec.slideIn
        phase == LinePhase.Past -> -spec.slideIn
        else -> spec.slideIn
    }
    val slide = animateFloatAsState(slideTarget, spring(0.9f, 240f), label = "lyricSlide")
    val glowAnim = animateFloatAsState(if (active) 1f else 0f, spring(1f, 160f), label = "lyricGlow")
    val clickMod = if (onClick != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    } else Modifier
    val isBg = line.isBackground

    Layout(
        content = {
            Layout(
                content = {},
                modifier = Modifier
                    .graphicsLayer {
                        val act = activation.value
                        val sc = 1f + (lerp(spec.inactiveScale, spec.activeScale, act) - 1f) * strength
                        scaleX = sc
                        scaleY = sc
                        alpha = lineAlpha.value.coerceIn(0f, 1f)
                        val fontPx = cfg.fontSizeSp * sizeScale * density
                        translationX = slide.value * fontPx * strength
                        transformOrigin = TransformOrigin(
                            when (cfg.align) {
                                LyricsTextAlign.LEFT -> 0.08f
                                LyricsTextAlign.CENTER -> 0.5f
                                LyricsTextAlign.RIGHT -> 0.92f
                            },
                            0.5f,
                        )
                        val br = blur.value
                        renderEffect = if (br > 0.3f) BlurEffect(br, br, TileMode.Decal) else null
                    }
                    .drawBehind {
                        val lay = holder.layout ?: return@drawBehind
                        val pad = GlowPadDp.dp.toPx()
                        translate(pad, pad) {
                            drawLyricLine(
                                lay, cfg, spec, accent, phase,
                                activation.value, glowAnim.value,
                                if (phase == LinePhase.Active) clock.value else 0f,
                                sizeScale, isBg,
                            )
                        }
                    },
            ) { _, c ->
                val pad = GlowPadDp.dp.roundToPx()
                val inner = (c.maxWidth - 2 * pad).coerceAtLeast(10)
                val key = inner.toLong()
                if (holder.layout == null || holder.key != key) {
                    holder.layout = buildLineLayout(measurer, line, cfg, sizeScale, isBg, inner, this)
                    holder.key = key
                }
                val lay = holder.layout!!
                layout(c.maxWidth, lay.height + 2 * pad) {}
            }
        },
        modifier = modifier.then(clickMod),
    ) { measurables, c ->
        val pad = GlowPadDp.dp.roundToPx()
        val p = measurables[0].measure(Constraints(minWidth = c.maxWidth + 2 * pad, maxWidth = c.maxWidth + 2 * pad))
        // The inner node is larger by the glow margin on every side; report the tight size.
        layout(c.maxWidth, p.height - 2 * pad) { p.place(IntOffset(-pad, -pad)) }
    }
}

internal const val GlowPadDp = 28

private val OutlineColor = Color.Black.copy(alpha = 0.5f)

/** Paints one line at the given activation (0..1), glow (0..1) and playback position. */
internal fun DrawScope.drawLyricLine(
    lay: LineLayout,
    cfg: LyricsConfig,
    spec: AnimationSpec,
    accent: Color,
    phase: LinePhase,
    activation: Float,
    glowAnim: Float,
    positionMs: Float,
    sizeScale: Float,
    isBackground: Boolean,
) {
    val fontPx = cfg.fontSizeSp * sizeScale * density * (if (isBackground) 0.7f else 1f)
    val strength = cfg.animStrength
    val a = activation.coerceIn(0f, 1f)
    val sweep = if (spec.sweep == SweepMode.NONE) SweepMode.FADE else spec.sweep
    val strokeW = (fontPx * 0.04f).coerceAtLeast(1.2f)
    val unsungActive = 0.36f
    val unsungAlpha = lerp(1f, unsungActive, a)
    val glowOn = cfg.glow > 0f && spec.glowFactor > 0f
    val glowRadius = fontPx * 0.5f * cfg.glow * spec.glowFactor + 2f
    val glowColorAlpha = (0.5f + 0.5f * cfg.glow).coerceAtMost(1f)
    val pad = glowRadius * 2f + strokeW * 2 + 6f
    val white = Color.White

    lay.units.forEachIndexed { i, u ->
        val p = when (phase) {
            LinePhase.Past -> 1f
            LinePhase.Future -> 0f
            LinePhase.Active -> when {
                spec.sweep == SweepMode.NONE || lay.wholeLine || !cfg.wordSync -> a
                else -> u.progressAt(positionMs)
            }
        }
        val env = if (phase == LinePhase.Active && p > 0f && p < 1f) sin(PI.toFloat() * p) else 0f
        val dy = -spec.wordLift * fontPx * env * strength
        val sc = 1f + spec.wordScale * env * strength
        val glowK = glowAnim * (if (p in 0.001f..0.999f) 1f else 0.55f)
        val shadow = if (glowOn && glowK > 0.01f && p > 0f) {
            Shadow(accent.copy(alpha = glowColorAlpha * glowK), Offset.Zero, glowRadius * (0.6f + 0.4f * glowK))
        } else null

        translate(u.x, u.y + dy) {
            scale(sc, sc, pivot = Offset(u.w / 2f, u.h)) {
                fun unsung() {
                    when (cfg.textStyle) {
                        LyricsTextStyle.HOLLOW -> drawText(
                            u.layout, color = white.copy(alpha = lerp(0.85f, 0.7f, a)),
                            drawStyle = Stroke(strokeW, join = StrokeJoin.Round),
                        )
                        LyricsTextStyle.OUTLINED -> {
                            drawText(u.layout, color = OutlineColor.copy(alpha = OutlineColor.alpha * unsungAlpha), drawStyle = Stroke(strokeW * 2.2f, join = StrokeJoin.Round))
                            drawText(u.layout, color = white.copy(alpha = unsungAlpha))
                        }
                        else -> drawText(u.layout, color = white.copy(alpha = unsungAlpha))
                    }
                }
                fun sung(alphaMul: Float) {
                    when (cfg.textStyle) {
                        LyricsTextStyle.HOLLOW -> {
                            drawText(u.layout, color = white.copy(alpha = 0.95f * alphaMul), shadow = shadow)
                            drawText(u.layout, color = white.copy(alpha = alphaMul), drawStyle = Stroke(strokeW, join = StrokeJoin.Round))
                        }
                        LyricsTextStyle.OUTLINED -> {
                            drawText(u.layout, color = OutlineColor.copy(alpha = OutlineColor.alpha * alphaMul), drawStyle = Stroke(strokeW * 2.2f, join = StrokeJoin.Round))
                            drawText(u.layout, color = white.copy(alpha = alphaMul), shadow = shadow)
                        }
                        LyricsTextStyle.GRADIENT -> {
                            val grad = Brush.horizontalGradient(
                                colors = listOf(lerpColor(accent, white, 0.35f), white),
                                startX = -u.x, endX = lay.width.toFloat() - u.x,
                            )
                            drawText(u.layout, brush = grad, alpha = alphaMul, shadow = shadow)
                        }
                        else -> drawText(u.layout, color = white.copy(alpha = alphaMul), shadow = shadow)
                    }
                }

                when {
                    p <= 0.001f -> unsung()
                    p >= 0.999f -> sung(1f)
                    sweep == SweepMode.FADE -> {
                        unsung()
                        sung(smooth(p))
                    }
                    else -> {
                        unsung()
                        val feather = (spec.feather * fontPx).coerceAtLeast(2f)
                        val bounds = Rect(-pad, -pad, u.w + pad, u.h + pad)
                        val ex = -feather + p * (u.w + 2f * feather)
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(bounds, Paint())
                            sung(1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black, 1f to Color.Transparent,
                                    startX = ex - feather, endX = ex + feather,
                                ),
                                topLeft = bounds.topLeft,
                                size = Size(bounds.width, bounds.height),
                                blendMode = BlendMode.DstIn,
                            )
                            canvas.restore()
                        }
                    }
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION") i
    }

    lay.translation?.let { t ->
        translate(lay.translationOffset.x, lay.translationOffset.y) {
            drawText(t, color = white.copy(alpha = lerp(0.6f, 0.85f, a)))
        }
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)
