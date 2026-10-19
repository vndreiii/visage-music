package com.music.vivi.compose.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import com.music.vivi.compose.theme.ensureContrastingOn
import com.music.vivi.compose.ui.motion.VisageMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class MoodCardSize(val height: Dp, val titleSize: Int) {
    Hero(244.dp, 34), Large(196.dp, 26), Medium(168.dp, 22),
}

/** Flat tonal palette for a mood card: solid colours only. */
internal class MoodPalette(
    val bg: Color, val ink: List<Color>, val tile: Color, val plate: Color, val onPlate: Color,
)

private fun hsvOf(c: Color): Pair<Float, Float> {
    val mx = maxOf(c.red, c.green, c.blue); val mn = minOf(c.red, c.green, c.blue); val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == c.red -> 60f * (((c.green - c.blue) / d) % 6f)
        mx == c.green -> 60f * ((c.blue - c.red) / d + 2f)
        else -> 60f * ((c.red - c.green) / d + 4f)
    }
    return (h + 360f) % 360f to (if (mx == 0f) 0f else d / mx)
}

/** variant 0/1 = vivid background, 2 = deep, 3 = pale. */
internal fun moodPalette(accent: Color, variant: Int): MoodPalette {
    val (h, s0) = hsvOf(accent)
    val s = s0.coerceIn(0.4f, 0.9f)
    val h2 = (h + 38f) % 360f
    return when (variant) {
        2 -> {
            val plate = Color.hsv(h, 0.12f, 0.97f)
            MoodPalette(
                bg = Color.hsv(h, s * 0.95f, 0.30f),
                ink = listOf(Color.hsv(h, s * 0.8f, 0.95f), Color.hsv(h, 0.15f, 0.97f), Color.hsv(h2, s * 0.7f, 0.92f)),
                tile = Color.hsv(h, s, 0.40f), plate = plate, onPlate = ensureContrastingOn(plate, Color.hsv(h, s, 0.18f), 7f),
            )
        }
        3 -> {
            val plate = Color.hsv(h, s, 0.22f)
            MoodPalette(
                bg = Color.hsv(h, 0.17f, 0.97f),
                ink = listOf(Color.hsv(h, s, 0.88f), Color.hsv(h, s, 0.36f), Color.hsv(h2, 0.7f, 0.96f)),
                tile = Color.hsv(h, 0.30f, 0.92f), plate = plate, onPlate = ensureContrastingOn(plate, Color.hsv(h, 0.1f, 0.98f), 7f),
            )
        }
        else -> {
            val plate = Color.hsv(h, s, 0.20f)
            MoodPalette(
                bg = Color.hsv(h, s, 0.88f),
                ink = listOf(Color.hsv(h, 0.12f, 0.98f), Color.hsv(h, s, 0.34f), Color.hsv(h2, 0.5f, 1f)),
                tile = Color.hsv(h, s * 0.95f, 0.78f), plate = plate, onPlate = ensureContrastingOn(plate, Color.hsv(h, 0.1f, 0.98f), 7f),
            )
        }
    }
}

private val moodShapes: List<Path> by lazy {
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
        RoundedPolygon.star(9, 1f, 0.82f, CornerRounding(0.5f)),        // 0 cookie
        RoundedPolygon.star(8, 1f, 0.72f, CornerRounding(0.35f)),       // 1 flower
        RoundedPolygon.star(4, 1f, 0.55f, CornerRounding(0.6f)),        // 2 clover
        RoundedPolygon.star(12, 1f, 0.86f, CornerRounding(0.12f)),      // 3 burst
        RoundedPolygon(5, 1f, rounding = CornerRounding(0.25f)),        // 4 pentagon
        RoundedPolygon.star(6, 1f, 0.8f, CornerRounding(0.4f)),         // 5 sunny
        RoundedPolygon(4, 1f, rounding = CornerRounding(0.35f)),        // 6 squircle
        RoundedPolygon.circle(),                                        // 7 circle
        RoundedPolygon.star(5, 1f, 0.42f, CornerRounding(0.04f)),       // 8 sharp star
        RoundedPolygon.star(8, 1f, 0.5f),                               // 9 sharp burst
        RoundedPolygon(4, 1f, rounding = CornerRounding(0.08f)),        // 10 diamond-ish
        RoundedPolygon(3, 1f, rounding = CornerRounding(0.4f)),         // 11 droplet / leaf
        RoundedPolygon(6, 1f, rounding = CornerRounding(0.2f)),         // 12 hex
        RoundedPolygon.star(6, 1f, 0.38f, CornerRounding(0.1f)),        // 13 snowflake-ish
    ).map { it.path() }
}

