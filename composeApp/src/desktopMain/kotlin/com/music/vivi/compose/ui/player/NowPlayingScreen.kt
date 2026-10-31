package com.music.vivi.compose.ui.player

import com.music.vivi.compose.ui.component.visageMarquee
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.music.vivi.compose.theme.LocalPlayerInk
import com.music.vivi.compose.theme.ProvidePlayerInk
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.music.vivi.compose.ui.glass.glassSource
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
import com.music.vivi.compose.ui.artist.openArtistByName
import com.music.vivi.compose.ui.album.openAlbumOf
import coil3.compose.AsyncImage
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.LocalWindowState
import com.music.vivi.compose.WindowFullscreen
import com.music.vivi.compose.navigation.Routes
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
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
import com.music.vivi.compose.ui.player.swap.songSwapBackground
import com.music.vivi.compose.ui.player.swap.songSwapCover
import com.music.vivi.compose.ui.player.swap.songSwapControls
import com.music.vivi.compose.ui.player.swap.songSwapViewport
import com.music.vivi.compose.ui.player.video.morphAspect
import com.music.vivi.compose.ui.player.video.morphClip
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import com.music.vivi.compose.ui.player.canvas.CanvasSource
import com.music.vivi.compose.ui.player.lyrics.LyricsPanel
import com.music.vivi.player.NowPlaying
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.QueueTrack
import com.music.vivi.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Top-level AnimatedVisibility — avoids RowScope/ColumnScope overload resolution inside nested layouts. */
@Composable
internal fun PlainAnimatedVisibility(
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
    queueOpen: Boolean = false,
    onCloseQueue: (() -> Unit)? = null,
) {
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observeInk = rememberPrefsVersion(prefs)
    val outerLook = prefs.nowPlayingLook()
    ProvidePlayerInk(
        solid = outerLook.backgroundStyle == PlayerBackgroundStyle.DEFAULT,
        backdrop = MaterialTheme.colorScheme.surface,
        darkness = outerLook.darkness,
        framedControls = outerLook.framedControls,
    ) {
        NowPlayingScreenContent(navController, onOpenPinnedQueue, onOpenFullscreenQueue, queueOpen, onCloseQueue)
    }
}

