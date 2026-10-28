package com.music.vivi.compose.ui.player.canvas

import com.music.vivi.canvas.CanvasArtwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

/** What a track needs to look up a canvas. */
data class CanvasQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.AUTO,
) {
    /** Case/whitespace-insensitive identity, so the same track asked for twice shares one lookup. */
    val key: String
        get() = listOf(source.name, title, artist, album.orEmpty())
            .joinToString("|") { it.trim().lowercase() }
}

/** Result of one attempt against the backing lookup. */
sealed interface CanvasFetch {
    data class Found(val art: CanvasArtwork) : CanvasFetch

    /** The lookup worked and there is nothing to show (not retried, cached briefly). */
    data object NotFound : CanvasFetch

    /** Network/server trouble worth another attempt; [retryAfterMs] is a server-provided floor (Retry-After). */
    data class Transient(val cause: Throwable? = null, val retryAfterMs: Long? = null) : CanvasFetch
}

fun interface CanvasFetcher {
    suspend fun fetch(query: CanvasQuery): CanvasFetch
}

/** What the UI should show for one track. Never "silently nothing": Error carries a retry path. */
sealed interface CanvasState {
    data object Idle : CanvasState
    data object Loading : CanvasState
    data class Ready(val art: CanvasArtwork) : CanvasState
    data object NoCanvas : CanvasState
    data class Error(val message: String) : CanvasState
}

/** The state belonging to the track most recently passed to [CanvasRepository.select]. */
data class CanvasSelection(val key: String?, val state: CanvasState)

class CanvasRepositoryConfig(
    val maxEntries: Int = 256,
    val positiveTtlMs: Long = 6 * 60 * 60_000L,
    /** Short on purpose: a lookup that failed quietly must not hide a canvas for the whole session. */
    val negativeTtlMs: Long = 3 * 60_000L,
    /** After retries are exhausted, how long before the same key may hit the network again. */
    val errorCooldownMs: Long = 10_000L,
    val maxAttempts: Int = 3,
    val baseBackoffMs: Long = 500L,
    val maxBackoffMs: Long = 8_000L,
    val attemptTimeoutMs: Long = 30_000L,
    val maxConcurrentFetches: Int = 3,
)

/**
 * One lookup layer for canvases: single-flight per query, bounded TTL cache (long for hits, short for
 * misses), retry with exponential backoff + jitter for transient failures only, and a state machine the
 * UI can observe. Time and delays are injectable so tests run instantly.
 */
