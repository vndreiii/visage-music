package com.music.vivi.compose.ui.player.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.reader
import com.music.vivi.compose.ui.component.hqArtworkUrl
import com.music.vivi.compose.ui.component.rememberReadyArtwork
import com.music.vivi.compose.ui.settings.rememberPrefsVersion
import com.music.vivi.player.NowPlaying
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest

/**
 * Binds a [SongSwapState] to the live [PlaybackController]: detects track changes and direction,
 * waits for the new song to be ready (state not Loading, artwork decoded) and exposes the
 * "committed" track that the layers should show.
 */
@Stable
class PlayerSongSwap internal constructor(val anim: SongSwapState) {
    /** The track whose content is currently shown (lags behind the live one while swapping). */
    var displayed by mutableStateOf<NowPlaying?>(null)
        internal set

    /** Decoded artwork for [displayed] (null until ready). */
    var displayedArt by mutableStateOf<String?>(null)
        internal set

    val config: SongSwapConfig get() = anim.config

    /** True when this screen should hand the animation to the engine (not legacy Slide / None). */
    val active: Boolean get() = anim.config.mode.engineInPlayer

    /** Track for title / artist / controls: delayed with the swap unless "animate controls" is off. */
    fun controlsTrack(live: NowPlaying?): NowPlaying? =
        if (active && anim.config.animateControls) displayed else live
}

@Composable
fun rememberPlayerSongSwap(controller: PlaybackController, prefs: DesktopPrefs): PlayerSongSwap {
    val anim = remember { SongSwapState() }
    val swap = remember(anim) { PlayerSongSwap(anim).also { it.displayed = controller.nowPlaying.value } }
    val version = rememberPrefsVersion(prefs)
    anim.config = remember(version) { SongSwapConfig.resolve(prefs.reader()) }

    val live by controller.nowPlaying.collectAsState()
    val liveArt = rememberReadyArtwork(live?.artworkUrl)
    val liveRef = rememberUpdatedState(live)
    val liveArtRef = rememberUpdatedState(liveArt)

    // Same song (metadata / sharper artwork arriving): refresh in place, no animation.
    LaunchedEffect(live, liveArt, anim.busy) {
        if (!anim.busy && (swap.displayed == null || live == null || live?.videoId == swap.displayed?.videoId)) {
            swap.displayed = live
            swap.displayedArt = liveArt
        }
    }

    LaunchedEffect(controller) {
        var lastId = controller.nowPlaying.value?.videoId
        var lastIds = controller.queueIds.value
        var lastIdx = controller.queueIndex.value
        controller.nowPlaying.map { it?.videoId }.distinctUntilChanged().collectLatest { id ->
            if (id == lastId) return@collectLatest
            withFrameNanos { } // let queueIds / queueIndex catch up with nowPlaying
            val ids = controller.queueIds.value
            val idx = controller.queueIndex.value
            val dir = SwapDirection.detect(lastId, lastIds, lastIdx, id, ids, idx)
            lastId = id; lastIds = ids; lastIdx = idx
            fun commit() {
                swap.displayed = liveRef.value
                swap.displayedArt = liveArtRef.value
            }
            if (dir == null) { commit(); return@collectLatest }
            anim.run(
                dir = dir,
                animate = anim.config.mode.engineInPlayer,
                isReady = {
                    val t = liveRef.value
                    val url = t?.artworkUrl
                    val art = liveArtRef.value
                    val artReady = url == null || art == url || art == hqArtworkUrl(url)
                    t?.videoId == id && artReady &&
                        controller.state.value.let { it != PlaybackState.Loading && it != PlaybackState.Idle }
                },
                isFailed = {
                    controller.state.value == PlaybackState.Error || controller.nowPlaying.value == null
                },
                commit = ::commit,
            )
        }
    }
    return swap
}

/** Records the viewport the camera distances are relative to (put on a full-screen layer). */
fun androidx.compose.ui.Modifier.songSwapViewport(swap: PlayerSongSwap): androidx.compose.ui.Modifier =
    songSwapViewport(swap.anim)

fun androidx.compose.ui.Modifier.songSwapBackground(swap: PlayerSongSwap): androidx.compose.ui.Modifier =
    songSwap(swap.anim, SwapLayer.Background)

fun androidx.compose.ui.Modifier.songSwapCover(swap: PlayerSongSwap): androidx.compose.ui.Modifier =
    songSwap(swap.anim, SwapLayer.Cover)

fun androidx.compose.ui.Modifier.songSwapControls(swap: PlayerSongSwap): androidx.compose.ui.Modifier =
    songSwap(swap.anim, SwapLayer.Controls)
