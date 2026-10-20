package com.music.vivi.player.video

import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.MusicVideoTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the music-video counterpart of a track and a playable silent stream for it.
 * Results (including "no video") are cached so track changes stay cheap.
 */
object MusicVideoResolver {
    private class Entry(val value: ResolvedVideo?, val at: Long)
    private val cache = ConcurrentHashMap<String, Entry>()
    private const val TTL_MS = 20 * 60_000L

    /** The video id to display for [trackId] (itself when already a video), or null. */
    suspend fun videoIdFor(trackId: String): String? = withContext(Dispatchers.IO) {
        val next = YouTube.next(WatchEndpoint(videoId = trackId)).getOrNull() ?: return@withContext null
        val item = next.items.firstOrNull { it.id == trackId } ?: next.items.firstOrNull() ?: return@withContext null
        MusicVideoTypes.videoIdFor(trackId, item.musicVideoType, item.counterpartVideoId)
    }

    /** Set by the app: the quality to prefetch at, or null while music videos are switched off. */
    @Volatile var prefetchQuality: () -> VideoQuality? = { null }

    /** Resolves [trackId]'s video ahead of time (cached; the stream's first chunks are fetched too). */
    suspend fun prefetch(trackId: String) {
        val cap = prefetchQuality() ?: return
        if (!VisualChain.hasVideoCandidate(trackId, null)) return
        resolve(trackId, cap)
    }

    /** The cached result for [trackId] at [cap], without any network work (null when not cached or expired). */
    fun cached(trackId: String, cap: VideoQuality): ResolvedVideo? =
        cache["$trackId@${cap.name}"]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.value

    suspend fun resolve(trackId: String, cap: VideoQuality): ResolvedVideo? {
        val key = "$trackId@${cap.name}"
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.let { return it.value }
        val result = runCatching {
            videoIdFor(trackId)?.let { VideoStreamResolver.resolve(it, cap) }
        }.getOrNull()
        cache[key] = Entry(result, System.currentTimeMillis())
        return result
    }
}
