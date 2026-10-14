package com.music.vivi.compose.ui.player.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import com.music.vivi.compose.theme.ArtworkPalette
import com.music.vivi.compose.theme.fetchImageBytesCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import kotlin.math.max

/**
 * Animated full-bleed background drawn by one of the [BackgroundShader]s, coloured from the cover.
 *
 * - The frame clock is only read in the draw phase, so nothing recomposes per frame.
 * - Palette colours animate between tracks instead of jumping.
 * - Shaders that sample the cover get a pre-shrunk copy; until it has loaded (or if it can't be
 *   fetched) the aurora shader is shown, so the background is never empty.
 */
@Composable
internal fun ShaderBackground(
    kind: BackgroundShader,
    palette: ArtworkPalette?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val image = if (kind.usesImage) rememberShrunkArtwork(artworkUrl, kind.imageEdge) else null
    val effective = if (kind.usesImage && image == null) BackgroundShader.AURORA else kind

    val effect = remember(effective) { runCatching { RuntimeEffect.makeForShader(effective.sksl) }.getOrNull() }
    if (effect == null) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }
    val builder = remember(effect) { RuntimeShaderBuilder(effect) }
    val imageShader = remember(image) {
        image?.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE))
    }

    val fallback = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surface,
    )
    val target = remember(palette, fallback) { shaderColors(palette, fallback) }
    val colors = target.map { animateColorAsState(it, tween(1100), label = "bgColor").value }

    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                builder.uniform("iResolution", size.width, size.height)
                builder.uniform("iTime", time.floatValue)
                colors.forEachIndexed { i, c -> builder.uniform("c$i", c.red, c.green, c.blue) }
                if (effective.usesImage && imageShader != null && image != null) {
                    builder.child("img", imageShader)
                    builder.uniform("imgSize", image.width.toFloat(), image.height.toFloat())
                }
                drawRect(ShaderBrush(builder.makeShader()))
            },
    )
}

/**
 * Five colours for the shaders: the vivid cover colours first, padded with lighter/darker
 * variants, never near-black (shaders darken them themselves) and never washed out.
 */
internal fun shaderColors(palette: ArtworkPalette?, fallback: List<Color>): List<Color> {
    val source = (palette?.glowColors.orEmpty() + palette?.gradient.orEmpty()).ifEmpty { fallback }
    val picked = ArrayList<Color>(5)
    for (c in source) {
        if (picked.size == 5) break
        val tooDark = c.luminance() < 0.02f
        val dup = picked.any { distance(it, c) < 0.12f }
        if (!tooDark && !dup) picked += c
    }
    var i = 0
    while (picked.size < 5) {
        val base = picked.getOrNull(i % max(1, picked.size)) ?: fallback.first()
        picked += if (i % 2 == 0) lighten(base, 0.25f) else darken(base, 0.35f)
        i++
    }
    return picked
}

private fun distance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
}

private fun lighten(c: Color, f: Float) = Color(c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f)
private fun darken(c: Color, f: Float) = Color(c.red * (1f - f), c.green * (1f - f), c.blue * (1f - f))

/** The cover shrunk to at most [edge] px (high-quality filtering), ready to use as a shader input. */
@Composable
private fun rememberShrunkArtwork(url: String?, edge: Int): Image? {
    var image by remember(url, edge) { mutableStateOf<Image?>(null) }
    LaunchedEffect(url, edge) {
        image = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val bytes = fetchImageBytesCached(url) ?: return@runCatching null
                shrink(Image.makeFromEncoded(bytes), edge)
            }.getOrNull()
        }
    }
    return image
}

private fun shrink(src: Image, edge: Int): Image {
    val scale = minOf(1f, edge.toFloat() / max(src.width, src.height))
    val w = max(2, (src.width * scale).toInt())
    val h = max(2, (src.height * scale).toInt())
    val surface = Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(
        src,
        Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        Rect.makeWH(w.toFloat(), h.toFloat()),
        FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        Paint(),
        true,
    )
    return surface.makeImageSnapshot()
}
