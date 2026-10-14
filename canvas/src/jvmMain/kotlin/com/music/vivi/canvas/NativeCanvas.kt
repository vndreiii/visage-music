package com.music.vivi.canvas

import com.music.vivi.nativelib.NativeLibLoader
import com.sun.jna.Library
import com.sun.jna.Pointer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Suppress("FunctionName")
internal interface VisageCanvasLib : Library {
    fun visage_canvas_resolve(request: String): Pointer?
    fun visage_free_string(p: Pointer?)
}

@Serializable
private data class CanvasRequest(
    val title: String,
    val artist: String,
    val album: String?,
    val source: String,
    val storefront: String,
    val cache_dir: String,
)

/**
 * Canvas lookup in the Rust helper: Apple Music, Tidal and the Visage manifest are queried
 * concurrently and results are cached on disk. [resolve] blocks on the network, call it off the UI
 * thread. [available] is false when the native library is missing; callers then use the Kotlin providers.
 */
object NativeCanvas {
    private val json = Json { ignoreUnknownKeys = true }
    private val lib: VisageCanvasLib? by lazy { NativeLibLoader.load(VisageCanvasLib::class.java) }

    val available: Boolean get() = lib != null

    private val cacheDir: File by lazy { File(com.music.vivi.nativelib.AppPaths.cache(), "canvas") }

    /** `source` is one of `auto`, `apple`, `tidal`, `vivi`. Returns null when nothing was found. */
    fun resolve(title: String, artist: String, album: String?, source: String, storefront: String): CanvasArtwork? {
        val l = lib ?: return null
        val request = json.encodeToString(
            CanvasRequest.serializer(),
            CanvasRequest(title, artist, album, source, storefront, cacheDir.absolutePath),
        )
        val p = l.visage_canvas_resolve(request) ?: return null
        val raw = try {
            p.getString(0, "UTF-8")
        } finally {
            l.visage_free_string(p)
        }
        if (raw == "null") return null
        return runCatching { json.decodeFromString(CanvasArtwork.serializer(), raw) }.getOrNull()
    }
}