class CanvasRepository(
    private val fetcher: CanvasFetcher,
    private val config: CanvasRepositoryConfig = CanvasRepositoryConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val jitter: (Long) -> Long = { Random.nextLong(0, it + 1) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    private sealed interface Entry {
        val expiresAt: Long
    }

    private class Hit(val art: CanvasArtwork, override val expiresAt: Long) : Entry
    private class Miss(override val expiresAt: Long) : Entry
    private class Failure(val message: String, override val expiresAt: Long) : Entry

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > config.maxEntries
    }
    private val inFlight = HashMap<String, Deferred<CanvasState>>()
    private val permits = Semaphore(config.maxConcurrentFetches)
    private val _selected = MutableStateFlow(CanvasSelection(null, CanvasState.Idle))
    private var selectJob: Job? = null
    private var generation = 0L
    @Volatile var networkFetches = 0
        private set

    val selected: StateFlow<CanvasSelection> = _selected.asStateFlow()

    val cachedEntryCount: Int get() = synchronized(lock) { entries.size }

    /** Cached or in-flight answer for [query] without starting any work. */
    fun peek(query: CanvasQuery): CanvasState = synchronized(lock) {
        when (val e = live(query.key)) {
            is Hit -> CanvasState.Ready(e.art)
            is Miss -> CanvasState.NoCanvas
            is Failure -> CanvasState.Error(e.message)
            null -> if (query.key in inFlight) CanvasState.Loading else CanvasState.Idle
        }
    }

    /**
     * Resolves [query], sharing one network lookup between every concurrent caller. The shared work runs in
     * the repository's own scope, so a caller leaving (track change) never cancels another caller's lookup.
     */
    suspend fun load(query: CanvasQuery, force: Boolean = false): CanvasState {
        val job = synchronized(lock) {
            if (force) entries.remove(query.key)
            when (val e = live(query.key)) {
                is Hit -> return CanvasState.Ready(e.art)
                is Miss -> return CanvasState.NoCanvas
                is Failure -> return CanvasState.Error(e.message)
                null -> Unit
            }
            inFlight.getOrPut(query.key) {
                scope.async { run(query) }.also { d ->
                    d.invokeOnCompletion { synchronized(lock) { if (inFlight[query.key] === d) inFlight.remove(query.key) } }
                }
            }
        }
        return job.await()
    }

    /** Convenience for callers that only want the artwork. */
    suspend fun artwork(query: CanvasQuery): CanvasArtwork? = (load(query) as? CanvasState.Ready)?.art

    /**
     * Makes [query] the current track: previous work for the selection is dropped, and the result is only
     * published if no other track was selected meanwhile (stale results are discarded).
     */
    fun select(query: CanvasQuery?) {
        val gen: Long
        synchronized(lock) {
            gen = ++generation
            selectJob?.cancel()
            selectJob = null
        }
        if (query == null) {
            _selected.value = CanvasSelection(null, CanvasState.Idle)
            return
        }
        val immediate = peek(query)
        _selected.value = CanvasSelection(query.key, if (immediate == CanvasState.Idle) CanvasState.Loading else immediate)
        if (immediate != CanvasState.Idle && immediate != CanvasState.Loading) return
        val j = scope.launch {
            val result = load(query)
            publish(gen, query.key, result)
        }
        synchronized(lock) { if (generation == gen) selectJob = j else j.cancel() }
    }

    /** User-triggered retry for the selected track (drops the cached failure/miss). */
    fun retrySelected(query: CanvasQuery) {
        val gen: Long
        synchronized(lock) {
            entries.remove(query.key)
            gen = ++generation
            selectJob?.cancel()
        }
        _selected.value = CanvasSelection(query.key, CanvasState.Loading)
        val j = scope.launch { publish(gen, query.key, load(query, force = true)) }
        synchronized(lock) { if (generation == gen) selectJob = j else j.cancel() }
    }

    private fun publish(gen: Long, key: String, state: CanvasState) {
        synchronized(lock) {
            if (gen == generation) _selected.value = CanvasSelection(key, state)
        }
    }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun live(key: String): Entry? {
        val e = entries[key] ?: return null
        if (clock() >= e.expiresAt) {
            entries.remove(key)
            return null
        }
        return e
    }

    private suspend fun run(query: CanvasQuery): CanvasState {
        var attempt = 0
        var lastFailure: Throwable? = null
        while (true) {
            attempt++
            val outcome: CanvasFetch = try {
                permits.withPermit {
                    networkFetches++
                    withTimeout(config.attemptTimeoutMs) { fetcher.fetch(query) }
                }
            } catch (e: CancellationException) {
                // withTimeout's own cancellation is a timeout (transient); a real cancellation must propagate.
                if (e is kotlinx.coroutines.TimeoutCancellationException) CanvasFetch.Transient(e) else throw e
            } catch (e: Exception) {
                CanvasFetch.Transient(e)
            }
            when (outcome) {
                is CanvasFetch.Found -> return remember(query.key, Hit(outcome.art, clock() + config.positiveTtlMs), CanvasState.Ready(outcome.art))
                CanvasFetch.NotFound -> return remember(query.key, Miss(clock() + config.negativeTtlMs), CanvasState.NoCanvas)
                is CanvasFetch.Transient -> {
                    lastFailure = outcome.cause ?: lastFailure
                    if (attempt >= config.maxAttempts) break
                    val exp = config.baseBackoffMs shl (attempt - 1).coerceAtMost(16)
                    val backoff = exp.coerceAtMost(config.maxBackoffMs)
                    val wait = maxOf(outcome.retryAfterMs ?: 0L, backoff + jitter(backoff / 2))
                    sleep(wait.coerceAtMost(config.maxBackoffMs * 4))
                }
            }
        }
        val message = lastFailure?.message?.takeIf { it.isNotBlank() } ?: "Canvas lookup failed"
        return remember(query.key, Failure(message, clock() + config.errorCooldownMs), CanvasState.Error(message))
    }

    private fun remember(key: String, entry: Entry, state: CanvasState): CanvasState {
        synchronized(lock) { entries[key] = entry }
        return state
    }

    companion object {
        /** Spec for HTTP-ish failures: 404 is a definite no, 408/425/429/5xx and IO errors are worth retrying. */
        fun classifyHttp(status: Int, retryAfterSeconds: Long? = null): CanvasFetch? = when {
            status in 200..299 -> null
            status == 404 || status == 410 -> CanvasFetch.NotFound
            status == 408 || status == 425 || status == 429 || status in 500..599 ->
                CanvasFetch.Transient(IllegalStateException("HTTP $status"), retryAfterSeconds?.times(1000))
            else -> CanvasFetch.NotFound
        }
    }
}
