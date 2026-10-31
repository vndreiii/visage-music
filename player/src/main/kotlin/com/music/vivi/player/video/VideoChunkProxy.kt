package com.music.vivi.player.video

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * YouTube throttles an open-ended read of a video URL to a trickle (about 0.7 MB/s in testing), but serves
 * bounded `range=` requests at full speed, which is how its own player and yt-dlp fetch. mpv cannot chunk
 * requests, so it reads from this loopback server instead: each request is answered from range fetches
 * ([ChunkLayout]: a small first chunk for a fast start, then 4 MiB chunks), with [AHEAD] chunks in flight at once
 * (3 connections). Failed chunks are retried with backoff and an expired URL is re-resolved through the optional
 * `refresh` callback of [wrap]. The first chunks of a stream can be fetched before it is played (`warm`).
 */
object VideoChunkProxy {
    /** Chunks fetched ahead of the read position; also the number of parallel connections for one stream. */
    private const val AHEAD = 3
    private const val MAX_CHUNKS_PER_STREAM = 10
    private const val MAX_STREAMS_WITH_DATA = 3
    private const val MAX_REGISTERED = 64

    /**
     * [refresh] asks the resolver for a fresh URL of the same stream (given its byte length) when the current one
     * expires or is refused; null when the stream cannot be re-resolved.
     */
    private class Stream(
        val id: String,
        url: String,
        val headers: Map<String, String>,
        val length: Long,
        val mime: String,
        val refresh: ((Long) -> String?)?,
    ) {
        @Volatile var url: String = url
        val layout = ChunkLayout(length)
        val chunks = ConcurrentHashMap<Int, CompletableFuture<ByteArray>>()
        @Volatile var lastUsed = System.nanoTime()
        /** Bumped by every new read; an older read notices it and stops. */
        @Volatile var readGen = 0L

        /**
         * A new read starting at [pos] supersedes the older ones (mpv seeks by opening a new range). Pending fetches
         * outside the window the new read needs are cancelled, so they do not queue ahead of it in the fetch pool.
         */
        @Synchronized
        fun startRead(pos: Long): Long {
            val gen = ++readGen
            val first = layout.indexOf(pos)
            val keep = first..(first + AHEAD)
            for ((idx, f) in chunks) {
                if (idx !in keep && !f.isDone) {
                    f.cancel(false)
                    chunks.remove(idx, f)
                }
            }
            return gen
        }
    }

