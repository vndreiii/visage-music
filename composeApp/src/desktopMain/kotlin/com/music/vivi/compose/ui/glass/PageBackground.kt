package com.music.vivi.compose.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import org.koin.compose.koinInject

/** Cover-tinted page background used behind every page while the blur setting is on. */
@Composable
fun PageBackground(modifier: Modifier = Modifier.fillMaxSize(), nowPlayingStyle: Boolean = false) {
    val prefs = koinInject<DesktopPrefs>()
    rememberPrefsVersion(prefs)
    // On Now Playing the floating chrome blurs that page's own background, so mirror its style and darkness.
    val style = runCatching {
        if (nowPlayingStyle) PlayerBackgroundStyle.valueOf(prefs.getString(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name))
        else PlayerBackgroundStyle.valueOf(prefs.getString(PrefKeys.PageBackgroundStyle, PlayerBackgroundStyle.BLUR.name))
    }.getOrDefault(PlayerBackgroundStyle.BLUR)
    val darkness = if (nowPlayingStyle) prefs.getInt(PrefKeys.PlayerBackgroundDarkness, 0).coerceIn(0, 100) / 100f
    else prefs.getInt(PrefKeys.PageBackgroundDarkness, 50).coerceIn(0, 100) / 100f
    val track by LocalPlaybackController.current.nowPlaying.collectAsState()
    val scroll = LocalBackdropScroll.current
    // Drift down as the page scrolls: the layer is drawn taller than the window and slid up by a fraction of the
    // scroll distance (up to the spare height), which reads as the background sitting behind the page.
    Box(
        modifier
            .clipToBounds()
            .layout { measurable, c ->
                val extra = if (nowPlayingStyle) 0 else (c.maxHeight * 0.6f).toInt()
                val placeable = measurable.measure(Constraints.fixed(c.maxWidth, c.maxHeight + extra))
                layout(c.maxWidth, c.maxHeight) { placeable.place(0, 0) }
            },
    ) {
        PlayerBackgroundLayer(
            style = style,
            artworkUrl = track?.artworkUrl,
            showClearArtwork = false,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val spare = size.height * 0.6f / 1.6f
                translationY = -((scroll?.y ?: 0f) * 0.25f).coerceIn(0f, spare)
            },
            darknessOverride = darkness,
        )
    }
}
