package com.music.vivi.compose.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
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
    // The background drifts down as the page scrolls (a quarter of the distance) and never runs out: it is drawn as
    // window-sized tiles where every other tile is mirrored, so the seam between two tiles is continuous and the
    // pattern repeats forever. Two tiles are enough to cover the window at any offset. Now Playing does not drift.
    Box(modifier.clipToBounds()) {
        for (slot in 0..(if (nowPlayingStyle) 0 else 1)) {
            Box(
                Modifier
                    // Each tile reaches 2px into its neighbours: where two clipped layers meet on a fractional pixel
                    // both edges are anti-aliased and the black behind them shows as a hairline. The content is
                    // mirrored around the join, so the overlap is invisible.
                    .layout { measurable, c ->
                        val ov = Overlap.dp.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(c.maxWidth, c.maxHeight + 2 * ov))
                        layout(c.maxWidth, c.maxHeight) { placeable.place(0, -ov) }
                    }
                    .graphicsLayer {
                        val ov = Overlap.dp.toPx()
                        val h = size.height - 2 * ov
                        val drift = if (nowPlayingStyle || h <= 0f) 0f else (scroll?.y ?: 0f) * 0.25f
                        val period = 2f * h
                        val p = if (period > 0f) ((drift % period) + period) % period else 0f
                        val tile = (p / h).toInt().coerceAtLeast(0) + slot
                        translationY = tile * h - p
                        scaleY = if (tile % 2 == 1) -1f else 1f
                        clip = true
                    },
            ) {
                // The layer blurs its own edges, which fades them to transparent. Draw it larger than the tile and let
                // the tile clip the excess, so that faded rim never shows (no dark band at the seams or window sides).
                Box(
                    Modifier.layout { measurable, c ->
                        val pad = BleedDp.dp.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(c.maxWidth + 2 * pad, c.maxHeight + 2 * pad))
                        layout(c.maxWidth, c.maxHeight) { placeable.place(-pad, -pad) }
                    },
                ) {
                    PlayerBackgroundLayer(
                        style = style,
                        artworkUrl = track?.artworkUrl,
                        showClearArtwork = false,
                        modifier = Modifier.fillMaxSize(),
                        darknessOverride = darkness,
                    )
                }
            }
        }
    }
}

/** How far past the tile the background layer is drawn; covers the reach of its 150dp blur. */
private const val BleedDp = 320

/** Pixels each tile extends past its slot on both sides. */
private const val Overlap = 2