@Composable
private fun NowPlayingScreenContent(
    navController: NavController,
    onOpenPinnedQueue: (() -> Unit)? = null,
    onOpenFullscreenQueue: (() -> Unit)? = null,
    queueOpen: Boolean = false,
    onCloseQueue: (() -> Unit)? = null,
) {
    val ink = LocalPlayerInk.current
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    @Suppress("UNUSED_VARIABLE")
    val observeSquare = rememberPrefsVersion(prefs)
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
    val look = prefs.nowPlayingLook()
    val squareCover = look.squareCover
    val limitControlsWidth = look.limitControlsWidth
    val controlsMaxWidth = look.controlsMaxWidth
    val playPauseHeight = look.playPauseHeight
    val queueMode = runCatching {
        QueuePanelMode.valueOf(
            prefs.getString(PrefKeys.QueuePanelMode, QueuePanelMode.PINNED_RIGHT.name),
        )
    }.getOrDefault(QueuePanelMode.PINNED_RIGHT)
    var underPlaybarOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val showUnderPlaybar = queueMode == QueuePanelMode.UNDER_PLAYBAR && underPlaybarOpen
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

    // Song swappings: while a driven swap runs, the layers keep showing the outgoing song.
    val swap = com.music.vivi.compose.ui.player.swap.rememberPlayerSongSwap(controller, prefs)
    val track = swap.controlsTrack(nowPlaying)
    val duration = durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L
    val playing = state == PlaybackState.Playing
    val hasPosition by remember { derivedStateOf { positionState.value > 0L } }
    val canPrev = queueIndex > 0 || hasPosition
    val canNext = track != null
    val upcoming = remember(queueTracks, queueIndex) {
        queueTracks.mapIndexedNotNull { i, t -> if (i > queueIndex) i to t else null }
    }

    val bgStyle = look.backgroundStyle
    val sliderStyle = look.sliderStyle
    val squigglySlider = look.squiggly
    val framedControls = look.framedControls
    val buttonsStyle = look.buttonsStyle
    val enableCanvas = prefs.getBoolean(PrefKeys.EnableCanvas, true)
    val canvasSource = runCatching {
        CanvasSource.valueOf(prefs.getString(PrefKeys.CanvasSource, CanvasSource.AUTO.name))
    }.getOrDefault(CanvasSource.AUTO)

    var canvasArt by remember { mutableStateOf<com.music.vivi.canvas.CanvasArtwork?>(null) }
    // Music video (when enabled and available) replaces the canvas: video -> canvas -> cover.
    val trackVideo = com.music.vivi.compose.ui.player.video.rememberTrackVideo(track)
    // "Original size" framing: the cover slot morphs from 1:1 to the video's aspect (and back).
    val coverMorph = com.music.vivi.compose.ui.player.video.rememberCoverMorph(
        com.music.vivi.compose.ui.player.video.rememberVideoFraming(prefs),
        trackVideo?.url,
    )
    val canvasSquareUrl = if (trackVideo != null) null else CanvasResolver.squareUrl(canvasArt)
    val canvasTallUrl = if (trackVideo != null) null else CanvasResolver.tallUrl(canvasArt)
    var lyricsOn by remember { mutableStateOf(com.music.vivi.nativelib.AppEnv.flag("DEV_LYRICS")) } // DEV_LYRICS: dev aid
    // Lyrics the user wants, minus tracks the lookup found nothing for (their toggle is left alone).
    val showLyrics = lyricsOn && !com.music.vivi.compose.ui.player.lyrics.LyricsAvailability.isNone(nowPlaying?.videoId)
    fun toggleLyrics() {
        if (lyricsOn && !showLyrics) com.music.vivi.compose.ui.player.lyrics.LyricsAvailability.noneFor = null // retry
        else lyricsOn = !lyricsOn
    }
    var showTrackActions by remember { mutableStateOf(false) }
    com.music.vivi.compose.keys.ShortcutEventEffect { e ->
        when (e) {
            com.music.vivi.compose.keys.ShortcutEvent.ToggleLyrics -> toggleLyrics()
            com.music.vivi.compose.keys.ShortcutEvent.ShowTrackActions -> showTrackActions = !showTrackActions
            else -> Unit
        }
    }
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

    val hideThumbnail = look.hideCover
    // Only animate to artwork that is already decoded at full quality.
    val readyArt = com.music.vivi.compose.ui.component.rememberReadyArtwork(track?.artworkUrl)
    var artTarget by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    LaunchedEffect(readyArt) { artTarget = track?.videoId to readyArt }

    // The lyrics view swaps to the portrait stream; keep its decoder open (parked on the
    // first frame) so toggling lyrics shows it instantly instead of cold-starting HLS.
    if (canvasTallUrl != null && canvasTallUrl != canvasSquareUrl) {
        com.music.vivi.compose.ui.player.background.rememberCanvasVideo(canvasTallUrl, playing = false)
    }

    val playerGlass = remember { dev.chrisbanes.haze.HazeState() }
    com.music.vivi.compose.ui.glass.ProvideGlass(playerGlass) {
    Box(Modifier.fillMaxSize()) {
        PlayerBackgroundLayer(
            style = bgStyle,
            artworkUrl = if (swap.active) swap.displayedArt else readyArt,
            modifier = Modifier.fillMaxSize()
                .songSwapViewport(swap)
                .songSwapBackground(swap)
                .glassSource(playerGlass),
            // Same URL as the cover slot: one shared decoder, frames stay in sync.
            canvasVideoUrl = canvasSquareUrl,
            canvasPlaying = playing,
            showClearArtwork = !showLyrics,
        )

        com.music.vivi.compose.ui.player.swap.SongSwapLoadingIndicator(swap.anim, Modifier.fillMaxSize())

        com.music.vivi.compose.ui.component.VisageToast(
            message = toastMessage,
            onDismiss = { toastMessage = null },
        )

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val coverState = rememberSharedContentState(key = "np-cover")

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = com.music.vivi.compose.ui.responsive.windowLayout().pagePadding,
                        vertical = if (com.music.vivi.compose.ui.responsive.windowLayout().isMobile) 8.dp else 16.dp,
                    )
                    // With the blur setting on, the top bar floats over this page: keep content below it.
                    .padding(top = com.music.vivi.compose.ui.glass.LocalTopBarInset.current),
            ) {
                val windowLayout = com.music.vivi.compose.ui.responsive.windowLayout()
                // Pane-based (not window-based): the pinned queue rail steals 360dp from this pane.
                // Stacked = media on top, controls below, queue in a tray. Narrow or portrait-ish panes.
                val stacked = maxWidth < 760.dp || maxHeight > maxWidth * 1.25f
                // Three panes (cover | lyrics | controls) when lyrics are open and the pane is ultra wide.
                // Not with a music video: it already plays above the controls, so a left cover would show it twice.
                val triple = !stacked && showLyrics && trackVideo == null && maxWidth >= 1700.dp && maxWidth.value / maxHeight.value >= 1.85f
                val paneGap = if (stacked) 16.dp else NowPlayingPaneGap
                val underQueue = showUnderPlaybar && !showLyrics && !stacked
                val idle = nowPlayingIdleMetrics(maxWidth, maxHeight, limitControlsWidth, controlsMaxWidth, expanded = windowLayout.isExpanded)
                val mainH = idle.mainHeight
                val idleArtSize = idle.coverSize
                val idleStackW = idle.stackWidth
                val idleContentW = idle.contentWidth
                val tripleArt = minOf(mainH * 0.78f, maxWidth * 0.24f)
                val lyricsStackW = if (triple) 520.dp else (maxWidth * 0.44f).coerceAtMost(440.dp)
                val lyricsContentW = if (triple) minOf(maxWidth, 3000.dp) else if (windowLayout.isExpanded) minOf(maxWidth, 2000.dp) else maxWidth
                val lyricsLeftW = if (triple) tripleArt else (lyricsContentW - paneGap - lyricsStackW).coerceAtLeast(0.dp)
                val tripleMidW = (lyricsContentW - tripleArt - lyricsStackW - paneGap * 2).coerceAtLeast(0.dp)

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
                    if (triple) tripleArt else if (showLyrics) mainH else idleArtSize,
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


                // ---- Shared pieces, placed by the wide (side by side) and stacked (compact) layouts below. ----
                val coverSlot: @Composable () -> Unit = {
                            PlainAnimatedVisibility(
                                visible = !hideThumbnail && (!showLyrics || triple),
                                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                                exit = fadeOut() + scaleOut(targetScale = 0.92f),
                                label = "npCoverLarge",
                            ) {
                                val coverVisibility = this
                                AlbumGestureCard(
                                    controller = controller,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .volumeWheel()
                                        .morphAspect { coverMorph.aspect }
                                        .sharedBounds(
                                            sharedContentState = coverState,
                                            animatedVisibilityScope = coverVisibility,
                                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                                ContentScale.Crop,
                                                Alignment.Center,
                                            ),
                                        ),
                                ) {
                                    AnimatedContent(
                                        targetState = if (swap.active) (swap.displayed?.videoId to swap.displayedArt) else artTarget,
                                        // Same song, sharper URL: swap in place, no second transition.
                                        contentKey = { it.first },
                                        transitionSpec = {
                                            if (swap.active) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None else
                                            (slideInHorizontally { it / 3 } + fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                                                (slideOutHorizontally { -it / 3 } + fadeOut() + scaleOut(targetScale = 0.92f))
                                        },
                                        label = "npArtwork",
                                        modifier = Modifier.fillMaxSize()
                                            .songSwapCover(swap)
                                            .morphClip(elevation = 28.dp) { nowPlayingCoverRadius(squareCover, coverMorph.progress) },
                                    ) { (_, artUrl) ->
                                        Box(Modifier.fillMaxSize().then(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))) {
                                            val squareUrl = canvasSquareUrl
                                            // The canvas takes over the cover: the static art only shows
                                            // until the first video frame, then it's removed entirely.
                                            var canvasShowing by remember(squareUrl, trackVideo?.url) { mutableStateOf(false) }
                                            // Cross-fade: the art lingers (fading out) while the video fades in.
                                            val artAlpha by animateFloatAsState(
                                                if (canvasShowing) 0f else 1f,
                                                VisageMotion.fade(500),
                                                label = "npArtFade",
                                            )
                                            if (artAlpha > 0.01f) {
                                                // Art with baked-in rounded corners is zoomed so its transparent
                                                // corners fall outside our own rounded slot.
                                                val bleed = com.music.vivi.compose.ui.component.rememberArtworkCornerBleed(artUrl)
                                                AsyncImage(
                                                    model = artUrl,
                                                    contentDescription = track?.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer { scaleX = bleed; scaleY = bleed; alpha = artAlpha },
                                                )
                                            }
                                            if (trackVideo != null) {
                                                com.music.vivi.compose.ui.player.video.MusicVideoSurface(
                                                    video = trackVideo,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onFirstFrame = {
                                                        coverMorph.report(trackVideo.url, it)
                                                        canvasShowing = true
                                                    },
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
                }
                val lyricsSlot: @Composable () -> Unit = {
                            PlainAnimatedVisibility(
                                visible = showLyrics,
                                enter = fadeIn() + slideInHorizontally { -it / 8 },
                                exit = fadeOut() + slideOutHorizontally { -it / 8 },
                                label = "npLyrics",
                            ) {
                                LyricsPanel(
                                    onClose = { lyricsOn = false },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 8.dp),
                                )
                            }
                }
                val controlsBlock: @Composable (Modifier, Boolean) -> Unit = { modifier, showThumb ->
                            Column(
                                modifier.songSwapControls(swap).playerControlsSurface(framedControls)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                PlainAnimatedVisibility(
                                    visible = showThumb,
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
                                            .clip(if (squareCover) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(14.dp)),
                                    )
                                }
                                NowPlayingTrackInfo(
                                    compactTitle = stacked,
                                    track = track,
                                    stateName = state.name,
                                    onOpenArtist = { navController.openArtistByName(it) },
                                    onOpenAlbum = { t -> scope.launch { navController.openAlbumOf(t.albumId, t.album.orEmpty(), t.artist) } },
                                    modifier = Modifier.weight(1f),
                                )
                                NowPlayingTrackActions(
                                    isFavorite = isFavorite,
                                    onDownload = {
                                        val t = track
                                        if (t == null) {
                                            toastMessage = "Nothing playing"
                                        } else {
                                            downloads.enqueue(t.videoId, t.title, t.artist)
                                        }
                                    },
                                    onFavorite = { scope.launch { controller.toggleFavorite() } },
                                )
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
                                playPauseHeight = playPauseHeight,
                                onPrev = { if (canPrev) controller.previous() },
                                onPlayPause = { if (playing) controller.pause() else controller.play() },
                                onNext = { controller.next() },
                                previousTracks = queuePreviewIndices(queueIndex, queueTracks.size, true, 3).map { it to queueTracks[it] },
                                nextTracks = queuePreviewIndices(queueIndex, queueTracks.size, false, 3).map { it to queueTracks[it] },
                                onPickTrack = { index -> scope.launch(Dispatchers.IO) { controller.playQueue(queueTracks, index) } },
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
                }
                val toolsRow: @Composable (Modifier, Boolean) -> Unit = { toolsMod, compactTools ->
                    NowPlayingBottomBar(
                        modifier = toolsMod,
                        compact = compactTools,
                        queueActive = showUnderPlaybar || queueOpen,
                        lyricsActive = showLyrics,
                        shuffle = shuffle,
                        repeat = repeat,
                        volume = displayVolume,
                        volumeStyle = look.volumeStyle,
                        onQueue = {
                            when {
                                queueOpen -> onCloseQueue?.invoke()
                                queueMode == QueuePanelMode.FULLSCREEN ->
                                    onOpenFullscreenQueue?.invoke()
                                queueMode == QueuePanelMode.PINNED_RIGHT ->
                                    onOpenPinnedQueue?.invoke()
                                        ?: onOpenFullscreenQueue?.invoke()
                                else -> underPlaybarOpen = !underPlaybarOpen
                            }
                        },
                        onLyrics = { toggleLyrics() },
                        onShuffle = { controller.toggleShuffle() },
                        onRepeat = { controller.cycleRepeatMode() },
                        onFullscreen = {
                            WindowFullscreen.enter(windowState)
                            navController.navigate(Routes.NOW_PLAYING_FULLSCREEN) {
                                launchSingleTop = true
                            }
                        },
                        onVolumeChange = {
                            scrubVolume = true
                            scrubVol = it
                            controller.setVolume(it)
                        },
                        onVolumeFinished = {
                            scrubVolume = false
                            prefs.setFloat(PrefKeys.PlaybackVolume, scrubVol)
                        },
                        more = {
                            ToolIconButton(Icons.Outlined.MoreVert, "More", circle = true) {
                                showTrackActions = true
                            }
                            TrackActionsMenu(
                                expanded = showTrackActions,
                                track = track,
                                onDismiss = { showTrackActions = false },
                            )
                        },
                    )

                }

                if (stacked) {
                    // Compact layout: media (cover or lyrics) on top, controls under it, tools last.
                    val colMax = 560.dp
                    val showThumbStacked = showLyrics && !hideThumbnail && maxHeight >= 600.dp
                    // Without lyrics the whole group (media, controls, tools) is centred as one block, so the
                    // controls sit right under the cover/video instead of at the bottom of a tall empty slot.
                    val stackedMaxH = maxHeight
                    val stackedMaxW = maxWidth
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (showLyrics) Arrangement.Top else Arrangement.Center,
                    ) {
                        if (showLyrics) {
                            Box(Modifier.weight(1f).fillMaxWidth()) { lyricsSlot() }
                        } else {
                            val aspect = if (trackVideo != null) 16f / 9f else 1f
                            val availH = (stackedMaxH - 300.dp).coerceAtLeast(120.dp)
                            val mediaW = minOf(stackedMaxW, colMax, availH * aspect)
                            Box(Modifier.size(mediaW, mediaW / aspect), contentAlignment = Alignment.Center) { coverSlot() }
                        }
                        Spacer(Modifier.height(12.dp))
                        controlsBlock(Modifier.widthIn(max = colMax).fillMaxWidth(), showThumbStacked)
                        Spacer(Modifier.height(8.dp))
                        toolsRow(Modifier.widthIn(max = colMax).fillMaxWidth(), true)
                    }
                } else
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
                            coverSlot()

                            if (!triple) lyricsSlot()

                            if (hideThumbnail && !showLyrics) {
                                Spacer(Modifier.fillMaxSize())
                            }
                        }

                        if (triple) {
                            Box(Modifier.width(tripleMidW).height(mainH)) { lyricsSlot() }
                        }

                        Column(
                            Modifier
                                .width(stackW)
                                .height(stackH),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.Start,
                        ) {
                            val rightCanvasUrl = canvasTallUrl ?: canvasSquareUrl
                            val showCanvasOnRight = showLyrics && rightCanvasUrl != null && trackVideo == null
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
                                    // A music video takes the free space above the controls while lyrics are open,
                                    // at its own 16:9 shape and sitting on the controls.
                                    PlainAnimatedVisibility(
                                        visible = showLyrics && trackVideo != null,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        label = "npRightVideo",
                                    ) {
                                        val videoShape = if (squareCover) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(24.dp)
                                        Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
                                            trackVideo?.let {
                                                com.music.vivi.compose.ui.player.video.MusicVideoSurface(
                                                    video = it,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(16f / 9f)
                                                        // Same swap layer as the controls below it, so the two move in step.
                                                        .songSwapControls(swap)
                                                        .shadow(24.dp, videoShape, clip = false)
                                                        .clip(videoShape),
                                                )
                                            }
                                        }
                                    }
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
                                                    // Moves with the swap like the cover does.
                                                    .songSwapCover(swap)
                                                    .padding(bottom = 16.dp)
                                                    .clip(if (squareCover) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(24.dp))
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

                            controlsBlock(
                                Modifier.align(Alignment.CenterHorizontally).nowPlayingControlsWidth(limitControlsWidth, if (showLyrics) adwaitaClampWidth(stackW, controlsMaxWidth) else controlsMaxWidth).then(if (underQueue && !showCanvasOnRight) Modifier.weight(1f) else Modifier),
                                showLyrics && !triple && !hideThumbnail && !showCanvasOnRight,
                            )
                            if (!underQueue) {
                                Spacer(Modifier.weight((1f - controlsBias).coerceIn(0.001f, 1f)))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // Same width as cover + stack above — tools | volume (fills) | more.
                    toolsRow(Modifier.width(contentW), false)
                }
            }
        }
    }
    }
}

