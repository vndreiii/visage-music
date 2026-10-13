package com.music.vivi.database

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** Raw C ABI of `native/vivi-native` (see `native/vivi-native/src/lib.rs`). */
@Suppress("FunctionName")
internal interface ViviNativeLib : Library {
    fun vivi_native_version(): Pointer?
    fun vivi_free_string(p: Pointer?)
    fun vivi_scan(request: String): Pointer?
    fun vivi_search_new(items: String): Long
    fun vivi_search_query(handle: Long, query: String, limit: Int): Pointer?
    fun vivi_search_free(handle: Long)
}

@Serializable
data class NativeTrack(
    val path: String,
    val size: Long = 0,
    val mtime_ms: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val album_artist: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val year: Long? = null,
    val track_no: Long? = null,
    val disc_no: Long? = null,
    val duration_sec: Long? = null,
    val bitrate: Long? = null,
    val sample_rate: Long? = null,
    val bit_depth: Long? = null,
    val lossless: Boolean = false,
    val lyrics: String? = null,
    val lrc_path: String? = null,
    val cover_path: String? = null,
    val unreadable: Boolean = false,
    /** Same size/mtime as the stored row: nothing was read, keep what the database has. */
    val unchanged: Boolean = false,
)

@Serializable
data class NativeScanStats(
    val files: Int = 0,
    val cached: Int = 0,
    val read: Int = 0,
    val unreadable: Int = 0,
    val walk_ms: Long = 0,
    val total_ms: Long = 0,
)

@Serializable
data class NativeScanResult(
    val tracks: List<NativeTrack> = emptyList(),
    val stats: NativeScanStats = NativeScanStats(),
    val error: String? = null,
)

@Serializable
data class KnownFileEntry(val path: String, val size: Long, val mtime_ms: Long, val cover_path: String? = null)

@Serializable
private data class ScanRequest(val roots: List<String>, val covers_dir: String, val known: List<KnownFileEntry>)

@Serializable
private data class SearchItem(val id: String, val text: String)

/**
 * Optional Rust acceleration. Everything here returns null / false when the shared library
 * is missing so callers keep their pure-JVM path.
 *
 * Library lookup is shared: see `NativeLibLoader`.
 */
object NativeHelpers {
    private val json = Json { ignoreUnknownKeys = true }

    private val lib: ViviNativeLib? by lazy { com.music.vivi.nativelib.NativeLibLoader.load(ViviNativeLib::class.java) }

    val available: Boolean get() = lib != null

    fun version(): String? = lib?.let { l -> take(l, l.vivi_native_version()) }

    private fun take(l: ViviNativeLib, p: Pointer?): String? {
        p ?: return null
        return try {
            p.getString(0, "UTF-8")
        } finally {
            l.vivi_free_string(p)
        }
    }

    /**
     * Parallel walk + tag read. Files matching an entry in [known] (same size and mtime) come back
     * flagged `unchanged` without being read; null if the library is unavailable or failed.
     */
    fun scan(roots: List<File>, coversDir: File, known: List<KnownFileEntry>): NativeScanResult? {
        val l = lib ?: return null
        val request = json.encodeToString(
            ScanRequest.serializer(),
            ScanRequest(roots.map { it.absolutePath }, coversDir.absolutePath, known),
        )
        val raw = take(l, l.vivi_scan(request)) ?: return null
        val result = runCatching { json.decodeFromString(NativeScanResult.serializer(), raw) }.getOrNull()
        return result?.takeIf { it.error == null }
    }

    /** Typo-tolerant search over (id, text) pairs, ranked best-first. Close when done. */
    fun searchIndex(items: List<Pair<String, String>>): NativeSearchIndex? {
        val l = lib ?: return null
        val payload = json.encodeToString(
            ListSerializer(SearchItem.serializer()),
            items.map { SearchItem(it.first, it.second) },
        )
        val handle = l.vivi_search_new(payload)
        return if (handle == 0L) null else NativeSearchIndex(l, handle, json)
    }
}

class NativeSearchIndex internal constructor(
    private val lib: ViviNativeLib,
    private val handle: Long,
    private val json: Json,
) : AutoCloseable {
    fun query(text: String, limit: Int = 50): List<String> {
        val p = lib.vivi_search_query(handle, text, limit) ?: return emptyList()
        val raw = try {
            p.getString(0, "UTF-8")
        } finally {
            lib.vivi_free_string(p)
        }
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrDefault(emptyList())
    }

    override fun close() = lib.vivi_search_free(handle)
}
