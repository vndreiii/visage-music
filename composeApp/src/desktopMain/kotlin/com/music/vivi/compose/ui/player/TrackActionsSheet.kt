package com.music.vivi.compose.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import com.music.vivi.compose.ui.motion.ViviMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.music.innertube.models.PlaylistItem
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.ui.component.InteractionCornerRadius
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.player.NowPlaying
import com.music.vivi.player.QueueTrack
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private enum class TrackMenuPane { Actions, Playlists }

/**
 * Track “more” menu anchored to the More button.
 *
 * Uses [Popup] + bounded [heightIn] then [verticalScroll] — not Material [DropdownMenu],
 * which crashes on Compose Desktop with infinite max-height constraints.
 */
private val MenuTransformOrigin = TransformOrigin(1f, 1f)

@Composable
fun TrackActionsMenu(
    expanded: Boolean,
    track: NowPlaying?,
    onDismiss: () -> Unit,
) {
    val controller = LocalPlaybackController.current
    val yt = koinInject<YouTubeMusicService>()
    val downloads = koinInject<com.music.vivi.compose.download.TrackDownloadService>()
    val scope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var pane by remember { mutableStateOf(TrackMenuPane.Actions) }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var playlistsError by remember { mutableStateOf<String?>(null) }
    val visibleState = remember { MutableTransitionState(false) }
    val downloadStatus by downloads.status.collectAsState()

    LaunchedEffect(downloadStatus) {
        when (val s = downloadStatus) {
            is com.music.vivi.compose.download.DownloadStatus.Succeeded ->
                toastMessage = if (s.file.isFile) "Available offline: ${s.file.name}" else "Available offline"
            is com.music.vivi.compose.download.DownloadStatus.Failed ->
                toastMessage = s.message
            else -> Unit
        }
    }

    LaunchedEffect(expanded) {
        visibleState.targetState = expanded
        if (!expanded) {
            pane = TrackMenuPane.Actions
            playlistsError = null
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage == null) return@LaunchedEffect
        delay(1_800)
        toastMessage = null
    }

    LaunchedEffect(pane, expanded) {
        if (!expanded || pane != TrackMenuPane.Playlists) return@LaunchedEffect
        if (playlists.isNotEmpty() || playlistsLoading) return@LaunchedEffect
        playlistsLoading = true
        playlistsError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { yt.libraryPlaylists().getOrThrow() }
        }
        playlistsLoading = false
        result.onSuccess { playlists = it }
            .onFailure { playlistsError = it.message ?: "Couldn't load playlists" }
    }

    toastMessage?.let { message ->
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, -96),
            properties = PopupProperties(focusable = false),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (visibleState.currentState || visibleState.targetState) {
        Popup(
            alignment = Alignment.BottomEnd,
            offset = IntOffset(0, -56),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                // Same scale+fade from the anchor corner as Material's DropdownMenu (Settings menu).
                enter = ViviMotion.menuEnter(MenuTransformOrigin),
                exit = ViviMotion.menuExit(MenuTransformOrigin),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 380.dp)
                        .heightIn(max = 480.dp),
                ) {
                    AnimatedContent(
                        targetState = pane,
                        transitionSpec = {
                            if (targetState == TrackMenuPane.Playlists) {
                                slideInHorizontally { it / 3 } togetherWith
                                    slideOutHorizontally { -it / 4 }
                            } else {
                                slideInHorizontally { -it / 3 } togetherWith
                                    slideOutHorizontally { it / 4 }
                            }
                        },
                        label = "trackMenuPane",
                    ) { current ->
                        when (current) {
                            TrackMenuPane.Actions -> TrackActionsPane(
                                track = track,
                                onDismiss = onDismiss,
                                onToast = { toastMessage = it },
                                onOpenPlaylists = { pane = TrackMenuPane.Playlists },
                                onPlayNext = {
                                    val t = track ?: return@TrackActionsPane
                                    controller.playNext(
                                        QueueTrack(
                                            videoId = t.videoId,
                                            title = t.title,
                                            artist = t.artist,
                                            artworkUrl = t.artworkUrl,
                                            localPath = t.localPath,
                                            durationMs = t.durationMs,
                                            album = t.album,
                                            lyricsPath = t.lyricsPath,
                                            embeddedLyrics = t.embeddedLyrics,
                                        ),
                                    )
                                    toastMessage = "Playing next"
                                    onDismiss()
                                },
                                onAddToQueue = {
                                    val t = track ?: return@TrackActionsPane
                                    controller.addToQueue(
                                        QueueTrack(
                                            videoId = t.videoId,
                                            title = t.title,
                                            artist = t.artist,
                                            artworkUrl = t.artworkUrl,
                                            localPath = t.localPath,
                                            durationMs = t.durationMs,
                                            album = t.album,
                                            lyricsPath = t.lyricsPath,
                                            embeddedLyrics = t.embeddedLyrics,
                                        ),
                                    )
                                    toastMessage = "Added to queue"
                                    onDismiss()
                                },
                                onFavorite = {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching { controller.toggleFavorite() }
                                    }
                                },
                                onShuffle = {
                                    controller.toggleShuffle()
                                    onDismiss()
                                },
                                onStartRadio = {
                                    val id = track?.videoId
                                    if (!id.isNullOrBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            runCatching { controller.startRadio(id) }
                                        }
                                    }
                                    onDismiss()
                                },
                                onDownload = {
                                    val t = track
                                    if (t == null) {
                                        toastMessage = "Nothing playing"
                                    } else {
                                        downloads.enqueue(t.videoId, t.title, t.artist)
                                        toastMessage = "Making ${t.title} available offline…"
                                    }
                                    onDismiss()
                                },
                                onShare = {
                                    val videoId = track?.videoId
                                    if (!videoId.isNullOrBlank()) {
                                        val link = "https://music.youtube.com/watch?v=$videoId"
                                        runCatching {
                                            Toolkit.getDefaultToolkit().systemClipboard
                                                .setContents(StringSelection(link), null)
                                        }.onSuccess { toastMessage = "Copied successfully" }
                                    }
                                    onDismiss()
                                },
                            )
                            TrackMenuPane.Playlists -> PlaylistPickerPane(
                                playlists = playlists,
                                loading = playlistsLoading,
                                error = playlistsError,
                                onBack = { pane = TrackMenuPane.Actions },
                                onPick = { playlist ->
                                    val videoId = track?.videoId
                                    if (videoId.isNullOrBlank()) return@PlaylistPickerPane
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                yt.addToPlaylist(playlist.id, videoId).getOrThrow()
                                            }
                                        }
                                        result.onSuccess {
                                            toastMessage = "Added to ${playlist.title}"
                                            onDismiss()
                                        }.onFailure {
                                            toastMessage = it.message ?: "Couldn't add to playlist"
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackActionsPane(
    track: NowPlaying?,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
    onOpenPlaylists: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            AsyncImage(
                model = track?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist.orEmpty().ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Favorite")
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Shuffle,
                label = "Shuffle",
                onClick = onShuffle,
            )
            if (track?.localPath == null) PillAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Radio,
                label = "Start radio",
                onClick = onStartRadio,
            )
        }

        Spacer(Modifier.height(8.dp))
        ActionRow(
            Icons.AutoMirrored.Outlined.PlaylistPlay,
            "Play next",
            "Add to the top of your queue",
            onPlayNext,
        )
        ActionRow(
            Icons.Outlined.QueueMusic,
            "Add to queue",
            "Add to the bottom of your queue",
            onAddToQueue,
        )
        if (track?.localPath == null) {
            ActionRow(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", "Add to one of your playlists", onOpenPlaylists)
            ActionRow(Icons.Outlined.Download, "Make available offline", "Save for offline playback", onDownload)
            ActionRow(Icons.Outlined.Share, "Share", "Share a link to this item", onShare)
        }

    }
}

@Composable
private fun PlaylistPickerPane(
    playlists: List<PlaylistItem>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPick: (PlaylistItem) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Add to playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> {
                Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            error != null -> {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            playlists.isEmpty() -> {
                Text(
                    "No playlists found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    playlists.forEach { playlist ->
                        Surface(
                            onClick = { onPick(playlist) },
                            shape = RoundedCornerShape(InteractionCornerRadius),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = playlist.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        playlist.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val sub = buildString {
                                        playlist.author?.name?.let { append(it) }
                                        playlist.songCountText?.let {
                                            if (isNotEmpty()) append(" · ")
                                            append(it)
                                        }
                                    }
                                    if (sub.isNotBlank()) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        modifier = modifier.height(40.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
