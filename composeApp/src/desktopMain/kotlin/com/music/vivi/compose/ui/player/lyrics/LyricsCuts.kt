package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.player.AudioLevels
import com.music.vivi.compose.ui.player.background.RingsField
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.theme.ArtworkPalette
import com.music.vivi.compose.theme.LocalArtworkPalette
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val CUTS_SKSL = com.music.vivi.compose.ui.player.background.RING_FIELD_SKSL + """
uniform float2 iResolution;
uniform float2 uCenter;
uniform float uTime;
uniform float uRingStep;
uniform float uLineWidth;
uniform float uRippleFreq;
uniform float uRippleSpeed;
uniform float uBaseRippleAmp;
uniform float uEnergyRippleAmp;
uniform float uSoundEnergy;
uniform float uSnakeAmp;
uniform float uSliceAmp;
uniform float uFused;
uniform float uCut;
uniform float uDiag;
uniform float uRScale;
uniform float uBass;
uniform float uMid;
uniform float uHigh;
uniform float uKick;
uniform shader textContent;

half4 main(float2 fc) {
    float2 dVec = fc - uCenter;
    float dist = length(dVec);

    // -------------------------------------------------------------------------
    // 1. SOUND-REACTIVE OUTWARD RIPPLE (Center -> Exterior)
    // -------------------------------------------------------------------------
    // Wave travels outward: phase = dist * freq - time * speed
    float ripplePhase = dist * uRippleFreq - uTime * uRippleSpeed;
    float rippleWave = sin(ripplePhase);
    float waveCrest = 0.5 + 0.5 * rippleWave;

    // Amplitude surges with sound energy (words, beats, inertial bounce)
    float rippleAmp = uBaseRippleAmp + uSoundEnergy * uEnergyRippleAmp;

    // Modulate physical ring distance: creates ripples propagating outward
    float rippleDist = dist + rippleWave * rippleAmp;
    float fusedE = ringEnergy(dist, uRingStep, uBass, uMid, uHigh);
    if (uFused > 0.5) {
        // Fused with the Rings background: the very same ring field, so the cuts sit exactly on the background's rings.
        rippleDist = ringDistance(dist, uRingStep, uDiag, uRScale, uTime, fusedE, uKick);
        waveCrest = 0.5 + 0.5 * sin(dist / uDiag * 46.0 / uRScale - uTime * 1.6);
    }

    // -------------------------------------------------------------------------
    // 2. CONCENTRIC CUT LINES (Vinyl Grooves)
    // -------------------------------------------------------------------------
    float ringCoord = rippleDist / uRingStep;
    float distFromLine = fract(ringCoord) * uRingStep;
    float distToLine = min(distFromLine, uRingStep - distFromLine);

    // Line width & glow swell with the ripple crest
    float localLineWidth = uLineWidth * (1.0 + waveCrest * 0.45 * (1.0 + uSoundEnergy));
    if (uFused > 0.5) localLineWidth = max(uDiag * 0.0021 * (1.0 + waveCrest * 0.5 + fusedE * 2.4 + uKick * 0.8), 2.0);
    float localGlow = 0.15 + 0.35 * waveCrest * (0.6 + uSoundEnergy * 0.8);

    // Crisp anti-aliased vinyl groove cut lines
    float lineAlpha = smoothstep(localLineWidth, 0.18, distToLine);
    float maxDist = length(iResolution) * 1.1;
    float ringDepthAlpha = clamp(0.30 - (dist / maxDist) * 0.16, 0.05, 0.36);
    float ringA = lineAlpha * ringDepthAlpha * (0.75 + localGlow) * (1.0 - uFused);
    half4 ringCol = half4(ringA, ringA, ringA, ringA);

    // -------------------------------------------------------------------------
    // 3. SLICED TEXT: every ring band carries its own slice of the line
    // -------------------------------------------------------------------------
    // The text is cut along the rings, and each band between two rings slides along the radius by its own amount.
    // As the ripple travels outward and the sound energy rises, the bands shift further, so the letters visibly
    // break apart and re-join at the cuts in real time.
    float band = floor(rippleDist / uRingStep);
    float2 radial = dVec / max(dist, 1.0);
    float bandPhase = band * 1.93 + sin(band * 4.1) * 1.7;
    float sliceEnergy = 1.0 - exp(-uSoundEnergy * 1.6); // soft-capped: loud peaks do not tear the letters apart
    float shift = sin(bandPhase + uTime * 2.4) * uSliceAmp * (0.16 + sliceEnergy * 0.34) * uCut;
    // Slight undulation so the whole line still breathes between the cuts.
    float floatY = sin(fc.x * 0.0036 - uTime * 2.2) * uSnakeAmp * 0.35;
    half4 textCol = textContent.eval(float2(fc.x, fc.y + floatY) - radial * shift);

    // -------------------------------------------------------------------------
    // 4. CUTS MASKING: the lines are real gaps in the lettering
    // -------------------------------------------------------------------------
    float cutFactor = smoothstep(localLineWidth * 0.5, localLineWidth * 1.7, distToLine);
    textCol *= mix(1.0, cutFactor, uCut);

    // -------------------------------------------------------------------------
    // 5. SMOOTH EDGE FADE ON ALL 4 SIDES (No hard rectangular borders!)
    // -------------------------------------------------------------------------
    float marginX = min(iResolution.x * 0.20, 160.0);
    float marginY = min(iResolution.y * 0.20, 140.0);
    float edgeFadeX = smoothstep(0.0, marginX, fc.x) * smoothstep(iResolution.x, iResolution.x - marginX, fc.x);
    float edgeFadeY = smoothstep(0.0, marginY, fc.y) * smoothstep(iResolution.y, iResolution.y - marginY, fc.y);
    float edgeFade = edgeFadeX * edgeFadeY;

    // Soft outer radial fade so outermost circles dissolve smoothly
    float maxR = length(iResolution) * 0.68;
    float radialFade = smoothstep(maxR, maxR * 0.50, dist);
    float totalFade = edgeFade * radialFade;

    // Composite text over rippling concentric vinyl cut lines
    // Only the rings dissolve with distance; the lyric keeps full strength along its whole width.
    half4 finalCol = textCol * edgeFade + ringCol * (1.0 - textCol.a * 0.75) * totalFade;
    return finalCol;
}
"""

