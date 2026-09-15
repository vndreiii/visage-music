package com.music.vivi.compose.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
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
    val positionMs by controller.positionMs.collectAsState()
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
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }
    val progress = when {
        scrubbing -> scrubPos
        duration <= 0L -> 0f
        else -> (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
    val displayPos = if (scrubbing) (scrubPos * duration).toLong() else positionMs
    val playing = state == PlaybackState.Playing
    val canPrev = queueIndex > 0 || positionMs > 0L
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

    var canvasVideoUrl by remember { mutableStateOf<String?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showTrackActions by remember { mutableStateOf(false) }
    val windowState = LocalWindowState.current

    LaunchedEffect(track?.videoId, bgStyle, enableCanvas, canvasSource) {
        canvasVideoUrl = null
        if (bgStyle != PlayerBackgroundStyle.APPLE_MUSIC || !enableCanvas || track == null) return@LaunchedEffect
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
        canvasVideoUrl = CanvasResolver.videoUrl(art)
    }

    val hideThumbnail = prefs.getBoolean(PrefKeys.HidePlayerThumbnail, false) ||
        (bgStyle == PlayerBackgroundStyle.APPLE_MUSIC && canvasVideoUrl != null)

    Box(Modifier.fillMaxSize()) {
        PlayerBackgroundLayer(
            style = bgStyle,
            artworkUrl = track?.artworkUrl,
            canvasVideoUrl = canvasVideoUrl,
            canvasPlaying = playing,
            showClearArtwork = !showLyrics,
        )

        com.music.vivi.compose.ui.component.ViviToast(
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

                val layoutSpring = spring<Dp>(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMediumLow,
                )
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
                                        targetState = track?.videoId to track?.artworkUrl,
                                        transitionSpec = {
                                            (slideInHorizontally { it / 3 } + fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                                                (slideOutHorizontally { -it / 3 } + fadeOut() + scaleOut(targetScale = 0.92f))
                                        },
                                        label = "npArtwork",
                                        modifier = Modifier.fillMaxSize().clip(coverShape),
                                    ) { (_, artUrl) ->
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = track?.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(coverShape),
                                        )
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
                                .height(stackH)
                                .playerControlsSurface(framedControls),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.Start,
                        ) {
                            // Center title/seek/transport when not filling with under-playbar queue.
                            if (!underQueue && !showLyrics) {
                                Spacer(Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                PlainAnimatedVisibility(
                                    visible = showLyrics && !hideThumbnail,
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
                                            .shadow(12.dp, RoundedCornerShape(14.dp), clip = false)
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
                            PlayerSeekBar(
                                progress = progress,
                                onScrub = { scrubbing = true; scrubPos = it },
                                onScrubFinished = {
                                    scrubbing = false
                                    controller.seekTo((scrubPos * duration).toLong().coerceAtLeast(0L))
                                },
                                style = sliderStyle,
                                squiggly = squigglySlider,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatMs(displayPos), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                                Text(formatMs(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                            }

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
                            } else if (!showLyrics) {
                                Spacer(Modifier.weight(1f))
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
                        Row(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.VolumeDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp),
                            )
                            Slider(
                                value = displayVolume,
                                onValueChange = {
                                    scrubVolume = true
                                    scrubVol = it
                                    controller.setVolume(it)
                                },
                                onValueChangeFinished = {
                                    scrubVolume = false
                                    prefs.setFloat(PrefKeys.PlaybackVolume, scrubVol)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                                ),
                                modifier = Modifier.weight(1f).height(24.dp),
                            )
                            Text(
                                "${(displayVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.widthIn(min = 36.dp),
                            )
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
        Surface(
            onClick = onPlayPause,
            enabled = enabledPlay,
            shape = RoundedCornerShape(50),
            color = playBg,
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
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = containerColor,
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
