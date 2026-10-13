package com.music.vivi.compose.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult

private val googleSizeSuffix = Regex("""=w\d+-h\d+[^/]*$|=s\d+[^/]*$""")

/** Best-quality variant of a YouTube Music / Google artwork URL (others unchanged). */
fun hqArtworkUrl(url: String): String = when {
    url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
        if (googleSizeSuffix.containsMatchIn(url)) url.replace(googleSizeSuffix, "=w1200-h1200-l90-rj") else "$url=w1200-h1200-l90-rj"
    else -> url
}

/**
 * Artwork URL that is already decoded in Coil's cache, so whatever shows it can animate
 * straight to the final high-quality image (no low-res frame, no second transition).
 * Holds the previous value until the new one is ready.
 */
@Composable
fun rememberReadyArtwork(url: String?): String? {
    val context = LocalPlatformContext.current
    var ready by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        if (url == null) {
            ready = null
            return@LaunchedEffect
        }
        val loader = SingletonImageLoader.get(context)
        for (candidate in listOf(hqArtworkUrl(url), url).distinct()) {
            val result = loader.execute(ImageRequest.Builder(context).data(candidate).build())
            if (result is SuccessResult) {
                ready = candidate
                return@LaunchedEffect
            }
        }
        ready = url
    }
    return ready
}
