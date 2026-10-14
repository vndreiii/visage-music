package com.music.vivi.compose.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import com.music.vivi.compose.ui.motion.VisageMotion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.LocalWindowState
import com.music.vivi.compose.WindowFullscreen
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PlayerButtonsStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.QueuePanelMode
import com.music.vivi.compose.prefs.SliderStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import com.music.vivi.compose.prefs.VolumeControlStyle

import com.music.vivi.compose.ui.component.PlayPauseIcon
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import com.music.vivi.compose.ui.player.canvas.CanvasSource
import com.music.vivi.compose.ui.player.lyrics.LyricsPanel
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Top-level AnimatedVisibility — avoids RowScope/ColumnScope overload resolution inside nested layouts. */
@Composable
private fun PlainAnimatedVisibility(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    label: String = "PlainAnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        label = label,
        content = content,
    )
}

/**
 * Full-page Now Playing — tablet landscape layout with upstream background styles,
 * optional canvas video, lyrics panel, and dual-mode queue.
 *
 * Lyrics reuses the same chrome: cover morphs into a top thumb; lyrics fill the left.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    navController: NavController,
    onOpenPinnedQueue: (() -> Unit)? = null,
    onOpenFullscreenQueue: (() -> Unit)? = null,
) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    val downloads = koinInject<com.music.vivi.compose.download.TrackDownloadService>()
    val scope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val downloadStatus by downloads.status.collectAsState()
    LaunchedEffect(downloadStatus) {
        when (val s = downloadStatus) {
            is com.music.vivi.compose.download.DownloadStatus.Running ->
                toastMessage = "Making ${s.title} available offline…"
            is com.music.vivi.compose.download.DownloadStatus.Succeeded ->
                toastMessage = if (s.file.isFile) "Available offline: ${s.file.name}" else "Available offline"
            is com.music.vivi.compose.download.DownloadStatus.Failed ->
                toastMessage = s.message
            else -> Unit
        }
    }
    var prefsTick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = { prefsTick += 1 }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE")
    val observePrefs = prefsTick
    val queueMode = runCatching {
        QueuePanelMode.valueOf(
            prefs.getString(PrefKeys.QueuePanelMode, QueuePanelMode.FULLSCREEN.name),
        )
    }.getOrDefault(QueuePanelMode.FULLSCREEN)
    val showUnderPlaybar = queueMode == QueuePanelMode.UNDER_PLAYBAR
    val nowPlaying by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    // Position is only read lazily (derivedStateOf / NowPlayingSeekSection) so ticks don't
    // recompose the whole screen with its backgrounds and shared transitions.
    val positionState = controller.positionMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val isFavorite by controller.isFavorite.collectAsState()
    val queueTracks by controller.queueTracks.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
    val volume by controller.volume.collectAsState()
    val shuffle by controller.shuffleEnabled.collectAsState()
    val repeat by controller.repeatMode.collectAsState()
    var scrubVolume by remember { mutableStateOf(false) }
    var scrubVol by remember { mutableFloatStateOf(volume) }
    val displayVolume = if (scrubVolume) scrubVol else volume

    val track = nowPlaying
    val duration = durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L
    val playing = state == PlaybackState.Playing
    val hasPosition by remember { derivedStateOf { positionState.value > 0L } }
    val canPrev = queueIndex > 0 || hasPosition
    val canNext = track != null
    val upcoming = remember(queueTracks, queueIndex) {
        queueTracks.mapIndexedNotNull { i, t -> if (i > queueIndex) i to t else null }
    }

    val bgStyle = runCatching {
        PlayerBackgroundStyle.valueOf(
            prefs.getString(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name),
        )
    }.getOrDefault(PlayerBackgroundStyle.GRADIENT)
    val sliderStyle = runCatching {
        SliderStyle.valueOf(prefs.getString(PrefKeys.SliderStyle, SliderStyle.DEFAULT.name))
    }.getOrDefault(SliderStyle.DEFAULT)
    val squigglySlider = prefs.getBoolean(PrefKeys.SquigglySlider, false)
    val framedControls = prefs.getBoolean(PrefKeys.UsePlayerV2, false)
    val buttonsStyle = runCatching {
        PlayerButtonsStyle.valueOf(
            prefs.getString(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT.name),
        )
    }.getOrDefault(PlayerButtonsStyle.DEFAULT)
    val enableCanvas = prefs.getBoolean(PrefKeys.EnableCanvas, true)
    val canvasSource = runCatching {
        CanvasSource.valueOf(prefs.getString(PrefKeys.CanvasSource, CanvasSource.AUTO.name))
    }.getOrDefault(CanvasSource.AUTO)

    var canvasArt by remember { mutableStateOf<com.music.vivi.canvas.CanvasArtwork?>(null) }
    val canvasSquareUrl = CanvasResolver.squareUrl(canvasArt)
    val canvasTallUrl = CanvasResolver.tallUrl(canvasArt)
    var showLyrics by remember { mutableStateOf(false) }
    var showTrackActions by remember { mutableStateOf(false) }
    val windowState = LocalWindowState.current

    LaunchedEffect(track?.videoId, bgStyle, enableCanvas, canvasSource) {
        canvasArt = null
        if (!enableCanvas || track == null) return@LaunchedEffect
        val art = withContext(Dispatchers.IO) {
            runCatching {
                CanvasResolver.resolve(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    source = canvasSource,
                )
            }.getOrNull()
        }
        canvasArt = art
    }

    val hideThumbnail = prefs.getBoolean(PrefKeys.HidePlayerThumbnail, false)
    // Only animate to artwork that is already decoded at full quality.
    val readyArt = com.music.vivi.compose.ui.component.rememberReadyArtwork(track?.artworkUrl)
    var artTarget by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    LaunchedEffect(readyArt) { artTarget = track?.videoId to readyArt }

    // The lyrics view swaps to the portrait stream; keep its decoder open (parked on the
    // first frame) so toggling lyrics shows it instantly instead of cold-starting HLS.
    if (canvasTallUrl != null && canvasTallUrl != canvasSquareUrl) {
        com.music.vivi.compose.ui.player.background.rememberCanvasVideo(canvasTallUrl, playing = false)
    }

    Box(Modifier.fillMaxSize()) {
        PlayerBackgroundLayer(
            style = bgStyle,
            artworkUrl = readyArt,
            // Same URL as the cover slot: one shared decoder, frames stay in sync.
            canvasVideoUrl = canvasSquareUrl,
            canvasPlaying = playing,
            showClearArtwork = !showLyrics,
        )

        com.music.vivi.compose.ui.component.VisageToast(
            message = toastMessage,
            onDismiss = { toastMessage = null },
        )

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val coverState = rememberSharedContentState(key = "np-cover")

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                val paneGap = 28.dp
                val bottomChromeH = 60.dp
                val underQueue = showUnderPlaybar && !showLyrics
                val mainH = (maxHeight - bottomChromeH).coerceAtLeast(200.dp)
                // Cover from height, then stack takes the rest so the row is wide and stack isn't skinny.
                val idleArtSize = minOf(mainH * 0.92f, maxWidth * 0.40f)
                val idleStackW = (maxWidth - paneGap - idleArtSize).coerceAtLeast(0.dp)
                val idleContentW = (idleArtSize + paneGap + idleStackW).coerceAtMost(maxWidth)
                val lyricsStackW = (maxWidth * 0.44f).coerceAtMost(440.dp)
                val lyricsContentW = maxWidth
                val lyricsLeftW = (lyricsContentW - paneGap - lyricsStackW).coerceAtLeast(0.dp)

                val layoutSpring = VisageMotion.glide<Dp>()
                val contentW by animateDpAsState(
                    if (showLyrics) lyricsContentW else idleContentW,
                    layoutSpring,
                    label = "npContentW",
                )
                val leftW by animateDpAsState(
                    if (showLyrics) lyricsLeftW else idleArtSize,
                    layoutSpring,
                    label = "npLeftW",
                )
                val leftH by animateDpAsState(
                    if (showLyrics) mainH else idleArtSize,
                    layoutSpring,
                    label = "npLeftH",
                )
                val stackW by animateDpAsState(
                    if (showLyrics) lyricsStackW else idleStackW,
                    layoutSpring,
                    label = "npStackW",
                )
                val stackH by animateDpAsState(
                    mainH,
                    layoutSpring,
                    label = "npStackH",
                )
                val stackGap by animateDpAsState(
                    if (underQueue || showLyrics) 16.dp else 28.dp,
                    layoutSpring,
                    label = "npStackGap",
                )

                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier
                            .width(contentW)
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(paneGap),
                    ) {
                        Box(
                            Modifier
                                .width(leftW)
                                .height(leftH),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlainAnimatedVisibility(
                                visible = !hideThumbnail && !showLyrics,
                                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                                exit = fadeOut() + scaleOut(targetScale = 0.92f),
                                label = "npCoverLarge",
                            ) {
                                val coverVisibility = this
                                val coverShape = RoundedCornerShape(28.dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedBounds(
                                            sharedContentState = coverState,
                                            animatedVisibilityScope = coverVisibility,
                                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                                ContentScale.Crop,
                                                Alignment.Center,
                                            ),
                                        )
                                        .shadow(28.dp, coverShape, clip = false)
                                        .clip(coverShape),
                                ) {
                                    AnimatedContent(
                                        targetState = artTarget,
                                        // Same song, sharper URL: swap in place, no second transition.
                                        contentKey = { it.first },
                                        transitionSpec = {
                                            (slideInHorizontally { it / 3 } + fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                                                (slideOutHorizontally { -it / 3 } + fadeOut() + scaleOut(targetScale = 0.92f))
                                        },
                                        label = "npArtwork",
                                        modifier = Modifier.fillMaxSize().clip(coverShape),
                                    ) { (_, artUrl) ->
                                        Box(Modifier.fillMaxSize().then(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))) {
                                            val squareUrl = canvasSquareUrl
                                            // The canvas takes over the cover: the static art only shows
                                            // until the first video frame, then it's removed entirely.
                                            var canvasShowing by remember(squareUrl) { mutableStateOf(false) }
                                            if (!canvasShowing) {
                                                // Art with baked-in rounded corners is zoomed so its transparent
                                                // corners fall outside our own rounded slot.
                                                val bleed = com.music.vivi.compose.ui.component.rememberArtworkCornerBleed(artUrl)
                                                AsyncImage(
                                                    model = artUrl,
                                                    contentDescription = track?.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer { scaleX = bleed; scaleY = bleed },
                                                )
                                            }
                                            if (squareUrl != null) {
                                                com.music.vivi.compose.ui.player.background.CanvasVideoSurface(
                                                    videoUrl = squareUrl,
                                                    playing = playing,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onFirstFrame = { canvasShowing = true },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            PlainAnimatedVisibility(
                                visible = showLyrics,
                                enter = fadeIn() + slideInHorizontally { -it / 8 },
                                exit = fadeOut() + slideOutHorizontally { -it / 8 },
                                label = "npLyrics",
                            ) {
                                LyricsPanel(
                                    onClose = { showLyrics = false },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 8.dp),
                                )
                            }

                            if (hideThumbnail && !showLyrics) {
                                Spacer(Modifier.fillMaxSize())
                            }
                        }

                        Column(
                            Modifier
                                .width(stackW)
                                .height(stackH),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.Start,
                        ) {
                            val rightCanvasUrl = canvasTallUrl ?: canvasSquareUrl
                            val showCanvasOnRight = showLyrics && rightCanvasUrl != null
                            // Free space above the controls: half of it when idle (controls centred), all of it
                            // with lyrics (controls at the bottom, canvas fills the rest). Animated so the
                            // controls glide between the two instead of snapping.
                            val controlsBias by animateFloatAsState(
                                if (showLyrics) 1f else 0.5f,
                                VisageMotion.settle(),
                                label = "npControlsBias",
                            )
                            if (!underQueue) {
                                Box(Modifier.fillMaxWidth().weight(controlsBias.coerceIn(0.001f, 1f))) {
                                    if (rightCanvasUrl != null) {
                                        PlainAnimatedVisibility(
                                            visible = showCanvasOnRight,
                                            enter = fadeIn(),
                                            exit = fadeOut(),
                                            label = "npRightCanvas",
                                        ) {
                                            com.music.vivi.compose.ui.player.background.CanvasVideoSurface(
                                                videoUrl = rightCanvasUrl,
                                                playing = playing,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(bottom = 16.dp)
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .graphicsLayer {
                                                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                                    }
                                                    .drawWithContent {
                                                        drawContent()
                                                        drawRect(
                                                            brush = Brush.verticalGradient(
                                                                0f to Color.Black,
                                                                0.75f to Color.Black,
                                                                1f to Color.Transparent,
                                                            ),
                                                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                                                        )
                                                    },
                                            )
                                        }
                                    }
                                }
                            }

                            Column(
                                Modifier.fillMaxWidth().then(if (underQueue && !showCanvasOnRight) Modifier.weight(1f) else Modifier).playerControlsSurface(framedControls)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                PlainAnimatedVisibility(
                                    visible = showLyrics && !hideThumbnail && !showCanvasOnRight,
                                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                                    label = "npCoverThumb",
                                ) {
                                    AsyncImage(
                                        model = track?.artworkUrl,
                                        contentDescription = track?.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .padding(end = 14.dp, top = 4.dp)
                                            .size(72.dp)
                                            .sharedBounds(
                                                sharedContentState = coverState,
                                                animatedVisibilityScope = this,
                                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                                    ContentScale.Crop,
                                                    Alignment.Center,
                                                ),
                                            )
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
                                    Text(
                                        track?.artist.orEmpty().ifBlank { state.name },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ToolIconButton(Icons.Outlined.Download, "Make available offline") {
                                        val t = track
                                        if (t == null) {
                                            toastMessage = "Nothing playing"
                                        } else {
                                            downloads.enqueue(t.videoId, t.title, t.artist)
                                        }
                                    }
                                    ToolIconButton(
                                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        "Favorite",
                                        active = isFavorite,
                                    ) { scope.launch { controller.toggleFavorite() } }
                                }
                            }

                            Spacer(Modifier.height(stackGap))
                            NowPlayingSeekSection(
                                duration = duration,
                                sliderStyle = sliderStyle,
                                squiggly = squigglySlider,
                            )

                            Spacer(Modifier.height(stackGap))
                            TransportRow(
                                playing = playing,
                                canPrev = canPrev || track != null,
                                canNext = canNext,
                                enabledPlay = track != null || state != PlaybackState.Idle,
                                style = buttonsStyle,
                                onPrev = { if (canPrev) controller.previous() },
                                onPlayPause = { if (playing) controller.pause() else controller.play() },
                                onNext = { controller.next() },
                            )

                            if (underQueue) {
                                QueuePillStrip(
                                    upcoming = upcoming,
                                    onPlayIndex = { index ->
                                        scope.launch(Dispatchers.IO) {
                                            controller.playQueue(queueTracks, index)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(top = 12.dp),
                                )
                            }
                            } // Close inner Column
                            if (!underQueue) {
                                Spacer(Modifier.weight((1f - controlsBias).coerceIn(0.001f, 1f)))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // Same width as cover + stack above — tools | volume (fills) | more.
                    Row(
                        Modifier.width(contentW),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ToolIconButton(
                                Icons.AutoMirrored.Outlined.QueueMusic,
                                "Queue",
                                active = showUnderPlaybar,
                            ) {
                                when (queueMode) {
                                    QueuePanelMode.FULLSCREEN ->
                                        onOpenFullscreenQueue?.invoke()
                                    QueuePanelMode.PINNED_RIGHT ->
                                        onOpenPinnedQueue?.invoke()
                                            ?: onOpenFullscreenQueue?.invoke()
                                    QueuePanelMode.UNDER_PLAYBAR -> Unit
                                }
                            }
                            ToolIconButton(Icons.Outlined.Lyrics, "Lyrics", active = showLyrics) {
                                showLyrics = !showLyrics
                            }
                            ToolIconButton(
                                Icons.Outlined.Shuffle,
                                "Shuffle",
                                active = shuffle,
                            ) { controller.toggleShuffle() }
                            ToolIconButton(
                                if (repeat == RepeatMode.One) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                                "Repeat",
                                active = repeat != RepeatMode.Off,
                            ) { controller.cycleRepeatMode() }
                            ToolIconButton(Icons.Outlined.OpenInFull, "Fullscreen player") {
                                WindowFullscreen.enter(windowState)
                                navController.navigate(Routes.NOW_PLAYING_FULLSCREEN) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        val volumeStyle = runCatching { VolumeControlStyle.valueOf(prefs.getString(PrefKeys.VolumeControlStyle, VolumeControlStyle.SLIDER.name)) }.getOrDefault(VolumeControlStyle.SLIDER)
                        Row(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.VolumeDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp),
                            )
                            if (volumeStyle == VolumeControlStyle.COMPACT_PILL) {
                                VolumePillControl(
                                    volume = displayVolume,
                                    onVolumeChange = {
                                        scrubVolume = true
                                        scrubVol = it
                                        controller.setVolume(it)
                                    },
                                    onVolumeChangeFinished = {
                                        scrubVolume = false
                                        prefs.setFloat(PrefKeys.PlaybackVolume, scrubVol)
                                    }
                                )
                                
                            } else {
                                PlayerSeekBar(
                                    progress = displayVolume,
                                    onScrub = {
                                        scrubVolume = true
                                        scrubVol = it
                                        controller.setVolume(it)
                                    },
                                    onScrubFinished = {
                                        scrubVolume = false
                                        prefs.setFloat(PrefKeys.PlaybackVolume, scrubVol)
                                    },
                                    style = SliderStyle.M3_EXPRESSIVE,
                                    squiggly = false,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(displayVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.widthIn(min = 36.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        Box {
                            ToolIconButton(Icons.Outlined.MoreVert, "More", circle = true) {
                                showTrackActions = true
                            }
                            TrackActionsMenu(
                                expanded = showTrackActions,
                                track = track,
                                onDismiss = { showTrackActions = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Seek bar + time labels; the only part of Now Playing that recomposes per position tick. */
