package com.music.vivi.compose.ui.charts

import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.vivi.compose.ui.home.QuickPicksCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * A chart of the whole world: every country's chart counts the same. YouTube's own "Global" chart is ranked by raw
 * views, so a few huge markets dominate it; this ranks songs by how high they place across countries instead.
 *
 * It takes ~120 small requests, so it is built slowly (one at a time with a pause between them), cached on disk, and
 * shared: the Charts page shows it, and Home's "Trending everywhere" and "Breaking out" shelves read the same result.
 */
object WorldChart {
    class Snapshot(val savedAtMs: Long, val ranked: List<SongItem>, val breaking: List<SongItem>)
    class Progress(val done: Int, val total: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rankedCache = QuickPicksCache(File(com.music.vivi.nativelib.AppPaths.config(), "world-chart.json"))
    private val breakingCache = QuickPicksCache(File(com.music.vivi.nativelib.AppPaths.config(), "world-breaking.json"))

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()
    private val _progress = MutableStateFlow<Progress?>(null)
    /** Non-null while a build is running. */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()
    private var job: Job? = null

    /** Pause between requests, so the build trickles instead of bursting. */
    private const val PAUSE_MS = 450L
    private const val PER_COUNTRY = 25

    fun cached(): Snapshot? {
        _snapshot.value?.let { return it }
        val r = rankedCache.load() ?: return null
        val b = breakingCache.load()
        return Snapshot(r.savedAtMs, r.songs, b?.songs.orEmpty()).also { _snapshot.value = it }
    }

    /** Starts a build unless a recent result exists (or one is already running). [force] rebuilds regardless. */
    fun ensureFresh(maxAgeMs: Long, force: Boolean = false) {
        val have = cached()
        if (!force && have != null && System.currentTimeMillis() - have.savedAtMs < maxAgeMs) return
        if (job?.isActive == true) return
        job = scope.launch { build() }
    }

    private suspend fun build() {
        val countries = ChartCountries
        val points = HashMap<String, Int>()
        val seen = HashMap<String, Int>()
        val best = HashMap<String, Int>()
        val songs = HashMap<String, SongItem>()
        _progress.value = Progress(0, countries.size)
        countries.forEachIndexed { i, code ->
            if (!scope.isActive) return
            runCatching {
                val page = YouTube.getChartsPage(country = code).getOrNull()
                delay(PAUSE_MS)
                val direct = page?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>()
                val list = direct.ifEmpty {
                    val playlists = page?.sections.orEmpty().flatMap { it.items }.filterIsInstance<PlaylistItem>()
                    val pick = playlists.firstOrNull { it.title.contains("Trending", ignoreCase = true) } ?: playlists.firstOrNull()
                    pick?.let { YouTube.playlist(it.id).getOrNull()?.songs }.orEmpty().also { delay(PAUSE_MS) }
                }
                list.take(PER_COUNTRY).forEachIndexed { rank, s ->
                    val pts = PER_COUNTRY + 1 - (rank + 1)
                    points[s.id] = (points[s.id] ?: 0) + pts
                    seen[s.id] = (seen[s.id] ?: 0) + 1
                    best[s.id] = maxOf(best[s.id] ?: 0, pts)
                    songs.putIfAbsent(s.id, s)
                }
            }
            _progress.value = Progress(i + 1, countries.size)
        }
        if (songs.isEmpty()) { _progress.value = null; return }
        val ranked = songs.values.sortedByDescending { points[it.id] ?: 0 }.take(100)
        val rankedIds = ranked.take(30).map { it.id }.toSet()
        // Hits in only a few countries that are near the top where they are: not everywhere yet, but climbing.
        val breaking = songs.values
            .filter { it.id !in rankedIds && (seen[it.id] ?: 0) <= 3 && (best[it.id] ?: 0) >= PER_COUNTRY - 3 }
            .sortedByDescending { (best[it.id] ?: 0) * 10 + (seen[it.id] ?: 0) }
            .take(30)
        val now = System.currentTimeMillis()
        rankedCache.save(ranked, now)
        breakingCache.save(breaking, now)
        _snapshot.value = Snapshot(now, ranked, breaking)
        _progress.value = null
    }
}