/**
 * Cuts scene mode: high-contrast circular optical slit-scan and vinyl groove visual style.
 *
 * Slices the typography along concentric circular rings radiating across the viewport.
 * The lines themselves act as the cuts, with text masked across them and shifting subtly
 * in the spaces between lines:
 *  - 100% GPU-accelerated via SkSL runtime effect (single draw pass, zero lag).
 *  - Smooth fade / vignette on all 4 sides: no hard rectangular edges.
 *  - Large, bold, highly legible text that stays as one unified line (no duplicate text).
 *  - Real circular annular cuts: letters are cleanly severed at each circular line boundary.
 *  - Big concentric circle remains on screen at all times.
 */
@Composable
internal fun LyricsCutsScene(
    timeline: LyricsTimeline,
    cfg: LyricsConfig,
    clock: State<Float>,
    accent: Color,
    isPlaying: Boolean = true,
    palette: ArtworkPalette? = LocalArtworkPalette.current,
    modifier: Modifier = Modifier,
    sizeScale: Float = 1f,
) {
    val density = LocalDensity.current
    val densityPx = density.density
    val measurer = rememberTextMeasurer(cacheSize = 64)

    val prefs = org.koin.compose.koinInject<com.music.vivi.compose.prefs.DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE") val prefsVersion = com.music.vivi.compose.ui.settings.rememberPrefsVersion(prefs)
    val ringOriginX = prefs.getFloat(com.music.vivi.compose.prefs.PrefKeys.RingsOriginX, 0.20f)
    val ringOriginY = prefs.getFloat(com.music.vivi.compose.prefs.PrefKeys.RingsOriginY, 0.50f)
    val canvasWindowOrigin = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val wall = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { wall.floatValue = (it - t0) / 1_000_000_000f }
    }

    val effect = remember {
        RuntimeEffect.makeForShader(CUTS_SKSL)
    }
    val shaderBuilder = remember(effect) {
        RuntimeShaderBuilder(effect)
    }

    // Reusable cached surface to avoid allocating textures per frame
    val surfaceCache = remember { arrayOfNulls<Surface>(1) }
    DisposableEffect(Unit) {
        onDispose {
            surfaceCache[0]?.close()
            surfaceCache[0] = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { canvasWindowOrigin.value = it.positionInWindow() }) {
        Canvas(Modifier.fillMaxSize()) {
            val pos = clock.value
            val timeWall = wall.floatValue
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 10f || canvasH <= 10f) return@Canvas

            val wInt = canvasW.toInt()
            val hInt = canvasH.toInt()

            // Ensure cached surface matches dimensions
            var surf = surfaceCache[0]
            if (surf == null || surf.width != wInt || surf.height != hInt) {
                surf?.close()
                surf = Surface.makeRasterN32Premul(wInt, hInt)
                surfaceCache[0] = surf
            }

            // The rings spread from the spawn point chosen in Settings (not tied to the lyric).
            var ringCenterX = canvasW * ringOriginX
            var ringCenterY = canvasH * ringOriginY
            // Fused with the Rings background: use its exact centre, spacing and clock (see RingsField).
            val fused = RingsField.live && RingsField.width > 10f
            val ringScale = prefs.getFloat(com.music.vivi.compose.prefs.PrefKeys.RingsScale, 1.6f).coerceIn(0.4f, 6f)
            val bgDiag = kotlin.math.hypot(RingsField.width, RingsField.height)
            if (fused) {
                ringCenterX = RingsField.left + ringOriginX * RingsField.width - canvasWindowOrigin.value.x
                ringCenterY = RingsField.top + ringOriginY * RingsField.height - canvasWindowOrigin.value.y
            }

            // Extra large, bold typography for maximum presence and legibility
            val fontPx = cfg.fontSizeSp * sizeScale * densityPx * 1.85f

            // Ring step tuned so 2-4 clean concentric cuts cross each text line (like reference posters)
            val ringStep = if (fused) bgDiag * 0.050f * ringScale else (fontPx * 0.42f).coerceIn(24f * densityPx, 44f * densityPx)
            val lineWidth = 1.9f * densityPx

            // Outward ripple parameters (sound-reactive traveling waves)
            val rippleFreq = 0.036f / densityPx
            val rippleSpeed = 5.2f
            val baseRippleAmp = 3.2f * densityPx
            val energyRippleAmp = 11.5f * densityPx * cfg.animStrength

            // Snake undulating amplitude for the text
            val snakeAmp = (fontPx * 0.16f * cfg.animStrength).coerceIn(8f * densityPx, 18f * densityPx)
            // How far a ring band slides along the radius when the sound peaks.
            val sliceAmp = (fontPx * 0.13f * cfg.animStrength).coerceIn(6f * densityPx, 24f * densityPx)

            // Find line index (supports both synced and unsynced)
            val curIndex = if (timeline.isSynced) {
                timeline.primaryIndex(pos.toLong())
            } else {
                if (timeline.lines.isNotEmpty()) ((pos / 4500f).toInt() % timeline.lines.size) else -1
            }
            val hasActiveLine = curIndex in timeline.lines.indices
            val curLine = if (hasActiveLine) timeline.lines[curIndex] else null

            // Active lyric timing
            val lineStart = (curLine?.timeMs ?: 0L).toFloat()
            val lineEnd = (curLine?.endMs ?: (lineStart + 3500f).toLong()).toFloat()
            val lineDur = max(lineEnd - lineStart, 100f)
            val isLineActive = hasActiveLine && (if (timeline.isSynced) (pos >= lineStart && pos <= lineEnd) else true)

            // The rings and the slicing react to the music itself (loudness and beats of the playing audio), not to the lyric.
            val soundEnergy = (AudioLevels.level * 1.1f + AudioLevels.beat * 0.9f).coerceIn(0f, 2.0f)

            // -------------------------------------------------------------------------
            // STEP 1: Render Text to Reusable Offscreen Texture
            // -------------------------------------------------------------------------
            val surfCanvas = surf.canvas
            surfCanvas.clear(0) // Clear to transparent

            if (curLine != null && curLine.text.isNotBlank()) {
                val align = when (cfg.align) {
                    LyricsTextAlign.LEFT -> TextAlign.Start
                    LyricsTextAlign.CENTER -> TextAlign.Center
                    LyricsTextAlign.RIGHT -> TextAlign.End
                }
                val mainStyle = TextStyle(
                    fontSize = (fontPx / densityPx).sp,
                    lineHeight = (fontPx * 1.30f / densityPx).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = cfg.font.family(),
                    fontStyle = if (curLine.isBackground) FontStyle.Italic else FontStyle.Normal,
                    textAlign = align,
                )

                val maxTextWidth = (canvasW * 0.88f).toInt().coerceAtLeast(200)
                val textLayout = measurer.measure(
                    cfg.textCase.apply(curLine.text),
                    mainStyle,
                    constraints = Constraints(maxWidth = maxTextWidth),
                )

                val tr = curLine.translation
                val transLayout: TextLayoutResult? = if (cfg.showTranslation && !tr.isNullOrBlank()) {
                    val transStyle = mainStyle.copy(
                        fontSize = (fontPx * 0.50f / densityPx).sp,
                        lineHeight = (fontPx * 0.65f / densityPx).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    measurer.measure(tr, transStyle, constraints = Constraints(maxWidth = maxTextWidth))
                } else null

                val textTotalH = textLayout.size.height + (transLayout?.size?.height ?: 0) + (if (transLayout != null) (fontPx * 0.25f).toInt() else 0)
                val textDrawX = (canvasW - textLayout.size.width) / 2f
                val textDrawY = (canvasH - textTotalH) / 2f

                // Tell the Rings background where the lyric is so it can darken the rings around it.
                run {
                    val pad = fontPx * 0.35f
                    RingsField.dimLeft = canvasWindowOrigin.value.x + textDrawX - pad
                    RingsField.dimTop = canvasWindowOrigin.value.y + textDrawY - pad
                    RingsField.dimRight = canvasWindowOrigin.value.x + textDrawX + textLayout.size.width + pad
                    RingsField.dimBottom = canvasWindowOrigin.value.y + textDrawY + textTotalH + pad
                    RingsField.dimStamp = System.nanoTime()
                }

                val glowOn = cfg.glow > 0f
                val glowRadius = fontPx * 0.50f * cfg.glow
                val textShadow = if (glowOn) Shadow(accent.copy(alpha = 0.70f * cfg.glow), Offset.Zero, glowRadius) else null

                // Draw text into the offscreen surface
                val drawScope = CanvasDrawScope()
                drawScope.draw(
                    density = density,
                    layoutDirection = LayoutDirection.Ltr,
                    canvas = surf.canvas.asComposeCanvas(),
                    size = Size(canvasW, canvasH),
                ) {
                    // Deep solid drop shadow layer for maximum legibility over any background
                    drawText(
                        textLayout,
                        color = Color.Black.copy(alpha = 0.95f),
                        topLeft = Offset(textDrawX, textDrawY + 3.0f * densityPx),
                    )

                    // Solid, brilliant pure white text
                    val alpha = if (isLineActive) 1.0f else 0.82f
                    drawText(
                        textLayout,
                        color = Color.White.copy(alpha = alpha),
                        topLeft = Offset(textDrawX, textDrawY),
                        shadow = if (isLineActive) textShadow else null,
                    )

                    // Translation below if present
                    if (transLayout != null) {
                        val transDrawX = (canvasW - transLayout.size.width) / 2f
                        val transDrawY = textDrawY + textLayout.size.height + fontPx * 0.25f
                        drawText(
                            transLayout,
                            color = Color.Black.copy(alpha = 0.90f),
                            topLeft = Offset(transDrawX, transDrawY + 2f * densityPx),
                        )
                        drawText(
                            transLayout,
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(transDrawX, transDrawY),
                        )
                    }
                }
            }

            val textImg = surf.makeImageSnapshot()
            val textShader = textImg.makeShader(
                FilterTileMode.DECAL,
                FilterTileMode.DECAL,
                FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
            )

            // -------------------------------------------------------------------------
            // STEP 2: Execute GPU SkSL Circular Cuts Shader (Zero Lag, 1 Draw Call)
            // -------------------------------------------------------------------------
            shaderBuilder.uniform("iResolution", canvasW, canvasH)
            shaderBuilder.uniform("uCenter", ringCenterX, ringCenterY)
            shaderBuilder.uniform("uTime", if (fused) RingsField.seconds() else timeWall)
            shaderBuilder.uniform("uFused", if (fused) 1f else 0f)
            shaderBuilder.uniform("uCut", if (cfg.cutsSliceText) 1f else 0f)
            shaderBuilder.uniform("uDiag", bgDiag.coerceAtLeast(1f))
            shaderBuilder.uniform("uRScale", ringScale)
            com.music.vivi.compose.ui.player.background.RingsReaction.update(
                prefs.getFloat(com.music.vivi.compose.prefs.PrefKeys.RingsReaction, 1f),
                prefs.getFloat(com.music.vivi.compose.prefs.PrefKeys.RingsSmoothing, 0f),
            )
            shaderBuilder.uniform("uBass", com.music.vivi.compose.ui.player.background.RingsReaction.bass)
            shaderBuilder.uniform("uMid", com.music.vivi.compose.ui.player.background.RingsReaction.mid)
            shaderBuilder.uniform("uHigh", com.music.vivi.compose.ui.player.background.RingsReaction.high)
            shaderBuilder.uniform("uKick", com.music.vivi.compose.ui.player.background.RingsReaction.kick)
            shaderBuilder.uniform("uRingStep", ringStep)
            shaderBuilder.uniform("uLineWidth", lineWidth)
            shaderBuilder.uniform("uRippleFreq", rippleFreq)
            shaderBuilder.uniform("uRippleSpeed", rippleSpeed)
            shaderBuilder.uniform("uBaseRippleAmp", baseRippleAmp)
            shaderBuilder.uniform("uEnergyRippleAmp", energyRippleAmp)
            shaderBuilder.uniform("uSoundEnergy", soundEnergy)
            shaderBuilder.uniform("uSnakeAmp", snakeAmp)
            shaderBuilder.uniform("uSliceAmp", sliceAmp)
            shaderBuilder.child("textContent", textShader)

            val finalShader = shaderBuilder.makeShader()
            drawIntoCanvas { composeCanvas ->
                val fullRect = Rect.makeXYWH(0f, 0f, canvasW, canvasH)
                val paint = Paint().apply { this.shader = finalShader }
                composeCanvas.nativeCanvas.drawRect(fullRect, paint)
                paint.close()
            }

            finalShader.close()
            textShader.close()
            textImg.close()
        }
    }
}
