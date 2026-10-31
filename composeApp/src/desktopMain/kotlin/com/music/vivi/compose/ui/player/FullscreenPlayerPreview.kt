package com.music.vivi.compose.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.lyricsync.LyricsParser
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.FullscreenCoverShape
import com.music.vivi.compose.prefs.FullscreenSettings
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.QueuePanelMode
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.theme.LocalPlayerInk
import com.music.vivi.compose.theme.ProvidePlayerInk
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.glass.ProvideGlass
import com.music.vivi.compose.ui.glass.glassSource
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import com.music.vivi.compose.ui.player.lyrics.LyricsBackground
import com.music.vivi.compose.ui.player.lyrics.LyricsList
import com.music.vivi.compose.ui.player.lyrics.LyricsGapStyle
import com.music.vivi.compose.ui.player.lyrics.lyricsAccent
import com.music.vivi.compose.ui.player.lyrics.lyricsConfig
import com.music.vivi.compose.ui.settings.PREVIEW_LRC
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import com.music.vivi.compose.ui.settings.rememberPreviewClock
import com.music.vivi.player.NowPlaying
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.RepeatMode
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

/**
 * The real fullscreen player layout ([FullscreenStage], [FullscreenControlsCard], [FullscreenCover], the background layer and
 * the lyrics list) fed with the sample data and fake controller that [com.music.vivi.compose.ui.settings.preview.SettingsPreviewFrame]
 * provides. Meant to fill that frame's 1280 x 720 virtual window.
 * It reads the same prefs as the screen (via [FullscreenSettings.resolve] on every pref change), so every setting shows at once.
 */
@Composable
fun FullscreenPlayerPreview(modifier: Modifier = Modifier) {
    val prefs = koinInject<DesktopPrefs>()
    val version = rememberPrefsVersion(prefs)
    val cfg = remember(version) { FullscreenSettings.resolve(prefs.reader()) }
    val lyricsCfg = remember(version) { prefs.lyricsConfig() }
    val queueMode = remember(version) {
        runCatching { QueuePanelMode.valueOf(prefs.getString(PrefKeys.QueuePanelMode, QueuePanelMode.PINNED_RIGHT.name)) }
            .getOrDefault(QueuePanelMode.PINNED_RIGHT)
    }
    val controller = LocalPlaybackController.current
    val sampleTrack = controller.nowPlaying.collectAsState().value ?: NowPlaying(videoId = "preview", title = "", artist = "", durationMs = 0L)
    val sampleQueue by controller.queueTracks.collectAsState()
    val playbackState by controller.state.collectAsState()
    val playing = playbackState == PlaybackState.Playing
    val favorite by controller.isFavorite.collectAsState()
    val volume by controller.volume.collectAsState()
    val playerGlass = remember { HazeState() }
    val lyricsClock = rememberPreviewClock()
    val parsed = remember { LyricsParser.parse(PREVIEW_LRC.trim()) }
    val timeline = remember(parsed, lyricsCfg.estimateWords, lyricsCfg.showBackgroundVocals, lyricsCfg.gapStyle) {
        LyricsTimeline.build(
            parsed, lyricsCfg.estimateWords,
            if (lyricsCfg.gapStyle == LyricsGapStyle.NONE) Long.MAX_VALUE / 4 else LyricsTimeline.DEFAULT_MIN_GAP_MS,
            lyricsCfg.showBackgroundVocals,
        )
    }
    val offset by rememberUpdatedState(lyricsCfg.syncOffsetMs)
    val clock = remember { derivedStateOf { lyricsClock.value + offset } }
    val inlineQueue = queueMode == QueuePanelMode.UNDER_PLAYBAR
    val upcoming = remember(sampleQueue) { sampleQueue.mapIndexedNotNull { i, t -> if (i > 1) i to t else null } }
    val previous = remember(sampleQueue) { sampleQueue.take(1).mapIndexed { i, t -> i to t } }
    val next = remember(sampleQueue) { sampleQueue.drop(2).mapIndexed { i, t -> (i + 2) to t } }

    ProvidePlayerInk(
        solid = cfg.backgroundStyle == PlayerBackgroundStyle.DEFAULT,
        backdrop = MaterialTheme.colorScheme.surface,
        darkness = prefs.getInt(PrefKeys.PlayerBackgroundDarkness, com.music.vivi.compose.prefs.LookDefaults.PlayerDarkness).coerceIn(0, 100) / 100f,
        framedControls = cfg.framedControls,
    ) {
        ProvideGlass(playerGlass) {
            Box(modifier.fillMaxSize()) {
                PreviewScene(cfg, lyricsCfg, timeline, clock, playerGlass, inlineQueue, upcoming, previous, next, sampleTrack, playing, favorite, volume)
            }
        }
    }
}