@Composable
internal fun NowPlayingSeekSection(
    duration: Long,
    sliderStyle: SliderStyle,
    squiggly: Boolean,
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current
    val positionMs by controller.positionMs.collectAsState()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }
    val progress = when {
        scrubbing -> scrubPos
        duration <= 0L -> 0f
        else -> (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
    val displayPos = if (scrubbing) (scrubPos * duration).toLong() else positionMs
    Column(modifier.fillMaxWidth()) {
        PlayerSeekBar(
            progress = progress,
            onScrub = { scrubbing = true; scrubPos = it },
            onScrubFinished = {
                scrubbing = false
                controller.seekTo((scrubPos * duration).toLong().coerceAtLeast(0L))
            },
            style = sliderStyle,
            squiggly = squiggly,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(displayPos), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            Text(formatMs(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
internal fun TransportRow(
    playing: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    enabledPlay: Boolean,
    style: PlayerButtonsStyle = PlayerButtonsStyle.DEFAULT,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (pillBg, pillIcon) = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.White.copy(alpha = 0.12f) to Color.White
        PlayerButtonsStyle.PRIMARY -> scheme.primary.copy(alpha = 0.85f) to scheme.onPrimary
        PlayerButtonsStyle.TERTIARY -> scheme.tertiary.copy(alpha = 0.85f) to scheme.onTertiary
    }
    val playBg = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.White
        PlayerButtonsStyle.PRIMARY -> scheme.primary
        PlayerButtonsStyle.TERTIARY -> scheme.tertiary
    }
    val playFg = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.Black
        PlayerButtonsStyle.PRIMARY -> scheme.onPrimary
        PlayerButtonsStyle.TERTIARY -> scheme.onTertiary
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TransportPill(
            enabled = canPrev,
            onClick = onPrev,
            width = 72.dp,
            containerColor = pillBg,
            content = {
            Icon(
                Icons.Filled.SkipPrevious,
                "Previous",
                tint = pillIcon,
                modifier = Modifier.size(28.dp),
            )
            },
        )
        val playInteraction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()
        // Expressive toggle morph: a full pill while paused, a squarer shape while playing, tighter on press.
        val playRadius by animateDpAsState(
            when {
                playPressed -> 12.dp
                playing -> 20.dp
                else -> 28.dp
            },
            com.music.vivi.compose.ui.motion.VisageMotion.spatialFast(),
            label = "playRadius",
        )
        Surface(
            onClick = onPlayPause,
            enabled = enabledPlay,
            shape = RoundedCornerShape(playRadius),
            color = playBg,
            interactionSource = playInteraction,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayPauseIcon(playing = playing, tint = playFg, iconSize = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (playing) "Pause" else "Play",
                    color = playFg,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        TransportPill(
            enabled = canNext,
            onClick = onNext,
            width = 72.dp,
            containerColor = pillBg,
            content = {
            Icon(
                Icons.Filled.SkipNext,
                "Next",
                tint = pillIcon,
                modifier = Modifier.size(28.dp),
            )
            },
        )
    }
}

@Composable
private fun QueuePillStrip(
    upcoming: List<Pair<Int, QueueTrack>>,
    onPlayIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            if (upcoming.isEmpty()) "Up next" else "Up next · ${upcoming.size}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(upcoming, key = { _, (i, t) -> "${t.videoId}-$i" }) { _, (index, track) ->
                Surface(
                    onClick = { onPlayIndex(index) },
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (track.artist.isNotBlank()) {
                                Text(
                                    track.artist,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "queue-end-sentinel") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Pets,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Nothing to see here...",
                            color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransportPill(
    enabled: Boolean,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    containerColor: Color = Color.White.copy(alpha = 0.12f),
    content: @Composable () -> Unit,
) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Expressive press morph: pill → tighter corners while held, springing back on release.
    val radius by animateDpAsState(if (pressed) 14.dp else 28.dp, com.music.vivi.compose.ui.motion.VisageMotion.spatialFast(), label = "pillRadius")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        color = containerColor,
        interactionSource = interaction,
        modifier = Modifier.size(width = width, height = 56.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = { content() })
    }
}

@Composable
internal fun ToolIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    circle: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (active) Color.White else Color.White.copy(alpha = 0.12f)
    val iconTint = if (active) Color.Black else tint
    Surface(
        onClick = onClick,
        shape = if (circle) CircleShape else RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(22.dp))
        }
    }
}

internal fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}

@Composable
fun VolumePillControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(volume) { mutableStateOf((volume * 100).toInt().toString()) }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(editing) {
        if (editing) {
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }
    
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.height(28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(28.dp).clickable {
                    onVolumeChange((volume - 0.05f).coerceAtLeast(0f))
                    onVolumeChangeFinished()
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            
            Box(
                Modifier.width(36.dp).clickable { editing = true },
                contentAlignment = Alignment.Center
            ) {
                if (editing) {
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= 3) text = it.filter { c -> c.isDigit() } },
                        textStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val p = text.toIntOrNull()?.coerceIn(0, 150) ?: (volume * 100).toInt()
                            onVolumeChange(p / 100f)
                            onVolumeChangeFinished()
                            editing = false
                        }),
                        modifier = Modifier.focusRequester(focusRequester).fillMaxWidth()
                    )
                } else {
                    Text(
                        "${(volume * 100).toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Box(
                Modifier.size(28.dp).clickable {
                    onVolumeChange((volume + 0.05f).coerceAtMost(1.5f))
                    onVolumeChangeFinished()
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
