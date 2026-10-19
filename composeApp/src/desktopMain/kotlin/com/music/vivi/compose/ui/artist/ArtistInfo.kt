package com.music.vivi.compose.ui.artist

import com.music.vivi.nativelib.AppPaths
import com.music.vivi.nativelib.NativeLibLoader
import com.sun.jna.Library
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** What the Rust helper found for an artist; every field except [name] may be missing. */
data class ArtistInfo(
    val name: String,
    val imageUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String? = null,
    val sources: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses the helper's response; the literal `null` (nothing found) and garbage give null. */
        fun parse(raw: String): ArtistInfo? {
            if (raw.isBlank() || raw == "null") return null
            val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
            fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
            val name = str("name") ?: return null
            val sources = (o["sources"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            return ArtistInfo(name, str("imageUrl"), str("bannerUrl"), str("bio"), sources)
        }
    }
}

/** Credit-string helpers shared by the UI (the Rust helper has the same splitting rules). */
object ArtistNames {
    private val delimiters = Regex(
        """\s*[,;]\s*|\s*&\s*|\s+×\s+|\s+x\s+|\s+(?:feat|ft)\.?\s+|\s+featuring\s+|\s*\(\s*(?:feat|ft)\.?\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** "DJ llz, XSKAVI1, & Dj v2be" -> ["DJ llz", "XSKAVI1", "Dj v2be"], original casing, deduplicated. */
    fun split(credits: String): List<String> {
        val out = ArrayList<String>()
        for (part in credits.split(delimiters)) {
            val p = part.trim().trim('(', ')').trim()
            if (p.isNotEmpty() && out.none { normalize(it) == normalize(p) }) out += p
        }
        return out
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** True when [artist] is one of the credits of [credits]. */
    fun credited(credits: String, artist: String): Boolean {
        val n = normalize(artist)
        return n.isNotEmpty() && (normalize(credits) == n || split(credits).any { normalize(it) == n })
    }

    /** Up to two initials ("DJ SMG" -> "DS"). */
    fun initials(name: String): String {
        val words = name.split(' ', '-', '_', '.').filter { w -> w.any { it.isLetterOrDigit() } }
        val letters = when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].filter { it.isLetterOrDigit() }.take(2)
            else -> words.take(2).joinToString("") { w -> w.first { it.isLetterOrDigit() }.toString() }
        }
        return letters.uppercase()
    }

    /** Stable hue 0..360 derived from the normalised name. */
    fun hue(name: String): Float {
        var h = 7
        for (c in normalize(name)) h = h * 31 + c.code
        return ((h % 360) + 360) % 360f
    }
}

@Suppress("FunctionName")
internal interface VisageArtistLib : Library {
    fun visage_artist_resolve(request: String): Pointer?
    fun visage_free_string(p: Pointer?)
}

/**
 * Artist portrait / banner / bio lookup. The heavy lifting (Deezer, TheAudioDB, Wikipedia in
 * parallel, disk cache with TTL and negative caching, polite rate limiting) lives in the Rust helper;
 * this adds an in-memory cache, de-duplication of concurrent requests for the same artist and a
 * small concurrency cap. [artistInfo] suspends on an IO thread, so it never blocks the UI.
 * Returns null when the helper is missing (`VISAGE_NATIVE_DISABLE`, no library) or nothing was found;
 * callers show a generated fallback.
 */
object ArtistInfoRepository {
    private val lib: VisageArtistLib? by lazy { NativeLibLoader.load(VisageArtistLib::class.java) }
    private val cacheDir: File by lazy { File(AppPaths.cache(), "artists") }
    private val gate = Semaphore(3)

    private val memory = ConcurrentHashMap<String, Result>()
    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<ArtistInfo?>>()

    private class Result(val info: ArtistInfo?, val atMs: Long)

    val available: Boolean get() = lib != null

    /** Cached answer without any I/O (for first composition); `found = false` means "not looked up yet". */
    fun peek(name: String): Pair<Boolean, ArtistInfo?> {
        val r = memory[ArtistNames.normalize(name)]
        return if (r != null && fresh(r)) true to r.info else false to null
    }

    private fun fresh(r: Result) =
        System.currentTimeMillis() - r.atMs < (if (r.info != null) 60 * 60_000L else 5 * 60_000L)

    suspend fun artistInfo(name: String): ArtistInfo? {
        val key = ArtistNames.normalize(name)
        if (key.isEmpty()) return null
        memory[key]?.takeIf(::fresh)?.let { return it.info }
        val mine = kotlinx.coroutines.CompletableDeferred<ArtistInfo?>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) return existing.await()
        try {
            val info = gate.withPermit { withContext(Dispatchers.IO) { lookup(name) } }
            memory[key] = Result(info?.copy(name = name.trim()), System.currentTimeMillis())
            mine.complete(memory[key]?.info)
        } catch (t: Throwable) {
            mine.complete(null)
            if (t is kotlinx.coroutines.CancellationException) throw t
        } finally {
            inFlight.remove(key)
        }
        return memory[key]?.info
    }

    private fun lookup(name: String): ArtistInfo? {
        val l = lib ?: return null
        val request = buildJsonObject {
            put("name", JsonPrimitive(name.trim()))
            put("cache_dir", JsonPrimitive(cacheDir.absolutePath))
        }.toString()
        val p = l.visage_artist_resolve(request) ?: return null
        val raw = try {
            p.getString(0, "UTF-8")
        } finally {
            l.visage_free_string(p)
        }
        return ArtistInfo.parse(raw)
    }
}