@Composable
private fun PreviewScene(
    cfg: FullscreenSettings,
    lyricsCfg: com.music.vivi.compose.ui.player.lyrics.LyricsConfig,
    timeline: LyricsTimeline,
    clock: androidx.compose.runtime.State<Float>,
    glass: HazeState,
    inlineQueue: Boolean,
    upcoming: List<Pair<Int, QueueTrack>>,
    previous: List<Pair<Int, QueueTrack>>,
    next: List<Pair<Int, QueueTrack>>,
    sampleTrack: NowPlaying,
    playing: Boolean,
    favorite: Boolean,
    volume: Float,
) {
    val ink = LocalPlayerInk.current
    val showLyrics = cfg.lyricsByDefault
    Box(Modifier.fillMaxSize()) {
        PlayerBackgroundLayer(
            style = cfg.backgroundStyle,
            artworkUrl = null,
            modifier = Modifier.fillMaxSize().glassSource(glass),
            canvasPlaying = false,
            showClearArtwork = !showLyrics,
        )
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 96.dp, bottom = 40.dp),
        ) {
            val ctrlLimit = if (cfg.limitControlsWidth) cfg.controlsMaxWidthDp.dp else Dp.Infinity
            FullscreenStage(
                width = maxWidth,
                height = maxHeight,
                side = cfg.coverSide,
                coverShown = !cfg.hideThumbnail,
                coverScale = cfg.coverScale,
                showLyrics = showLyrics,
                controlsMaxWidth = ctrlLimit,
                cover = {
                    FullscreenCover(
                        artworkUrl = null,
                        seed = "visage-preview",
                        canvasUrl = null,
                        playing = true,
                        shape = remember(cfg.coverShape) { MorphingCoverShape(cfg.coverShape) { 0f } },
                        morph = com.music.vivi.compose.ui.player.video.rememberCoverMorph(
                            com.music.vivi.compose.ui.player.video.VideoFraming.Fit, null,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                lyrics = {
                    Box(Modifier.fillMaxSize()) {
                        LyricsBackground(
                            mode = lyricsCfg.background, intensity = lyricsCfg.backgroundIntensity, artworkUrl = null,
                            palette = LocalArtworkPalette.current, playerBackgroundStyle = cfg.backgroundStyle,
                        )
                        LyricsList(
                            timeline = timeline, cfg = lyricsCfg, clock = clock,
                            accent = lyricsAccent(lyricsCfg.accent), onSeek = null,
                        )
                    }
                },
                controls = {
                    FullscreenControlsCard(
                        cfg = cfg,
                        track = sampleTrack,
                        stateName = (if (playing) PlaybackState.Playing else PlaybackState.Paused).name,
                        playing = playing,
                        isFavorite = favorite,
                        duration = sampleTrack.durationMs,
                        canPrev = true,
                        canNext = true,
                        shuffle = false,
                        repeat = RepeatMode.Off,
                        volume = volume,
                        inlineQueue = inlineQueue,
                        upcoming = upcoming,
                        previousPreview = previous,
                        nextPreview = next,
                        chromeMod = Modifier,
                        onOpenArtist = {}, onOpenAlbum = {}, onToggleFavorite = {}, onPrev = {}, onPlayPause = {}, onNext = {},
                        onShuffle = {}, onRepeat = {}, onRadio = {}, onVolumeChange = {}, onVolumeFinished = {}, onPlayIndex = {},
                    )
                },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconButton(Icons.Outlined.ArrowBack, "Back") {}
            Spacer(Modifier.weight(1f))
            PrefHorizontal(cfg.showQueueButton) {
                Row {
                    ToolIconButton(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", active = inlineQueue) {}
                    Spacer(Modifier.width(10.dp))
                }
            }
            ToolIconButton(Icons.Outlined.Lyrics, "Lyrics", active = showLyrics) {}
        }
        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
            PlainAnimatedVisibility(
                visible = cfg.showClock,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                label = "fsClockPreview",
            ) {
                Text("21:04", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ink.text(0.8f))
            }
        }
    }
}
