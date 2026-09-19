package com.music.vivi.compose.ui.player

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.LocalWindowState
import com.music.vivi.compose.WindowFullscreen
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.VolumeControlStyle
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.ui.component.PlayPauseIcon
import com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer
import com.music.vivi.compose.ui.player.canvas.CanvasResolver
import com.music.vivi.compose.ui.player.canvas.CanvasSource
import com.music.vivi.player.PlaybackState
import com.music.vivi.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Immersive fullscreen Now Playing — tighter stack, no Up-next queue.
 * Enter from the OpenInFull control on the standard NP page; exit returns there.
 */
@Composable
fun NowPlayingFullscreenScreen(navController: NavController) {
    val controller = LocalPlaybackController.current
    val windowState = LocalWindowState.current
    val prefs = koinInject<DesktopPrefs>()
    val scope = rememberCoroutineScope()

    DisposableEffect(windowState) {
        WindowFullscreen.enter(windowState)
        onDispose { WindowFullscreen.exit(windowState) }
    }

    val nowPlaying by controller.nowPlaying.collectAsState()
    val state by controller.state.collectAsState()
    val positionMs by controller.positionMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val isFavorite by controller.isFavorite.collectAsState()
    val volume by controller.volume.collectAsState()
    val shuffle by controller.shuffleEnabled.collectAsState()
    val repeat by controller.repeatMode.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
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

    val bgStyle = runCatching {
        PlayerBackgroundStyle.valueOf(
            prefs.getString(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name),
        )
    }.getOrDefault(PlayerBackgroundStyle.GRADIENT)
    val enableCanvas = prefs.getBoolean(PrefKeys.EnableCanvas, true)
    val canvasSource = runCatching {
        CanvasSource.valueOf(prefs.getString(PrefKeys.CanvasSource, CanvasSource.AUTO.name))
    }.getOrDefault(CanvasSource.AUTO)

    var canvasVideoUrl by remember { mutableStateOf<String?>(null) }
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
            showClearArtwork = true,
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            if (!hideThumbnail) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val size = minOf(maxWidth, maxHeight).coerceAtMost(600.dp)
                    val coverShape = RoundedCornerShape(20.dp)
                    AsyncImage(
                        model = track?.artworkUrl,
                        contentDescription = track?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(size)
                            .shadow(28.dp, coverShape, clip = false)
                            .clip(coverShape),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 520.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "Nothing playing",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track?.artist.orEmpty().ifBlank { state.name },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    ToolIconButton(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                    ) { scope.launch { controller.toggleFavorite() } }
                    Spacer(Modifier.width(8.dp))
                    ToolIconButton(Icons.Filled.CloseFullscreen, "Exit fullscreen") {
                        WindowFullscreen.exit(windowState)
                        navController.popBackStack()
                    }
                }

                Spacer(Modifier.height(32.dp))
                PlayerSeekBar(
                    progress = progress,
                    onScrub = { scrubbing = true; scrubPos = it },
                    onScrubFinished = {
                        scrubbing = false
                        controller.seekTo((scrubPos * duration).toLong().coerceAtLeast(0L))
                    },
                    style = runCatching { com.music.vivi.compose.prefs.SliderStyle.valueOf(prefs.getString(PrefKeys.SliderStyle, "DEFAULT")) }.getOrDefault(com.music.vivi.compose.prefs.SliderStyle.DEFAULT),
                    squiggly = prefs.getBoolean(PrefKeys.SquigglySlider, false),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(displayPos), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Text(formatMs(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TransportPill(
                        enabled = canPrev || track != null,
                        onClick = { if (canPrev) controller.previous() },
                        width = 72.dp,
                    ) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Surface(
                        onClick = { if (playing) controller.pause() else controller.play() },
                        enabled = track != null || state != PlaybackState.Idle,
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            PlayPauseIcon(playing = playing, tint = Color.Black, iconSize = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (playing) "Pause" else "Play",
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    TransportPill(
                        enabled = canNext,
                        onClick = { controller.next() },
                        width = 72.dp,
                    ) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                    Spacer(Modifier.width(4.dp))
                    val volumeStyle = runCatching { VolumeControlStyle.valueOf(prefs.getString(PrefKeys.VolumeControlStyle, VolumeControlStyle.SLIDER.name)) }.getOrDefault(VolumeControlStyle.SLIDER)
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
                        Icon(
                            Icons.Outlined.VolumeDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp),
                        )
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
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "${(displayVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.widthIn(min = 36.dp),
                        )
                    }
                }
            }
        }
    }
}

