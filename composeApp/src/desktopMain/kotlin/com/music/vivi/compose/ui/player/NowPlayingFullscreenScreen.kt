package com.music.vivi.compose.ui.player

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.LocalWindowState
import com.music.vivi.compose.WindowFullscreen
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.FullscreenCoverShape
import com.music.vivi.compose.prefs.FullscreenLayout
import com.music.vivi.compose.prefs.FullscreenSettings
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.VolumeControlStyle
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.ui.artist.ArtistLinks
import com.music.vivi.compose.ui.artist.openArtistByName
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.VisageBackButton
import com.music.vivi.compose.ui.component.rememberReadyArtwork
import com.music.vivi.compose.ui.motion.VisageMotion
import com.music.vivi.compose.ui.player.background.CanvasVideoSurface
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import com.music.vivi.compose.ui.player.swap.songSwapBackground
import com.music.vivi.compose.ui.player.swap.songSwapControls
import com.music.vivi.compose.ui.player.swap.songSwapCover
import com.music.vivi.compose.ui.player.swap.songSwapViewport
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import com.music.vivi.compose.ui.player.lyrics.LyricsPanel
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun FullscreenCoverShape.toShape(): Shape = when (this) {
    FullscreenCoverShape.ROUNDED -> RoundedCornerShape(20.dp)
    FullscreenCoverShape.SOFT -> RoundedCornerShape(44.dp)
    FullscreenCoverShape.SQUARE -> RoundedCornerShape(4.dp)
    FullscreenCoverShape.CIRCLE -> CircleShape
}

/**
 * [FullscreenCoverShape] as an outline that stays sensible when the cover morphs to 16:9:
 * a circle relaxes into a rounded rectangle as [progress] goes 0 -> 1.
 */
private class MorphingCoverShape(
    private val base: FullscreenCoverShape,
    private val progress: () -> Float,
) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val half = minOf(size.width, size.height) / 2f
        val r = with(density) {
            when (base) {
                FullscreenCoverShape.ROUNDED -> 20.dp.toPx()
                FullscreenCoverShape.SOFT -> 44.dp.toPx()
                FullscreenCoverShape.SQUARE -> 4.dp.toPx()
                FullscreenCoverShape.CIRCLE -> {
                    val p = progress().coerceIn(0f, 1f)
                    half + (28.dp.toPx() - half) * p
                }
            }
        }.coerceIn(0f, half)
        return androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r, r)),
        )
    }
}

/** Fully transparent cursor shown while the controls are auto-hidden. */
private val blankCursor: PointerIcon by lazy {
    runCatching {
        val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        PointerIcon(java.awt.Toolkit.getDefaultToolkit().createCustomCursor(img, java.awt.Point(0, 0), "blank"))
    }.getOrDefault(PointerIcon.Default)
}

/** Swallows pointer input before children see it, so invisible (faded) controls cannot be clicked. */
private fun Modifier.blockPointer(block: Boolean): Modifier = if (!block) this else pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

@Composable
private fun PrefHorizontal(visible: Boolean, content: @Composable () -> Unit) {
    PlainAnimatedVisibility(
        visible,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
        label = "fsPrefH",
    ) { content() }
}

@Composable
private fun PrefVertical(visible: Boolean, content: @Composable () -> Unit) {
    PlainAnimatedVisibility(
        visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        label = "fsPrefV",
    ) { content() }
}

/**
 * Immersive fullscreen Now Playing. Honours the Now Playing preferences (background, canvas, buttons,
 * seek bar, controls width, ...) with optional overrides from Settings -> Fullscreen player.
 */
