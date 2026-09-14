package com.music.vivi.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.vivi.player.art.ArtworkCache
import com.music.vivi.player.mpris.MprisService
import com.music.vivi.player.mpv.MpvPlayer
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class DefaultPlaybackController(
    applyStoredSession: Boolean = true,
    private val favorites: FavoriteStore? = null,
    private val fadeSettings: () -> PlaybackFadeSettings = { PlaybackFadeSettings() },
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val playMutex = Mutex()
    private val streams = StreamPrefetchCache(scope)
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
    override val volume: StateFlow<Float> = _volume.asStateFlow()
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val artwork = ArtworkCache()
    private var artFileUrl: String? = null

    /** Coalesce rapid next/prev into the final landed index. */
    private val desiredIndex = AtomicInteger(-1)
    private var navigateJob: Job? = null
    private var loadGeneration = AtomicInteger(0)

    private val mpv = MpvPlayer(
        onState = { mpvState ->
            when (mpvState) {
                MpvPlayer.State.Idle -> {
                    _state.value = PlaybackState.Idle
                    mpris.updatePlaybackState(PlaybackState.Idle)
                }
                MpvPlayer.State.Playing -> {
                    // Don't clobber an in-flight skip's Loading with a late Playing from halt/load.
                    if (_state.value != PlaybackState.Loading) {
                        _state.value = PlaybackState.Playing
                        mpris.updatePlaybackState(PlaybackState.Playing)
                    }
                }
                MpvPlayer.State.Paused -> {
                    _state.value = PlaybackState.Paused
                    mpris.updatePlaybackState(PlaybackState.Paused)
                }
                MpvPlayer.State.Ended -> {
                    // Never call requestIndex/halt on the mpv-events thread — that both
                    // races libmpv and previously crashed the event loop (killing Next).
                    if (_state.value == PlaybackState.Loading) return@MpvPlayer
                    _state.value = PlaybackState.Ended
                    mpris.updatePlaybackState(PlaybackState.Ended)
                    scope.launch { advanceAfterNaturalEnd() }
                }
                MpvPlayer.State.Error -> {
                    _state.value = PlaybackState.Error
                    mpris.updatePlaybackState(PlaybackState.Error)
                }
            }
        },
        onPosition = { posSec, durSec ->
            val posMs = (posSec * 1000.0).toLong().coerceAtLeast(0L)
            val durMs = (durSec * 1000.0).toLong().coerceAtLeast(0L)
            _positionMs.value = posMs
            if (durMs > 0) _durationMs.value = durMs
            mpris.updatePosition(posMs)
        },
        onError = { msg ->
            _lastError.value = msg
            println("mpv error: $msg")
        },
    )

    private val mpris = MprisService(
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
    )

    init {
        if (applyStoredSession) {
            SessionSecretStore.loadSession()?.applyToYouTube()
        }
    }

    override suspend fun playVideo(videoId: String) {
        val existingIdx = _queueTracks.value.indexOfFirst { it.videoId == videoId }
        val track = _queueTracks.value.getOrNull(existingIdx)
            ?: QueueTrack(videoId = videoId)
        val jumpInQueue = existingIdx >= 0
        val gen = beginTrackChange(track, haltPlayer = false)
        val fadeIn = fadeOutThenHaltIfNeeded(haltPlayer = true, generation = gen)
        if (gen != loadGeneration.get()) return
        playMutex.withLock {
            if (jumpInQueue) {
                // Keep history through the pick; drop stale radio ahead of it.
                setQueueLocked(_queueTracks.value.take(existingIdx + 1), existingIdx)
            } else {
                setQueueLocked(listOf(track), 0)
            }
        }
        loadAndPlay(videoId, generation = gen, fadeIn = fadeIn)
        if (gen != loadGeneration.get()) return
        // New seed (or in-queue jump): rebuild upcoming radio from this track.
        expandQueueFromRadio(videoId, replaceUpcoming = true)
        schedulePrefetchAfterCurrent()
    }

    override suspend fun playQueue(tracks: List<QueueTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[start]
        val sameQueue = tracks.map { it.videoId } == _queueIds.value
        val gen = beginTrackChange(track, haltPlayer = false)
        val fadeIn = fadeOutThenHaltIfNeeded(haltPlayer = true, generation = gen)
        if (gen != loadGeneration.get()) return
        playMutex.withLock {
            if (sameQueue) {
                // Clicking an upcoming/history row: re-seed radio from that song onward.
                setQueueLocked(tracks.take(start + 1), start)
            } else {
                setQueueLocked(tracks, start)
            }
        }
        loadAndPlay(track.videoId, generation = gen, fadeIn = fadeIn)
        if (gen != loadGeneration.get()) return
        runCatching { enrichQueueMetadata(_queueIds.value) }
            .onFailure { t ->
                println(
                    "enrichQueueMetadata schedule failed: " +
                        "${t::class.java.name}: ${t.message}",
                )
            }
        when {
            sameQueue || tracks.size <= 1 ->
                expandQueueFromRadio(track.videoId, replaceUpcoming = true)
            start >= tracks.lastIndex - 2 ->
                expandQueueFromRadio(track.videoId, replaceUpcoming = false)
        }
        schedulePrefetchAfterCurrent()
    }

    override suspend fun startRadio(videoId: String) {
        if (videoId.isBlank()) return
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
    private fun beginTrackChange(track: QueueTrack, haltPlayer: Boolean = true): Int {
        val gen = loadGeneration.incrementAndGet()
        navigateJob?.cancel()
        cancelVolumeFade()
        streams.dropNext()
        applyOptimisticNowPlaying(track)
        if (haltPlayer) {
            runCatching { mpv.halt() }
        }
        return gen
    }

    private fun isAudible(): Boolean =
        _state.value == PlaybackState.Playing || _state.value == PlaybackState.Paused

    private fun userVolumePercent(): Int = (_volume.value * 100f).toInt().coerceIn(0, 150)

    private fun cancelVolumeFade() {
        volumeFadeJob?.cancel()
        volumeFadeJob = null
    }

    /**
     * When crossfade is on and we would halt a playing track: ramp mpv volume to 0, then halt.
     * @return whether [loadAndPlay] should fade in.
     */
    private suspend fun fadeOutThenHaltIfNeeded(haltPlayer: Boolean, generation: Int): Boolean {
        val fades = fadeSettings()
        val cross = haltPlayer && fades.crossfadeEnabled && isAudible()
        if (!cross) {
            if (haltPlayer) runCatching { mpv.halt() }
            // Natural EOF (no halt): still fade in the next track when crossfade is enabled.
            return fades.crossfadeEnabled && !haltPlayer
        }
        rampMpvVolume(
            from = userVolumePercent(),
            to = 0,
            durationMs = fades.crossfadeHalfMs,
            generation = generation,
        )
        if (generation != loadGeneration.get()) return false
        runCatching { mpv.halt() }
        return true
    }

    private suspend fun rampMpvVolume(
        from: Int,
        to: Int,
        durationMs: Long,
        generation: Int? = null,
    ) {
        if (durationMs <= 0L || from == to) {
            runCatching { mpv.setVolume(to) }
            return
        }
        val steps = (durationMs / 40L).toInt().coerceIn(4, 80)
        val stepMs = (durationMs / steps).coerceAtLeast(1L)
        for (i in 1..steps) {
            if (generation != null && generation != loadGeneration.get()) return
            if (!coroutineContext.isActive) return
            val t = i.toFloat() / steps
            val v = (from + (to - from) * t).toInt().coerceIn(0, 150)
            runCatching { mpv.setVolume(v) }
            delay(stepMs)
        }
        runCatching { mpv.setVolume(to) }
    }

    private fun applyOptimisticNowPlaying(track: QueueTrack) {
        val cached = streams.get(track.videoId)?.nowPlaying
        val np = cached ?: NowPlaying(
            videoId = track.videoId,
            title = track.title.ifBlank { "Loading…" },
            artist = track.artist,
            artworkUrl = track.artworkUrl,
        )
        _nowPlaying.value = np
        _positionMs.value = 0L
        _durationMs.value = cached?.durationMs ?: 0L
        _state.value = PlaybackState.Loading
        _isFavorite.value = false
        _lastError.value = null
        mergeNowPlayingIntoQueue(np)
        mpris.updatePlaybackState(PlaybackState.Loading)
        runCatching { mpris.updateNowPlaying(np, null, favorite = false) }
        refreshFavorite(track.videoId)
    }

    private fun refreshFavorite(videoId: String) {
        val store = favorites ?: return
        scope.launch(Dispatchers.IO) {
            val liked = runCatching { store.isLiked(videoId) }.getOrDefault(false)
            if (_nowPlaying.value?.videoId != videoId) return@launch
            _isFavorite.value = liked
            runCatching {
                mpris.updateFavorite(liked)
                mpris.updateNowPlaying(_nowPlaying.value, artFileUrl, liked)
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
                    seekTo(0L)
                    play()
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
        val gen = beginTrackChange(track, haltPlayer = false)
        navigateJob?.cancel()
        navigateJob = scope.launch {
            val fadeIn = fadeOutThenHaltIfNeeded(haltPlayer = haltPlayer, generation = gen)
            if (gen != loadGeneration.get()) return@launch
            advanceToIndex(clamped, generation = gen, fadeIn = fadeIn)
        }
    }

    private suspend fun advanceToIndex(
        index: Int,
        generation: Int = loadGeneration.incrementAndGet(),
        fadeIn: Boolean = false,
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
        loadAndPlay(id, generation = generation, fadeIn = fadeIn)
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
        fadeIn: Boolean = false,
    ) {
        if (generation != loadGeneration.get()) return
        _state.value = PlaybackState.Loading
        mpris.updatePlaybackState(PlaybackState.Loading)
        try {
            val resolved: ResolvedStream = streams.get(videoId)
                ?: withContext(Dispatchers.IO) {
                    if (generation != loadGeneration.get()) {
                        throw CancellationLike()
                    }
                    StreamResolver.resolve(videoId)
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
                mpris.updateNowPlaying(resolved.nowPlaying, artFileUrl, favorite = _isFavorite.value)
            }
            refreshFavorite(videoId)
            val targetVol = userVolumePercent()
            val doFadeIn = fadeIn && fadeSettings().crossfadeEnabled
            withContext(Dispatchers.IO) {
                if (generation != loadGeneration.get()) return@withContext
                if (doFadeIn) {
                    mpv.setVolume(0)
                } else {
                    mpv.setVolume(targetVol)
                }
                mpv.load(resolved.streamUrl, resolved.httpHeaders)
            }
            if (generation != loadGeneration.get()) return
            if (doFadeIn) {
                rampMpvVolume(
                    from = 0,
                    to = targetVol,
                    durationMs = fadeSettings().crossfadeHalfMs,
                    generation = generation,
                )
            }
            if (generation == loadGeneration.get()) {
                _state.value = PlaybackState.Playing
                mpris.updatePlaybackState(PlaybackState.Playing)
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
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
            _lastError.value = detail
            _state.value = PlaybackState.Error
            mpris.updatePlaybackState(PlaybackState.Error)
            println("play failed: $detail")
            t.printStackTrace(System.out)
        }
    }

    private class CancellationLike : RuntimeException()

    private fun schedulePrefetchAfterCurrent() {
        val q = _queueIds.value
        val i = _queueIndex.value
        val nextVideo = q.getOrNull(i + 1)
        streams.prefetchNext(nextVideo)
        // Also keep previous resolvable — already in cache via onBecameCurrent
        val prevVideo = q.getOrNull(i - 1)
        if (prevVideo != null && streams.get(prevVideo) == null) {
            scope.launch {
                runCatching { streams.resolveOrGet(prevVideo) }
            }
        }
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
        scope.launch { expandQueueFromRadioAwait(seedId, replaceUpcoming) }
    }

    /**
     * Fetch RDAMVM / next for [seedId] and grow the queue.
     *
     * [replaceUpcoming] drops everything after the current index and installs a fresh
     * radio from [seedId] (used when the user jumps to a song mid-radio). Otherwise
     * new tracks are appended after the existing upcoming list; if that yields nothing
     * useful (radio overlaps history), we fall back to replacing upcoming.
     */
    private suspend fun expandQueueFromRadioAwait(
        seedId: String,
        replaceUpcoming: Boolean = false,
    ) {
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
        val chunks = videoIds.distinct().chunked(MAX_GET_QUEUE)
        val byId = mutableMapOf<String, QueueTrack>()
        for (chunk in chunks) {
            val songs = withContext(Dispatchers.IO) {
                YouTube.queue(videoIds = chunk).getOrNull().orEmpty()
            }
            songs.forEach { byId[it.id] = it.toQueueTrack() }
        }
        if (byId.isEmpty()) return
        playMutex.withLock {
            val updated = _queueTracks.value.map { t -> byId[t.videoId] ?: t }
            _queueTracks.value = updated
        }
    }

    override fun play() {
        volumeFadeJob?.cancel()
        volumeFadeJob = scope.launch {
            val fades = fadeSettings()
            val target = userVolumePercent()
            if (fades.playPauseFadeEnabled && fades.playPauseFadeMs > 0L) {
                runCatching { mpv.setVolume(0) }
                runCatching { mpv.play() }
                rampMpvVolume(from = 0, to = target, durationMs = fades.playPauseFadeMs)
            } else {
                runCatching { mpv.setVolume(target) }
                runCatching { mpv.play() }
            }
        }
    }

    override fun pause() {
        volumeFadeJob?.cancel()
        volumeFadeJob = scope.launch {
            val fades = fadeSettings()
            val target = userVolumePercent()
            if (fades.playPauseFadeEnabled && fades.playPauseFadeMs > 0L &&
                _state.value == PlaybackState.Playing
            ) {
                rampMpvVolume(from = target, to = 0, durationMs = fades.playPauseFadeMs)
                runCatching { mpv.pause() }
                // Keep mpv at user target while paused so a hard play isn't silent.
                runCatching { mpv.setVolume(target) }
            } else {
                runCatching { mpv.pause() }
            }
        }
    }

    override fun stop() {
        cancelVolumeFade()
        runCatching { mpv.stop() }
        _state.value = PlaybackState.Idle
        mpris.updatePlaybackState(PlaybackState.Idle)
    }

    override fun seekTo(positionMs: Long) {
        runCatching { mpv.seek(positionMs / 1000.0) }
        _positionMs.value = positionMs
        mpris.updatePosition(positionMs)
    }

    override fun setVolume(volume: Float) {
        cancelVolumeFade()
        val v = volume.coerceIn(0f, 1f)
        _volume.value = v
        runCatching { mpv.setVolume((v * 100f).toInt()) }
        mpris.updateVolume(v)
    }

    override fun next() {
        val q = _queueIds.value
        val i = desiredIndex.get().takeIf { it >= 0 } ?: _queueIndex.value
        if (_repeatMode.value == RepeatMode.One && i >= 0) {
            seekTo(0L)
            play()
            return
        }
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
        mpris.updateShuffle(next)
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
        mpris.updateLoopStatus(
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
            YouTube.likeVideo(id, next).getOrThrow()
            favorites?.setLiked(id, next)
        }
        _isFavorite.value = next
        mpris.updateFavorite(next)
        mpris.updateNowPlaying(_nowPlaying.value, artFileUrl, next)
    }

    override fun release() {
        navigateJob?.cancel()
        cancelVolumeFade()
        streams.clearAll()
        scope.cancel()
        runCatching { mpv.close() }
        runCatching { mpris.close() }
        runCatching { artwork.close() }
    }

    companion object {
        private const val MAX_GET_QUEUE = 20

        fun create(
            applyStoredSession: Boolean = true,
            favorites: FavoriteStore? = null,
            fadeSettings: () -> PlaybackFadeSettings = { PlaybackFadeSettings() },
        ): PlaybackController =
            DefaultPlaybackController(applyStoredSession, favorites, fadeSettings)
    }
}
