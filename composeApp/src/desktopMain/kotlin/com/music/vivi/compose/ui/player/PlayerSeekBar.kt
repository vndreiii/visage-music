package com.music.vivi.compose.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.SliderStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live Now Playing / fullscreen seek bar; the style (and squiggly) change the drawing.
 */
@Composable
fun PlayerSeekBar(
    progress: Float,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    style: SliderStyle,
    squiggly: Boolean,
    compact: Boolean = false,
    /** M3 Expressive: the played part is a travelling wave while playing and eases flat when paused. */
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.28f),
) {
    val p = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val scrub by rememberUpdatedState(onScrub)
    val finish by rememberUpdatedState(onScrubFinished)
    val infinite = rememberInfiniteTransition(label = "seekWave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = com.music.vivi.compose.ui.motion.VisageMotion.linear(1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val wavy = squiggly || style == SliderStyle.WAVY
    // Wave height eases in/out with play state (Expressive wavy-progress behaviour).
    val waveAmount by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPlaying && !compact) 1f else 0f,
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.linear(450),
        label = "waveAmount",
    )
    // Freeze wave animation while scrubbing feels odd; keep phase but OK.

    fun fractionForX(x: Float, width: Float): Float =
        if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height.coerceAtLeast(40.dp))
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f)
                setProgress { value -> scrub(value.coerceIn(0f, 1f)); finish(); true }
            }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) false
                else when (it.key) {
                    Key.DirectionLeft -> { scrub((p - .02f).coerceAtLeast(0f)); finish(); true }
                    Key.DirectionRight -> { scrub((p + .02f).coerceAtMost(1f)); finish(); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    scrub(fractionForX(offset.x, size.width.toFloat()))
                    finish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrub(fractionForX(offset.x, size.width.toFloat()))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrub(fractionForX(change.position.x, size.width.toFloat()))
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                )
            },
    ) {
        val midY = size.height / 2f
        val m3Expressive = style == SliderStyle.M3_EXPRESSIVE
        if (m3Expressive) {
            val wavyTrack = !compact
            val trackH = if (wavyTrack) 8.dp.toPx() else 16.dp.toPx()
            val thumbW = 4.dp.toPx()
            val thumbH = if (compact) 34.dp.toPx() else 44.dp.toPx()
            val gap = 6.dp.toPx()

            val tx = size.width * p

            // Active track: a wave (seek bar) or a flat rounded bar (volume / compact).
            val activeEnd = (tx - gap - thumbW/2f).coerceAtLeast(0f)
            if (activeEnd > 0f) {
                if (wavyTrack) {
                    val amp = 3.5.dp.toPx() * waveAmount
                    val wavelength = 36.dp.toPx()
                    val inset = trackH / 2f
                    val path = Path()
                    // The round caps extend by trackH/2 at each end, so run the centre line inset on both sides.
                    val x0 = inset
                    val x1 = (activeEnd - inset).coerceAtLeast(x0)
                    val steps = ((x1 - x0) / 3f).toInt().coerceIn(2, 400)
                    for (i in 0..steps) {
                        val x = x0 + (x1 - x0) * i / steps
                        // ease the wave to flat at both ends so it meets the cap and the gap calmly
                        val edge = minOf((x1 - x) / 14.dp.toPx(), (x - x0) / 8.dp.toPx()).coerceIn(0f, 1f)
                        val y = midY + sin(2f * PI.toFloat() * (x / wavelength - phase)) * amp * edge
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, activeColor, style = Stroke(width = trackH, cap = StrokeCap.Round))
                } else {
                    val activePath = Path().apply {
                        addRoundRect(RoundRect(
                            left = 0f, top = midY - trackH/2f,
                            right = activeEnd, bottom = midY + trackH/2f,
                            topLeftCornerRadius = CornerRadius(trackH/2f),
                            bottomLeftCornerRadius = CornerRadius(trackH/2f),
                            topRightCornerRadius = CornerRadius(2.dp.toPx()),
                            bottomRightCornerRadius = CornerRadius(2.dp.toPx()),
                        ))
                    }
                    drawPath(activePath, activeColor)
                }
            }

            // Thumb
            val thumbLeft = (tx - thumbW/2f).coerceIn(0f, (size.width - thumbW).coerceAtLeast(0f))
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(thumbLeft, midY - thumbH/2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW/2f)
            )
            
            // Inactive track
            val inactiveStart = (tx + gap + thumbW/2f).coerceAtMost(size.width)
            if (inactiveStart < size.width) {
                val inactivePath = Path().apply {
                    addRoundRect(RoundRect(
                        left = inactiveStart, top = midY - trackH/2f,
                        right = size.width, bottom = midY + trackH/2f,
                        topLeftCornerRadius = CornerRadius(2.dp.toPx()),
                        bottomLeftCornerRadius = CornerRadius(2.dp.toPx()),
                        topRightCornerRadius = CornerRadius(trackH/2f),
                        bottomRightCornerRadius = CornerRadius(trackH/2f),
                    ))
                }
                drawPath(inactivePath, inactiveColor)
                
                // Dot
                drawCircle(
                    color = activeColor,
                    radius = 2.dp.toPx(),
                    center = Offset((size.width - trackH/2f).coerceAtLeast(inactiveStart), midY)
                )
            }
            return@Canvas
        }

        drawClassicSeek(style, wavy, p, phase, waveAmount, activeColor, inactiveColor)
    }
}

