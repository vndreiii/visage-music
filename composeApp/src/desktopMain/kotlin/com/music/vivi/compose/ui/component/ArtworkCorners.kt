package com.music.vivi.compose.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.music.vivi.compose.theme.fetchImageBytesCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * Some artwork ships with rounded corners baked in (transparent pixels outside the radius). Shown in
 * our own rounded cover slot, those corners expose whatever is behind and look like a rounded picture
 * on a square container. This measures how far the transparency reaches and returns the zoom needed
 * to push it outside the slot: 1.0 for normal square art.
 */
internal fun artworkCornerBleed(encoded: ByteArray): Float = runCatching {
    val bitmap = Bitmap.makeFromImage(Image.makeFromEncoded(encoded))
    val w = bitmap.width
    val h = bitmap.height
    if (w < 16 || h < 16) return@runCatching 1f
    fun alphaAt(x: Int, y: Int) = (bitmap.getColor(x, y) ushr 24) and 0xFF
    if (alphaAt(0, 0) >= 250) return@runCatching 1f
    // Walk the corner diagonal until the picture becomes opaque.
    val limit = minOf(w, h) / 2
    var d = 0
    while (d < limit && alphaAt(d, d) < 250) d++
    val fraction = d.toFloat() / minOf(w, h)
    // Scale about the centre so the first opaque diagonal pixel lands on the slot's corner.
    (1f / (1f - 2f * fraction)).coerceIn(1f, 1.45f)
}.getOrDefault(1f)

@Composable
fun rememberArtworkCornerBleed(url: String?): Float {
    var bleed by remember(url) { mutableFloatStateOf(1f) }
    LaunchedEffect(url) {
        bleed = 1f
        if (url.isNullOrBlank()) return@LaunchedEffect
        bleed = withContext(Dispatchers.IO) { fetchImageBytesCached(url)?.let(::artworkCornerBleed) ?: 1f }
    }
    return bleed
}