/** Gap between the cover and the controls column. */
internal val NowPlayingPaneGap = 28.dp

/** Sizes of the idle (no lyrics) Now Playing layout in a window of [maxWidth] x [maxHeight]. */
internal class NowPlayingIdleMetrics(val mainHeight: Dp, val coverSize: Dp, val stackWidth: Dp, val contentWidth: Dp)

/** Shared by the screen and the settings preview, so both lay the cover and the controls out the same way. */
internal fun nowPlayingIdleMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    limitControlsWidth: Boolean,
    controlsMaxWidth: Dp,
    bottomChrome: Dp = 60.dp,
    /** Large / ultra wide windows: cap the cover and the controls column instead of stretching them. */
    expanded: Boolean = false,
): NowPlayingIdleMetrics {
    val mainH = (maxHeight - bottomChrome).coerceAtLeast(200.dp)
    // Cover from height, then stack takes the rest so the row is wide and stack isn't skinny.
    val art = minOf(mainH * 0.92f, maxWidth * 0.40f, 900.dp)
    val available = (maxWidth - NowPlayingPaneGap - art).coerceAtLeast(0.dp)
    val stack = minOf(if (limitControlsWidth) adwaitaClampWidth(available, controlsMaxWidth) else available, if (expanded) 820.dp else 1200.dp)
    return NowPlayingIdleMetrics(mainH, art, stack, (art + NowPlayingPaneGap + stack).coerceAtMost(maxWidth))
}