/**
 * Draws the classic (non-M3) seek styles; shared by the live seek bar and the settings preview.
 * WAVY: a travelling sine wave on the played part, eased flat at both ends. SLIM: a thin line.
 * EXPRESSIVE: a bold pill track with a ring handle.
 */
internal fun DrawScope.drawClassicSeek(
    style: SliderStyle,
    wavy: Boolean,
    p: Float,
    phase: Float,
    waveAmount: Float,
    active: Color,
    inactive: Color,
) {
    val midY = size.height / 2f
    val slim = style == SliderStyle.SLIM
    val bold = style == SliderStyle.EXPRESSIVE
    val stroke = when { slim -> 2.5.dp.toPx(); bold -> 12.dp.toPx(); else -> 4.dp.toPx() }
    val thumbR = when { slim -> 5.dp.toPx(); bold -> 11.dp.toPx(); else -> 7.dp.toPx() }
    val half = stroke / 2f
    // Keep the whole handle inside the bar at both ends.
    val tx = thumbR + (size.width - 2f * thumbR).coerceAtLeast(0f) * p

    if (tx < size.width - half) {
        drawLine(inactive, Offset(tx, midY), Offset(size.width - half, midY), stroke, StrokeCap.Round)
    }
    if (tx > half) {
        if (wavy && waveAmount > 0.01f) {
            val amp = (if (bold) 3.5.dp.toPx() else 3.dp.toPx()) * waveAmount
            val wavelength = 26.dp.toPx()
            val x0 = half
            val x1 = tx
            val path = Path()
            val steps = ((x1 - x0) / 2.dp.toPx()).toInt().coerceIn(2, 600)
            for (i in 0..steps) {
                val x = x0 + (x1 - x0) * i / steps
                val edge = minOf((x1 - x) / 14.dp.toPx(), (x - x0) / 10.dp.toPx()).coerceIn(0f, 1f)
                val y = midY + sin(2f * PI.toFloat() * (x / wavelength - phase)) * amp * edge
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, active, style = Stroke(width = stroke, cap = StrokeCap.Round))
        } else {
            drawLine(active, Offset(half, midY), Offset(tx, midY), stroke, StrokeCap.Round)
        }
    }

    drawCircle(active, radius = thumbR, center = Offset(tx, midY))
    if (bold) drawCircle(Color.Black.copy(alpha = 0.4f), radius = thumbR * 0.52f, center = Offset(tx, midY))
}
