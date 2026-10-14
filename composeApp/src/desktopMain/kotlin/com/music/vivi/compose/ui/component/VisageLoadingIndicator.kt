package com.music.vivi.compose.ui.component

import androidx.collection.FloatFloatPair
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive indeterminate loading indicator — solid morphing shape
 * (soft-burst → cookie → pentagon → pill → sunny → cookie4 → oval → …).
 *
 * Official `LoadingIndicator` is internal on Compose Multiplatform desktop; this is
 * a Canvas/Morph port of that animation using [androidx.graphics.shapes].
 */
@Composable
fun VisageLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val polygons = remember { indeterminatePolygons() }
    val morphSequence = remember(polygons) {
        morphSequence(polygons = polygons, circularSequence = true)
    }
    val shapesScaleFactor = remember(polygons) {
        calculateScaleFactor(polygons) * ActiveIndicatorScale
    }

    val morphProgress = remember { Animatable(0f) }
    var morphRotationTargetAngle by remember { mutableFloatStateOf(QuarterRotation) }
    val globalRotation = remember { Animatable(0f) }
    var currentMorphIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        launch {
            val morphAnimationSpec =
                com.music.vivi.compose.ui.motion.VisageMotion.morph<Float>()
            while (true) {
                val deferred = async {
                    val animationResult =
                        morphProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = morphAnimationSpec,
                        )
                    if (animationResult.endReason == AnimationEndReason.Finished) {
                        currentMorphIndex = (currentMorphIndex + 1) % morphSequence.size
                        morphProgress.snapTo(0f)
                        morphRotationTargetAngle =
                            (morphRotationTargetAngle + QuarterRotation) % FullRotation
                    }
                }
                delay(MorphIntervalMillis)
                deferred.await()
            }
        }
        launch {
            globalRotation.animateTo(
                targetValue = FullRotation,
                animationSpec =
                    infiniteRepeatable(
                        com.music.vivi.compose.ui.motion.VisageMotion.linear(GlobalRotationDurationMillis),
                        repeatMode = RepeatMode.Restart,
                    ),
            )
        }
    }

    val path = remember { Path() }
    val scaleMatrix = remember { Matrix() }

    Box(
        modifier =
            modifier
                .progressSemantics()
                .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .drawWithContent {
                        val progress = morphProgress.value.coerceIn(0f, 1f)
                        rotate(progress * 90f + morphRotationTargetAngle + globalRotation.value) {
                            drawPath(
                                path =
                                    processPath(
                                        path =
                                            morphSequence[currentMorphIndex].toComposePath(
                                                progress = progress,
                                                path = path,
                                                startAngle = 0,
                                            ),
                                        size = this.size,
                                        scaleFactor = shapesScaleFactor,
                                        scaleMatrix = scaleMatrix,
                                    ),
                                color = color,
                                style = Fill,
                            )
                        }
                    },
        )
    }
}

// --- Morph → Compose Path (from material3 ShapeUtil) ---

private fun Morph.toComposePath(
    progress: Float,
    path: Path = Path(),
    startAngle: Int = 270,
    closePath: Boolean = true,
    rotationPivotX: Float = 0f,
    rotationPivotY: Float = 0f,
): Path {
    pathFromCubics(
        path = path,
        startAngle = startAngle,
        closePath = closePath,
        cubics = asCubics(progress),
        rotationPivotX = rotationPivotX,
        rotationPivotY = rotationPivotY,
    )
    return path
}

private fun pathFromCubics(
    path: Path,
    startAngle: Int,
    closePath: Boolean,
    cubics: List<Cubic>,
    rotationPivotX: Float,
    rotationPivotY: Float,
) {
    var first = true
    var firstCubic: Cubic? = null
    path.rewind()
    for (cubic in cubics) {
        if (first) {
            path.moveTo(cubic.anchor0X, cubic.anchor0Y)
            if (startAngle != 0) firstCubic = cubic
            first = false
        }
        path.cubicTo(
            cubic.control0X,
            cubic.control0Y,
            cubic.control1X,
            cubic.control1Y,
            cubic.anchor1X,
            cubic.anchor1Y,
        )
    }
    if (closePath) path.close()
    if (startAngle != 0 && firstCubic != null && cubics.isNotEmpty()) {
        val angleToFirstCubic =
            atan2(
                cubics[0].anchor0Y - rotationPivotY,
                cubics[0].anchor0X - rotationPivotX,
            ) * 180f / PI.toFloat()
        path.transform(Matrix().apply { rotateZ(-angleToFirstCubic + startAngle) })
    }
}

private fun processPath(
    path: Path,
    size: Size,
    scaleFactor: Float,
    scaleMatrix: Matrix,
): Path {
    scaleMatrix.reset()
    scaleMatrix.scale(x = size.width * scaleFactor, y = size.height * scaleFactor)
    path.transform(scaleMatrix)
    path.translate(size.center - path.getBounds().center)
    return path
}

private fun morphSequence(
    polygons: List<RoundedPolygon>,
    circularSequence: Boolean,
): List<Morph> = buildList {
    for (i in polygons.indices) {
        when {
            i + 1 < polygons.size ->
                add(Morph(polygons[i].normalized(), polygons[i + 1].normalized()))
            circularSequence ->
                add(Morph(polygons[i].normalized(), polygons[0].normalized()))
        }
    }
}

