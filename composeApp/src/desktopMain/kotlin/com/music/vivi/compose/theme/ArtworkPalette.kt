package com.music.vivi.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.max
import kotlin.math.min

/**
 * Colors derived from the current track's cover art — drives the whole app theme
 * (Material seed) and mini-player / now-playing gradients, matching tablet VIVI.
 */
data class ArtworkPalette(
    val seed: Color,
    /** Horizontal/vertical gradient stops: vibrant → darker → near-black */
    val gradient: List<Color>,
    /** Up to 6 vibrant samples for GLOW_ANIMATED orbs */
    val glowColors: List<Color> = gradient,
)

val LocalArtworkPalette = compositionLocalOf<ArtworkPalette?> { null }

/** Idle / no-track miniplayer & theme fallback — light blue-gray, never coral/red. */
val IdleArtworkPalette = ArtworkPalette(
    seed = Color(0xFF8BA3B8),
    gradient = listOf(
        Color(0xFF9BB0C2),
        Color(0xFF5C6F82),
        Color(0xFF1A2229),
    ),
    glowColors = listOf(
        Color(0xFF9BB0C2),
        Color(0xFF7A93A8),
        Color(0xFF5C6F82),
        Color(0xFF4A5D6E),
        Color(0xFF3A4A58),
        Color(0xFF2A3844),
    ),
)

@Composable
fun rememberArtworkPalette(artworkUrl: String?): ArtworkPalette? {
    var palette by remember { mutableStateOf<ArtworkPalette?>(null) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            palette = null
            return@LaunchedEffect
        }
        // Drop stale colors immediately so we don't keep the previous track's red/etc.
        palette = null
        palette = extractArtworkPalette(artworkUrl)
    }
    return palette
}

suspend fun extractArtworkPalette(url: String): ArtworkPalette? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = fetchImageBytes(url) ?: return@runCatching null
        val skia = Image.makeFromEncoded(bytes) ?: return@runCatching null
        val bitmap = Bitmap.makeFromImage(skia)
        val seeds = pickVibrantSeeds(bitmap, count = 6)
        val seed = seeds.firstOrNull() ?: return@runCatching null
        // Soft seed for Material Dynamic Color — MCU owns chroma/tone for contrast.
        // Keep stronger vividness only for decorative gradients/glow.
        val themeSeed = enhanceVividness(seed, satFactor = 1.05f, valueClamp = 0.28f..0.78f)
        val vivid = enhanceVividness(seed, satFactor = 1.2f, valueClamp = 0.35f..0.82f)
        val mid = vivid.copy(
            red = vivid.red * 0.55f,
            green = vivid.green * 0.55f,
            blue = vivid.blue * 0.55f,
        )
        val glow = seeds.map { enhanceVividness(it, satFactor = 1.15f, valueClamp = 0.35f..0.82f) }
            .ifEmpty { listOf(vivid) }
            .let { list ->
                if (list.size >= 6) list
                else list + List(6 - list.size) { i ->
                    enhanceVividness(list[i % list.size], 1f + (i % 3) * 0.08f)
                }
            }
        ArtworkPalette(
            seed = themeSeed,
            gradient = listOf(vivid, mid, Color.Black),
            glowColors = glow,
        )
    }.getOrNull()
}

private fun fetchImageBytes(url: String): ByteArray? {
    val candidates = listOf(url, url.substringBefore("=").takeIf { it != url && it.isNotBlank() })
        .filterNotNull()
        .distinct()
    for (candidate in candidates) {
        runCatching {
            val conn = URI(candidate).toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) VIVI/1.0")
            conn.connect()
            if (conn.responseCode in 200..299) {
                return conn.inputStream.use { it.readBytes() }
            }
        }
    }
    return null
}

private fun pickVibrantSeeds(bitmap: Bitmap, count: Int): List<Color> {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return emptyList()

    val step = max(1, min(w, h) / 48)
    data class Cand(val color: Color, val score: Float)
    val cands = mutableListOf<Cand>()
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var n = 0

    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val rgb = bitmap.getColor(x, y)
            val a = (rgb ushr 24) and 0xFF
            if (a < 200) {
                x += step
                continue
            }
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            rSum += r
            gSum += g
            bSum += b
            n++

            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f
            val maxC = max(rf, max(gf, bf))
            val minC = min(rf, min(gf, bf))
            val delta = maxC - minC
            val sat = if (maxC == 0f) 0f else delta / maxC
            val bri = maxC
            if (sat > 0.18f && bri in 0.15f..0.92f) {
                val score = sat * 1.4f + (1f - kotlin.math.abs(bri - 0.55f)) + delta
                cands += Cand(Color(rf, gf, bf), score)
            }
            x += step
        }
        y += step
    }

    if (cands.isEmpty()) {
        if (n == 0) return emptyList()
        return listOf(Color(rSum / n / 255f, gSum / n / 255f, bSum / n / 255f))
    }
    val sorted = cands.sortedByDescending { it.score }
    val out = mutableListOf<Color>()
    for (c in sorted) {
        if (out.any { similarHue(it, c.color) }) continue
        out += c.color
        if (out.size >= count) break
    }
    if (out.isEmpty()) out += sorted.first().color
    return out
}

private fun similarHue(a: Color, b: Color): Boolean {
    val (h1, _, _) = rgbToHsv(a.red, a.green, a.blue)
    val (h2, _, _) = rgbToHsv(b.red, b.green, b.blue)
    val d = kotlin.math.abs(h1 - h2).let { if (it > 0.5f) 1f - it else it }
    return d < 0.08f
}

private fun enhanceVividness(
    color: Color,
    satFactor: Float,
    valueClamp: ClosedFloatingPointRange<Float> = 0.35f..0.82f,
): Color {
    val (h, s, v) = rgbToHsv(color.red, color.green, color.blue)
    val ns = (s * satFactor).coerceAtMost(1f)
    val nv = (v * 0.92f).coerceIn(valueClamp.start, valueClamp.endInclusive)
    val (r, g, b) = hsvToRgb(h, ns, nv)
    return Color(r, g, b)
}

private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val d = maxC - minC
    val h = when {
        d == 0f -> 0f
        maxC == r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6f
        maxC == g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    val s = if (maxC == 0f) 0f else d / maxC
    return floatArrayOf(h, s, maxC)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
    val i = (h * 6).toInt()
    val f = h * 6 - i
    val p = v * (1 - s)
    val q = v * (1 - f * s)
    val t = v * (1 - (1 - f) * s)
    return when (i % 6) {
        0 -> floatArrayOf(v, t, p)
        1 -> floatArrayOf(q, v, p)
        2 -> floatArrayOf(p, v, t)
        3 -> floatArrayOf(p, q, v)
        4 -> floatArrayOf(t, p, v)
        else -> floatArrayOf(v, p, q)
    }
}

fun ArtworkPalette.contrastingOnSeed(): Color = contrastingOn(seed)
