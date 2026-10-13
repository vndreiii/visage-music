package com.music.vivi.compose.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vivi.compose.ui.component.horizontalWheelScroll
import com.music.vivi.compose.ui.component.interactiveScale
import com.music.vivi.database.Play_event
import java.time.LocalTime

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Up late"
}

/** Greeting + shortcuts to the browse pages that have no tab of their own. */
@Composable
internal fun HomeHeader(
    name: String?,
    onNewReleases: () -> Unit,
    onCharts: () -> Unit,
    onMoods: () -> Unit,
    onHistory: () -> Unit,
) {
    val greeting = remember { greetingFor(LocalTime.now()) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            if (name.isNullOrBlank()) greeting else "$greeting, ${name.substringBefore(' ')}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutPill(Icons.Outlined.NewReleases, "New releases", onNewReleases)
            ShortcutPill(Icons.AutoMirrored.Outlined.TrendingUp, "Charts", onCharts)
            ShortcutPill(Icons.Outlined.Mood, "Moods & genres", onMoods)
            ShortcutPill(Icons.Outlined.History, "History", onHistory)
        }
    }
}

@Composable
private fun ShortcutPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.interactiveScale(hoveredScale = 1.03f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

/** Last played songs from local history — one click to jump back in. */
@Composable
internal fun RecentlyPlayedShelf(
    recent: List<Play_event>,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (index: Int) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Jump back in",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) { Text("History") }
        }
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).horizontalWheelScroll(rowState),
        ) {
            itemsIndexed(recent, key = { _, e -> e.song_id }) { index, event ->
                RecentCard(
                    event = event,
                    active = event.song_id == playingId,
                    playing = isPlaying,
                    onClick = { onPlay(index) },
                )
            }
        }
    }
}

@Composable
private fun RecentCard(event: Play_event, active: Boolean, playing: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.width(148.dp).interactiveScale(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))) {
                if (event.thumbnail != null) {
                    AsyncImage(
                        model = event.thumbnail,
                        contentDescription = event.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, null) }
                    }
                }
                if (active) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = if (playing) "Playing" else "Paused",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Text(
                event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            )
            Text(
                event.artists,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