private fun calculateScaleFactor(indicatorPolygons: List<RoundedPolygon>): Float {
    var scaleFactor = 1f
    val bounds = FloatArray(4)
    val maxBounds = FloatArray(4)
    for (polygon in indicatorPolygons) {
        polygon.calculateBounds(bounds)
        polygon.calculateMaxBounds(maxBounds)
        val scaleX = (bounds[2] - bounds[0]) / (maxBounds[2] - maxBounds[0])
        val scaleY = (bounds[3] - bounds[1]) / (maxBounds[3] - maxBounds[1])
        scaleFactor = min(scaleFactor, max(scaleX, scaleY))
    }
    return scaleFactor
}

// --- M3 IndeterminateIndicatorPolygons subset ---

private fun indeterminatePolygons(): List<RoundedPolygon> = listOf(
    softBurst().normalized(),
    cookie9().normalized(),
    pentagon().normalized(),
    pill().normalized(),
    sunny().normalized(),
    cookie4().normalized(),
    oval().normalized(),
)

private val cornerRound15 = CornerRounding(radius = 0.15f)
private val cornerRound50 = CornerRounding(radius = 0.5f)
private val rotateNeg45 = Matrix().apply { rotateZ(-45f) }
private val rotateNeg90 = Matrix().apply { rotateZ(-90f) }

private fun RoundedPolygon.transformedBy(matrix: Matrix): RoundedPolygon =
    transformed { x, y ->
        val p = matrix.map(Offset(x, y))
        FloatFloatPair(p.x, p.y)
    }

private fun oval(): RoundedPolygon {
    val m = Matrix().apply { scale(1f, 0.64f) }
    return RoundedPolygon.circle().transformedBy(m).transformedBy(rotateNeg45)
}

private fun pill(): RoundedPolygon =
    customPolygon(
        listOf(
            PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
            PointNRound(Offset(1.001f, 0.428f)),
            PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f)),
        ),
        reps = 2,
        mirroring = true,
    )

private fun pentagon(): RoundedPolygon =
    customPolygon(
        listOf(
            PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
            PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
            PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f)),
        ),
        reps = 1,
        mirroring = true,
    )

private fun sunny(): RoundedPolygon =
    RoundedPolygon.star(
        numVerticesPerRadius = 8,
        innerRadius = 0.8f,
        rounding = cornerRound15,
    )

private fun cookie4(): RoundedPolygon =
    customPolygon(
        listOf(
            PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
            PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f)),
        ),
        reps = 4,
    )

private fun cookie9(): RoundedPolygon =
    RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.8f,
        rounding = cornerRound50,
    ).transformedBy(rotateNeg90)

private fun softBurst(): RoundedPolygon =
    customPolygon(
        listOf(
            PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
            PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f)),
        ),
        reps = 10,
    )

private data class PointNRound(
    val o: Offset,
    val r: CornerRounding = CornerRounding.Unrounded,
)

private fun doRepeat(
    points: List<PointNRound>,
    reps: Int,
    center: Offset,
    mirroring: Boolean,
): List<PointNRound> =
    if (mirroring) {
        buildList {
            val angles = points.map { (it.o - center).angleDegrees() }
            val distances = points.map { (it.o - center).getDistance() }
            val actualReps = reps * 2
            val sectionAngle = 360f / actualReps
            repeat(actualReps) { it ->
                points.indices.forEach { index ->
                    val i = if (it % 2 == 0) index else points.lastIndex - index
                    if (i > 0 || it % 2 == 0) {
                        val a =
                            (
                                sectionAngle * it +
                                    if (it % 2 == 0) angles[i]
                                    else sectionAngle - angles[i] + 2 * angles[0]
                                ).toRadians()
                        val finalPoint = Offset(cos(a), sin(a)) * distances[i] + center
                        add(PointNRound(finalPoint, points[i].r))
                    }
                }
            }
        }
    } else {
        val np = points.size
        (0 until np * reps).map {
            val point = points[it % np].o.rotateDegrees((it / np) * 360f / reps, center)
            PointNRound(point, points[it % np].r)
        }
    }

private fun Offset.rotateDegrees(angle: Float, center: Offset = Offset.Zero): Offset {
    val a = angle.toRadians()
    val off = this - center
    return Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
}

private fun Float.toRadians(): Float = this / 360f * 2 * PI.toFloat()

private fun Offset.angleDegrees(): Float = atan2(y, x) * 180f / PI.toFloat()

private fun customPolygon(
    pnr: List<PointNRound>,
    reps: Int,
    center: Offset = Offset(0.5f, 0.5f),
    mirroring: Boolean = false,
): RoundedPolygon {
    val actualPoints = doRepeat(pnr, reps, center, mirroring)
    return RoundedPolygon(
        vertices =
            FloatArray(actualPoints.size * 2) { ix ->
                val o = actualPoints[ix / 2].o
                if (ix % 2 == 0) o.x else o.y
            },
        perVertexRounding = actualPoints.map { it.r },
        centerX = center.x,
        centerY = center.y,
    )
}

// Spec timing / scale (LoadingIndicatorDefaults / tokens)
private const val ActiveIndicatorScale = 0.8f
private const val GlobalRotationDurationMillis = 4666
private const val MorphIntervalMillis = 650L
private const val FullRotation = 360f
private const val QuarterRotation = FullRotation / 4f
