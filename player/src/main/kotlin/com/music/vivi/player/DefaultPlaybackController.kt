package com.music.vivi.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.vivi.player.art.ArtworkCache
import com.music.vivi.player.mpris.MprisService
import com.music.vivi.player.mpv.MpvPlayer
import com.music.vivi.player.mpv.PlaybackEngine
import com.music.vivi.player.session.SessionSecretStore
import com.music.vivi.player.stream.ResolvedStream
import com.music.vivi.player.stream.StreamPrefetchCache
import com.music.vivi.player.stream.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class DefaultPlaybackController(
    applyStoredSession: Boolean = true,
    private val favorites: FavoriteStore? = null,
    private val fadeSettings: () -> PlaybackFadeSettings = { PlaybackFadeSettings() },
    private val streamSettings: () -> com.music.vivi.player.stream.StreamSettings = { com.music.vivi.player.stream.StreamSettings() },
    private val fileTrackResolver: suspend (String) -> QueueTrack? = { null },
    private val enableMpris: Boolean = true,
    private val engineFactory: ((MpvPlayer.State) -> Unit, (Double, Double) -> Unit, (String) -> Unit) -> PlaybackEngine =
        { state, position, error -> MpvPlayer(state, position, error) },
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val playMutex = Mutex()
    private val streams = StreamPrefetchCache(scope, streamSettings)
    private var volumeFadeJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState.Idle)
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _isFavorite = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)
    private val _queueTracks = MutableStateFlow<List<QueueTrack>>(emptyList())
    private val _queueIds = MutableStateFlow<List<String>>(emptyList())
    private val _queueIndex = MutableStateFlow(-1)
    private val _queueLoading = MutableStateFlow(false)
    private val radioInFlight = AtomicInteger(0)
    private val _volume = MutableStateFlow(1f)
    private val _shuffleEnabled = MutableStateFlow(false)
    private val _repeatMode = MutableStateFlow(RepeatMode.Off)

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()
    override val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()
    override val queueIds: StateFlow<List<String>> = _queueIds.asStateFlow()
    override val queueTracks: StateFlow<List<QueueTrack>> = _queueTracks.asStateFlow()
    override val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()
    override val queueLoading: StateFlow<Boolean> = _queueLoading.asStateFlow()
    override val volume: StateFlow<Float> = _volume.asStateFlow()
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val artwork = ArtworkCache()
    private var artFileUrl: String? = null
    private var restoredPosition: Pair<String, Long>? = null

    /** Coalesce rapid next/prev into the final landed index. */
    private val desiredIndex = AtomicInteger(-1)
    private var navigateJob: Job? = null
    private var loadGeneration = AtomicInteger(0)

    /**
     * Serializes halt/load on the mpv slots. Without it a superseded load (already past its
     * generation check) can land `halt()` (pause=yes) after the new track's `load()`.
     */
    private val playerOps = Mutex()

    /** Dual libmpv: active plays UI clock; idle receives the incoming crossfade stream. */
    private val pauseRequested = AtomicBoolean(false)
    private val activeSlot = AtomicInteger(0)
    private val dualOverlapActive = AtomicBoolean(false)
    private val endCrossfadeArmed = AtomicBoolean(false)
    /**
     * While true, the seek bar only follows the incoming (idle) player — never the
     * outgoing track near EOF. Set at soft UI / dual start; cleared after swap.
     */
    private val crossfadeUiHold = AtomicBoolean(false)
    private val mpris = if (enableMpris) MprisService(
        onPlay = { play() },
        onPause = { pause() },
        onStop = { stop() },
        onSeekUs = { us -> seekTo(us / 1000L) },
        onNext = { next() },
        onPrevious = { previous() },
        onVolume = { v -> setVolume(v.toFloat()) },
        onShuffle = { enabled ->
            if (_shuffleEnabled.value != enabled) toggleShuffle()
        },
    ) else null

    private val players: Array<PlaybackEngine> = arrayOf(
        createMpvPlayer(0),
        createMpvPlayer(1),
    )

    private fun activeMpv(): PlaybackEngine = players[activeSlot.get()]
    private fun idleMpv(): PlaybackEngine = players[1 - activeSlot.get()]

    private fun createMpvPlayer(slot: Int): PlaybackEngine = engineFactory(
         { mpvState -> onPlayerState(slot, mpvState) },
        { posSec, durSec -> onPlayerPosition(slot, posSec, durSec) },
        { msg ->
            if (slot == activeSlot.get() || dualOverlapActive.get()) {
                _lastError.value = msg
                println("mpv[$slot] error: $msg")
            }
        },
    )

    private fun onPlayerState(slot: Int, mpvState: MpvPlayer.State) {
        // Incoming (idle) player must not drive UI / queue advance.
        if (slot != activeSlot.get()) {
            if (mpvState == MpvPlayer.State.Error && dualOverlapActive.get()) {
                val generation = loadGeneration.get()
                scope.launch {
                    if (generation == loadGeneration.get()) failPlayback(_lastError.value ?: "Incoming audio could not be played")
                }
            }
            return
        }
        when (mpvState) {
            MpvPlayer.State.Idle -> {
                if (dualOverlapActive.get()) return
                _state.value = PlaybackState.Idle
                mpris?.updatePlaybackState(PlaybackState.Idle)
            }
            MpvPlayer.State.Playing -> {
                if (_state.value != PlaybackState.Loading &&
                    _state.value != PlaybackState.Paused
                ) {
                    _state.value = PlaybackState.Playing
                    mpris?.updatePlaybackState(PlaybackState.Playing)
                }
            }
            MpvPlayer.State.Paused -> {
                _state.value = PlaybackState.Paused
                mpris?.updatePlaybackState(PlaybackState.Paused)
            }
            MpvPlayer.State.Ended -> {
                if (_state.value == PlaybackState.Loading) return
                if (dualOverlapActive.get() || endCrossfadeArmed.get()) {
                    // Outgoing finished during overlap — incoming already owns audio.
                    return
                }
                _state.value = PlaybackState.Ended
                mpris?.updatePlaybackState(PlaybackState.Ended)
                scope.launch { advanceAfterNaturalEnd() }
            }
            MpvPlayer.State.Error -> {
                if (dualOverlapActive.get()) return
                _state.value = PlaybackState.Error
                mpris?.updatePlaybackState(PlaybackState.Error)
            }
        }
    }

    private fun onPlayerPosition(slot: Int, posSec: Double, durSec: Double) {
        // During crossfade: ONLY the incoming player's clock. Outgoing near-EOF
        // must never yank the playbar back to the old song's end.
        if (crossfadeUiHold.get() || dualOverlapActive.get()) {
            val incomingSlot = 1 - activeSlot.get()
            if (slot != incomingSlot) return
            val posMs = (posSec * 1000.0).toLong().coerceAtLeast(0L)
            val durMs = (durSec * 1000.0).toLong().coerceAtLeast(0L)
            if (durMs > 0L) _durationMs.value = durMs
            _positionMs.value = posMs
            mpris?.updatePosition(posMs)
            return
        }
        if (slot != activeSlot.get()) return
        val posMs = (posSec * 1000.0).toLong().coerceAtLeast(0L)
        val durMs = (durSec * 1000.0).toLong().coerceAtLeast(0L)
        _positionMs.value = posMs
        if (durMs > 0) _durationMs.value = durMs
        mpris?.updatePosition(posMs)
        maybeArmEndCrossfade(posSec, durSec)
    }


    init {
        if (applyStoredSession) {
            SessionSecretStore.loadSession()?.applyToYouTube()
        }
        if (enableMpris) {
            mpris?.updatePlaybackState(PlaybackState.Idle)
        }
    }

    override suspend fun restoreQueue(tracks: List<QueueTrack>, startIndex: Int, positionMs: Long) {
        if (tracks.isEmpty()) return
        playMutex.withLock {
            if (_queueTracks.value.isNotEmpty()) return
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            setQueueLocked(tracks, index)
            applyOptimisticNowPlaying(tracks[index])
            _state.value = PlaybackState.Idle
            _positionMs.value = positionMs.coerceAtLeast(0)
            restoredPosition = tracks[index].videoId to _positionMs.value
            mpris?.updatePlaybackState(PlaybackState.Idle)
        }
    }

    override suspend fun playVideo(videoId: String) {
        val existingIdx = _queueTracks.value.indexOfFirst { it.videoId == videoId }
        val track = _queueTracks.value.getOrNull(existingIdx)
            ?: if (videoId.startsWith("local:") || videoId.startsWith("offline:")) {
                withContext(Dispatchers.IO) { fileTrackResolver(videoId) } ?: run {
                    failPlayback("This file is no longer in your library. Rescan its folder or download it again.")
                    return
                }
            } else QueueTrack(videoId = videoId)
        if (track.localPath != null) {
            if (existingIdx >= 0) playLocalQueue(_queueTracks.value, existingIdx)
            else playLocalQueue(listOf(track))
            return
        }
        val jumpInQueue = existingIdx >= 0
        val dual = shouldDualCrossfade(haltOutgoing = true)
        val gen = beginTrackChange(track, haltPlayer = !dual, softUi = dual)
        if (gen != loadGeneration.get()) return
        playMutex.withLock {
            if (jumpInQueue) {
                // Keep history through the pick; drop stale radio ahead of it.
                setQueueLocked(_queueTracks.value.take(existingIdx + 1), existingIdx)
            } else {
                setQueueLocked(listOf(track), 0)
            }
        }
        // New seed (or in-queue jump): rebuild upcoming radio from this track. Started before
        // the stream resolves so follow-ups load in parallel with the first audio.
        expandQueueFromRadio(videoId, replaceUpcoming = true)
        loadAndPlay(videoId, generation = gen, dualCrossfade = dual)
        if (gen != loadGeneration.get()) return
        schedulePrefetchAfterCurrent()
    }

    override suspend fun playQueue(tracks: List<QueueTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[start]
        val sameQueue = tracks.map { it.videoId } == _queueIds.value
        val dual = shouldDualCrossfade(haltOutgoing = true)
        val gen = beginTrackChange(track, haltPlayer = !dual, softUi = dual)
        if (gen != loadGeneration.get()) return
        playMutex.withLock {
            if (sameQueue && track.localPath == null) {
                // Clicking an upcoming/history row: re-seed radio from that song onward.
                setQueueLocked(tracks.take(start + 1), start)
            } else {
                setQueueLocked(tracks, start)
            }
        }
        if (track.localPath == null) {
            // Follow-ups load in parallel with the stream so the queue fills straight away.
            when {
                sameQueue || tracks.size <= 1 ->
                    expandQueueFromRadio(track.videoId, replaceUpcoming = true)
                start >= tracks.lastIndex - 2 ->
                    expandQueueFromRadio(track.videoId, replaceUpcoming = false)
            }
        }
        loadAndPlay(track.videoId, generation = gen, dualCrossfade = dual)
        if (gen != loadGeneration.get()) return
        if (track.localPath != null) return
        runCatching { enrichQueueMetadata(_queueIds.value) }
            .onFailure { t ->
                println(
                    "enrichQueueMetadata schedule failed: " +
                        "${t::class.java.name}: ${t.message}",
                )
            }
        schedulePrefetchAfterCurrent()
    }

    override suspend fun playLocalQueue(tracks: List<QueueTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val withPaths = tracks.filter { !it.localPath.isNullOrBlank() }
        if (withPaths.isEmpty()) return
        val requested = tracks.getOrNull(startIndex)?.videoId
        val start = withPaths.indexOfFirst { it.videoId == requested }.coerceAtLeast(0)
        val track = withPaths[start]
        // Dual-crossfade into local/offline when enabled (outgoing YouTube or local fades out
        // on active while the file loads on idle). Hard-cut when crossfade is off.
        val dual = shouldDualCrossfade(haltOutgoing = true)
        val gen = beginTrackChange(track, haltPlayer = !dual, softUi = dual)
        if (gen != loadGeneration.get()) return
        playMutex.withLock {
            setQueueLocked(withPaths, start)
        }
        loadAndPlay(track.videoId, generation = gen, dualCrossfade = dual)
    }

    override suspend fun startRadio(videoId: String) {
        if (videoId.isBlank() || isFileTrack(videoId)) return
        val alreadyPlaying = _nowPlaying.value?.videoId == videoId &&
            _state.value != PlaybackState.Idle &&
            _state.value != PlaybackState.Error
        if (alreadyPlaying) {
            playMutex.withLock {
                val tracks = _queueTracks.value
                val idx = _queueIndex.value.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                val head = when {
                    tracks.isEmpty() -> listOf(QueueTrack(videoId = videoId))
                    else -> tracks.take(idx + 1)
                }
                setQueueLocked(head, head.lastIndex.coerceAtLeast(0))
            }
            expandQueueFromRadioAwait(videoId, replaceUpcoming = true)
            schedulePrefetchAfterCurrent()
        } else {
            playVideo(videoId)
        }
    }

    override fun playNext(track: QueueTrack) {
        if (track.videoId.isBlank()) return
        scope.launch {
            playMutex.withLock {
                val tracks = _queueTracks.value.toMutableList()
                val playingId = tracks.getOrNull(_queueIndex.value)?.videoId
                if (tracks.isEmpty() || playingId == null) {
                    setQueueLocked(listOf(track), 0)
                    return@withLock
                }
                tracks.removeAll { it.videoId == track.videoId && it.videoId != playingId }
                val idx = tracks.indexOfFirst { it.videoId == playingId }.coerceAtLeast(0)
                tracks.add((idx + 1).coerceAtMost(tracks.size), track)
                setQueueLocked(tracks, idx)
            }
            schedulePrefetchAfterCurrent()
        }
    }

    override fun addToQueue(track: QueueTrack) {
        if (track.videoId.isBlank()) return
        scope.launch {
            playMutex.withLock {
                val tracks = _queueTracks.value.toMutableList()
                if (tracks.any { it.videoId == track.videoId }) return@withLock
                if (tracks.isEmpty()) {
                    setQueueLocked(listOf(track), 0)
                } else {
                    tracks.add(track)
                    _queueTracks.value = tracks
                    _queueIds.value = tracks.map { it.videoId }
                }
            }
            schedulePrefetchAfterCurrent()
        }
    }

    /**
     * Instant feedback for any track change: UI flips now, audio of the old track dies,
     * in-flight resolves/loads are invalidated via [loadGeneration].
     *
     * @param haltPlayer false when mpv already reached natural EOF (no need to stop again).
     */
    private fun beginTrackChange(
        track: QueueTrack,
        haltPlayer: Boolean = true,
        softUi: Boolean = false,
    ): Int {
        val gen = loadGeneration.incrementAndGet()
        pauseRequested.set(false)
        navigateJob?.cancel()
        cancelVolumeFade()
        streams.dropNext()
        applyOptimisticNowPlaying(track, softUi = softUi)
        if (haltPlayer) {
            endCrossfadeArmed.set(false)
            dualOverlapActive.set(false)
            crossfadeUiHold.set(false)
            runCatching { activeMpv().halt() }
            runCatching { idleMpv().halt() }
        }
        return gen
    }

    private fun isAudible(): Boolean =
        _state.value == PlaybackState.Playing

    private fun userVolumePercent(): Int = (_volume.value * 100f).toInt().coerceIn(0, 150)

    private fun cancelVolumeFade() {
        volumeFadeJob?.cancel()
        volumeFadeJob = null
    }

    /** Whether a dual-stream crossfade should overlap the currently audible track. */
    private fun shouldDualCrossfade(haltOutgoing: Boolean): Boolean =
        haltOutgoing && fadeSettings().crossfadeEnabled && isAudible() && !dualOverlapActive.get()

    private suspend fun rampMpvVolume(
        player: PlaybackEngine = activeMpv(),
        from: Int,
        to: Int,
        durationMs: Long,
        generation: Int? = null,
    ) {
        if (durationMs <= 0L || from == to) {
            runCatching { player.setVolume(to) }
            return
        }
        val steps = (durationMs / 40L).toInt().coerceIn(4, 80)
        val stepMs = (durationMs / steps).coerceAtLeast(1L)
        for (i in 1..steps) {
            if (generation != null && generation != loadGeneration.get()) return
            coroutineContext.ensureActive()
            val t = i.toFloat() / steps
            val rampTarget = if (to > 0) userVolumePercent() else 0
            val v = (from + (rampTarget - from) * t).toInt().coerceIn(0, 150)
            runCatching { player.setVolume(v) }
            delay(stepMs)
        }
        runCatching { player.setVolume(if (to > 0) userVolumePercent() else 0) }
    }

    /** Concurrent volume swap: outgoing down, incoming up, then promote idle → active. */
    private suspend fun dualCrossfadeVolumes(
        outgoing: PlaybackEngine,
        incoming: PlaybackEngine,
        targetVol: Int,
        durationMs: Long,
        generation: Int,
    ) {
        dualOverlapActive.set(true)
        try {
            val steps = (durationMs / 40L).toInt().coerceIn(4, 120)
            val stepMs = (durationMs / steps).coerceAtLeast(1L)
            for (i in 1..steps) {
                if (generation != loadGeneration.get()) return
                if (!coroutineContext.isActive) return
                val t = i.toFloat() / steps
                val targetVol = userVolumePercent()
                runCatching { outgoing.setVolume((targetVol * (1f - t)).toInt().coerceIn(0, 150)) }
                runCatching { incoming.setVolume((targetVol * t).toInt().coerceIn(0, 150)) }
                delay(stepMs)
            }
            playerOps.withLock {
                if (generation != loadGeneration.get()) return
                runCatching { outgoing.setVolume(0) }
                runCatching { incoming.setVolume(userVolumePercent()) }
                runCatching { outgoing.halt() }
                // Promote incoming to active; UI already shows the new track.
                activeSlot.set(1 - activeSlot.get())
                endCrossfadeArmed.set(false)
            }
        } finally {
            if (generation == loadGeneration.get()) {
                dualOverlapActive.set(false)
                crossfadeUiHold.set(false)
            }
        }
    }

    /**
     * Near end of the active track: start the next item on the idle player so both
     * overlap for [PlaybackFadeSettings.crossfadeMs].
     */
    private fun maybeArmEndCrossfade(posSec: Double, durSec: Double) {
        val fades = fadeSettings()
        if (!fades.crossfadeEnabled) return
        if (dualOverlapActive.get() || endCrossfadeArmed.get()) return
        if (_state.value != PlaybackState.Playing) return
        if (durSec < fades.crossfadeSeconds + 1.5) return
        val remain = durSec - posSec
        if (remain > fades.crossfadeSeconds || remain < 0.2) return
        if (!endCrossfadeArmed.compareAndSet(false, true)) return
        scope.launch {
            runCatching { startNaturalDualCrossfade() }
                .onFailure {
                    endCrossfadeArmed.set(false)
                    println("end crossfade failed: ${it.message}")
                }
        }
    }

    private suspend fun startNaturalDualCrossfade() {
        when (_repeatMode.value) {
            RepeatMode.One -> {
                endCrossfadeArmed.set(false)
                return
            }
            RepeatMode.Off, RepeatMode.All -> Unit
        }
        val q = _queueIds.value
        val i = desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value
        val nextIdx = when {
            i >= 0 && i + 1 < q.size ->
                if (_shuffleEnabled.value) pickShuffledNext(i) else i + 1
            _repeatMode.value == RepeatMode.All && q.isNotEmpty() -> 0
            else -> {
                val seed = q.getOrNull(i)
                if (seed != null) {
                    expandQueueFromRadioAwait(seed, replaceUpcoming = true)
                    val after = (desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value) + 1
                    if (after < _queueIds.value.size) after else -1
                } else -1
            }
        }
        if (nextIdx < 0) {
            endCrossfadeArmed.set(false)
            return
        }
        desiredIndex.set(nextIdx)
        val track = _queueTracks.value.getOrNull(nextIdx)
            ?: QueueTrack(_queueIds.value[nextIdx])
        val gen = beginTrackChange(track, haltPlayer = false, softUi = true)
        playMutex.withLock {
            if (gen != loadGeneration.get()) return
            _queueIndex.value = nextIdx
        }
        loadAndPlay(track.videoId, generation = gen, dualCrossfade = true)
        if (gen == loadGeneration.get()) {
            schedulePrefetchAfterCurrent()
        }
    }

    private fun applyOptimisticNowPlaying(track: QueueTrack, softUi: Boolean = false) {
        val cached = streams.get(track.videoId)?.nowPlaying
        val np = cached ?: NowPlaying(
            videoId = track.videoId,
            title = track.title.ifBlank { "Loading…" },
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            album = track.album,
            localPath = track.localPath,
            lyricsPath = track.lyricsPath,
            embeddedLyrics = track.embeddedLyrics,
        )
        _nowPlaying.value = np
        _isFavorite.value = false
        _lastError.value = null
        mergeNowPlayingIntoQueue(np)
        _state.value = PlaybackState.Loading
        mpris?.updatePlaybackState(PlaybackState.Loading)
        if (softUi) {
            // New track owns the playbar immediately at 0:00 — ignore outgoing EOF clock.
            crossfadeUiHold.set(true)
            _positionMs.value = 0L
            _durationMs.value = np.durationMs
        } else {
            crossfadeUiHold.set(false)
            _positionMs.value = 0L
            _durationMs.value = np.durationMs
            _state.value = PlaybackState.Loading
            mpris?.updatePlaybackState(PlaybackState.Loading)
        }
        runCatching { mpris?.updateNowPlaying(np, null, favorite = false) }
        refreshFavorite(track.videoId)
    }

    private fun refreshFavorite(videoId: String) {
        val store = favorites ?: return
        scope.launch(Dispatchers.IO) {
            val liked = runCatching { store.isLiked(videoId.removePrefix("offline:")) }.getOrDefault(false)
            if (_nowPlaying.value?.videoId != videoId) return@launch
            _isFavorite.value = liked
            runCatching {
                mpris?.updateFavorite(liked)
                mpris?.updateNowPlaying(_nowPlaying.value, artFileUrl, liked)
            }
        }
    }

    private fun setQueueLocked(tracks: List<QueueTrack>, index: Int) {
        _queueTracks.value = tracks
        _queueIds.value = tracks.map { it.videoId }
        _queueIndex.value = index
        desiredIndex.set(index)
    }

    /** Natural EOF — hop off the mpv thread before touching queue / load. */
    private suspend fun advanceAfterNaturalEnd() {
        when (_repeatMode.value) {
            RepeatMode.One -> {
                val i = desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value
                if (i >= 0) {
                    requestIndex(i, haltPlayer = false)
                }
                return
            }
            RepeatMode.Off, RepeatMode.All -> Unit
        }
        val q = _queueIds.value
        val i = desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value
        if (i >= 0 && i + 1 < q.size) {
            val next = if (_shuffleEnabled.value) pickShuffledNext(i) else i + 1
            requestIndex(next, haltPlayer = false)
            return
        }
        if (_repeatMode.value == RepeatMode.All && q.isNotEmpty()) {
            requestIndex(0, haltPlayer = false)
            return
        }
        val seed = q.getOrNull(i) ?: return
        expandQueueFromRadioAwait(seed, replaceUpcoming = true)
        val nextIdx = (desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value) + 1
        if (nextIdx < _queueIds.value.size) {
            requestIndex(nextIdx, haltPlayer = false)
        }
    }

    /** Next index when shuffle is on — random among remaining, else any other track. */
    private fun pickShuffledNext(fromIndex: Int): Int {
        val q = _queueIds.value
        if (q.size <= 1) return fromIndex
        val remaining = (fromIndex + 1 until q.size).toList()
        if (remaining.isNotEmpty()) return remaining.random()
        return (q.indices.filter { it != fromIndex }).random()
    }

    /** User/MPRIS skip — coalesce rapid clicks onto the final landed index. */
    private fun requestIndex(index: Int, haltPlayer: Boolean = true) {
        val q = _queueIds.value
        if (q.isEmpty()) return
        val clamped = index.coerceIn(0, q.lastIndex)
        desiredIndex.set(clamped)
        val track = _queueTracks.value.getOrNull(clamped) ?: QueueTrack(q[clamped])
        val dualPreview = shouldDualCrossfade(haltOutgoing = haltPlayer)
        val gen = beginTrackChange(track, haltPlayer = haltPlayer && !dualPreview, softUi = dualPreview)
        navigateJob?.cancel()
        navigateJob = scope.launch {
            val dual = dualPreview
            if (gen != loadGeneration.get()) return@launch
            advanceToIndex(clamped, generation = gen, dualCrossfade = dual)
        }
    }

    private suspend fun advanceToIndex(
        index: Int,
        generation: Int = loadGeneration.incrementAndGet(),
        dualCrossfade: Boolean = false,
    ) {
        playMutex.withLock {
            if (generation != loadGeneration.get()) return@withLock
            val q = _queueIds.value
            if (index !in q.indices) return@withLock
            if (desiredIndex.get() != index && desiredIndex.get() >= 0) return@withLock
            _queueIndex.value = index
        }
        if (generation != loadGeneration.get()) return
        val id = _queueIds.value.getOrNull(index) ?: return
        loadAndPlay(id, generation = generation, dualCrossfade = dualCrossfade)
        if (generation != loadGeneration.get()) return
        schedulePrefetchAfterCurrent()
        val q = _queueIds.value
        if (index >= q.size - 2) {
            q.getOrNull(index)?.let {
                // Near the end of whatever is left — refill from current seed.
                expandQueueFromRadio(it, replaceUpcoming = index >= q.lastIndex)
            }
        }
    }

    private suspend fun loadAndPlay(
        videoId: String,
        generation: Int = loadGeneration.get(),
        dualCrossfade: Boolean = false,
    ) {
        if (generation != loadGeneration.get()) return
        if (!dualCrossfade) {
            _state.value = PlaybackState.Loading
            mpris?.updatePlaybackState(PlaybackState.Loading)
        }
        try {
            val queued = _queueTracks.value.firstOrNull { it.videoId == videoId }
            val localPath = queued?.localPath
            if (!localPath.isNullOrBlank()) {
                loadAndPlayLocal(
                    track = queued,
                    path = localPath,
                    generation = generation,
                    dualCrossfade = dualCrossfade,
                )
                return
            }

            var resolved: ResolvedStream = streams.get(videoId)
                ?: withContext(Dispatchers.IO) {
                    if (generation != loadGeneration.get()) {
                        throw CancellationLike()
                    }
                    StreamResolver.resolve(videoId, settings = streamSettings())
                }
            if (generation != loadGeneration.get()) return

            println(
                "Stream source=${resolved.source} for $videoId " +
                    "headers=${resolved.httpHeaders.keys} urlLen=${resolved.streamUrl.length}",
            )
            if (System.getenv("VIVI_DUMP_STREAM") == "1") {
                runCatching {
                    java.io.File("/tmp/vivi-stream.url").writeText(resolved.streamUrl)
                    java.io.File("/tmp/vivi-stream.headers").writeText(
                        resolved.httpHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    )
                }
            }
            resolved = resolved.copy(nowPlaying = mergeWithQueued(resolved.nowPlaying, queued))
            streams.onBecameCurrent(videoId, resolved)
            // Refresh metadata from resolved stream (may be richer than optimistic)
            if (generation != loadGeneration.get()) return
            _nowPlaying.value = resolved.nowPlaying
            _durationMs.value = resolved.nowPlaying.durationMs
            _positionMs.value = 0L
            mergeNowPlayingIntoQueue(resolved.nowPlaying)
            artFileUrl = withContext(Dispatchers.IO) {
                artwork.fileUrlFor(videoId, resolved.nowPlaying.artworkUrl)
            }
            if (generation != loadGeneration.get()) return
            runCatching {
                mpris?.updateNowPlaying(resolved.nowPlaying, artFileUrl, favorite = _isFavorite.value)
            }
            refreshFavorite(videoId)
            try {
                startDecodedPlayback(
                    url = resolved.streamUrl,
                    httpHeaders = resolved.httpHeaders,
                    generation = generation,
                    dualCrossfade = dualCrossfade,
                )
            } catch (t: Throwable) {
                // A resolved URL can still be refused by the CDN (403 / expired). Try the
                // next source once before giving up, instead of failing the whole track.
                if (t is kotlinx.coroutines.CancellationException && t !is kotlinx.coroutines.TimeoutCancellationException) throw t
                if (generation != loadGeneration.get()) return
                println("Stream ${resolved.source} failed for $videoId (${t.message}); trying another source")
                val alternate = withContext(Dispatchers.IO) {
                    StreamResolver.resolve(videoId, settings = streamSettings(), skipSources = setOf(resolved.source))
                }
                if (generation != loadGeneration.get()) return
                resolved = alternate.copy(nowPlaying = mergeWithQueued(alternate.nowPlaying, queued))
                streams.onBecameCurrent(videoId, resolved)
                startDecodedPlayback(
                    url = resolved.streamUrl,
                    httpHeaders = resolved.httpHeaders,
                    generation = generation,
                    dualCrossfade = false,
                )
            }
        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
            if (generation == loadGeneration.get()) failPlayback("Audio did not become ready. Try playing again.")
        } catch (t: kotlinx.coroutines.CancellationException) {
            if (generation == loadGeneration.get()) stop()
            throw t
        } catch (_: CancellationLike) {
            // superseded by a newer pick
        } catch (t: Throwable) {
            if (generation != loadGeneration.get()) return
            val detail = buildString {
                append(t::class.java.name)
                t.message?.let { append(": ").append(it) }
                generateSequence(t.cause) { it.cause }.forEach { c ->
                    append(" | caused by ").append(c::class.java.name)
                    c.message?.let { append(": ").append(it) }
                }
            }
            failPlayback(detail)
            println("play failed: $detail")
            t.printStackTrace(System.out)
        }
    }

    private suspend fun loadAndPlayLocal(
        track: QueueTrack,
        path: String,
        generation: Int,
        dualCrossfade: Boolean,
    ) {
        val file = java.io.File(path)
        if (!file.isFile) {
            failPlayback("File is unavailable: $path. Reconnect its folder or rescan your library.")
            return
        }
        val art = track.artworkUrl?.let { url ->
            when {
                url.startsWith("file:") || url.startsWith("http") -> url
                else -> java.io.File(url).toURI().toString()
            }
        }
        val np = NowPlaying(
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            artworkUrl = art ?: track.artworkUrl,
            durationMs = track.durationMs,
            album = track.album,
            localPath = path,
            lyricsPath = track.lyricsPath,
            embeddedLyrics = track.embeddedLyrics,
        )
        if (generation != loadGeneration.get()) return
        _nowPlaying.value = np
        _durationMs.value = track.durationMs
        _positionMs.value = 0L
        mergeNowPlayingIntoQueue(np)
        artFileUrl = art
        runCatching {
            mpris?.updateNowPlaying(np, artFileUrl, favorite = _isFavorite.value)
        }
        refreshFavorite(track.videoId)
        startDecodedPlayback(
            url = file.absolutePath,
            httpHeaders = emptyMap(),
            generation = generation,
            dualCrossfade = dualCrossfade,
        )
    }

    /**
     * Load [url] on the idle player (dual) or active player (hard cut), then optionally
     * overlap volumes for a true concurrent crossfade.
     */
    private suspend fun startDecodedPlayback(
        url: String,
        httpHeaders: Map<String, String>,
        generation: Int,
        dualCrossfade: Boolean,
    ) {
        if (generation != loadGeneration.get()) return
        val targetVol = userVolumePercent()
        val fades = fadeSettings()
        // [dualCrossfade] was decided while the previous track was still audible;
        // don't re-check isAudible() — beginTrackChange already flipped UI to Loading.
        if (dualCrossfade && fades.crossfadeEnabled) {
            val outgoing = activeMpv()
            val incoming = idleMpv()
            // Hold UI on incoming before load so outgoing EOF can't thrash the playbar.
            crossfadeUiHold.set(true)
            dualOverlapActive.set(true)
            withContext(Dispatchers.IO) {
                playerOps.withLock {
                    if (generation != loadGeneration.get()) return@withLock
                    incoming.setVolume(0)
                    incoming.load(url, httpHeaders)
                    incoming.setVolume(0)
                }
            }
            if (generation != loadGeneration.get()) {
                dualOverlapActive.set(false)
                return
            }
            incoming.awaitReady()
            if (generation != loadGeneration.get()) return
            if (pauseRequested.get()) incoming.pause() else incoming.play()
            _state.value = if (pauseRequested.get()) PlaybackState.Paused else PlaybackState.Playing
            mpris?.updatePlaybackState(_state.value)
            _positionMs.value = 0L
            dualCrossfadeVolumes(
                outgoing = outgoing,
                incoming = incoming,
                targetVol = targetVol,
                durationMs = fades.crossfadeMs,
                generation = generation,
            )
            if (generation == loadGeneration.get()) {
                runCatching { activeMpv().setVolume(userVolumePercent()) }
                runCatching { idleMpv().halt() }
            }
            return
        }
        // Hard cut: kill both players so YouTube never overlaps local (or vice versa).
        endCrossfadeArmed.set(false)
        dualOverlapActive.set(false)
        crossfadeUiHold.set(false)
        withContext(Dispatchers.IO) {
            playerOps.withLock {
                if (generation != loadGeneration.get()) return@withLock
                runCatching { idleMpv().halt() }
                runCatching { activeMpv().halt() }
                activeMpv().setVolume(targetVol)
                activeMpv().load(url, httpHeaders)
                // loadfile can reset volume — re-apply user level.
                activeMpv().setVolume(targetVol)
            }
        }
        activeMpv().awaitReady()
        if (generation == loadGeneration.get()) {
            restoredPosition?.let { (id, position) ->
                if (_nowPlaying.value?.videoId == id) activeMpv().seek(position / 1000.0)
                restoredPosition = null
            }
            if (pauseRequested.get()) activeMpv().pause() else activeMpv().play()
            _state.value = if (pauseRequested.get()) PlaybackState.Paused else PlaybackState.Playing
            mpris?.updatePlaybackState(_state.value)
        }
    }

    private fun failPlayback(message: String) {
        stop()
        _lastError.value = message
        _state.value = PlaybackState.Error
        mpris?.updatePlaybackState(PlaybackState.Error)
    }

    private class CancellationLike : RuntimeException()

    private fun schedulePrefetchAfterCurrent() {
        val tracks = _queueTracks.value
        val i = _queueIndex.value
        val next = tracks.getOrNull(i + 1)
        if (next?.localPath != null) return
        val nextVideo = next?.videoId ?: _queueIds.value.getOrNull(i + 1)
        streams.prefetchNext(nextVideo)
        // Also keep previous resolvable — already in cache via onBecameCurrent
        val prev = tracks.getOrNull(i - 1)
        if (prev?.localPath != null) return
        val prevVideo = prev?.videoId ?: _queueIds.value.getOrNull(i - 1)
        if (prevVideo != null && streams.get(prevVideo) == null) {
            scope.launch {
                runCatching { streams.resolveOrGet(prevVideo) }
            }
        }
    }

    /**
     * Resolvers fall back to placeholder metadata (title = video id, "Unknown" artist) when
     * their metadata lookup fails; never let that replace what the queue already knows.
     */
    private fun mergeWithQueued(resolved: NowPlaying, queued: QueueTrack?): NowPlaying {
        if (queued == null) return resolved
        val placeholderTitle = resolved.title.isBlank() || resolved.title == resolved.videoId
        val placeholderArtist = resolved.artist.isBlank() || resolved.artist == "Unknown"
        return resolved.copy(
            title = if (placeholderTitle) queued.title.ifBlank { resolved.title } else resolved.title,
            artist = if (placeholderArtist) queued.artist.ifBlank { resolved.artist } else resolved.artist,
            artworkUrl = resolved.artworkUrl ?: queued.artworkUrl,
            album = resolved.album ?: queued.album,
            durationMs = resolved.durationMs.takeIf { it > 0 } ?: queued.durationMs,
        )
    }

    private fun mergeNowPlayingIntoQueue(np: NowPlaying) {
        val tracks = _queueTracks.value.toMutableList()
        val i = tracks.indexOfFirst { it.videoId == np.videoId }
            .takeIf { it >= 0 }
            ?: _queueIndex.value.takeIf { it in tracks.indices }
        if (i == null || i !in tracks.indices) return
        tracks[i] = tracks[i].copy(
            title = np.title.ifBlank { tracks[i].title },
            artist = np.artist.ifBlank { tracks[i].artist },
            artworkUrl = np.artworkUrl ?: tracks[i].artworkUrl,
        )
        _queueTracks.value = tracks
    }

    private fun expandQueueFromRadio(seedId: String, replaceUpcoming: Boolean = false) {
        if (isFileTrack(seedId)) return
        // Flag flips synchronously so the UI can show placeholders the moment a track is picked.
        radioInFlight.incrementAndGet()
        _queueLoading.value = true
        scope.launch {
            try {
                expandQueueFromRadioInner(seedId, replaceUpcoming)
            } finally {
                if (radioInFlight.decrementAndGet() == 0) _queueLoading.value = false
            }
        }
    }

    /**
     * Fetch RDAMVM / next for [seedId] and grow the queue.
     *
     * [replaceUpcoming] drops everything after the current index and installs a fresh
     * radio from [seedId] (used when the user jumps to a song mid-radio). Otherwise
     * new tracks are appended after the existing upcoming list; if that yields nothing
     * useful (radio overlaps history), we fall back to replacing upcoming.
     */
    private fun isFileTrack(id: String): Boolean =
        id.startsWith("local:") || id.startsWith("offline:") ||
            _queueTracks.value.any { it.videoId == id && it.localPath != null }

    private suspend fun expandQueueFromRadioAwait(
        seedId: String,
        replaceUpcoming: Boolean = false,
    ) {
        if (isFileTrack(seedId)) return
        radioInFlight.incrementAndGet()
        _queueLoading.value = true
        try {
            expandQueueFromRadioInner(seedId, replaceUpcoming)
        } finally {
            if (radioInFlight.decrementAndGet() == 0) _queueLoading.value = false
        }
    }

    private suspend fun expandQueueFromRadioInner(
        seedId: String,
        replaceUpcoming: Boolean,
    ) {
        val generation = loadGeneration.get()
        val result = withContext(Dispatchers.IO) {
            YouTube.next(
                WatchEndpoint(
                    videoId = seedId,
                    playlistId = "RDAMVM$seedId",
                ),
            ).getOrNull()
                ?: YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
        } ?: return

        playMutex.withLock {
            if (generation != loadGeneration.get()) return@withLock
            val current = _queueTracks.value
            val idx = _queueIndex.value.coerceAtLeast(0)
            val playingId = current.getOrNull(idx)?.videoId
            // Stale expand from an older seed after the user already moved on.
            if (playingId != null && playingId != seedId) {
                return@withLock
            }

            val head = when {
                current.isEmpty() -> listOf(QueueTrack(seedId))
                else -> current.take((idx + 1).coerceAtMost(current.size))
            }
            val headIds = head.map { it.videoId }.toHashSet()
            val additions = result.items
                .asSequence()
                .filter { it.id !in headIds }
                .map { it.toQueueTrack() }
                .distinctBy { it.videoId }
                .toList()
            if (additions.isEmpty()) return@withLock

            val merged = if (replaceUpcoming || current.size <= head.size) {
                head + additions
            } else {
                val existing = current.map { it.videoId }.toHashSet()
                val appended = additions.filter { it.videoId !in existing }
                if (appended.isEmpty()) {
                    // History already contains this radio — re-seed upcoming instead of dying.
                    head + additions
                } else {
                    current + appended
                }
            }
            _queueTracks.value = merged
            _queueIds.value = merged.map { it.videoId }
            println(
                "Queue expanded: ${merged.size} tracks " +
                    "(radio seed=$seedId replaceUpcoming=$replaceUpcoming)",
            )
        }
        schedulePrefetchAfterCurrent()
    }

    private fun enrichQueueMetadata(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        scope.launch {
            runCatching { enrichQueueMetadataSuspend(videoIds) }
                .onFailure { t ->
                    println(
                        "enrichQueueMetadata failed: " +
                            "${t::class.java.name}: ${t.message}",
                    )
                }
        }
    }

    private suspend fun enrichQueueMetadataSuspend(videoIds: List<String>) {
        val chunks = videoIds.filterNot(::isFileTrack).distinct().chunked(MAX_GET_QUEUE)
        val byId = mutableMapOf<String, QueueTrack>()
        for (chunk in chunks) {
            val songs = withContext(Dispatchers.IO) {
                YouTube.queue(videoIds = chunk).getOrNull().orEmpty()
            }
            songs.forEach { byId[it.id] = it.toQueueTrack() }
        }
        if (byId.isEmpty()) return
        playMutex.withLock {
            val updated = _queueTracks.value.map { t -> if (t.localPath != null) t else byId[t.videoId] ?: t }
            _queueTracks.value = updated
        }
    }

    override fun play() {
        if (_state.value == PlaybackState.Playing) return
        if (_state.value in listOf(PlaybackState.Ended, PlaybackState.Error, PlaybackState.Idle)) {
            if (_queueIndex.value >= 0) requestIndex(_queueIndex.value)
            return
        }
        pauseRequested.set(false)
        if (_state.value == PlaybackState.Loading) return
        volumeFadeJob?.cancel()
        if (dualOverlapActive.get()) {
            runCatching { activeMpv().play() }
            runCatching { idleMpv().play() }
            _state.value = PlaybackState.Playing
            mpris?.updatePlaybackState(PlaybackState.Playing)
            return
        }
        // Instant UI: Playing before volume ramp finishes.
        _state.value = PlaybackState.Playing
        mpris?.updatePlaybackState(PlaybackState.Playing)
        volumeFadeJob = scope.launch {
            val fades = fadeSettings()
            val target = userVolumePercent()
            if (fades.playPauseFadeEnabled && fades.playPauseFadeMs > 0L) {
                runCatching { activeMpv().setVolume(0) }
                runCatching { activeMpv().play() }
                rampMpvVolume(from = 0, to = target, durationMs = fades.playPauseFadeMs)
            } else {
                runCatching { activeMpv().setVolume(userVolumePercent()) }
                runCatching { activeMpv().play() }
            }
        }
    }

    override fun pause() {
        if (_state.value !in listOf(PlaybackState.Playing, PlaybackState.Loading)) return
        pauseRequested.set(true)
        volumeFadeJob?.cancel()
        // Instant UI: Paused before volume ramp finishes (audio fades in background).
        _state.value = PlaybackState.Paused
        mpris?.updatePlaybackState(PlaybackState.Paused)
        if (dualOverlapActive.get()) {
            runCatching { activeMpv().pause() }
            runCatching { idleMpv().pause() }
            return
        }
        volumeFadeJob = scope.launch {
            val fades = fadeSettings()
            val target = userVolumePercent()
            if (fades.playPauseFadeEnabled && fades.playPauseFadeMs > 0L) {
                rampMpvVolume(from = target, to = 0, durationMs = fades.playPauseFadeMs)
                // A track change cancels this job; never pause the track that replaced ours.
                coroutineContext.ensureActive()
                runCatching { activeMpv().pause() }
                // Keep mpv at user target while paused so a hard play isn't silent.
                runCatching { activeMpv().setVolume(userVolumePercent()) }
            } else {
                runCatching { activeMpv().pause() }
            }
        }
    }

    override fun stop() {
        loadGeneration.incrementAndGet()
        navigateJob?.cancel()
        streams.dropNext()
        pauseRequested.set(false)
        cancelVolumeFade()
        dualOverlapActive.set(false)
        endCrossfadeArmed.set(false)
        crossfadeUiHold.set(false)
        runCatching { activeMpv().stop() }
        runCatching { idleMpv().halt() }
        _positionMs.value = 0L
        _state.value = PlaybackState.Idle
        mpris?.updatePlaybackState(PlaybackState.Idle)
    }

    override fun seekTo(positionMs: Long) {
        if (dualOverlapActive.get()) {
            // Seek the incoming stream the UI is showing.
            runCatching { idleMpv().seek(positionMs / 1000.0) }
        } else {
            runCatching { activeMpv().seek(positionMs / 1000.0) }
        }
        _positionMs.value = positionMs
        mpris?.updatePosition(positionMs)
    }

    override fun setVolume(volume: Float) {
        if (!volume.isFinite()) return
        val v = volume.coerceIn(0f, 1f)
        _volume.value = v
        if (!dualOverlapActive.get() && volumeFadeJob?.isActive != true) {
            runCatching { activeMpv().setVolume((v * 100f).toInt()) }
        }
        mpris?.updateVolume(v)
    }

    override fun next() {
        val q = _queueIds.value
        val i = desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value
        if (i >= 0 && i + 1 < q.size) {
            val next = if (_shuffleEnabled.value) pickShuffledNext(i) else i + 1
            requestIndex(next, haltPlayer = _state.value != PlaybackState.Ended)
            return
        }
        if (_repeatMode.value == RepeatMode.All && q.isNotEmpty()) {
            requestIndex(0, haltPlayer = _state.value != PlaybackState.Ended)
            return
        }
        val seed = q.getOrNull(i) ?: return
        scope.launch {
            expandQueueFromRadioAwait(seed, replaceUpcoming = true)
            val nextIdx = (desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value) + 1
            if (nextIdx < _queueIds.value.size) {
                requestIndex(nextIdx, haltPlayer = _state.value != PlaybackState.Ended)
            }
        }
    }

    override fun previous() {
        if (positionMs.value > 3000L && desiredIndex.get() == _queueIndex.value) {
            seekTo(0L)
            return
        }
        val q = _queueIds.value
        val i = desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value
        if (i <= 0 || q.isEmpty()) {
            seekTo(0L)
            return
        }
        requestIndex(i - 1)
    }

    override fun toggleShuffle() {
        val next = !_shuffleEnabled.value
        _shuffleEnabled.value = next
        mpris?.updateShuffle(next)
        if (next) {
            // Shuffle upcoming after the current track; keep history + current fixed.
            val tracks = _queueTracks.value.toMutableList()
            val i = _queueIndex.value.coerceAtLeast(0)
            if (i < tracks.lastIndex) {
                val head = tracks.subList(0, i + 1).toList()
                val tail = tracks.subList(i + 1, tracks.size).shuffled()
                setQueueLocked(head + tail, i)
            }
        }
    }

    override fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        _repeatMode.value = next
        mpris?.updateLoopStatus(
            when (next) {
                RepeatMode.Off -> "None"
                RepeatMode.All -> "Playlist"
                RepeatMode.One -> "Track"
            },
        )
    }

    override suspend fun toggleFavorite() {
        val id = _nowPlaying.value?.videoId ?: return
        val next = !_isFavorite.value
        withContext(Dispatchers.IO) {
            if (!id.startsWith("local:")) YouTube.likeVideo(id.removePrefix("offline:"), next).getOrThrow()
            favorites?.setLiked(id.removePrefix("offline:"), next)
        }
        if (_nowPlaying.value?.videoId != id) return
        _isFavorite.value = next
        mpris?.updateFavorite(next)
        mpris?.updateNowPlaying(_nowPlaying.value, artFileUrl, next)
    }

    override fun release() {
        navigateJob?.cancel()
        cancelVolumeFade()
        streams.clearAll()
        scope.cancel()
        runCatching { players.forEach { it.close() } }
        runCatching { mpris?.close() }
        runCatching { artwork.close() }
    }

    companion object {
        private const val MAX_GET_QUEUE = 20

        fun create(
            applyStoredSession: Boolean = true,
            favorites: FavoriteStore? = null,
            fadeSettings: () -> PlaybackFadeSettings = { PlaybackFadeSettings() },
            streamSettings: () -> com.music.vivi.player.stream.StreamSettings = { com.music.vivi.player.stream.StreamSettings() },
            fileTrackResolver: suspend (String) -> QueueTrack? = { null },
        ): PlaybackController =
            DefaultPlaybackController(applyStoredSession, favorites, fadeSettings, streamSettings, fileTrackResolver)
    }
}
