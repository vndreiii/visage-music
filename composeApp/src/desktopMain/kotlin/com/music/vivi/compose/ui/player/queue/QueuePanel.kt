package com.music.vivi.compose.ui.player.queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.ui.component.InteractionCornerRadius
import com.music.vivi.database.PlayHistoryRepository
import com.music.vivi.database.Play_event
import com.music.vivi.player.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private enum class QueueTab { Queue, Recent }

private val PinOffsetSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)
private val PinSizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)
private val TrayOffsetSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Right-rail queue: slides in from the trailing edge and expands width so the
 * main Now Playing column is pushed inward.
 */
@Composable
fun AnimatedQueuePinnedRail(
    visible: Boolean,
    onDismiss: () -> Unit,
    onExpandFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = PinOffsetSpring) { it } +
            expandHorizontally(animationSpec = PinSizeSpring, expandFrom = Alignment.End),
        exit = slideOutHorizontally(animationSpec = PinOffsetSpring) { it } +
            shrinkHorizontally(animationSpec = PinSizeSpring, shrinkTowards = Alignment.End),
        modifier = modifier,
    ) {
        QueuePinnedPanel(
            onDismiss = onDismiss,
            onSwitchMode = onExpandFullscreen,
        )
    }
}

/**
 * Bottom tray queue: flush to the window bottom (not a floating dialog), rounded top only.
 */
@Composable
fun BoxScope.AnimatedQueueBottomTray(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = TrayOffsetSpring) { it } + fadeIn(),
        exit = slideOutVertically(animationSpec = TrayOffsetSpring) { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            QueueFullscreenPanel(
                onDismiss = onDismiss,
                onSwitchMode = onPin,
            )
        }
    }
}

@Composable
fun QueuePinnedPanel(
    onDismiss: () -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight()
            .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
    ) {
        QueuePanelBody(
            pinned = true,
            onDismiss = onDismiss,
            onSwitchMode = onSwitchMode,
        )
    }
}

@Composable
fun QueueFullscreenPanel(
    onDismiss: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    QueuePanelBody(
        pinned = false,
        onDismiss = onDismiss,
        onSwitchMode = onSwitchMode,
    )
}

@Composable
private fun QueuePanelBody(
    pinned: Boolean,
    onDismiss: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val controller = LocalPlaybackController.current
    val history = koinInject<PlayHistoryRepository>()
    val scope = rememberCoroutineScope()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val queueTracks by controller.queueTracks.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
    var tab by remember { mutableStateOf(QueueTab.Queue) }
    var recent by remember { mutableStateOf<List<Play_event>>(emptyList()) }

    LaunchedEffect(tab) {
        if (tab == QueueTab.Recent) {
            recent = withContext(Dispatchers.IO) { history.recentDistinct(80) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabLabel("Queue", selected = tab == QueueTab.Queue) { tab = QueueTab.Queue }
            Spacer(Modifier.width(16.dp))
            TabLabel("Recently played", selected = tab == QueueTab.Recent) { tab = QueueTab.Recent }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSwitchMode) {
                Icon(
                    if (pinned) Icons.Outlined.OpenInFull else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Fullscreen queue" else "Pin queue to the right",
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        if (!pinned) {
            Spacer(Modifier.height(12.dp))
            NowPlayingHeader(nowPlaying)
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip(Icons.Outlined.Shuffle, "Shuffle", Modifier.weight(1f)) {
                controller.toggleShuffle()
            }
            ModeChip(Icons.Outlined.Repeat, "Repeat", Modifier.weight(1f)) {
                controller.cycleRepeatMode()
            }
            ModeChip(Icons.Outlined.Radio, "Radio", Modifier.weight(1f)) {
                val id = nowPlaying?.videoId ?: return@ModeChip
                scope.launch(Dispatchers.IO) {
                    runCatching { controller.startRadio(id) }
                }
            }
        }
        if (!pinned) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nowPlaying?.album ?: "Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Next in Queue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${queueTracks.size} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = listOf("All", "Popular", "Discover", "Deep cuts", "Party", "Electronic")
                items(chips) { label ->
                    FilterChip(
                        selected = label == "All",
                        onClick = {},
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        when (tab) {
            QueueTab.Queue -> {
                if (pinned && nowPlaying != null) {
                    Text(
                        "Now playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    QueueSongRow(
                        title = nowPlaying!!.title,
                        subtitle = nowPlaying!!.artist,
                        artworkUrl = nowPlaying!!.artworkUrl,
                        active = true,
                        onClick = {},
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Next from queue",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val listState = rememberLazyListState()
                
                LaunchedEffect(queueIndex) {
                    if (queueIndex in queueTracks.indices) {
                        listState.animateScrollToItem(queueIndex)
                    }
                }
                
                LazyColumn(
                    Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (queueTracks.isEmpty()) {
                        item {
                            Text(
                                "Queue is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        itemsIndexed(queueTracks, key = { i, t -> "${t.videoId}-$i" }) { index, track ->
                            val active = index == queueIndex
                            QueueSongRow(
                                title = track.title.ifBlank {
                                    if (active) nowPlaying?.title ?: track.videoId else track.videoId
                                },
                                subtitle = track.artist.ifBlank {
                                    if (active) nowPlaying?.artist.orEmpty() else "Queued track"
                                }.ifBlank { "Queued track" },
                                artworkUrl = track.artworkUrl
                                    ?: if (active) nowPlaying?.artworkUrl else null,
                                active = active,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        controller.playQueue(queueTracks, index)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            QueueTab.Recent -> {
                val listState = rememberLazyListState()
                
                LaunchedEffect(queueIndex) {
                    if (queueIndex in queueTracks.indices) {
                        listState.animateScrollToItem(queueIndex)
                    }
                }
                
                LazyColumn(
                    Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (recent.isEmpty()) {
                        item {
                            Text(
                                "Nothing played yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        items(recent, key = { it.id }) { ev ->
                            QueueSongRow(
                                title = ev.title,
                                subtitle = ev.artists,
                                artworkUrl = ev.thumbnail,
                                active = ev.song_id == nowPlaying?.videoId,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        controller.playVideo(ev.song_id)
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
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(InteractionCornerRadius),
        color = Color.Transparent,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                // Same weight always — bold↔normal also nudges intrinsic size.
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Always reserve indicator height so maximize/close don't jump on tab change.
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .height(3.dp)
                    .width(28.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun NowPlayingHeader(track: NowPlaying?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = track?.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueueSongRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (active) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
    }
}
