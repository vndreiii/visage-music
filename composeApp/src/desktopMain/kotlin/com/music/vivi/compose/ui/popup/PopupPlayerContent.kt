package com.music.vivi.compose.ui.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.prefs.SliderStyle
import com.music.vivi.compose.ui.component.CoverImage
import com.music.vivi.compose.ui.component.PlayPauseIcon
import com.music.vivi.compose.ui.player.PlayerSeekBar
import com.music.vivi.compose.ui.player.formatMs
import com.music.vivi.compose.ui.player.lyrics.LyricsPanel
import com.music.vivi.player.PlaybackState
import org.koin.compose.koinInject

/** Everything inside the popup except the window chrome (header), wired to the shared [LocalPlaybackController]. */
@Composable
fun PopupPlayerContent(modifier: Modifier = Modifier) {
    val controller = LocalPlaybackController.current
    val nowPlaying by controller.nowPlaying.collectAsState()
    PopupPlayerLayout(
        modifier = modifier,
        art = {
            CoverImage(
                model = nowPlaying?.artworkUrl,
                seed = nowPlaying?.videoId ?: "idle",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).testTag("popup-art"),
                contentScale = ContentScale.Crop,
            )
        },
        info = { compact -> PopupTrackInfo(nowPlaying?.title, nowPlaying?.artist, nowPlaying?.album, compact) },
        controls = { compact -> PopupControls(compact) },
        lyrics = {
            // Same panel as Now Playing: fetches, caches and syncs on its own from the shared controller.
            LyricsPanel(onClose = {}, modifier = Modifier.fillMaxSize())
        },
    )
}

@Composable
internal fun PopupTrackInfo(title: String?, artist: String?, album: String?, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title ?: "Nothing playing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("popup-title"),
        )
        if (!artist.isNullOrBlank()) Text(
            artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("popup-artist"),
        )
        if (!compact && !album.isNullOrBlank()) Text(
            album,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("popup-album"),
        )
    }
}

@Composable
private fun PopupControls(compact: Boolean) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    val state by controller.state.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val position by controller.positionMs.collectAsState()
    val volume by controller.volume.collectAsState()
    val queueIds by controller.queueIds.collectAsState()
    val queueIndex by controller.queueIndex.collectAsState()
    val playing = state == PlaybackState.Playing || state == PlaybackState.Loading
    val style = remember {
        runCatching { SliderStyle.valueOf(prefs.getString(PrefKeys.SliderStyle, SliderStyle.M3_EXPRESSIVE.name)) }
            .getOrDefault(SliderStyle.M3_EXPRESSIVE)
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }
    val progress = when {
        scrubbing -> scrubPos
        duration <= 0L -> 0f
        else -> (position.toFloat() / duration).coerceIn(0f, 1f)
    }
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PlayerSeekBar(
            progress = progress,
            onScrub = { scrubbing = true; scrubPos = it },
            onScrubFinished = {
                scrubbing = false
                controller.seekTo((scrubPos * duration).toLong().coerceAtLeast(0L))
            },
            style = style,
            squiggly = false,
            compact = true,
            isPlaying = state == PlaybackState.Playing,
            activeColor = scheme.primary,
            inactiveColor = scheme.onSurface.copy(alpha = 0.22f),
            modifier = Modifier.fillMaxWidth().testTag("popup-seek"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (scrubbing) (scrubPos * duration).toLong() else position
            Text(formatMs(shown), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(formatMs(duration), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller.previous() }, enabled = queueIndex > 0 || position > 3_000) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(30.dp))
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = { if (playing) controller.pause() else controller.play() },
                shape = CircleShape,
                color = scheme.primary,
                modifier = Modifier.size(if (compact) 48.dp else 56.dp).testTag("popup-playpause"),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlayPauseIcon(playing = playing, tint = scheme.onPrimary, iconSize = 28.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { controller.next() }, enabled = queueIndex < queueIds.lastIndex || queueIds.isEmpty()) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(30.dp))
            }
        }
        if (!compact) Row(Modifier.fillMaxWidth().padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp, "Volume", Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
            Slider(
                value = volume,
                onValueChange = { controller.setVolume(it) },
                onValueChangeFinished = { prefs.setFloat(PrefKeys.PlaybackVolume, controller.volume.value) },
                colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary),
                modifier = Modifier.weight(1f).testTag("popup-volume"),
            )
        }
    }
}
