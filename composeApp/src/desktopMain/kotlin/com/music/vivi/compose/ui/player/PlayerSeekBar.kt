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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.SliderStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live Now Playing / fullscreen seek bar. [SliderStyle.DEFAULT] matches the previous
 * Material3 white track look; other styles (and squiggly) actually change the drawing.
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

        val slim = style == SliderStyle.SLIM
        val expressive = style == SliderStyle.EXPRESSIVE
        val stroke = when {
            slim -> 2.5.dp.toPx()
            expressive -> 7.dp.toPx()
            else -> 4.5.dp.toPx()
        }
        val amp = when {
            wavy && expressive -> 2.25.dp.toPx()
            wavy -> 1.75.dp.toPx()
            else -> 0f
        }

        fun waveY(x: Float): Float {
            if (amp == 0f) return midY
            val t = x / size.width + phase
            // Slightly denser but smaller waves.
            return midY + sin(t * 2f * PI.toFloat() * 4f) * amp
        }

        if (wavy) {
            val splitX = size.width * p
            // Inactive (right): straight track — no waviness.
            if (splitX < size.width - 0.5f) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(splitX, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            // Active (left): wavy white path only.
            if (splitX > 0.5f) {
                val active = Path()
                val steps = ((splitX / 4f).toInt()).coerceIn(8, 80)
                for (i in 0..steps) {
                    val x = splitX * i / steps
                    val y = waveY(x)
                    if (i == 0) active.moveTo(x, y) else active.lineTo(x, y)
                }
                // Ease into the straight inactive line at the playhead.
                active.lineTo(splitX, midY)
                drawPath(
                    active,
                    activeColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        } else {
            val trackH = stroke
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, midY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH),
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, midY - trackH / 2f),
                size = Size(size.width * p, trackH),
                cornerRadius = CornerRadius(trackH),
            )
        }

        val thumbR = when {
            expressive -> 8.dp.toPx()
            slim -> 5.dp.toPx()
            else -> 6.5.dp.toPx()
        }
        val tx = size.width * p
        // Thumb sits on the join between wave and straight track.
        val ty = if (wavy) midY else midY
        drawCircle(color = activeColor, radius = thumbR, center = Offset(tx, ty))
        if (expressive) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = thumbR * 0.35f,
                center = Offset(tx, ty),
            )
        }
    }
}
