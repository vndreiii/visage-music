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
import com.music.vivi.compose.ui.album.openAlbumOf
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

/**
 * The controls card of the fullscreen player: title and credits, seek bar, transport, tool buttons, volume and the inline
 * queue strip. Stateless (everything comes in as values and callbacks) so Settings can render the real card with sample data.
 */
@Composable
internal fun FullscreenControlsCard(
    cfg: FullscreenSettings,
    track: com.music.vivi.player.NowPlaying?,
    stateName: String,
    playing: Boolean,
    isFavorite: Boolean,
    duration: Long,
    canPrev: Boolean,
    canNext: Boolean,
    shuffle: Boolean,
    repeat: RepeatMode,
    volume: Float,
    inlineQueue: Boolean,
    upcoming: List<Pair<Int, QueueTrack>>,
    previousPreview: List<Pair<Int, QueueTrack>>,
    nextPreview: List<Pair<Int, QueueTrack>>,
    chromeMod: Modifier,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onRadio: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeFinished: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current
        Column(
            modifier
                .fillMaxWidth()
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
                            stateName,
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
                                onOpen = onOpenArtist,
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
                            val canOpen = com.music.vivi.compose.ui.album.AlbumLinks.isOpenable(album, track?.albumId, track?.localPath != null)
                            com.music.vivi.compose.ui.album.AlbumLink(
                                album,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ink.text(0.55f),
                                hoverColor = ink.text(),
                                modifier = Modifier.weight(1f, fill = false),
                                onClick = if (!canOpen) null else onOpenAlbum,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                ToolIconButton(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Favorite",
                    active = isFavorite,
                ) { onToggleFavorite() }
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
                    enabledPlay = track != null || stateName != PlaybackState.Idle.name,
                    style = cfg.buttonsStyle,
                    playPauseHeight = cfg.playPauseHeightDp.dp,
                    onPrev = onPrev,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    previousTracks = previousPreview,
                    nextTracks = nextPreview,
                    onPickTrack = onPlayIndex,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrefHorizontal(cfg.showShuffle) {
                        Row {
                            ToolIconButton(Icons.Outlined.Shuffle, "Shuffle", active = shuffle) { onShuffle() }
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    PrefHorizontal(cfg.showRepeat) {
                        Row {
                            ToolIconButton(
                                if (repeat == RepeatMode.One) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                                "Repeat",
                                active = repeat != RepeatMode.Off,
                            ) { onRepeat() }
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    PrefHorizontal(cfg.showRadio) {
                        Row {
                            ToolIconButton(Icons.Outlined.Radio, "Start radio") { onRadio() }
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        PrefHorizontal(cfg.showVolume) {
                            VolumeGroup(
                                style = cfg.volumeStyle,
                                volume = volume,
                                onChange = onVolumeChange,
                    onFinished = onVolumeFinished,
                            )
                        }
                    }
                }
                PrefVertical(inlineQueue) {
                    QueuePillStrip(
                        upcoming = upcoming,
                        onPlayIndex = onPlayIndex,
                        modifier = Modifier.fillMaxWidth().height(InlineQueueHeight).padding(top = 16.dp),
                    )
                }
            }
        }
}

/**
 * Where the fullscreen player puts its pieces: the cover square, and the column that holds the lyrics over the controls.
 * The geometry comes from [FullscreenLyricsLayout.split]; the pieces glide to their new place when the side, cover size
 * or lyrics state change. Shared by the real screen and the Settings preview, so the preview cannot drift from the layout.
 *
 * [cover] receives the animated side of the cover slot.
 */
@Composable
internal fun FullscreenStage(
    width: Dp,
    height: Dp,
    side: com.music.vivi.compose.prefs.FullscreenCoverSide,
    coverShown: Boolean,
    coverScale: Float,
    showLyrics: Boolean,
    controlsMaxWidth: Dp,
    cover: @Composable (size: () -> Dp) -> Unit,
    lyrics: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    val split = FullscreenLyricsLayout.split(width, height, side, coverShown, coverScale)
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

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset { IntOffset(coverX.value.roundToPx(), coverY.value.roundToPx()) }) {
            Box(
                Modifier.widthOf { coverDim.value }.heightOf { coverDim.value },
                contentAlignment = Alignment.Center,
            ) {
                PlainAnimatedVisibility(
                    visible = coverShown,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    label = "fsCover",
                ) { cover { coverDim.value } }
            }
        }
        val ctrlLimitPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            if (controlsMaxWidth == Dp.Infinity) Int.MAX_VALUE else controlsMaxWidth.roundToPx()
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
                    ) { lyrics() }
                }
                Box(Modifier.layoutId("ctrl")) { controls() }
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
            val lyricsP = measurables.first { it.layoutId == "lyrics" }
                .measure(androidx.compose.ui.unit.Constraints(c.maxWidth, c.maxWidth, lyricsH, lyricsH))
            val ctrlBottom = c.maxHeight - ctrl.height
            val ctrlY = (ctrlBottom + (ctrlBottom / 2 - ctrlBottom) * lyricsClosed.value).toInt()
            layout(c.maxWidth, c.maxHeight) {
                lyricsP.place(0, 0)
                ctrl.place((c.maxWidth - ctrlW) / 2, ctrlY)
            }
        }
    }
}
