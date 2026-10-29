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
import androidx.compose.runtime.Stable
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

/** The sample songs the preview swaps between (the shared preview queue). */
private val samples get() = com.music.vivi.compose.ui.settings.preview.PreviewSampleData.queue.take(4)

/**
 * Drives the song-swap settings preview: which sample song is shown and the running transition. The scene
 * ([SongSwapPreviewScene]) lives inside the settings preview frame, which cannot be clicked, so the buttons that call [play]
 * are the frame's own header actions. [config] is applied live, so changing any setting shows on the next play.
 */
@Stable
class SongSwapPreviewState internal constructor(internal val state: SongSwapState, private val scope: kotlinx.coroutines.CoroutineScope) {
    var index by mutableIntStateOf(0)
        private set
    var slowLoad by mutableStateOf(false)
    private var job: Job? = null

    fun play(dir: SwapDirection) {
        job?.cancel()
        val target = if (dir.forward) (index + 1) % samples.size else (index - 1 + samples.size) % samples.size
        var ready = false
        job = scope.launch {
            launch { delay(if (slowLoad) 2200L else 0L); ready = true }
            state.run(dir, isReady = { ready }, commit = { index = target })
        }
    }
}

@Composable
fun rememberSongSwapPreviewState(config: SongSwapConfig): SongSwapPreviewState {
    val scope = rememberCoroutineScope()
    val holder = remember { SongSwapPreviewState(SongSwapState(), scope) }
    holder.state.config = config
    return holder
}

/**
 * A miniature Now Playing scene for a 16:9 window (the frame's virtual window): the real background layer, cover, track info,
 * seek bar and transport row, with the song-swap layers applied to them as the real screen does.
 */
@Composable
fun SongSwapPreviewScene(preview: SongSwapPreviewState, modifier: Modifier = Modifier) {
    val state = preview.state
    val song = samples[preview.index]
    val glass = remember { dev.chrisbanes.haze.HazeState() }
    com.music.vivi.compose.theme.ProvidePlayerInk(
        solid = false, backdrop = MaterialTheme.colorScheme.surface, darkness = 0.35f, framedControls = false,
    ) {
        Box(modifier.fillMaxSize()) {
            com.music.vivi.compose.ui.player.background.PlayerBackgroundLayer(
                style = com.music.vivi.compose.prefs.PlayerBackgroundStyle.GRADIENT,
                artworkUrl = null,
                canvasPlaying = false,
                darknessOverride = 0.35f,
                modifier = Modifier.fillMaxSize().songSwapViewport(state).songSwap(state, SwapLayer.Background),
            )
            Row(Modifier.fillMaxSize().padding(40.dp), verticalAlignment = Alignment.CenterVertically) {
                com.music.vivi.compose.ui.component.CoverImage(
                    null, song.videoId,
                    Modifier.fillMaxHeight().aspectRatio(1f).songSwap(state, SwapLayer.Cover).clip(RoundedCornerShape(28.dp)),
                )
                Spacer(Modifier.width(40.dp))
                Column(Modifier.weight(1f).songSwap(state, SwapLayer.Controls), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    com.music.vivi.compose.ui.player.NowPlayingTrackInfo(
                        track = com.music.vivi.player.NowPlaying(song.videoId, song.title, song.artist, song.album, null, song.durationMs),
                        stateName = "Playing", onOpenArtist = {}, onOpenAlbum = {},
                    )
                    com.music.vivi.compose.ui.player.NowPlayingSeekSection(
                        duration = song.durationMs,
                        sliderStyle = com.music.vivi.compose.prefs.SliderStyle.M3_EXPRESSIVE,
                        squiggly = false,
                    )
                    com.music.vivi.compose.ui.player.TransportRow(
                        playing = true, canPrev = true, canNext = true, enabledPlay = true,
                        onPrev = {}, onPlayPause = {}, onNext = {},
                    )
                }
            }
            SongSwapLoadingIndicator(state, Modifier.fillMaxSize())
        }
    }
}
