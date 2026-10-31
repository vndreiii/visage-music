package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.music.lyricsync.LinePhase
import com.music.lyricsync.LyricsLine
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.compose.prefs.LyricsTextAlign
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/* Scene tuning constants (multiples of font size / spacing). */
private const val ROW_SPACING_EM = 7.0f
private const val FOCUS_SPACINGS = 1.30f // camera rests this far in front of the focal plane
private const val SPAWN_LEAD_MS = 2400f
private const val CULL_FAR = 4.8f // multiples of focus distance
private const val ACTIVE_MAX_SCALE = 1.06f // gentle, natural 6% swell as words are sung without clipping
private const val EXIT_MIN_SCALE = 0.98f // subtle, smooth 2% relaxation on completion
private const val MAX_PLANE_SCALE = 1.18f // hard upper bound so passing lines never get too big

/**
 * Renders lyrics as words floating in projected 3D space.
 *
 * ROW ("3D"): a winding corridor of lyric billboards. Words fly in with realistic inertial bounce
 * on settle (After Effects damped spring physics); the active line swells subtly (+6%) as it completes,
 * then zooms out smoothly into bokeh as the camera swerves along the corridor to the next line.
 */
@Composable
internal fun Lyrics3DScene(
    timeline: LyricsTimeline,
    cfg: LyricsConfig,
    clock: State<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    sizeScale: Float = 1f,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)

    // Main (non-background) lines in song order; neighbours define the camera path.
    val sceneLines = remember(timeline) {
        timeline.lines.mapIndexed { i, l -> i to l }.filter { (_, l) -> !l.isBackground && l.text.isNotBlank() }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val densityPx = density.density
        val maxWpx = with(density) { maxWidth.toPx().coerceAtLeast(200f) }
        val wrapW = (maxWpx * 0.65f).coerceIn(240f, 740f * sizeScale).toInt()
        val fontPx = cfg.fontSizeSp * sizeScale * densityPx
        val spacing = fontPx * ROW_SPACING_EM
        val focusDist = spacing * FOCUS_SPACINGS

        // Configurable dissolve distance: controls when passing lyrics melt away before getting too close/big
        val dissolveThreshold = focusDist * cfg.sceneDissolve.coerceIn(0.55f, 0.95f)

        // World layout, measured once per song / configuration change.
        val ko = remember(sceneLines, cfg.layoutKey, cfg.fontSizeSp, sizeScale, wrapW, cfg.scene) {
            buildSceneObjects(measurer, sceneLines, cfg, sizeScale, wrapW, density)
        }

        // Wall clock for handheld camera shake & subtle idle breathing.
        val wall = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val t0 = withFrameNanos { it }
            while (true) withFrameNanos { wall.floatValue = (it - t0) / 1_000_000_000f }
        }

        // Reusable scratch projection buffer.
        val scratch = remember(ko.maxUnits) { Scratch(ko.maxUnits + 4) }

        Canvas(Modifier.fillMaxSize()) {
            val pos = clock.value
            val entries = ko.entries
            if (entries.isEmpty()) return@Canvas

            val focalU = computeFocalU(entries, pos)
            val cam = sceneCamera(cfg.scene, entries, focalU, wall.value, fontPx, focusDist, spacing, cfg.animStrength)
            val camBefore = sceneCamera(cfg.scene, entries, computeFocalU(entries, pos - 100f), wall.value, fontPx, focusDist, spacing, cfg.animStrength)
            val motionBlur = (abs(cam.z - camBefore.z) * 0.06f).coerceAtMost(fontPx * 0.6f) * cfg.blur

            val cx = size.width / 2f
            val cy = size.height / 2f
            val cullFar = focusDist * CULL_FAR

            val yawC = cos(cam.yaw); val yawS = sin(cam.yaw)
            val pitC = cos(cam.pitch); val pitS = sin(cam.pitch)

            val items = ArrayList<PlaneItem>(entries.size)

            for (si in entries.indices) {
                val e = entries[si]
                val phase = timeline.phase(e.lineIndex, pos.toLong())

                // Spawning: smooth forward glide in depth into resting background position
                val spawnLeadMs = 2000f
                val spawnTime = pos - (e.startMs - spawnLeadMs)
                if (spawnTime <= 0f) continue // Too far in the future to spawn

                val flightDur = 650f
                val spawnEase = smooth01((spawnTime / flightDur).coerceIn(0f, 1f))
                val spawnZ = (1f - spawnEase) * focusDist * 0.20f
                val spawnAlpha = smooth01((spawnTime / 450f).coerceIn(0f, 1f))

                // Outgoing animation: gentle, natural exit duration adapted for fast lyrics
                val exitDur = if (si < entries.lastIndex) {
                    val gap = entries[si + 1].startMs - e.endMs
                    if (gap >= 400f) min(850f, max(550f, gap * 0.85f))
                    else if (gap > 0f) min(450f, max(280f, gap + 120f))
                    else min(350f, max(220f, (e.endMs - e.startMs) * 0.35f))
                } else 650f

                // A line is not gone the moment its last word is sung: songs don't clear the page that fast, so it
                // lingers for a moment before the exit starts.
                val exitStartMs = e.endMs + cfg.sceneLingerSec.coerceIn(0.5f, 8f) * 1000f
                val isPast = pos > exitStartMs
                val exitProgress = if (isPast) ((pos - exitStartMs) / exitDur).coerceIn(0f, 1f) else 0f
                if (exitProgress >= 1f) continue // Fully dissolved

                val exitEase = smooth01(exitProgress)
                val exitAlpha = (1f - exitEase).pow(1.3f)
                val exitZ = exitEase * fontPx * 1.2f

                // Inertial bounce on lyric change (when line becomes active & starts singing!)
                var activeBounceScale = 0f
                var activeBounceY = 0f

                if (!isPast && pos >= e.startMs && cfg.bounceAmp > 0.01f) {
                    val t = (pos - e.startMs) / 1000f
                    if (t < 1.2f) {
                        val decayVal = exp(cfg.bounceDecay * t)
                        val osc = sin(t * 2.0f * PI.toFloat() * cfg.bounceFreq) / decayVal
                        activeBounceScale = osc * 0.12f * cfg.bounceAmp
                        activeBounceY = -osc * (fontPx * 0.35f) * cfg.bounceAmp
                    }
                }

                // Scale: swells gently during singing + pops with inertial bounce, relaxes on completion
                val lineScale: Float = if (isPast) {
                    lerp(ACTIVE_MAX_SCALE, EXIT_MIN_SCALE, exitEase)
                } else if (pos >= e.startMs) {
                    val sungP = ((pos - e.startMs) / max(e.endMs - e.startMs, 80f)).coerceIn(0f, 1f)
                    val swell = sin(sungP * (PI.toFloat() * 0.5f)) * (ACTIVE_MAX_SCALE - 1.0f)
                    (1.0f + swell + activeBounceScale).coerceAtLeast(0.5f)
                } else {
                    1.0f
                }

                // Camera-relative depth
                val totalZ = e.z + spawnZ + exitZ
                val rcz = totalZ - cam.z
                if (rcz < dissolveThreshold || rcz > cullFar) continue

                // Passing melt: dissolves smoothly as it approaches dissolveThreshold so it never gets too big
                val passMelt = if (rcz < focusDist) {
                    smooth01(((rcz - dissolveThreshold) / (focusDist - dissolveThreshold).coerceAtLeast(1f)).coerceIn(0f, 1f))
                } else 1f

                // Far dissolve
                val aFar = smooth01(((cullFar - rcz) / (focusDist * 1.1f)).coerceIn(0f, 1f))

                // Base opacity
                val baseAlpha = when {
                    isPast -> 0.70f * exitAlpha
                    pos >= e.startMs -> 1.0f
                    else -> {
                        val depthAhead = ((totalZ - cam.z) - focusDist) / spacing
                        (0.40f / (1f + max(0f, depthAhead) * 0.30f)).coerceIn(0.12f, 0.42f)
                    }
                }

                val alpha = (baseAlpha * passMelt * aFar * spawnAlpha).coerceIn(0f, 1f)
                if (alpha < 0.015f) continue

                // Optical bokeh blur
                val depthDiff = rcz - focusDist
                val blurMultiplier = (cfg.blur.coerceAtLeast(0.35f) / 0.6f)
                val blur: Float = if (isPast) {
                    (exitEase * fontPx * 0.35f + (1f - passMelt) * fontPx * 0.40f + motionBlur).coerceAtMost(fontPx * 1.4f)
                } else if (abs(depthDiff) < spacing * 0.25f && pos >= e.startMs) {
                    motionBlur
                } else if (depthDiff > 0f) {
                    val distSpacings = depthDiff / spacing
                    (distSpacings.pow(1.12f) * fontPx * 0.24f * blurMultiplier + motionBlur)
                        .coerceIn(1.8f, fontPx * 1.5f)
                } else {
                    ((-depthDiff / focusDist).pow(1.2f) * fontPx * 0.30f * blurMultiplier + motionBlur)
                        .coerceIn(1.5f, fontPx * 1.5f)
                }

                items += PlaneItem(
                    e = e,
                    phase = phase,
                    relX = e.x - cam.x,
                    relY = e.y - cam.y + activeBounceY,
                    relZ = totalZ - cam.z,
                    lineScale = lineScale,
                    alpha = alpha,
                    blur = blur,
                )
            }

            // Painter's algorithm: sort back-to-front by projected distance
            items.sortByDescending { it.relZ }

            rotate(cam.rollRad * 180f / PI.toFloat(), pivot = Offset(cx, cy)) {
                for (item in items) {
                    val n = item.project(
                        scratch, cx, cy, focusDist, fontPx * 0.5f,
                        yawC, yawS, pitC, pitS, dissolveThreshold,
                    )
                    if (n == 0) continue
                    blurredLayer(item.blur, scratch.boundsRect(fontPx * 0.4f)) {
                        drawPlane(item, scratch, n, pos, accent, cfg.glow, cfg.animStrength, fontPx)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Per-frame plane projection & bounds                                */
/* ------------------------------------------------------------------ */

private class PlaneItem(
    val e: SceneEntry,
    val phase: LinePhase,
    val relX: Float,
    val relY: Float,
    val relZ: Float,
    val lineScale: Float,
    val alpha: Float,
    val blur: Float,
) {
    /**
     * Projects the entire lyric line as a single 3D plane billboard:
     * Word layout offsets scale uniformly with the plane, strictly preserving intra-word
     * spacing and preventing words from merging or crashing into each other under tilt.
     */
    fun project(
        s: Scratch,
        cx: Float,
        cy: Float,
        fd: Float,
        fontPad: Float,
        yawC: Float,
        yawS: Float,
        pitC: Float,
        pitS: Float,
        dissolveNear: Float,
    ): Int {
        val vz0 = relZ
        if (vz0 < dissolveNear) return 0

        // Project plane center into camera space
        val vx1 = relX * yawC + vz0 * yawS
        val vz1 = vz0 * yawC - relX * yawS
        if (vz1 < dissolveNear) return 0
        val vy1 = relY * pitC - vz1 * pitS
        val vz2 = vy1 * pitS + vz1 * pitC
        if (vz2 < dissolveNear) return 0

        val planeScale = ((fd / vz2) * lineScale).coerceAtMost(MAX_PLANE_SCALE)
        if (planeScale <= 0.02f) return 0

        val planeCX = cx + vx1 * planeScale
        val planeCY = cy + vy1 * planeScale

        s.reset()
        s.ensure(e.unitCount)
        val rollC = cos(e.rollRad); val rollS = sin(e.rollRad)
        var n = 0

        for (i in 0 until e.unitCount) {
            val lx0 = if (i < e.localX.size) e.localX[i] else e.transX
            val ly0 = if (i < e.localY.size) e.localY[i] else e.transY
            val w = if (i < e.unitW.size) e.unitW[i] else e.transW
            val h = if (i < e.unitH.size) e.unitH[i] else e.transH

            // Rotate layout offset by the plane's own wall roll in BOX mode
            val lxx = lx0 * rollC - ly0 * rollS
            val lyy = lx0 * rollS + ly0 * rollC

            // Uniform scaling on the plane preserves typography 100%
            val px = planeCX + lxx * planeScale
            val py = planeCY + lyy * planeScale

            s.x[n] = px
            s.y[n] = py
            s.z[n] = planeScale
            s.w[n] = w
            s.h[n] = h
            n++

            val hw = w * planeScale * 0.55f + fontPad
            val hh = h * planeScale * 0.60f + fontPad
            if (px - hw < s.bx0) s.bx0 = px - hw
            if (px + hw > s.bx1) s.bx1 = px + hw
            if (py - hh < s.by0) s.by0 = py - hh
            if (py + hh > s.by1) s.by1 = py + hh
        }
        return n
    }
}

private class Scratch(capacityHint: Int) {
    var x = FloatArray(capacityHint); var y = FloatArray(capacityHint)
    var z = FloatArray(capacityHint)
    var w = FloatArray(capacityHint); var h = FloatArray(capacityHint)
    var bx0 = 0f; var by0 = 0f; var bx1 = 0f; var by1 = 0f

    fun reset() {
        bx0 = Float.MAX_VALUE; by0 = Float.MAX_VALUE; bx1 = -Float.MAX_VALUE; by1 = -Float.MAX_VALUE
    }

    fun ensure(n: Int) {
        if (x.size >= n) return
        x = FloatArray(n); y = FloatArray(n); z = FloatArray(n)
        w = FloatArray(n); h = FloatArray(n)
    }

    fun boundsRect(pad: Float): Rect {
        if (bx0 > bx1) return Rect(0f, 0f, 1f, 1f)
        return Rect(bx0 - pad, by0 - pad, bx1 + pad, by1 + pad)
    }
}

/* ------------------------------------------------------------------ */
/* World building                                                      */
/* ------------------------------------------------------------------ */

private class SceneEntry(
    val lineIndex: Int,
    val layout: LineLayout?,
    val units: List<UnitBox>,
    val x: Float, val y: Float, val z: Float, val rollRad: Float,
    val startMs: Float, val endMs: Float,
    val localX: FloatArray, val localY: FloatArray,
    val unitW: FloatArray, val unitH: FloatArray,
    val transX: Float, val transY: Float, val transW: Float, val transH: Float,
) {
    val unitCount: Int = units.size + (if (layout?.translation != null) 1 else 0)
    val unitCountMain: Int = units.size
}

private class SceneObjects(val entries: List<SceneEntry>) {
    val maxUnits: Int = entries.maxOfOrNull { it.unitCount } ?: 1
}

private fun buildSceneObjects(
    measurer: androidx.compose.ui.text.TextMeasurer,
    sceneLines: List<Pair<Int, LyricsLine>>,
    cfg: LyricsConfig,
    sizeScale: Float,
    wrapW: Int,
    density: androidx.compose.ui.unit.Density,
): SceneObjects {
    val fontPx = cfg.fontSizeSp * sizeScale * density.density
    val centreCfg = cfg.copy(align = LyricsTextAlign.CENTER)
    val entries = ArrayList<SceneEntry>(sceneLines.size)

    for ((depth, indexed) in sceneLines.withIndex()) {
        val (lineIndex, line) = indexed
        val rz = depth * fontPx * ROW_SPACING_EM

        // Subtle, elegant lateral curve along corridor axis
        val wx = sin(depth * 0.45f) * fontPx * 0.75f
        val wy = sin(depth * 0.28f + 0.4f) * fontPx * 0.25f
        val rollRad = 0f

        val lay = buildLineLayout(measurer, line, centreCfg, sizeScale, isBackground = false, maxWidth = wrapW, density = density)
        val units = lay.units
        val n = units.size
        val lx = FloatArray(n); val ly = FloatArray(n)
        val uw = FloatArray(n); val uh = FloatArray(n)
        for (i in 0 until n) {
            val u = units[i]
            lx[i] = u.x + u.w / 2f - lay.width / 2f
            ly[i] = u.y + u.h / 2f - lay.height / 2f
            uw[i] = u.w; uh[i] = u.h
        }
        var tX = 0f; var tY = 0f; var tW = 1f; var tH = 1f
        lay.translation?.let { t ->
            tW = t.size.width.toFloat(); tH = t.size.height.toFloat()
            val cxL = lay.translationOffset.x + tW / 2f - lay.width / 2f
            val cyL = lay.translationOffset.y + tH / 2f - lay.height / 2f
            tX = cxL; tY = cyL
        }
        entries += SceneEntry(
            lineIndex = lineIndex,
            layout = lay,
            units = units,
            x = wx, y = wy, z = rz, rollRad = rollRad,
            startMs = (line.timeMs ?: 0L).toFloat(),
            endMs = max(line.endMs ?: 0L, line.timeMs ?: 0L).toFloat(),
            localX = lx, localY = ly, unitW = uw, unitH = uh,
            transX = tX, transY = tY, transW = tW, transH = tH,
        )
    }
    return SceneObjects(entries)
}

/* ------------------------------------------------------------------ */
/* Camera & Timeline Transition                                       */
/* ------------------------------------------------------------------ */

private class Cam(
    val x: Float, val y: Float, val z: Float,
    val yaw: Float, val pitch: Float, val rollRad: Float,
)

/**
 * Computes a strictly continuous focal parameter u in [0, entries.lastIndex] as a smooth function
 * of playback timestamp posMs. Transitions between lines dynamically adapt when lyrics happen fast.
 */
private fun computeFocalU(entries: List<SceneEntry>, posMs: Float): Float {
    if (entries.isEmpty()) return 0f
    if (entries.size == 1) return 0f
    if (posMs <= entries[0].startMs) return 0f
    if (posMs >= entries.last().endMs) return entries.lastIndex.toFloat()

    var prevTransEnd = 0f

    for (i in 0 until entries.lastIndex) {
        val cur = entries[i]
        val next = entries[i + 1]
        val curEnd = cur.endMs
        val nextStart = next.startMs
        val gap = nextStart - curEnd

        val transStart: Float
        val transEnd: Float

        if (gap >= 500f) {
            val dur = min(850f, max(550f, gap * 0.75f))
            transStart = max(prevTransEnd, curEnd)
            transEnd = transStart + dur
        } else if (gap >= 0f) {
            val dur = min(480f, max(240f, (curEnd - cur.startMs) * 0.25f + gap))
            val rawStart = max(cur.startMs + (curEnd - cur.startMs) * 0.50f, nextStart - dur)
            transStart = max(prevTransEnd, rawStart)
            transEnd = max(transStart + 80f, nextStart)
        } else {
            val dur = min(380f, max(200f, (nextStart - cur.startMs) * 0.35f))
            val rawStart = max(cur.startMs, nextStart - dur)
            transStart = max(prevTransEnd, rawStart)
            transEnd = max(transStart + 80f, nextStart)
        }

        prevTransEnd = transEnd

        if (posMs < transStart) {
            return i.toFloat()
        }
        if (posMs <= transEnd) {
            val t = ((posMs - transStart) / max(transEnd - transStart, 1f)).coerceIn(0f, 1f)
            return i.toFloat() + smooth01(t)
        }
    }

    return entries.lastIndex.toFloat()
}

/**
 * Pure continuous camera function.
 * In ROW mode: winding corridor with gentle banking.
 * In BOX mode: camera descends the 4-sided tunnel, smoothly rolling 90° per line to face each wall right-side up.
 */
private fun sceneCamera(
    mode: LyricsScene,
    entries: List<SceneEntry>,
    focalU: Float,
    wall: Float,
    fontPx: Float,
    focusDist: Float,
    spacing: Float,
    strength: Float,
): Cam {
    if (entries.isEmpty()) return Cam(0f, 0f, 0f, 0f, 0f, 0f)

    val k = focalU.toInt().coerceIn(0, entries.lastIndex)
    val frac = (focalU - k).coerceIn(0f, 1f)
    val k2 = min(k + 1, entries.lastIndex)

    val cur = entries[k]
    val next = entries[k2]

    val targetX = lerp(cur.x, next.x, frac)
    val targetY = lerp(cur.y, next.y, frac)
    val targetZ = lerp(cur.z, next.z, frac)

    val s = strength
    val shakeX = wob(wall, 1.9f, 3.4f, 0.8f) * fontPx * 0.040f * s
    val shakeY = wob(wall, 2.6f, 1.3f, 0.55f, 2.1f) * fontPx * 0.030f * s
    val shakeRoll = (sin(wall * 1.75f) * 0.9f + 0.35f * sin(wall * 0.82f + 1.9f)) * 0.010f * s
    val driftYaw = sin(wall * 0.35f) * 0.015f * s
    val driftPitch = cos(wall * 0.27f + 1.1f) * 0.010f * s

    val camZ = targetZ - focusDist

    return when (mode) {
        LyricsScene.ROW -> {
            val pK = max(0, k - 1)
            val prev = entries[pK]
            val startCamX = if (k > 0) (prev.x + cur.x) * 0.35f else cur.x * 0.25f
            val startCamY = if (k > 0) (prev.y + cur.y) * 0.35f else cur.y * 0.25f
            val endCamX = (cur.x + next.x) * 0.35f
            val endCamY = (cur.y + next.y) * 0.35f

            val baseCamX = lerp(startCamX, endCamX, frac)
            val baseCamY = lerp(startCamY, endCamY, frac)

            val camX = baseCamX + shakeX
            val camY = baseCamY + shakeY

            val dx = targetX - camX
            val dy = targetY - camY
            val dz = targetZ - camZ
            val distXZ = sqrt(dx * dx + dz * dz)

            val yaw = -atan2(dx, dz) * 0.70f + driftYaw
            val pitch = atan2(dy, distXZ) * 0.70f + driftPitch

            Cam(camX, camY, camZ, yaw, pitch, shakeRoll)
        }
        else -> Cam(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun wob(t: Float, f1: Float, f2: Float, f3: Float = 1f, phase: Float = 0f): Float =
    sin(t * f1) + 0.55f * sin(t * f2 + phase) + 0.3f * sin(t * f3 + 2.2f)

private fun smooth01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f) // quintic smootherstep (zero 1st & 2nd derivative at ends)
}

/* ------------------------------------------------------------------ */
/* Drawing & Depth-of-Field Blur                                      */
/* ------------------------------------------------------------------ */

private fun DrawScope.drawPlane(
    item: PlaneItem,
    s: Scratch,
    n: Int,
    posMs: Float,
    accent: Color,
    glow: Float,
    strength: Float,
    fontPx: Float,
) {
    val e = item.e
    val lay = e.layout ?: return
    val glowOn = glow > 0f
    val glowRadius = fontPx * 0.5f * glow

    for (i in 0 until n) {
        val px = s.x[i]; val py = s.y[i]; val sc = s.z[i]
        val w = s.w[i]; val h = s.h[i]
        val isTrans = i >= e.unitCountMain

        if (isTrans) {
            val t = lay.translation ?: continue
            val a = if (item.phase == LinePhase.Active) 0.78f else 0.42f
            translate(px, py) {
                scale(sc, sc, pivot = Offset.Zero) {
                    drawText(t, color = Color.White.copy(alpha = a * item.alpha), topLeft = Offset(-w / 2f, -h / 2f))
                }
            }
            continue
        }

        val u = e.units[i]
        val p = u.progressAt(posMs)
        val env = if (item.phase == LinePhase.Active && p > 0f && p < 1f) sin(PI.toFloat() * p) else 0f
        val ws = sc * (1f + 0.06f * env * strength)
        val sungAlpha = when {
            item.phase == LinePhase.Active -> smooth01(p)
            item.phase == LinePhase.Past -> 1f
            else -> 0f
        }
        val shadow = if (glowOn && sungAlpha > 0.01f) {
            Shadow(accent.copy(alpha = (0.45f * sungAlpha * item.alpha).coerceIn(0f, 1f)), Offset.Zero, glowRadius)
        } else null

        translate(px, py) {
            scale(ws, ws, pivot = Offset.Zero) {
                if (p < 0.999f) {
                    drawText(u.layout, color = Color.White.copy(alpha = 0.34f * item.alpha), topLeft = Offset(-u.w / 2f, -u.h / 2f))
                }
                if (sungAlpha > 0.001f) {
                    drawText(u.layout, color = Color.White.copy(alpha = sungAlpha * item.alpha), topLeft = Offset(-u.w / 2f, -u.h / 2f), shadow = shadow)
                }
            }
        }
    }
}

/**
 * Optical depth-of-field Gaussian blur via Skia ImageFilter on saveLayer.
 * Bounds are generously padded by 3.5 sigmas to prevent rectangular clipping edges.
 */
private inline fun DrawScope.blurredLayer(radius: Float, bounds: Rect, block: DrawScope.() -> Unit) {
    if (radius < 0.8f) {
        block()
        return
    }
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().imageFilter = ImageFilter.makeBlur(
            radius,
            radius,
            FilterTileMode.DECAL,
        )
        val pad = radius * 3.5f
        val layerBounds = Rect(
            left = bounds.left - pad,
            top = bounds.top - pad,
            right = bounds.right + pad,
            bottom = bounds.bottom + pad,
        )
        canvas.saveLayer(layerBounds, paint)
        block()
        canvas.restore()
    }
}
