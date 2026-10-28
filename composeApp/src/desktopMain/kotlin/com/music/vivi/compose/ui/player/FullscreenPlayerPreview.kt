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
import androidx.compose.runtime.LaunchedEffect
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
import com.music.vivi.compose.ui.settings.previewOnly
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

/** Window size the preview lays the fullscreen player out at, before scaling it down into the settings page. */
private val VirtualWidth = 1280.dp
private val VirtualHeight = 720.dp

private val sampleTrack = NowPlaying(
    videoId = "preview-1", title = "Midnight Drive", artist = "Neon Harbor", album = "City Lights",
    durationMs = 215_000L,
)
private val sampleQueue = listOf(
    QueueTrack("preview-0", "Glass Hours", "Neon Harbor", durationMs = 190_000L),
    QueueTrack("preview-1", "Midnight Drive", "Neon Harbor", durationMs = 215_000L),
    QueueTrack("preview-2", "Afterglow", "Neon Harbor", durationMs = 201_000L),
    QueueTrack("preview-3", "Slow Tide", "Harbor Lights", durationMs = 233_000L),
    QueueTrack("preview-4", "Paper Moons", "Harbor Lights", durationMs = 178_000L),
)

/** A playback controller with a fixed sample track and a looping position; nothing it does reaches the real player. */
internal class PreviewPlaybackController : com.music.vivi.player.PlaybackController {
    override val state = MutableStateFlow(PlaybackState.Playing)
    override val nowPlaying = MutableStateFlow<NowPlaying?>(sampleTrack)
    override val positionMs = MutableStateFlow(42_000L)
    override val durationMs = MutableStateFlow(sampleTrack.durationMs)
    override val isFavorite = MutableStateFlow(true)
    override val lastError = MutableStateFlow<String?>(null)
    override val notice = MutableStateFlow<String?>(null)
    override fun clearNotice() {}
    override val queueIds = MutableStateFlow(sampleQueue.map { it.videoId })
    override val queueTracks = MutableStateFlow(sampleQueue)
    override val queueIndex = MutableStateFlow(1)
    override val queueLoading = MutableStateFlow(false)
    override val radioActive = MutableStateFlow(false)
    override val volume = MutableStateFlow(0.7f)
    override val shuffleEnabled = MutableStateFlow(false)
    override val repeatMode = MutableStateFlow(RepeatMode.Off)
    override suspend fun restoreQueue(tracks: List<QueueTrack>, startIndex: Int, positionMs: Long) {}
    override suspend fun playVideo(videoId: String) {}
    override suspend fun playQueue(tracks: List<QueueTrack>, startIndex: Int) {}
    override suspend fun playLocalQueue(tracks: List<QueueTrack>, startIndex: Int) {}
    override suspend fun startRadio(videoId: String) {}
    override fun playNext(track: QueueTrack) {}
    override fun addToQueue(track: QueueTrack) {}
    override fun moveQueueItem(from: Int, to: Int) {}
    override fun removeFromQueue(index: Int) {}
    override fun play() {}
    override fun pause() {}
    override fun toggleFromList() {}
    override fun stop() {}
    override fun seekTo(positionMs: Long) {}
    override fun next() {}
    override fun previous() {}
    override fun setVolume(volume: Float) {}
    override fun toggleShuffle() {}
    override fun cycleRepeatMode() {}
    override suspend fun toggleFavorite() {}
    override suspend fun setLiked(videoId: String, like: Boolean) {}
    override fun release() {}
}

/**
 * The real fullscreen player layout ([FullscreenStage], [FullscreenControlsCard], [FullscreenCover], the background layer and
 * the lyrics list) fed with sample data and a fake controller, laid out at 1280 x 720 and scaled to the width it is given.
 * It reads the same prefs as the screen (via [FullscreenSettings.resolve] on every pref change), so every setting shows at once.
 * The preview is not interactive.
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
    val controller = remember { PreviewPlaybackController() }
    LaunchedEffect(controller) {
        // A slow loop of the position so the seek bar and time labels move.
        while (true) {
            delay(250)
            val next = controller.positionMs.value + 250
            controller.positionMs.value = if (next >= controller.durationMs.value) 0L else next
        }
    }
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
    val upcoming = remember { sampleQueue.mapIndexedNotNull { i, t -> if (i > 1) i to t else null } }
    val previous = remember { listOf(0 to sampleQueue[0]) }
    val next = remember { sampleQueue.drop(2).mapIndexed { i, t -> (i + 2) to t } }

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).previewOnly()) {
        val scale = maxWidth / VirtualWidth
        CompositionLocalProvider(LocalPlaybackController provides controller) {
            ProvidePlayerInk(
                solid = cfg.backgroundStyle == PlayerBackgroundStyle.DEFAULT,
                backdrop = MaterialTheme.colorScheme.surface,
                darkness = prefs.getInt(PrefKeys.PlayerBackgroundDarkness, com.music.vivi.compose.prefs.LookDefaults.PlayerDarkness).coerceIn(0, 100) / 100f,
                framedControls = cfg.framedControls,
            ) {
                ProvideGlass(playerGlass) {
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(VirtualWidth, VirtualHeight)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    ) {
                        PreviewScene(cfg, lyricsCfg, timeline, clock, playerGlass, inlineQueue, upcoming, previous, next)
                    }
                }
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
                        stateName = PlaybackState.Playing.name,
                        playing = true,
                        isFavorite = true,
                        duration = sampleTrack.durationMs,
                        canPrev = true,
                        canNext = true,
                        shuffle = false,
                        repeat = RepeatMode.Off,
                        volume = 0.7f,
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
