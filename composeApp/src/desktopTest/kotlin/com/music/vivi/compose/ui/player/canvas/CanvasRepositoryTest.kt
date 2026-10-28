package com.music.vivi.compose.ui.player.canvas

import com.music.vivi.canvas.CanvasArtwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CanvasRepositoryTest {
    private val art = CanvasArtwork(canvasKey = "k", animated = "https://x/a.m3u8")
    private fun q(t: String) = CanvasQuery(t, "artist")

    private class Env(cfg: CanvasRepositoryConfig = CanvasRepositoryConfig(), fetcher: CanvasFetcher) {
        var now = 1_000_000L
        val sleeps = mutableListOf<Long>()
        val repo = CanvasRepository(
            fetcher, cfg, clock = { now },
            sleep = { sleeps += it; now += it },
            jitter = { 0 },
        )
    }

    @Test
    fun concurrentRequestsShareOneFetch() = runBlocking {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val env = Env { calls.incrementAndGet(); gate.await(); CanvasFetch.Found(art) }
        val all = (1..5).map { async { env.repo.load(q("Song")) } }
        delay(50)
        gate.complete(Unit)
        val results = all.awaitAll()
        assertEquals(1, calls.get())
        results.forEach { assertEquals(CanvasState.Ready(art), it) }
        assertEquals(CanvasState.Ready(art), env.repo.peek(q("song ")), "key is case/space insensitive")
    }

    @Test
    fun staleResultIsDiscardedOnTrackChange() = runBlocking {
        val gateA = CompletableDeferred<Unit>()
        val artB = art.copy(canvasKey = "b")
        val env = Env { query ->
            if (query.title == "A") { gateA.await(); CanvasFetch.Found(art) } else CanvasFetch.Found(artB)
        }
        env.repo.select(q("A"))
        env.repo.select(q("B"))
        withTimeout(5_000) { while (env.repo.selected.value.state !is CanvasState.Ready) delay(10) }
        gateA.complete(Unit)
        delay(100)
        val sel = env.repo.selected.value
        assertEquals(q("B").key, sel.key)
        assertEquals(CanvasState.Ready(artB), sel.state)
    }

    @Test
    fun negativeResultsExpireQuickly() = runBlocking {
        val calls = AtomicInteger()
        val env = Env { calls.incrementAndGet(); if (calls.get() == 1) CanvasFetch.NotFound else CanvasFetch.Found(art) }
        assertEquals(CanvasState.NoCanvas, env.repo.load(q("S")))
        env.now += 60_000
        assertEquals(CanvasState.NoCanvas, env.repo.load(q("S")))
        assertEquals(1, calls.get(), "still negative-cached")
        env.now += 3 * 60_000
        assertEquals(CanvasState.Ready(art), env.repo.load(q("S")))
        assertEquals(2, calls.get())
    }

    @Test
    fun transientErrorsRetryWithExponentialBackoff() = runBlocking {
        val calls = AtomicInteger()
        val env = Env { if (calls.incrementAndGet() < 3) throw IOException("boom") else CanvasFetch.Found(art) }
        assertEquals(CanvasState.Ready(art), env.repo.load(q("S")))
        assertEquals(3, calls.get())
        assertEquals(listOf(500L, 1000L), env.sleeps)
    }

    @Test
    fun retryAfterIsHonoured() = runBlocking {
        val calls = AtomicInteger()
        val env = Env { if (calls.incrementAndGet() == 1) CanvasFetch.Transient(null, 5_000) else CanvasFetch.Found(art) }
        env.repo.load(q("S"))
        assertEquals(listOf(5_000L), env.sleeps)
    }

    @Test
    fun notFoundIsNotRetried() = runBlocking {
        val calls = AtomicInteger()
        val env = Env { calls.incrementAndGet(); CanvasRepository.classifyHttp(404)!! }
        assertEquals(CanvasState.NoCanvas, env.repo.load(q("S")))
        assertEquals(1, calls.get())
        assertTrue(env.sleeps.isEmpty())
    }

    @Test
    fun httpClassification() {
        assertEquals(null, CanvasRepository.classifyHttp(200))
        assertEquals(CanvasFetch.NotFound, CanvasRepository.classifyHttp(404))
        assertIs<CanvasFetch.Transient>(CanvasRepository.classifyHttp(503))
        assertEquals(7_000L, (CanvasRepository.classifyHttp(429, 7) as CanvasFetch.Transient).retryAfterMs)
    }

    @Test
    fun exhaustedRetriesYieldErrorThenCooldownThenRetryAction() = runBlocking {
        val calls = AtomicInteger()
        val env = Env { calls.incrementAndGet(); throw IOException("down") }
        val first = env.repo.load(q("S"))
        assertIs<CanvasState.Error>(first)
        assertEquals(3, calls.get())
        env.repo.load(q("S"))
        assertEquals(3, calls.get(), "cooldown prevents hammering")
        env.repo.load(q("S"), force = true)
        assertEquals(6, calls.get(), "explicit retry bypasses the cooldown")
        env.now += 11_000
        env.repo.load(q("S"))
        assertEquals(9, calls.get(), "cooldown expiry allows a new attempt")
    }

    @Test
    fun cacheIsBounded() = runBlocking {
        val env = Env(CanvasRepositoryConfig(maxEntries = 4)) { CanvasFetch.Found(art) }
        for (i in 1..20) env.repo.load(q("t$i"))
        assertEquals(4, env.repo.cachedEntryCount)
    }

    @Test
    fun fetchConcurrencyIsCapped() = runBlocking {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val env = Env(CanvasRepositoryConfig(maxConcurrentFetches = 2)) {
            val n = running.incrementAndGet()
            peak.accumulateAndGet(n, ::maxOf)
            delay(30)
            running.decrementAndGet()
            CanvasFetch.Found(art)
        }
        (1..8).map { async { env.repo.load(q("t$it")) } }.awaitAll()
        assertEquals(2, peak.get())
    }

    @Test
    fun cancelledCallerDoesNotPoisonSharedLookup() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val env = Env { gate.await(); CanvasFetch.Found(art) }
        val a = async { env.repo.load(q("S")) }
        val b = async { env.repo.load(q("S")) }
        delay(30)
        a.cancel()
        gate.complete(Unit)
        assertEquals(CanvasState.Ready(art), b.await())
    }
}
