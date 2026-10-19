package com.music.vivi.compose.ui.player.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class SampleSong(val title: String, val artist: String, val top: Color, val bottom: Color, val cover: Color)

private val samples = listOf(
    SampleSong("Golden Hour", "Sample Artist", Color(0xFF7A3E12), Color(0xFF1B0F06), Color(0xFFFFB347)),
    SampleSong("Midnight Drive", "Another Band", Color(0xFF123A7A), Color(0xFF060F1B), Color(0xFF5DA9FF)),
    SampleSong("Green Room", "The Examples", Color(0xFF1B6B3A), Color(0xFF06150C), Color(0xFF7BE0A0)),
    SampleSong("Rosewater", "Demo Duo", Color(0xFF7A1245), Color(0xFF1B0610), Color(0xFFFF7DB5)),
)

/**
 * A miniature Now Playing scene with sample covers that plays the configured swap on demand.
 * [config] is applied live, so changing any setting is reflected on the next play.
 */
@Composable
fun SongSwapPreview(config: SongSwapConfig, modifier: Modifier = Modifier) {
    val state = remember { SongSwapState() }
    state.config = config
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    var slowLoad by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun play(dir: SwapDirection) {
        job?.cancel()
        val target = if (dir.forward) (index + 1) % samples.size else (index - 1 + samples.size) % samples.size
        var ready = false
        job = scope.launch {
            launch { delay(if (slowLoad) 2200L else 0L); ready = true }
            state.run(dir, isReady = { ready }, commit = { index = target })
        }
    }

    val song = samples[index]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black),
        ) {
            Box(
                Modifier.fillMaxSize().songSwapViewport(state).songSwap(state, SwapLayer.Background)
                    .background(Brush.verticalGradient(listOf(song.top, song.bottom))),
            )
            Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.fillMaxHeight().aspectRatio(1f).songSwap(state, SwapLayer.Cover)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(song.cover, song.top))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.displayMedium, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f).songSwap(state, SwapLayer.Controls), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().background(Color.White))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { Box(Modifier.height(28.dp).width(if (it == 1) 56.dp else 36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) }
                    }
                }
            }
            SongSwapLoadingIndicator(state, Modifier.fillMaxSize())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { play(SwapDirection.PREVIOUS) }) { Text("Previous") }
            FilledTonalButton(onClick = { play(SwapDirection.NEXT) }) { Text("Play preview (next)") }
            FilterChip(
                selected = slowLoad,
                onClick = { slowLoad = !slowLoad },
                label = { Text("Simulate slow load") },
            )
        }
    }
}
