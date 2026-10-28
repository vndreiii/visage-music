package com.music.vivi.compose.ui.player

import com.music.vivi.compose.ui.component.visageMarquee
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Album
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
import com.music.vivi.compose.theme.LocalPlayerInk
import com.music.vivi.compose.theme.ProvidePlayerInk
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.music.vivi.compose.ui.glass.glassSource
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.LocalWindowState
import com.music.vivi.compose.WindowFullscreen
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.FullscreenCoverShape
import com.music.vivi.compose.prefs.FullscreenKeys
import com.music.vivi.compose.prefs.FullscreenSettings
import com.music.vivi.compose.prefs.QueuePanelMode
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
fun NowPlayingFullscreenScreen(
    navController: NavController,
    onOpenPinnedQueue: (() -> Unit)? = null,
    onOpenFullscreenQueue: (() -> Unit)? = null,
    queueOpen: Boolean = false,
    onCloseQueue: (() -> Unit)? = null,
) {
    val prefs = koinInject<DesktopPrefs>()
    val prefsVersion = com.music.vivi.compose.ui.settings.rememberPrefsVersion(prefs)
    val cfg = remember(prefsVersion) { FullscreenSettings.resolve(prefs.reader()) }
    ProvidePlayerInk(
        solid = cfg.backgroundStyle == PlayerBackgroundStyle.DEFAULT,
        backdrop = MaterialTheme.colorScheme.surface,
        darkness = prefs.getInt(PrefKeys.PlayerBackgroundDarkness, com.music.vivi.compose.prefs.LookDefaults.PlayerDarkness).coerceIn(0, 100) / 100f,
        framedControls = cfg.framedControls,
    ) {
        NowPlayingFullscreenContent(navController, onOpenPinnedQueue, onOpenFullscreenQueue, queueOpen, onCloseQueue)
    }
}

