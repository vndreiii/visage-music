package com.music.vivi.compose.ui.tags

import com.music.vivi.nativelib.AppPaths
import com.music.vivi.nativelib.NativeLibLoader
import com.sun.jna.Library
import com.sun.jna.Pointer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Suppress("FunctionName")
internal interface VisageTagsLib : Library {
    fun visage_tags_read(request: String): Pointer?
    fun visage_tags_write(request: String): Pointer?
    fun visage_tags_restore(request: String): Pointer?
    fun visage_tags_lookup(request: String): Pointer?
    fun visage_free_string(p: Pointer?)
}

/** JSON <-> model mapping for the native tag calls (separate from JNA so it can be unit-tested). */
object TagJson {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != "null" }?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    fun parseRead(raw: String): TagReadResult? {
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        if (o.containsKey("error")) return null
        val tags = buildMap {
            for (f in TagField.entries) o.str(f.key)?.let { put(f, it) }
        }
        return TagReadResult(
            path = o.str("path").orEmpty(),
            tags = tags,
            format = o.str("format").orEmpty(),
            readonly = (o["readonly"] as? JsonPrimitive)?.booleanOrNull ?: false,
            hasCover = (o["hasCover"] as? JsonPrimitive)?.booleanOrNull ?: false,
            coverPath = o.str("coverPath"),
            supportsCover = (o["supportsCover"] as? JsonPrimitive)?.booleanOrNull ?: true,
            durationSec = (o["durationSec"] as? JsonPrimitive)?.longOrNull,
        )
    }

    fun writeRequest(s: WriteSpec): String = buildJsonObject {
        put("path", s.path)
        put("fields", buildJsonObject { s.fields.forEach { (f, v) -> put(f.key, v) } })
        s.coverFile?.let { put("cover_path", it) }
        put("remove_cover", s.removeCover)
        put("id3v23", s.id3v23)
        s.backupPath?.let { put("backup_path", it) }
    }.toString()

    fun parseOutcome(raw: String): WriteOutcome {
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return WriteOutcome(false, "no answer from the tag writer")
        return WriteOutcome(
            ok = (o["ok"] as? JsonPrimitive)?.booleanOrNull ?: false,
            error = o.str("error"),
            size = (o["size"] as? JsonPrimitive)?.longOrNull ?: 0,
            mtimeMs = (o["mtimeMs"] as? JsonPrimitive)?.longOrNull ?: 0,
        )
    }

    fun lookupRequest(q: TagQuery, fp: Fingerprint?, acoustidKey: String?): String = buildJsonObject {
        put("title", q.title)
        put("artist", q.artist)
        put("album", q.album)
        q.durationSec?.let { put("duration_sec", it) }
        if (fp != null && !acoustidKey.isNullOrBlank()) {
            put("fingerprint", fp.fingerprint)
            put("duration_sec", fp.durationSec)
            put("acoustid_key", acoustidKey)
        }
    }.toString()

    fun parseLookup(raw: String): LookupResult {
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return LookupResult(emptyList(), listOf("bad answer"))
        val errors = (o["errors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val cands = (o["candidates"] as? JsonArray).orEmpty().mapNotNull { e -> candidate(e) }
        return LookupResult(cands, errors)
    }

    private fun candidate(e: JsonElement): TagCandidate? {
        val o = e as? JsonObject ?: return null
        val tags = buildMap {
            for (f in TagField.entries) o.str(f.key)?.let { put(f, it) }
        }
        if (tags.value(TagField.Title).isBlank()) return null
        return TagCandidate(
            source = o.str("source").orEmpty(),
            tags = tags,
            coverUrl = o.str("coverUrl"),
            durationSec = (o["durationSec"] as? JsonPrimitive)?.longOrNull,
            sourceScore = (o["sourceScore"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull(),
        )
    }
}

/** Rust/lofty-backed [TagWriter]. [available] is false when the native library is missing. */
class NativeTagWriter(private val coversDir: () -> File) : TagWriter {
    private val lib: VisageTagsLib? by lazy { NativeLibLoader.load(VisageTagsLib::class.java) }
    val available: Boolean get() = lib != null

    private fun call(f: (VisageTagsLib) -> Pointer?): String? {
        val l = lib ?: return null
        val p = f(l) ?: return null
        return try { p.getString(0, "UTF-8") } finally { l.visage_free_string(p) }
    }

    override fun read(path: String): TagReadResult? {
        val req = buildJsonObject { put("path", path); put("covers_dir", coversDir().apply { mkdirs() }.absolutePath) }.toString()
        return call { it.visage_tags_read(req) }?.let(TagJson::parseRead)
    }

    override fun write(spec: WriteSpec): WriteOutcome {
        if (lib == null) return WriteOutcome(false, "Native tag writer not available (build native/visage-native)")
        val raw = call { it.visage_tags_write(TagJson.writeRequest(spec)) } ?: return WriteOutcome(false, "no answer from the tag writer")
        return TagJson.parseOutcome(raw)
    }

    override fun restore(backup: String, dest: String): WriteOutcome {
        if (lib == null) return WriteOutcome(false, "Native tag writer not available")
        val req = buildJsonObject { put("backup", backup); put("dest", dest) }.toString()
        return call { it.visage_tags_restore(req) }?.let(TagJson::parseOutcome) ?: WriteOutcome(false, "no answer from the tag writer")
    }
}

/** MusicBrainz / Deezer / iTunes / TheAudioDB (and AcoustID when configured) through the Rust helper. */
class NativeTagLookup(private val acoustidKey: () -> String?) : TagLookup {
    private val lib: VisageTagsLib? by lazy { NativeLibLoader.load(VisageTagsLib::class.java) }
    val available: Boolean get() = lib != null

    override suspend fun lookup(q: TagQuery, fingerprint: Fingerprint?): LookupResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val l = lib ?: return@withContext LookupResult(emptyList(), listOf("native helper not available"))
            val p = l.visage_tags_lookup(TagJson.lookupRequest(q, fingerprint, acoustidKey()))
                ?: return@withContext LookupResult(emptyList(), listOf("no answer"))
            val raw = try { p.getString(0, "UTF-8") } finally { l.visage_free_string(p) }
            TagJson.parseLookup(raw)
        }
}

/** Audio fingerprints via chromaprint's `fpcalc`, only when the binary exists. */
object Fingerprinter {
    val binary: File? by lazy {
        System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).map { File(it, "fpcalc") }.firstOrNull { it.canExecute() }
    }
    val available: Boolean get() = binary != null

    fun fingerprint(path: String): Fingerprint? {
        val bin = binary ?: return null
        return runCatching {
            val proc = ProcessBuilder(bin.absolutePath, "-json", path).redirectErrorStream(false).start()
            val out = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { proc.destroyForcibly(); return null }
            val o = Json.parseToJsonElement(out) as JsonObject
            Fingerprint((o["duration"] as JsonPrimitive).content.toDouble().toLong(), (o["fingerprint"] as JsonPrimitive).content)
        }.getOrNull()
    }
}

/** Downloads cover images to a temp folder (size-capped, verified by magic bytes). */
object CoverFetcher {
    private val client by lazy {
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(6)).build()
    }
    private const val MAX_BYTES = 12L * 1024 * 1024
    val dir: File by lazy { File(AppPaths.cache(), "tag-covers").apply { mkdirs() } }

    fun looksLikeImage(b: ByteArray): Boolean =
        b.size > 16 && ((b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) ||
            (b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) || (b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte()) ||
            (b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte()))

    /** Local copy of the image at [url] (cached by URL), or null. Blocking: call on IO. */
    fun download(url: String): File? = runCatching {
        val uri = URI.create(url.trim())
        require(uri.scheme == "http" || uri.scheme == "https")
        val name = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        val out = File(dir, "$name.img")
        if (out.isFile && out.length() > 0) return out
        val req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", "VIsage tag manager (https://github.com/vndreiii/visage-music)").GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (resp.statusCode() != 200) return null
        val bytes = resp.body().use { it.readNBytes(MAX_BYTES.toInt() + 1) }
        if (bytes.size > MAX_BYTES || !looksLikeImage(bytes)) return null
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(out)
        out
    }.getOrNull()
}
