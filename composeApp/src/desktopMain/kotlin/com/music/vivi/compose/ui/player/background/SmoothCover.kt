package com.music.vivi.compose.ui.player.background

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.vivi.compose.ui.motion.VisageMotion

/**
 * Cover image that never pops: the previous cover stays up until the next one has finished
 * loading, then the new one fades in over it. Used behind shaders so a track change can't flash.
 */
@Composable
internal fun SmoothCover(url: String?, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    var base by remember { mutableStateOf(url) }
    var incoming by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(url) {
        when {
            url == null -> { base = null; incoming = null }
            url == base -> incoming = null
            else -> { ready = false; fade.snapTo(0f); incoming = url }
        }
    }
    LaunchedEffect(incoming, ready) {
        if (incoming != null && ready) {
            fade.animateTo(1f, VisageMotion.ambient())
            base = incoming
            incoming = null
            ready = false
            fade.snapTo(0f)
        }
    }
    Box(modifier) {
        base?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = colorFilter) }
        incoming?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                onSuccess = { ready = true },
            )
        }
    }
}