/** Corner radius of the big cover: square when the setting says so, easing from 28 to 20 dp while it morphs to a video. */
internal fun nowPlayingCoverRadius(square: Boolean, morphProgress: Float = 0f): Dp =
    if (square) 0.dp else (28f - 8f * morphProgress.coerceIn(0f, 1f)).dp

/** Width rule of the controls column ("Limit controls width"). */
internal fun Modifier.nowPlayingControlsWidth(limit: Boolean, maxWidth: Dp): Modifier =
    (if (limit) widthIn(max = maxWidth) else this).fillMaxWidth()

/** Title, credited artists (linked) and album of the playing song. Used by Now Playing and its settings preview. */
@Composable
internal fun NowPlayingTrackInfo(
    track: NowPlaying?,
    stateName: String,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (NowPlaying) -> Unit,
    modifier: Modifier = Modifier,
    /** Smaller title for narrow, stacked layouts. */
    compactTitle: Boolean = false,
) {
    val ink = LocalPlayerInk.current
    Column(modifier) {
        Text(
            track?.title ?: "Nothing playing",
            style = if (compactTitle) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ink.text(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val credits = track?.artist.orEmpty()
        if (credits.isBlank()) {
            Text(
                stateName,
                style = MaterialTheme.typography.titleMedium,
                color = ink.text(0.65f),
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            com.music.vivi.compose.ui.artist.ArtistLinks(
                credits = credits,
                style = MaterialTheme.typography.titleMedium,
                color = ink.text(0.65f),
                hoverColor = ink.text(),
                onOpen = onOpenArtist,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val albumName = track?.album.orEmpty()
        if (albumName.isNotBlank()) {
            val canOpenAlbum = com.music.vivi.compose.ui.album.AlbumLinks.isOpenable(albumName, track?.albumId, track?.localPath != null)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Album, "Album", tint = ink.text(0.5f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                com.music.vivi.compose.ui.album.AlbumLink(
                    albumName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.text(0.55f),
                    hoverColor = ink.text(),
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = if (!canOpenAlbum || track == null) null else { { onOpenAlbum(track) } },
                )
            }
        }
    }
}

/** Download and favourite buttons next to the title. */
@Composable
internal fun NowPlayingTrackActions(isFavorite: Boolean, onDownload: () -> Unit, onFavorite: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolIconButton(Icons.Outlined.Download, "Make available offline", onClick = onDownload)
        ToolIconButton(
            if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            "Favorite",
            active = isFavorite,
            onClick = onFavorite,
        )
    }
}

/** Bottom row of Now Playing: tool buttons | volume (fills) | more. Shared with the settings preview. */
@Composable
internal fun NowPlayingBottomBar(
    queueActive: Boolean,
    lyricsActive: Boolean,
    shuffle: Boolean,
    repeat: RepeatMode,
    volume: Float,
    volumeStyle: VolumeControlStyle,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFullscreen: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Compact windows: icons spread over the row, no volume slider (the system volume / wheel still work). */
    compact: Boolean = false,
    more: @Composable () -> Unit,
) {
    // Compact: one row, fixed spacing, anchored in the centre; "more" is part of it (7 x 44dp + 6 x 4dp = 332dp).
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally) else Arrangement.Start,
    ) {
        Row(
            Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        ) {
            ToolIconButton(Icons.AutoMirrored.Outlined.QueueMusic, "Queue", active = queueActive, onClick = onQueue)
            ToolIconButton(Icons.Outlined.Lyrics, "Lyrics", active = lyricsActive, onClick = onLyrics)
            ToolIconButton(Icons.Outlined.Shuffle, "Shuffle", active = shuffle, onClick = onShuffle)
            ToolIconButton(
                if (repeat == RepeatMode.One) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                "Repeat",
                active = repeat != RepeatMode.Off,
                onClick = onRepeat,
            )
            ToolIconButton(Icons.Outlined.PictureInPictureAlt, "Pop out mini player") {
                com.music.vivi.compose.ui.popup.PopupPlayer.toggle()
            }
            ToolIconButton(Icons.Outlined.OpenInFull, "Fullscreen player", onClick = onFullscreen)
        }
        if (!compact) Row(
            Modifier.weight(1f).volumeWheel().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingVolumeControl(volume = volume, style = volumeStyle, onChange = onVolumeChange, onFinished = onVolumeFinished)
        }
        Box { more() }
    }
}

/**
 * The Now Playing look settings, read in one place for the screen and the settings preview. Call it with a prefs version
 * you observe ([com.music.vivi.compose.ui.settings.rememberPrefsVersion]) so a change recomposes the caller.
 */
internal data class NowPlayingLook(
    val backgroundStyle: PlayerBackgroundStyle,
    val darkness: Float,
    val squareCover: Boolean,
    val hideCover: Boolean,
    val framedControls: Boolean,
    val buttonsStyle: PlayerButtonsStyle,
    val sliderStyle: SliderStyle,
    val squiggly: Boolean,
    val limitControlsWidth: Boolean,
    val controlsMaxWidth: Dp,
    val playPauseHeight: Dp,
    val volumeStyle: VolumeControlStyle,
)

internal fun DesktopPrefs.nowPlayingLook(): NowPlayingLook {
    fun <E : Enum<E>> enumOf(key: String, default: E, parse: (String) -> E): E =
        runCatching { parse(getString(key, default.name)) }.getOrDefault(default)
    return NowPlayingLook(
        backgroundStyle = enumOf(PrefKeys.PlayerBackgroundStyle, com.music.vivi.compose.prefs.LookDefaults.PlayerStyle) { PlayerBackgroundStyle.valueOf(it) },
        darkness = getInt(PrefKeys.PlayerBackgroundDarkness, com.music.vivi.compose.prefs.LookDefaults.PlayerDarkness).coerceIn(0, 100) / 100f,
        squareCover = getBoolean(PrefKeys.SquareCover, false),
        hideCover = getBoolean(PrefKeys.HidePlayerThumbnail, false),
        framedControls = getBoolean(PrefKeys.UsePlayerV2, false),
        buttonsStyle = enumOf(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT) { PlayerButtonsStyle.valueOf(it) },
        sliderStyle = enumOf(PrefKeys.SliderStyle, SliderStyle.M3_EXPRESSIVE) { SliderStyle.valueOf(it) },
        squiggly = getBoolean(PrefKeys.SquigglySlider, false),
        limitControlsWidth = getBoolean(PrefKeys.LimitPlayerControlsWidth, getBoolean(PrefKeys.AdwaitaInspired, false)),
        controlsMaxWidth = getInt(PrefKeys.PlayerControlsWidth, 600).coerceIn(400, 600).dp,
        playPauseHeight = getInt(PrefKeys.PlayPauseHeight, 56).coerceIn(48, 96).dp,
        volumeStyle = enumOf(PrefKeys.VolumeControlStyle, VolumeControlStyle.SLIDER) { VolumeControlStyle.valueOf(it) },
    )
}

/** Volume icon plus the slider or pill (and its percentage), per the "Volume control" setting. */
@Composable
internal fun RowScope.NowPlayingVolumeControl(
    volume: Float,
    style: VolumeControlStyle,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val ink = LocalPlayerInk.current
    Icon(
        Icons.Outlined.VolumeDown,
        contentDescription = null,
        tint = ink.text(0.75f),
        modifier = Modifier.size(20.dp),
    )
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
        Text(
            "${(volume * 100).toInt()}%",
            style = com.music.vivi.compose.theme.VisageType.readout,
            color = ink.text(0.7f),
            modifier = Modifier.widthIn(min = 40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Seek bar + time labels; the only part of Now Playing that recomposes per position tick. */
@Composable
internal fun NowPlayingSeekSection(
    duration: Long,
    sliderStyle: SliderStyle,
    squiggly: Boolean,
    modifier: Modifier = Modifier,
    showTimes: Boolean = true,
) {
    val ink = LocalPlayerInk.current
    val controller = LocalPlaybackController.current
    val positionMs by controller.positionMs.collectAsState()
    val playbackState by controller.state.collectAsState()
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
            activeColor = ink.primary,
            inactiveColor = ink.fill(0.28f),
            style = sliderStyle,
            squiggly = squiggly,
            isPlaying = playbackState == com.music.vivi.player.PlaybackState.Playing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showTimes) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(displayPos), style = com.music.vivi.compose.theme.VisageType.readout, color = ink.text(0.7f))
            Text(formatMs(duration), style = com.music.vivi.compose.theme.VisageType.readout, color = ink.text(0.7f))
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
    playPauseHeight: androidx.compose.ui.unit.Dp = 56.dp,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    previousTracks: List<Pair<Int, QueueTrack>> = emptyList(),
    nextTracks: List<Pair<Int, QueueTrack>> = emptyList(),
    onPickTrack: (Int) -> Unit = {},
) {
    val ink = LocalPlayerInk.current
    var eat by androidx.compose.runtime.remember { mutableStateOf(0f) }
    var eatSide by androidx.compose.runtime.remember { mutableStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    val (pillBg, pillIcon) = when (style) {
        PlayerButtonsStyle.DEFAULT -> ink.fill(0.12f) to ink.primary
        PlayerButtonsStyle.PRIMARY -> scheme.primary.copy(alpha = 0.85f) to scheme.onPrimary
        PlayerButtonsStyle.TERTIARY -> scheme.tertiary.copy(alpha = 0.85f) to scheme.onTertiary
    }
    val playBg = when (style) {
        PlayerButtonsStyle.DEFAULT -> ink.primary
        PlayerButtonsStyle.PRIMARY -> scheme.primary
        PlayerButtonsStyle.TERTIARY -> scheme.tertiary
    }
    val playFg = when (style) {
        PlayerButtonsStyle.DEFAULT -> ink.inverse
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
            previewTracks = previousTracks,
            onPickPreview = onPickTrack,
            eatDirection = 1,
            onEat = { eat = it; eatSide = 1 },
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
            modifier = Modifier.weight(1f).height(playPauseHeight).graphicsLayer {
                scaleX = 1f - 0.5f * eat
                alpha = 1f - 0.7f * eat
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (eatSide < 0) 0f else 1f, 0.5f)
            },
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayPauseIcon(playing = playing, tint = playFg, iconSize = 28.dp)
            }
        }
        TransportPill(
            enabled = canNext,
            onClick = onNext,
            width = 72.dp,
            containerColor = pillBg,
            previewTracks = nextTracks,
            onPickPreview = onPickTrack,
            eatDirection = -1,
            onEat = { eat = it; eatSide = -1 },
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
internal fun QueuePillStrip(
    upcoming: List<Pair<Int, QueueTrack>>,
    onPlayIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current
    Column(modifier.fillMaxWidth()) {
        Text(
            if (upcoming.isEmpty()) "Up next" else "Up next · ${upcoming.size}",
            style = MaterialTheme.typography.labelLarge,
            color = ink.text(0.55f),
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
                    color = ink.text(0.14f),
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
                                color = ink.text(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.visageMarquee(),
                            )
                            if (track.artist.isNotBlank()) {
                                Text(
                                    track.artist,
                                    color = ink.text(0.6f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.visageMarquee(),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "queue-end-sentinel") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ink.text(0.08f),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Pets,
                            contentDescription = null,
                            tint = ink.text(0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Nothing to see here...",
                            color = ink.text(0.45f),
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
    containerColor: Color = LocalPlayerInk.current.fill(0.12f),
    /** Queue entries (index, track) listed in a dropdown while the pill is held; empty disables hold. */
    previewTracks: List<Pair<Int, QueueTrack>> = emptyList(),
    onPickPreview: (Int) -> Unit = {},
    /** -1: dragging left eats the play button (Next); 1: dragging right (Previous); 0: no drag. */
    eatDirection: Int = 0,
    onEat: (Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val ink = LocalPlayerInk.current
    var pressed by androidx.compose.runtime.remember { mutableStateOf(false) }
    var menu by androidx.compose.runtime.remember { mutableStateOf(false) }
    var dragX by androidx.compose.runtime.remember { mutableStateOf(0f) }
    val radius by animateDpAsState(if (pressed) 14.dp else 28.dp, com.music.vivi.compose.ui.motion.VisageMotion.spatialFast(), label = "pillRadius")
    val offsetX by animateFloatAsState(dragX, if (pressed) spring(stiffness = 10000f) else spring(stiffness = 400f), label = "pillDrag")
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentOnEat by androidx.compose.runtime.rememberUpdatedState(onEat)
    val currentTracks by androidx.compose.runtime.rememberUpdatedState(previewTracks)
    Box {
        Surface(
            shape = RoundedCornerShape(radius),
            color = containerColor.copy(alpha = if (enabled) containerColor.alpha else containerColor.alpha * 0.4f),
            modifier = Modifier
                .size(width = width, height = 56.dp)
                .graphicsLayer { translationX = offsetX }
                .pointerInput(enabled, eatDirection) {
                    if (!enabled) return@pointerInput
                    val travel = 140.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        var total = 0f
                        var moved = false
                        var held = false
                        try {
                            while (true) {
                                val event = if (!moved && !held && currentTracks.isNotEmpty()) {
                                    withTimeoutOrNull(450) { awaitPointerEvent() }
                                } else awaitPointerEvent()
                                if (event == null) { held = true; menu = true; continue }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    val progress = if (eatDirection == 0) 0f else (total * -eatDirection / travel).coerceIn(0f, 1f)
                                    when {
                                        held -> Unit
                                        !moved -> currentOnClick()
                                        progress >= 0.5f -> currentOnClick()
                                    }
                                    break
                                }
                                total += change.position.x - change.previousPosition.x
                                if (!moved && abs(total) > viewConfiguration.touchSlop && eatDirection != 0 && !held) {
                                    moved = true
                                    menu = false
                                }
                                if (moved) {
                                    change.consume()
                                    val toward = (total * -eatDirection).coerceIn(0f, travel)
                                    dragX = toward * -eatDirection
                                    currentOnEat(toward / travel)
                                }
                            }
                        } finally {
                            pressed = false
                            dragX = 0f
                            currentOnEat(0f)
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = { content() })
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for ((i, t) in previewTracks) {
                DropdownMenuItem(
                    text = { Column { Text(t.title, maxLines = 1); Text(t.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1) } },
                    leadingIcon = { AsyncImage(t.artworkUrl, null, Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop) },
                    onClick = { menu = false; onPickPreview(i) },
                )
            }
        }
    }
}

@Composable
internal fun ToolIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = LocalPlayerInk.current.primary,
    circle: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = LocalPlayerInk.current
    val bg = if (active) ink.primary else ink.fill(0.12f)
    val iconTint = if (active) ink.inverse else tint
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
    val ink = LocalPlayerInk.current
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
        color = ink.text(0.12f),
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
                Icon(Icons.Default.Remove, null, tint = ink.text(), modifier = Modifier.size(16.dp))
            }
            
            Box(
                Modifier.width(36.dp).clickable { editing = true },
                contentAlignment = Alignment.Center
            ) {
                if (editing) {
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= 3) text = it.filter { c -> c.isDigit() } },
                        textStyle = MaterialTheme.typography.labelSmall.copy(color = ink.text(), textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        cursorBrush = SolidColor(ink.text()),
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
                        color = ink.text(),
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
                Icon(Icons.Default.Add, null, tint = ink.text(), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Ease out toward the width limit while preserving the available width on small panes. */
internal fun adwaitaClampWidth(available: Dp, maximum: Dp): Dp {
    val threshold = maximum * 0.75f
    if (available <= threshold) return available
    val span = maximum - threshold
    val progress = ((available - threshold).value / (3f * span.value)).coerceIn(0f, 1f)
    val remaining = 1f - progress
    return threshold + span * (1f - remaining * remaining * remaining)
}