@Composable
private fun NowPlayingFullscreenContent(
    navController: NavController,
    onOpenPinnedQueue: (() -> Unit)?,
    onOpenFullscreenQueue: (() -> Unit)?,
    queueOpen: Boolean,
    onCloseQueue: (() -> Unit)?,
) {
    val ink = LocalPlayerInk.current
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

    // Opens the way it was left; the first time it follows "Show lyrics by default" (on unless turned off).
    var showLyrics by remember { mutableStateOf(prefs.getBoolean(FullscreenKeys.LyricsOpen, cfg.lyricsByDefault)) }
    fun setLyrics(open: Boolean) {
        showLyrics = open
        prefs.setBoolean(FullscreenKeys.LyricsOpen, open)
    }
    com.music.vivi.compose.keys.ShortcutEventEffect { e ->
        if (e == com.music.vivi.compose.keys.ShortcutEvent.ToggleLyrics) setLyrics(!showLyrics)
    }
    // The queue follows the app-wide Queue setting (Settings -> Player): side panel and sheet are owned by the
    // app scaffold, inline sits under the controls.
    val queueMode = remember(prefsVersion) {
        runCatching { QueuePanelMode.valueOf(prefs.getString(PrefKeys.QueuePanelMode, QueuePanelMode.PINNED_RIGHT.name)) }
            .getOrDefault(QueuePanelMode.PINNED_RIGHT)
    }
    var underPlaybarOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val inlineQueue = queueMode == QueuePanelMode.UNDER_PLAYBAR && underPlaybarOpen

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

    val playerGlass = remember { dev.chrisbanes.haze.HazeState() }
    com.music.vivi.compose.ui.glass.ProvideGlass(playerGlass) {
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
                .songSwapBackground(swap)
                .glassSource(playerGlass),
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
            val coverShown = !hideCover
            val ctrlLimit = if (cfg.limitControlsWidth) cfg.controlsMaxWidthDp.dp else Dp.Infinity
            // Big cover / video in one column, lyrics over the controls in the other. The queue overlays this
            // (it never narrows it), so the geometry only depends on the window and the cover side.
            val split = FullscreenLyricsLayout.split(w, h, cfg.coverSide, coverShown, cfg.coverScale)
            val spring = VisageMotion.glide<Dp>()
            val coverX = animateDpAsState(split.coverX, spring, label = "fsCoverX")
            val coverY = animateDpAsState(split.coverY, spring, label = "fsCoverY")
            val coverDim = animateDpAsState(split.coverSize, spring, label = "fsCoverDim")
            val colX = animateDpAsState(split.columnX, spring, label = "fsColX")
            val colY = animateDpAsState(split.columnY, spring, label = "fsColY")
            val colW = animateDpAsState(split.columnWidth, spring, label = "fsColW")
            val colH = animateDpAsState(split.columnHeight, spring, label = "fsColH")
            // 0 = lyrics open (controls under them), 1 = closed (controls centred in the column).
            val lyricsClosed = animateFloatAsState(if (showLyrics) 0f else 1f, VisageMotion.glide(), label = "fsLyricsClosed")

            val coverContent: @Composable () -> Unit = {
                Box(
                    Modifier.widthOf { coverDim.value }.heightOf { coverDim.value },
                    contentAlignment = Alignment.Center,
                ) {
                    PlainAnimatedVisibility(
                        visible = coverShown,
                        enter = fadeIn() + scaleIn(initialScale = 0.92f),
                        exit = fadeOut() + scaleOut(targetScale = 0.92f),
                        label = "fsCover",
                    ) {
                        AlbumGestureCard(
                            controller,
                            Modifier.fillMaxWidth().heightOf { coverDim.value / coverMorph.aspect }.songSwapCover(swap).volumeWheel(),
                        ) {
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

            // Stable lists: TransportRow can skip while the layout springs run.
            val previousPreview = remember(queueIndex, queueTracks) {
                queuePreviewIndices(queueIndex, queueTracks.size, true, 3).map { it to queueTracks[it] }
            }
            val nextPreview = remember(queueIndex, queueTracks) {
                queuePreviewIndices(queueIndex, queueTracks.size, false, 3).map { it to queueTracks[it] }
            }

            val controlsContent: @Composable () -> Unit = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .songSwapControls(swap)
                        .playerControlsSurface(cfg.framedControls),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track?.title ?: "Nothing playing",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = ink.text(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val credits = track?.artist.orEmpty()
                            if (credits.isBlank()) {
                                Text(
                                    state.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ink.text(0.65f),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            } else {
                                PrefVertical(cfg.showArtist) {
                                    ArtistLinks(
                                        credits = credits,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ink.text(0.8f),
                                        hoverColor = ink.text(),
                                        onOpen = {
                                            WindowFullscreen.exit(windowState)
                                            navController.openArtistByName(it)
                                        },
                                        maxLines = 2,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            val album = track?.album.orEmpty()
                            PrefVertical(cfg.showAlbum && album.isNotBlank()) {
                                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Album, "Album", tint = ink.text(0.5f), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        album,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ink.text(0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.weight(1f).visageMarquee(),
                                    )
                                }
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
                        Spacer(Modifier.height(16.dp))
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
                            previousTracks = previousPreview,
                            nextTracks = nextPreview,
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
                        PrefVertical(inlineQueue) {
                            QueuePillStrip(
                                upcoming = upcoming,
                                onPlayIndex = { index -> scope.launch(Dispatchers.IO) { controller.playQueue(queueTracks, index) } },
                                modifier = Modifier.fillMaxWidth().height(InlineQueueHeight).padding(top = 16.dp),
                            )
                        }
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                Box(Modifier.offset { IntOffset(coverX.value.roundToPx(), coverY.value.roundToPx()) }) { coverContent() }
                val ctrlLimitPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    if (ctrlLimit == Dp.Infinity) Int.MAX_VALUE else ctrlLimit.roundToPx()
                }
                val lyricsGapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.roundToPx() }
                // Lyrics fill the column above the controls; with lyrics closed the controls glide to the middle.
                androidx.compose.ui.layout.Layout(
                    content = {
                        Box(Modifier.layoutId("lyrics")) {
                            PlainAnimatedVisibility(
                                visible = showLyrics,
                                enter = fadeIn() + slideInHorizontally { it / 8 },
                                exit = fadeOut() + slideOutHorizontally { it / 8 },
                                label = "fsLyrics",
                            ) {
                                LyricsPanel(
                                    onClose = { setLyrics(false) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Box(Modifier.layoutId("ctrl")) { controlsContent() }
                    },
                    modifier = Modifier
                        .offset { IntOffset(colX.value.roundToPx(), colY.value.roundToPx()) }
                        .widthOf { colW.value }
                        .heightOf { colH.value },
                ) { measurables, c ->
                    val ctrlW = minOf(c.maxWidth, ctrlLimitPx)
                    val ctrl = measurables.first { it.layoutId == "ctrl" }
                        .measure(androidx.compose.ui.unit.Constraints(ctrlW, ctrlW, 0, c.maxHeight))
                    val lyricsH = (c.maxHeight - ctrl.height - lyricsGapPx).coerceAtLeast(0)
                    val lyrics = measurables.first { it.layoutId == "lyrics" }
                        .measure(androidx.compose.ui.unit.Constraints(c.maxWidth, c.maxWidth, lyricsH, lyricsH))
                    val ctrlBottom = c.maxHeight - ctrl.height
                    val ctrlY = (ctrlBottom + (ctrlBottom / 2 - ctrlBottom) * lyricsClosed.value).toInt()
                    layout(c.maxWidth, c.maxHeight) {
                        lyrics.place(0, 0)
                        ctrl.place((c.maxWidth - ctrlW) / 2, ctrlY)
                    }
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
                    ToolIconButton(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", active = inlineQueue || queueOpen) {
                        when {
                            queueOpen -> onCloseQueue?.invoke()
                            queueMode == QueuePanelMode.FULLSCREEN -> onOpenFullscreenQueue?.invoke()
                            queueMode == QueuePanelMode.PINNED_RIGHT ->
                                onOpenPinnedQueue?.invoke() ?: onOpenFullscreenQueue?.invoke()
                            else -> underPlaybarOpen = !underPlaybarOpen
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }
            ToolIconButton(Icons.Outlined.Lyrics, "Lyrics", active = showLyrics) { setLyrics(!showLyrics) }
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
                    color = ink.text(0.8f),
                )
            }
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
    val ink = LocalPlayerInk.current
    Row(
        Modifier.fillMaxWidth().volumeWheel(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.VolumeDown, null, tint = ink.text(0.75f), modifier = Modifier.size(20.dp))
        if (style == VolumeControlStyle.COMPACT_PILL) {
            VolumePillControl(volume = volume, onVolumeChange = onChange, onVolumeChangeFinished = onFinished)
        } else {
            PlayerSeekBar(
                progress = volume,
                onScrub = onChange,
                onScrubFinished = onFinished,
                activeColor = ink.primary,
                inactiveColor = ink.fill(0.28f),
                style = SliderStyle.M3_EXPRESSIVE,
                squiggly = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.VolumeUp, null, tint = ink.text(0.75f), modifier = Modifier.size(20.dp))
            Text(
                "${(volume * 100).toInt()}%",
                style = com.music.vivi.compose.theme.VisageType.readout,
                color = ink.text(0.7f),
                modifier = Modifier.widthIn(min = 40.dp),
            )
        }
    }
}

private val InlineQueueHeight = 220.dp

/** Fixed width read in the layout phase, so an animated value never recomposes the screen. */
private fun Modifier.widthOf(width: () -> Dp): Modifier = layout { m, c ->
    val px = width().roundToPx().coerceIn(c.minWidth, c.maxWidth.coerceAtLeast(c.minWidth))
    val p = m.measure(c.copy(minWidth = px, maxWidth = px))
    layout(p.width, p.height) { p.place(0, 0) }
}

/** Fixed height read in the layout phase, so an animated value never recomposes the screen. */
private fun Modifier.heightOf(height: () -> Dp): Modifier = layout { m, c ->
    val px = height().roundToPx().coerceIn(c.minHeight, c.maxHeight.coerceAtLeast(c.minHeight))
    val p = m.measure(c.copy(minHeight = px, maxHeight = px))
    layout(p.width, p.height) { p.place(0, 0) }
}
