package com.music.vivi.compose.ui.settings.preview

/*
 * Live previews for the Interface and Now Playing settings pages. Each one is the REAL component (top-bar tabs, MiniPlayerBar,
 * PageBackground, the Now Playing controls) inside a [SettingsPreviewFrame], reading the user's prefs through the same code the
 * app uses and observing them itself, so every setting shows the moment it changes.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.MiniPlayerStyle
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.theme.ProvidePlayerInk
import com.music.vivi.compose.ui.MiniPlayerBar
import com.music.vivi.compose.ui.TopBarBrand
import com.music.vivi.compose.ui.TopNavItems
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.glass.PageBackground
import com.music.vivi.compose.ui.glass.ProvideGlass
import com.music.vivi.compose.ui.glass.glassSource
import com.music.vivi.compose.ui.glass.rememberGlassEnabled
import com.music.vivi.compose.ui.player.NowPlayingBottomBar
import com.music.vivi.compose.ui.player.NowPlayingPaneGap
import com.music.vivi.compose.ui.player.NowPlayingSeekSection
import com.music.vivi.compose.ui.player.NowPlayingTrackActions
import com.music.vivi.compose.ui.player.NowPlayingTrackInfo
import com.music.vivi.compose.ui.player.TransportRow
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import com.music.vivi.compose.ui.player.nowPlayingControlsWidth
import com.music.vivi.compose.ui.player.nowPlayingCoverRadius
import com.music.vivi.compose.ui.player.nowPlayingIdleMetrics
import com.music.vivi.compose.ui.player.nowPlayingLook
import com.music.vivi.compose.ui.player.playerControlsSurface
import com.music.vivi.compose.ui.resolveMiniPlayerStyle
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import com.music.vivi.player.PlaybackState
import dev.chrisbanes.haze.HazeState
import org.koin.compose.koinInject

/**
 * The app's backdrop for a preview: the theme background, plus the real [PageBackground] (as a glass source) while the blur
 * setting is on, exactly as the app window does. Glass surfaces inside use this preview's own haze state.
 */
@Composable
internal fun PreviewAppSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glassOn = rememberGlassEnabled()
    val hazeState = remember { HazeState() }
    val pageHaze = remember { HazeState() }
    ProvideGlass(hazeState, pageState = pageHaze) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (glassOn) {
                Box(Modifier.fillMaxSize().glassSource(hazeState).glassSource(pageHaze)) { PageBackground() }
            }
            content()
        }
    }
}

/** Virtual window sizes the previews below are meant to be framed with. */
object AppPreviewSizes {
    const val TopBarWidth = 900
    const val TopBarHeight = 96
    const val MiniPlayerWidth = 1000
    const val MiniPlayerHeight = 260
    const val PageBackgroundWidth = 1000
    const val PageBackgroundHeight = 420
}