    private val streams = LinkedHashMap<String, Stream>()
    private val idByUrl = HashMap<String, String>()
    private val fetchPool = Executors.newFixedThreadPool(AHEAD + 1) { r -> Thread(r, "video-chunk-fetch").apply { isDaemon = true } }
    private val http: HttpClient by lazy {
        // HTTP/1.1 so parallel chunks use separate connections instead of one multiplexed one.
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(6)).build()
    }
    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            executor = Executors.newCachedThreadPool { r -> Thread(r, "video-chunk-serve").apply { isDaemon = true } }
            createContext("/v/") { serve(it) }
            start()
        }
    }

    /**
     * A loopback URL that serves [url] through chunked range requests, or [url] itself when it cannot be
     * chunked (no `clen`, or the first chunk cannot be fetched). With [warm] the first [AHEAD] chunks are
     * requested straight away.
     */
    fun wrap(
        url: String,
        headers: Map<String, String>,
        warm: Boolean = true,
        refresh: ((contentLength: Long) -> String?)? = null,
    ): String = runCatching {
        val length = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: return url
        if (length <= 0) return url
        val mime = Regex("[?&]mime=([^&]+)").find(url)?.groupValues?.get(1)
            ?.let { URLDecoder.decode(it, "UTF-8") } ?: "video/mp4"
        val stream = synchronized(this) {
            val existing = idByUrl[url]?.let { streams[it] }
            existing ?: Stream(UUID.randomUUID().toString(), url, headers, length, mime, refresh).also {
                streams[it.id] = it
                idByUrl[url] = it.id
                while (streams.size > MAX_REGISTERED) {
                    val oldest = streams.values.first()
                    streams.remove(oldest.id); idByUrl.remove(oldest.url)
                }
            }
        }
        // The first chunk doubles as a check that range fetching works for this URL.
        runCatching { chunk(stream, 0).get(10, TimeUnit.SECONDS) }.getOrElse {
            synchronized(this) { streams.remove(stream.id); idByUrl.remove(stream.url) }
            return url
        }
        if (warm) for (i in 1 until AHEAD.coerceAtMost(stream.layout.count)) chunk(stream, i)
        "http://127.0.0.1:${server.address.port}/v/${stream.id}"
    }.getOrDefault(url)

    /** Drops every downloaded chunk (memory pressure); streams stay registered and refetch on demand. */
    fun purge() {
        synchronized(this) { streams.values.forEach { it.chunks.clear() } }
    }

    private fun chunk(s: Stream, idx: Int): CompletableFuture<ByteArray> {
        s.lastUsed = System.nanoTime()
        val future = s.chunks.computeIfAbsent(idx) { fetch(s, idx) }
        if (s.chunks.size > MAX_CHUNKS_PER_STREAM) {
            // Only finished chunks are evicted: dropping one still in flight would fetch it twice.
            s.chunks.entries.filter { it.key != idx && it.value.isDone }
                .maxByOrNull { kotlin.math.abs(it.key - idx) }
                ?.let { s.chunks.remove(it.key, it.value) }
        }
        dropColdStreams()
        return future
    }

    private fun dropColdStreams() {
        val withData = synchronized(this) { streams.values.filter { it.chunks.isNotEmpty() } }
        if (withData.size > MAX_STREAMS_WITH_DATA) {
            withData.sortedBy { it.lastUsed }.take(withData.size - MAX_STREAMS_WITH_DATA).forEach { it.chunks.clear() }
        }
    }

    private fun fetch(s: Stream, idx: Int): CompletableFuture<ByteArray> {
        val start = s.layout.start(idx)
        val end = s.layout.end(idx)
        val result = CompletableFuture<ByteArray>()
        fetchPool.execute {
            var last: Throwable? = null
            for (attempt in 0 until RetryBackoff.MAX_ATTEMPTS) {
                if (result.isDone) return@execute
                if (attempt > 0) Thread.sleep(RetryBackoff.delayMs(attempt - 1))
                refreshIfExpiring(s)
                var status = 0
                try {
                    val req = HttpRequest.newBuilder(URI("${s.url}&range=$start-$end")).timeout(Duration.ofSeconds(20)).GET()
                    s.headers.forEach { (k, v) -> if (!k.equals("Host", true)) req.header(k, v) }
                    val resp = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray())
                    status = resp.statusCode()
                    if (status in 200..299 && resp.body().size.toLong() == end - start + 1) {
                        result.complete(resp.body()); return@execute
                    }
                    last = IOException("chunk $idx: HTTP $status, ${resp.body().size} bytes")
                    if (status in 200..299) status = 0 // short body: retried like a transport error
                } catch (t: Throwable) {
                    last = t
                    status = 0
                }
                // googlevideo refuses expired URLs with 403/410: re-resolve once and try the same chunk again.
                if (status == 403 || status == 410) {
                    if (refreshUrl(s)) continue
                    break
                }
                if (!RetryBackoff.isRetryable(status)) break
            }
            s.chunks.remove(idx, result)
            result.completeExceptionally(last ?: IOException("chunk $idx failed"))
        }
        return result
    }

    /** Swaps in a fresh URL for [s] (same byte length); false when none is available. */
    private fun refreshUrl(s: Stream): Boolean {
        val refresh = s.refresh ?: return false
        val fresh = runCatching { refresh(s.length) }.getOrNull() ?: return false
        s.url = fresh
        return true
    }

    private fun refreshIfExpiring(s: Stream) {
        if (UrlExpiry.needsRefresh(s.url, System.currentTimeMillis() / 1000)) refreshUrl(s)
    }

    /**
     * A chunk that fails is re-requested with backoff instead of ending the response: a short body looks like
     * end of file to mpv and freezes playback until the next seek.
     */
    private fun chunkBytes(s: Stream, idx: Int, gen: Long): ByteArray {
        var failure: Throwable? = null
        for (retry in 0 until RetryBackoff.MAX_ATTEMPTS) {
            if (s.readGen != gen) throw IOException("chunk $idx: read superseded")
            try {
                return chunk(s, idx).get(30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                failure = e
                Thread.sleep(RetryBackoff.delayMs(retry))
            }
        }
        throw failure ?: IOException("chunk $idx failed")
    }

    private fun serve(ex: HttpExchange) {
        try {
            val s = synchronized(this) { streams[ex.requestURI.path.removePrefix("/v/")] }
            if (s == null) { ex.sendResponseHeaders(404, -1); return }
            val range = ex.requestHeaders.getFirst("Range")?.let { Regex("bytes=(\\d*)-(\\d*)").find(it) }
            val start = range?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val end = (range?.groupValues?.get(2)?.toLongOrNull() ?: (s.length - 1)).coerceAtMost(s.length - 1)
            if (start > end) {
                ex.responseHeaders.add("Content-Range", "bytes */${s.length}")
                ex.sendResponseHeaders(416, -1); return
            }
            ex.responseHeaders.add("Content-Type", s.mime)
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            val partial = range != null
            if (partial) ex.responseHeaders.add("Content-Range", "bytes $start-$end/${s.length}")
            val total = end - start + 1
            if (ex.requestMethod == "HEAD") { ex.responseHeaders.add("Content-Length", total.toString()); ex.sendResponseHeaders(if (partial) 206 else 200, -1); return }
            ex.sendResponseHeaders(if (partial) 206 else 200, total)
            val gen = s.startRead(start)
            ex.responseBody.use { out ->
                var pos = start
                while (pos <= end) {
                    // mpv moved on (seek): stop feeding this response instead of prefetching for a position nobody reads.
                    if (s.readGen != gen) break
                    val idx = s.layout.indexOf(pos)
                    for (k in 1..AHEAD) if (idx + k < s.layout.count) chunk(s, idx + k)
                    val bytes = chunkBytes(s, idx, gen)
                    val off = (pos - s.layout.start(idx)).toInt()
                    val n = minOf((bytes.size - off).toLong(), end - pos + 1).toInt()
                    out.write(bytes, off, n)
                    pos += n
                }
            }
        } catch (_: IOException) {
            // mpv closed the connection (seek or stop).
        } catch (_: Throwable) {
            runCatching { ex.close() }
        } finally {
            ex.close()
        }
    }
}