/**
 * Big expressive card for a mood / genre. The whole background is a generated full-bleed shape composition
 * (see [generateMoodLayout]) in flat tonal colours derived from [accentColor]; the title sits bottom-left on a
 * solid rounded plate so it is always readable. Static at rest; hover/focus lifts the card and gently rotates
 * and scales the shapes inside their own cells; press morphs the corners.
 */
@Composable
fun MoodCard(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: MoodCardSize = MoodCardSize.Medium,
    subtitle: String = "",
    /** >= 0 plays a fade+rise entrance after this delay; negative shows the card immediately. */
    entranceDelayMs: Int = -1,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || focused
    val hover = animateFloatAsState(if (active) 1f else 0f, VisageMotion.spatialDefault(), label = "moodHover")
    val lift by animateFloatAsState(if (pressed) 0.97f else if (active) 1.025f else 1f, VisageMotion.spatialFast(), label = "moodLift")
    val corner by animateDpAsState(if (pressed) 44.dp else 28.dp, VisageMotion.spatialFast(), label = "moodCorner")
    val entrance = remember { Animatable(if (entranceDelayMs >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entranceDelayMs >= 0) { delay(entranceDelayMs.toLong()); entrance.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
    }
    val theme = remember(title) { MoodTheme.forTitle(title) }
    val seedHash = remember(title) { stableSeed(title) }
    val palette = remember(accentColor, seedHash) { moodPalette(accentColor, Math.floorMod(seedHash, 4)) }

    BoxWithConstraints(
        modifier.height(size.height)
            .graphicsLayer {
                val p = entrance.value
                alpha = p
                translationY = (1f - p) * 28.dp.toPx() - (lift - 1f) * 80f
                scaleX = lift; scaleY = lift
            }
            .clip(RoundedCornerShape(corner))
            .background(palette.bg)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        val aspect = if (constraints.hasBoundedWidth && constraints.maxHeight > 0)
            ((constraints.maxWidth.toFloat() / constraints.maxHeight) * 20).roundToInt() / 20f else 1.2f
        val cells = remember(title, aspect, theme) { generateMoodLayout(title, aspect, theme) }
        Canvas(Modifier.fillMaxSize()) {
            val u = this.size.height
            val h = hover.value
            cells.forEach { drawMoodCell(it, palette, u, h) }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                Modifier.weight(1f, fill = false).clip(RoundedCornerShape(20.dp)).background(palette.plate)
                    .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 12.dp),
            ) {
                Text(
                    title,
                    style = TextStyle(
                        fontSize = size.titleSize.sp, lineHeight = (size.titleSize * 1.08f).sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp,
                    ),
                    color = palette.onPlate, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = ensureContrastingOn(palette.ink[0], palette.plate, 4.5f),
                        modifier = Modifier.clip(CircleShape).background(palette.ink[0]).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(if (size == MoodCardSize.Hero) 56.dp else 46.dp).clip(CircleShape).background(palette.plate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null,
                    tint = palette.onPlate,
                    modifier = Modifier.size(24.dp).rotate(-45f * hover.value),
                )
            }
        }
    }
}

private fun DrawScope.drawMoodCell(c: MoodCell, pal: MoodPalette, u: Float, h: Float) {
    val r = c.rect
    if (c.tile) drawRoundRect(pal.tile, Offset(r.l * u, r.t * u), Size(r.w * u, r.h * u), CornerRadius(0.07f * u))
    val fg = pal.ink[c.colorIdx]
    val fg2 = pal.ink[c.color2Idx]
    val k = c.ink
    val hw = k.w * u / 2f; val hh = k.h * u / 2f; val rad = minOf(hw, hh)
    val rotates = c.motif == MoodMotif.SHAPE || c.motif == MoodMotif.SUN || c.motif == MoodMotif.RINGS || c.motif == MoodMotif.DISC
    val rot = (if (c.motif == MoodMotif.SHAPE) c.rotation else 0f) + if (rotates) h * 22f * c.spinDir else 0f
    val sc = 1f + 0.07f * h
    withTransform({ translate(k.cx * u, k.cy * u); rotate(rot, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
        when (c.motif) {
            MoodMotif.SHAPE -> withTransform({ scale(rad, rad, Offset.Zero) }) { drawPath(moodShapes[c.shapeIdx], fg) }
            MoodMotif.DISC -> drawCircle(fg, rad, Offset.Zero)
            MoodMotif.RINGS -> {
                val sw = rad * 0.16f
                drawCircle(fg, rad - sw / 2, Offset.Zero, style = Stroke(sw))
                drawCircle(fg2, rad * 0.58f, Offset.Zero, style = Stroke(sw))
                drawCircle(fg, rad * 0.24f, Offset.Zero)
            }
            MoodMotif.BLOCK -> drawRoundRect(fg, Offset(-rad, -rad), Size(2 * rad, 2 * rad), CornerRadius(rad * 0.28f))
            MoodMotif.PILL -> {
                val vertical = hh >= hw
                val w = if (vertical) hw else hw * 2f; val hgt = if (vertical) hh * 2f else hh
                drawRoundRect(fg, Offset(-w / 2f, -hgt / 2f), Size(w, hgt), CornerRadius(minOf(w, hgt) / 2f))
            }
            MoodMotif.STRIPES -> {
                val n = c.count
                val th = (if (c.vertical) 2 * hw else 2 * hh) / (2 * n - 1)
                for (i in 0 until n) {
                    val col = if (i % 2 == 0) fg else fg2
                    val len = (if (c.vertical) 2 * hh else 2 * hw) * (if (i % 2 == 0) 1f else 0.7f)
                    if (c.vertical) drawRoundRect(col, Offset(-hw + i * 2 * th, -len / 2), Size(th, len), CornerRadius(th / 2))
                    else drawRoundRect(col, Offset(-len / 2, -hh + i * 2 * th), Size(len, th), CornerRadius(th / 2))
                }
            }
            MoodMotif.DIAG -> clipRect(-hw, -hh, hw, hh) {
                val n = c.count + 2
                val pitch = (hw + hh) * 2f / n
                val sw = pitch * 0.5f
                for (i in 0..n) {
                    val x0 = -hw - hh + i * pitch
                    drawLine(if (i % 2 == 0) fg else fg2, Offset(x0, hh), Offset(x0 + 2 * hh, -hh), sw)
                }
            }
            MoodMotif.BARS -> {
                val n = c.count + 3
                val bw = 2 * hw / (2 * n - 1)
                for (i in 0 until n) {
                    val f = 0.35f + 0.65f * (0.5f + 0.5f * sin(c.rotation + i * 1.9f + c.count))
                    val bh = 2 * hh * f
                    drawRoundRect(if (i % 2 == 0) fg else fg2, Offset(-hw + i * 2 * bw, hh - bh), Size(bw, bh), CornerRadius(bw / 2))
                }
            }
            MoodMotif.DOTS -> {
                val cols = c.count + 1
                val pitch = 2 * hw / cols
                val rows = maxOf(2, (2 * hh / pitch).toInt())
                val ox = -pitch * cols / 2f; val oy = -pitch * rows / 2f
                for (i in 0 until cols) for (j in 0 until rows) drawCircle(fg, pitch * 0.3f, Offset(ox + pitch * (i + 0.5f), oy + pitch * (j + 0.5f)))
            }
            MoodMotif.GRID -> {
                val cols = c.count + 1
                val pitch = 2 * hw / cols
                val rows = maxOf(2, (2 * hh / pitch).toInt())
                val ox = -pitch * cols / 2f; val oy = -pitch * rows / 2f
                for (i in 0 until cols) for (j in 0 until rows) {
                    val v = (i * 7 + j * 13 + c.corner * 5 + c.count) % 4
                    if (v == 0) continue
                    drawRect(if (v == 1) fg2 else fg, Offset(ox + pitch * i + pitch * 0.07f, oy + pitch * j + pitch * 0.07f), Size(pitch * 0.86f, pitch * 0.86f))
                }
            }
            MoodMotif.QUARTER -> {
                val (px, py, start) = cornerOf(c.corner, rad)
                drawArc(fg, start, 90f, true, Offset(px - 2 * rad, py - 2 * rad), Size(4 * rad, 4 * rad))
            }
            MoodMotif.HALF -> drawArc(fg, 180f + c.corner * 90f, 180f, true, Offset(-rad, -rad), Size(2 * rad, 2 * rad))
            MoodMotif.ARCS -> {
                val (px, py, start) = cornerOf(c.corner, rad)
                val sw = rad * 0.26f
                for (i in 0 until 3) {
                    val q = 2 * rad * (1f - i * 0.31f) - sw / 2
                    drawArc(if (i % 2 == 0) fg else fg2, start, 90f, false, Offset(px - q, py - q), Size(2 * q, 2 * q), style = Stroke(sw))
                }
            }
            MoodMotif.ZIGZAG -> {
                val rows = if (hh > hw * 0.6f) 2 else 1
                val teeth = c.count + 2
                val sw = minOf(hw, hh) * 0.28f
                for (row in 0 until rows) {
                    val cy = if (rows == 1) 0f else (row - 0.5f) * hh
                    val amp = (if (rows == 1) hh else hh * 0.4f) - sw / 2
                    val path = Path()
                    for (i in 0..teeth) {
                        val x = -hw + sw / 2 + (2 * hw - sw) * i / teeth
                        val y = cy + if (i % 2 == 0) amp else -amp
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, if (row % 2 == 0) fg else fg2, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            MoodMotif.SCALLOP -> {
                val cols = c.count
                val d = 2 * hw / cols
                val rows = maxOf(2, (2 * hh / (d * 0.5f)).toInt())
                clipRect(-hw, -hh, hw, hh) {
                    for (j in 0 until rows) for (i in 0..cols) {
                        val x = -hw + d * i + if (j % 2 == 1) d / 2f else 0f
                        drawArc(if (j % 2 == 0) fg else fg2, 180f, 180f, true, Offset(x - d / 2f, -hh + j * d * 0.5f - d / 2f + d * 0.5f), Size(d, d))
                    }
                }
            }
            MoodMotif.SUN -> {
                drawCircle(fg, rad * 0.42f, Offset.Zero)
                val sw = rad * 0.13f
                for (i in 0 until 10) {
                    val a = i * 2 * PI.toFloat() / 10
                    drawLine(fg2, Offset(cos(a) * rad * 0.62f, sin(a) * rad * 0.62f), Offset(cos(a) * (rad - sw / 2), sin(a) * (rad - sw / 2)), sw, StrokeCap.Round)
                }
            }
        }
    }
}

/** Corner point (local coords, relative to a square ink of half-size [rad]) and the arc start angle for it. */
private fun cornerOf(corner: Int, rad: Float): Triple<Float, Float, Float> = when (corner) {
    0 -> Triple(-rad, -rad, 0f)
    1 -> Triple(rad, -rad, 90f)
    2 -> Triple(rad, rad, 180f)
    else -> Triple(-rad, rad, 270f)
}