/** The top bar's brand ([TopBarBrand]) and tab chips ([TopNavItems]): the same composables the window bar uses. */
@Composable
fun TopBarPreview() {
    PreviewAppSurface {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            TopBarBrand(title = "Home", onLogoClick = {}, modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight())
            TopNavItems(
                destinations = Routes.mainDestinations,
                currentRoute = Routes.HOME,
                onNavigate = {},
                anchors = false,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The real [MiniPlayerBar] at the bottom of a sample page; it reads style, background and darkness from the prefs itself. */
@Composable
fun MiniPlayerPreview() {
    val prefs = koinInject<DesktopPrefs>()
    val version = rememberPrefsVersion(prefs)
    val style = remember(version) { resolveMiniPlayerStyle(prefs) }
    PreviewAppSurface {
        Box(Modifier.fillMaxSize()) {
            PreviewHomePane(PreviewPage.HOME)
            MiniPlayerBar(
                onExpand = null,
                interactive = false,
                modifier = Modifier.align(if (style == MiniPlayerStyle.COMPACT_EDGE) Alignment.BottomEnd else Alignment.BottomCenter),
            )
        }
    }
}

/** The real [PageBackground] (as every screen gets it with the blur setting on) behind a sample page. */
@Composable
fun PageBackgroundPreview() {
    PreviewAppSurface {
        // PreviewAppSurface only draws the page background with blur on; this preview exists to show it, so always draw it.
        if (!rememberGlassEnabled()) PageBackground()
        PreviewHomePane(PreviewPage.HOME)
    }
}

/**
 * Now Playing at idle: the real background layer, cover, title row, seek bar, transport row and bottom bar, laid out by the
 * same metrics as the screen and styled by [nowPlayingLook] (cover shape / hidden, framed controls, button colour, seek style,
 * play/pause height, width limit, volume control, background and its darkness).
 */
@Composable
fun NowPlayingPreview() {
    val prefs = koinInject<DesktopPrefs>()
    val version = rememberPrefsVersion(prefs)
    val look = remember(version) { prefs.nowPlayingLook() }
    val controller = LocalPlaybackController.current
    val track by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val volume by controller.volume.collectAsState()
    val shuffle by controller.shuffleEnabled.collectAsState()
    val repeat by controller.repeatMode.collectAsState()
    val isFavorite by controller.isFavorite.collectAsState()
    val playing = state == PlaybackState.Playing
    val glass = remember { HazeState() }

    ProvidePlayerInk(
        solid = look.backgroundStyle == com.music.vivi.compose.prefs.PlayerBackgroundStyle.DEFAULT,
        backdrop = MaterialTheme.colorScheme.surface,
        darkness = look.darkness,
        framedControls = look.framedControls,
    ) {
        ProvideGlass(glass) {
            Box(Modifier.fillMaxSize()) {
                PlayerBackgroundLayer(
                    style = look.backgroundStyle,
                    artworkUrl = track?.artworkUrl,
                    canvasPlaying = false,
                    modifier = Modifier.fillMaxSize().glassSource(glass),
                )
                BoxWithConstraints(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 16.dp)) {
                    val m = nowPlayingIdleMetrics(maxWidth, maxHeight, look.limitControlsWidth, look.controlsMaxWidth)
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            Modifier.width(m.contentWidth).weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NowPlayingPaneGap),
                        ) {
                            Box(Modifier.width(m.coverSize).height(m.coverSize), contentAlignment = Alignment.Center) {
                                if (!look.hideCover) {
                                    val shape = RoundedCornerShape(nowPlayingCoverRadius(look.squareCover))
                                    CoverImage(
                                        track?.artworkUrl, track?.videoId ?: "preview",
                                        Modifier.fillMaxSize().shadow(28.dp, shape, clip = false).clip(shape),
                                    )
                                }
                            }
                            Column(Modifier.width(m.stackWidth).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                                Column(
                                    Modifier.align(Alignment.CenterHorizontally)
                                        .nowPlayingControlsWidth(look.limitControlsWidth, look.controlsMaxWidth)
                                        .playerControlsSurface(look.framedControls),
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                        NowPlayingTrackInfo(track, state.name, onOpenArtist = {}, onOpenAlbum = {}, modifier = Modifier.weight(1f))
                                        NowPlayingTrackActions(isFavorite, onDownload = {}, onFavorite = {})
                                    }
                                    Spacer(Modifier.height(28.dp))
                                    NowPlayingSeekSection(duration, look.sliderStyle, look.squiggly)
                                    Spacer(Modifier.height(28.dp))
                                    TransportRow(
                                        playing = playing, canPrev = true, canNext = true, enabledPlay = true,
                                        style = look.buttonsStyle, playPauseHeight = look.playPauseHeight,
                                        onPrev = {}, onPlayPause = {}, onNext = {},
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        NowPlayingBottomBar(
                            modifier = Modifier.width(m.contentWidth),
                            queueActive = false, lyricsActive = false, shuffle = shuffle, repeat = repeat,
                            volume = volume, volumeStyle = look.volumeStyle,
                            onQueue = {}, onLyrics = {}, onShuffle = {}, onRepeat = {}, onFullscreen = {},
                            onVolumeChange = {}, onVolumeFinished = {},
                            more = {},
                        )
                    }
                }
            }
        }
    }
}
