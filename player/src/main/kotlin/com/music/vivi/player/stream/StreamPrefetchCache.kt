package com.music.vivi.player.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Keeps resolved streams for instant skip:
 * - [previous] — last played (back)
 * - [current]
 * - [next] — prefetched + start bytes warmed
 *
 * Rapid skips cancel in-flight work and resolve only the landed target.
 */
class StreamPrefetchCache(
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val byId = ConcurrentHashMap<String, ResolvedStream>()

    @Volatile
    var previousId: String? = null
        private set

    @Volatile
    var currentId: String? = null
        private set

    @Volatile
    var nextId: String? = null
        private set

    private var prefetchJob: Job? = null
    private var warmJob: Job? = null

    fun get(videoId: String): ResolvedStream? = byId[videoId]

    suspend fun resolveOrGet(videoId: String): ResolvedStream {
        byId[videoId]?.let { return it }
        val resolved = withContext(Dispatchers.IO) { StreamResolver.resolve(videoId) }
        byId[videoId] = resolved
        return resolved
    }

    /** After a track becomes the active one. */
    suspend fun onBecameCurrent(videoId: String, resolved: ResolvedStream) {
        mutex.withLock {
            val oldCurrent = currentId
            if (oldCurrent != null && oldCurrent != videoId) {
                previousId = oldCurrent
            }
            currentId = videoId
            byId[videoId] = resolved
            if (nextId == videoId) nextId = null
        }
    }

    /**
     * Prefetch the next queue item: resolve URL and pull the first ~512 KiB so CDN/mpv
     * can start almost immediately on skip. Cancels any prior prefetch.
     */
    fun prefetchNext(videoId: String?) {
        prefetchJob?.cancel()
        warmJob?.cancel()
        if (videoId == null || videoId == currentId) {
            nextId = null
            return
        }
        nextId = videoId
        prefetchJob = scope.launch {
            try {
                val resolved = resolveOrGet(videoId)
                ensureActive()
                if (nextId != videoId) return@launch
                warmJob = launch { warmStart(resolved) }
            } catch (_: Throwable) {
                // Prefetch failures are silent — skip will resolve on demand.
            }
        }
    }

    /** Drop next prefetch (e.g. after double-skip past it). */
    fun dropNext() {
        prefetchJob?.cancel()
        warmJob?.cancel()
        nextId = null
    }

    fun clearAll() {
        prefetchJob?.cancel()
        warmJob?.cancel()
        previousId = null
        currentId = null
        nextId = null
        byId.clear()
    }

    private suspend fun warmStart(resolved: ResolvedStream, bytes: Int = WARM_BYTES) {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URI(resolved.streamUrl).toURL().openConnection() as HttpURLConnection
                try {
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 5_000
                    conn.readTimeout = 8_000
                    conn.requestMethod = "GET"
                    resolved.httpHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                    // Prefer first bytes only
                    conn.setRequestProperty("Range", "bytes=0-${bytes - 1}")
                    conn.connect()
                    conn.inputStream.use { input ->
                        val buf = ByteArray(32_768)
                        var readTotal = 0
                        while (readTotal < bytes && coroutineContext.isActive) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            readTotal += n
                        }
                    }
                    println("Prefetch warm ${resolved.nowPlaying.videoId}: ${conn.responseCode}")
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    companion object {
        private const val WARM_BYTES = 512 * 1024
    }
}