@Composable
fun NowPlayingFullscreenScreen(navController: NavController) {
    val controller = LocalPlaybackController.current
    val windowState = LocalWindowState.current
    val prefs = koinInject<DesktopPrefs>()
    val prefsVersion = com.music.vivi.compose.ui.settings.rememberPrefsVersion(prefs)
    val cfg = remember(prefsVersion) { FullscreenSettings.resolve(prefs.reader()) }
    val scope = rememberCoroutineScope()

    DisposableEffect(windowState) {
        WindowFullscreen.enter(windowState)
        onDispose { WindowFullscreen.exit(windowState) }
    }
    fun exitFullscreen() {
        WindowFullscreen.exit(windowState)
        navController.popBackStack()
    }

    val nowPlaying by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    val positionState = controller.positionMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val isFavorite by controller.isFavorite.collectAsState()
    val volume by controller.volume.collectAsState()
    val shuffle by controller.shuffleEnabled.collectAsState()
    val repeat by controller.repeatMode.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
    val queueTracks by controller.queueTracks.collectAsState()
    var scrubVolume by remember { mutableStateOf(false) }
    var scrubVol by remember { mutableFloatStateOf(volume) }
    val displayVolume = if (scrubVolume) scrubVol else volume

    // Song swappings: while a driven swap runs, the layers keep showing the outgoing song.
    val swap = com.music.vivi.compose.ui.player.swap.rememberPlayerSongSwap(controller, prefs)
    val track = swap.controlsTrack(nowPlaying)
    val coverTrack = if (swap.active) swap.displayed else track
    val duration = durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L
    val playing = state == PlaybackState.Playing
    val hasPosition by remember { derivedStateOf { positionState.value > 0L } }
    val canPrev = queueIndex > 0 || hasPosition
    val canNext = track != null
    val upcoming = remember(queueTracks, queueIndex) {
        queueTracks.mapIndexedNotNull { i, t -> if (i > queueIndex) i to t else null }
    }

    var showLyrics by remember { mutableStateOf(cfg.lyricsByDefault) }
    com.music.vivi.compose.keys.ShortcutEventEffect { e ->
        if (e == com.music.vivi.compose.keys.ShortcutEvent.ToggleLyrics) showLyrics = !showLyrics
    }
    var showQueue by remember { mutableStateOf(false) }

    // Canvas video (same resolver as Now Playing).
    var canvasArt by remember { mutableStateOf<com.music.vivi.canvas.CanvasArtwork?>(null) }
    LaunchedEffect(track?.videoId, cfg.canvasEnabled, cfg.canvasSource) {
        canvasArt = null
        if (!cfg.canvasEnabled || track == null) return@LaunchedEffect
        canvasArt = withContext(Dispatchers.IO) {
            runCatching {
                CanvasResolver.resolve(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    source = cfg.canvasSource,
                )
            }.getOrNull()
        }
    }
    // Music video (when enabled and available) replaces the canvas: video -> canvas -> cover.
    val trackVideo = com.music.vivi.compose.ui.player.video.rememberTrackVideo(track)
    // "Original size" framing: cover width stays, height morphs to width / video aspect.
    val coverMorph = com.music.vivi.compose.ui.player.video.rememberCoverMorph(
        com.music.vivi.compose.ui.player.video.rememberVideoFraming(prefs),
        trackVideo?.url,
    )
    val canvasSquareUrl = if (trackVideo != null) null else CanvasResolver.squareUrl(canvasArt)
    val canvasTallUrl = if (trackVideo != null) null else CanvasResolver.tallUrl(canvasArt)
    val appleBg = cfg.backgroundStyle == PlayerBackgroundStyle.APPLE_MUSIC
    val bgCanvasUrl = if (appleBg) canvasTallUrl ?: canvasSquareUrl else canvasSquareUrl
    val hideCover = cfg.hideThumbnail || (appleBg && bgCanvasUrl != null)
    val readyArt = rememberReadyArtwork(track?.artworkUrl)

    // Auto-hide: any pointer activity reveals the chrome; idle for N seconds fades it (and the cursor).
    val activityStamp = remember { LongArray(1) { System.nanoTime() } }
    var chromeVisible by remember { mutableStateOf(true) }
    val chromeShown = !cfg.autoHide || chromeVisible
    LaunchedEffect(cfg.autoHide, cfg.autoHideSeconds) {
        chromeVisible = true
        activityStamp[0] = System.nanoTime()
        if (!cfg.autoHide) return@LaunchedEffect
        while (true) {
            delay(250)
            val idleMs = (System.nanoTime() - activityStamp[0]) / 1_000_000L
            if (chromeVisible && idleMs > cfg.autoHideSeconds * 1000L) chromeVisible = false
        }
    }
    val chromeAlpha by animateFloatAsState(
        if (chromeShown) 1f else 0f,
        if (chromeShown) VisageMotion.effectsDefault() else VisageMotion.effectsSlow(),
        label = "fsChromeAlpha",
    )
    val chromeMod = Modifier
        .graphicsLayer { alpha = chromeAlpha }
        .blockPointer(!chromeShown)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        activityStamp[0] = System.nanoTime()
                        if (!chromeVisible) chromeVisible = true
                    }
                }
            }
            .then(if (!chromeShown) Modifier.pointerHoverIcon(blankCursor, overrideDescendants = true) else Modifier),
    ) {
        PlayerBackgroundLayer(
            style = cfg.backgroundStyle,
            artworkUrl = if (swap.active) swap.displayedArt else readyArt,
            modifier = Modifier.fillMaxSize()
                .songSwapViewport(swap)
                .songSwapBackground(swap),
            canvasVideoUrl = bgCanvasUrl,
            canvasPlaying = playing,
            canvasBlurred = !appleBg,
            showClearArtwork = !showLyrics,
        )

        com.music.vivi.compose.ui.player.swap.SongSwapLoadingIndicator(swap.anim, Modifier.fillMaxSize())

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 96.dp, bottom = 40.dp),
        ) {
            val w = maxWidth
            val h = maxHeight
            val vertical = cfg.layout == FullscreenLayout.CENTERED
            val coverShown = !hideCover && !showLyrics
            // With lyrics open the cover slot is gone; a playing music video takes the room above the controls.
            val lyricsVideo = showLyrics && !hideCover && trackVideo != null
            val baseGap = if (vertical) 32.dp else 48.dp
            val autoCover = if (vertical) {
                minOf(h - 380.dp, w * 0.5f, 520.dp).coerceAtLeast(160.dp)
            } else {
                minOf(h, w * 0.42f, 600.dp)
            }
            val baseCover = autoCover * cfg.coverScale
            // A real video should be big: as the cover morphs to the video's aspect, its width grows to
            // fill most of the page (heights left for the controls when centred, space beside the controls otherwise).
            val wideProgress = ((coverMorph.aspect - 1f) / 0.78f).coerceIn(0f, 1f)
            val wideWidth = if (vertical) {
                minOf(w, (h - 330.dp) * coverMorph.aspect, 1500.dp)
            } else {
                minOf(w - 360.dp - baseGap, h * coverMorph.aspect, 1300.dp)
            }.coerceAtLeast(baseCover)
            val coverSize = baseCover + (wideWidth - baseCover) * wideProgress
            val ctrlLimit = if (cfg.limitControlsWidth) cfg.controlsMaxWidthDp.dp else 520.dp
            val idleCtrlW = when {
                vertical || hideCover -> minOf(ctrlLimit, w)
                else -> minOf(ctrlLimit, (w - coverSize - baseGap).coerceAtLeast(320.dp))
            }
            val lyricsMainW = (w * 0.46f).coerceAtMost(560.dp)
            val spring = VisageMotion.glide<Dp>()
            val mainW by animateDpAsState(if (showLyrics) lyricsMainW else w, spring, label = "fsMainW")
            val ctrlW by animateDpAsState(
                if (showLyrics) minOf(ctrlLimit, lyricsMainW) else idleCtrlW, spring, label = "fsCtrlW",
            )
            val coverDim by animateDpAsState(if (coverShown) coverSize else 0.dp, spring, label = "fsCoverDim")
            val coverGap by animateDpAsState(if (coverShown) baseGap else 0.dp, spring, label = "fsCoverGap")
            val paneGap by animateDpAsState(if (showLyrics) 32.dp else 0.dp, spring, label = "fsPaneGap")
            val lyricsW = (w - mainW - paneGap).coerceAtLeast(0.dp)

            val coverContent: @Composable () -> Unit = {
                val coverH = coverSize / coverMorph.aspect
                val slotMod = if (vertical) Modifier.width(coverSize).height(coverDim * (coverH / coverSize)) else Modifier.width(coverDim).height(coverH)
                Box(slotMod, contentAlignment = Alignment.Center) {
                    PlainAnimatedVisibility(
                        visible = coverShown,
                        enter = fadeIn() + scaleIn(initialScale = 0.92f),
                        exit = fadeOut() + scaleOut(targetScale = 0.92f),
                        label = "fsCover",
                    ) {
                        AlbumGestureCard(controller, Modifier.requiredWidth(coverSize).requiredHeight(coverH).songSwapCover(swap)) {
                            FullscreenCover(
                                artworkUrl = coverTrack?.artworkUrl,
                                seed = coverTrack?.videoId ?: coverTrack?.title ?: "none",
                                canvasUrl = canvasSquareUrl,
                                video = trackVideo,
                                playing = playing,
                                shape = remember(cfg.coverShape, coverMorph) { MorphingCoverShape(cfg.coverShape) { coverMorph.progress } },
                                morph = coverMorph,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            val lyricsVideoCard: @Composable () -> Unit = {
                val cardShape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .width(ctrlW)
                        .height(minOf(ctrlW * 9f / 16f, h - 340.dp).coerceAtLeast(120.dp))
                        .shadow(24.dp, cardShape, clip = false)
                        .clip(cardShape)
                        .background(Color.Black),
                ) {
                    trackVideo?.let {
                        com.music.vivi.compose.ui.player.video.MusicVideoSurface(video = it, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            val controlsContent: @Composable () -> Unit = {
                Column(
                    Modifier
                        .width(ctrlW)
                        .songSwapControls(swap)
                        .playerControlsSurface(cfg.framedControls),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        PrefHorizontal(showLyrics && !hideCover && !lyricsVideo) {
                            CoverImage(
                                model = track?.artworkUrl,
                                seed = track?.videoId ?: "none",
                                modifier = Modifier
                                    .padding(end = 14.dp, top = 4.dp)
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                track?.title ?: "Nothing playing",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val credits = track?.artist.orEmpty()
                            if (credits.isBlank()) {
                                Text(
                                    state.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            } else {
                                PrefVertical(cfg.showArtist) {
                                    ArtistLinks(
                                        credits = credits,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        hoverColor = Color.White,
                                        onOpen = {
                                            WindowFullscreen.exit(windowState)
                                            navController.openArtistByName(it)
                                        },
                                        maxLines = 2,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                            val album = track?.album.orEmpty()
                            PrefVertical(cfg.showAlbum && album.isNotBlank()) {
                                Text(
                                    album,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        ToolIconButton(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            "Favorite",
                            active = isFavorite,
                        ) { scope.launch { controller.toggleFavorite() } }
                    }

                    Column(chromeMod) {
                        Spacer(Modifier.height(24.dp))
                        NowPlayingSeekSection(
                            duration = duration,
                            sliderStyle = cfg.sliderStyle,
                            squiggly = cfg.squiggly,
                            showTimes = cfg.showTimes,
                        )
                        Spacer(Modifier.height(20.dp))
                        TransportRow(
                            playing = playing,
                            canPrev = canPrev || track != null,
                            canNext = canNext,
                            enabledPlay = track != null || state != PlaybackState.Idle,
                            style = cfg.buttonsStyle,
                            playPauseHeight = cfg.playPauseHeightDp.dp,
                            onPrev = { if (canPrev) controller.previous() },
                            onPlayPause = { if (playing) controller.pause() else controller.play() },
                            onNext = { controller.next() },
                            previousTracks = queuePreviewIndices(queueIndex, queueTracks.size, true, 3).map { it to queueTracks[it] },
                            nextTracks = queuePreviewIndices(queueIndex, queueTracks.size, false, 3).map { it to queueTracks[it] },
                            onPickTrack = { index -> scope.launch(Dispatchers.IO) { controller.playQueue(queueTracks, index) } },
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PrefHorizontal(cfg.showShuffle) {
                                Row {
                                    ToolIconButton(Icons.Outlined.Shuffle, "Shuffle", active = shuffle) { controller.toggleShuffle() }
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                            PrefHorizontal(cfg.showRepeat) {
                                Row {
                                    ToolIconButton(
                                        if (repeat == RepeatMode.One) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                                        "Repeat",
                                        active = repeat != RepeatMode.Off,
                                    ) { controller.cycleRepeatMode() }
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                            PrefHorizontal(cfg.showRadio) {
                                Row {
                                    ToolIconButton(Icons.Outlined.Radio, "Start radio") {
                                        track?.let { t -> scope.launch { runCatching { controller.startRadio(t.videoId) } } }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                PrefHorizontal(cfg.showVolume) {
                                    VolumeGroup(
                                        style = cfg.volumeStyle,
                                        volume = displayVolume,
                                        onChange = {
                                            scrubVolume = true
                                            scrubVol = it
                                            controller.setVolume(it)
                                        },
                                        onFinished = {
                                            scrubVolume = false
                                            prefs.setFloat(PrefKeys.PlaybackVolume, scrubVol)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(mainW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (showLyrics) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            PlainAnimatedVisibility(
                                visible = lyricsVideo,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                label = "fsLyricsVideo",
                            ) {
                                Column {
                                    lyricsVideoCard()
                                    Spacer(Modifier.height(20.dp))
                                }
                            }
                            controlsContent()
                        }
                    } else if (vertical) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            coverContent()
                            Spacer(Modifier.height(coverGap))
                            controlsContent()
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (cfg.layout == FullscreenLayout.COVER_LEFT) {
                                coverContent(); Spacer(Modifier.width(coverGap)); controlsContent()
                            } else {
                                controlsContent(); Spacer(Modifier.width(coverGap)); coverContent()
                            }
                        }
                    }
                }
                Spacer(Modifier.width(paneGap))
                Box(Modifier.width(lyricsW).fillMaxHeight()) {
                    PlainAnimatedVisibility(
                        visible = showLyrics,
                        enter = fadeIn() + slideInHorizontally { it / 8 },
                        exit = fadeOut() + slideOutHorizontally { it / 8 },
                        label = "fsLyrics",
                    ) {
                        LyricsPanel(
                            onClose = { showLyrics = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Up-next overlay.
        Box(Modifier.fillMaxSize().padding(top = 96.dp, end = 32.dp, bottom = 32.dp), contentAlignment = Alignment.CenterEnd) {
            PlainAnimatedVisibility(
                visible = showQueue,
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut() + slideOutHorizontally { it / 2 },
                label = "fsQueue",
            ) {
                Column(
                    Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(20.dp),
                ) {
                    QueuePillStrip(
                        upcoming = upcoming,
                        onPlayIndex = { index -> scope.launch(Dispatchers.IO) { controller.playQueue(queueTracks, index) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Toolbar.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .then(chromeMod),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisageBackButton(onClick = ::exitFullscreen)
            Spacer(Modifier.weight(1f))
            PrefHorizontal(cfg.showQueueButton) {
                Row {
                    ToolIconButton(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", active = showQueue) { showQueue = !showQueue }
                    Spacer(Modifier.width(10.dp))
                }
            }
            ToolIconButton(Icons.Outlined.Lyrics, "Lyrics", active = showLyrics) { showLyrics = !showLyrics }
        }

        // Clock stays visible while the controls are hidden.
        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
            PlainAnimatedVisibility(
                visible = cfg.showClock,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                label = "fsClock",
            ) {
                var now by remember { mutableStateOf(LocalTime.now()) }
                LaunchedEffect(Unit) {
                    while (true) { now = LocalTime.now(); delay(5_000) }
                }
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun FullscreenCover(
    artworkUrl: String?,
    seed: String,
    canvasUrl: String?,
    video: com.music.vivi.player.video.ResolvedVideo? = null,
    playing: Boolean,
    shape: Shape,
    morph: com.music.vivi.compose.ui.player.video.CoverMorph,
    modifier: Modifier = Modifier,
) {
    val shapeAnimatedKey = shape
    Box(
        modifier
            .shadow(28.dp, shapeAnimatedKey, clip = false)
            .clip(shapeAnimatedKey)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        var canvasShowing by remember(canvasUrl, video?.url) { mutableStateOf(false) }
        val artAlpha by androidx.compose.animation.core.animateFloatAsState(
            if (canvasShowing) 0f else 1f,
            VisageMotion.fade(500),
            label = "fsArtFade",
        )
        if (artAlpha > 0.01f) {
            CoverImage(model = artworkUrl, seed = seed, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha })
        }
        if (video != null) {
            com.music.vivi.compose.ui.player.video.MusicVideoSurface(
                video = video,
                modifier = Modifier.fillMaxSize(),
                onFirstFrame = {
                    morph.report(video.url, it)
                    canvasShowing = true
                },
            )
        }
        if (canvasUrl != null) {
            CanvasVideoSurface(
                videoUrl = canvasUrl,
                playing = playing,
                modifier = Modifier.fillMaxSize(),
                onFirstFrame = { canvasShowing = true },
            )
        }
    }
}

@Composable
private fun VolumeGroup(
    style: VolumeControlStyle,
    volume: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.VolumeDown, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        if (style == VolumeControlStyle.COMPACT_PILL) {
            VolumePillControl(volume = volume, onVolumeChange = onChange, onVolumeChangeFinished = onFinished)
        } else {
            PlayerSeekBar(
                progress = volume,
                onScrub = onChange,
                onScrubFinished = onFinished,
                style = SliderStyle.M3_EXPRESSIVE,
                squiggly = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.VolumeUp, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
            Text(
                "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.widthIn(min = 40.dp),
            )
        }
    }
}
