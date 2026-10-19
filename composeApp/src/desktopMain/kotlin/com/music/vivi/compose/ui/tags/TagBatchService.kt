package com.music.vivi.compose.ui.tags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/** One track's pending write. */
data class BatchItem(
    val trackId: String,
    val path: String,
    val edits: Tags,
    val coverFile: String? = null,
    val removeCover: Boolean = false,
) {
    val isNoop: Boolean get() = edits.isEmpty() && coverFile == null && !removeCover
}

data class ItemResult(val item: BatchItem, val ok: Boolean, val error: String? = null)

data class BatchReport(val results: List<ItemResult>, val undo: Boolean = false) {
    val succeeded: Int get() = results.count { it.ok }
    val failed: List<ItemResult> get() = results.filterNot { it.ok }
}

data class ManifestEntry(val trackId: String, val path: String, val backup: String)

data class BatchManifest(val id: String, val createdAtMs: Long, val entries: List<ManifestEntry>)

/** Receives the truth about a file after a write/undo so the library database can follow. */
interface LibrarySink {
    fun synced(trackId: String, path: String, read: TagReadResult, size: Long, mtimeMs: Long)
    suspend fun refresh()
}

/**
 * Applies edits through a [TagWriter] with per-track results and a one-step Undo.
 *
 * Every batch takes a backup of each original (needed for Undo). With [keepBackups] off, the
 * previous batch's backups are deleted when the next batch starts (so exactly one level of Undo
 * stays on disk); with it on, up to [maxBatches] batches are kept.
 */
class TagBatchService(
    private val writer: TagWriter,
    private val backupRoot: File,
    private val sink: LibrarySink,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val maxBatches: Int = 10,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Hand-written JSON: the serialization compiler plugin isn't applied to this module.
    private fun encode(m: BatchManifest): String = buildJsonObject {
        put("id", m.id)
        put("createdAtMs", m.createdAtMs)
        put("entries", buildJsonArray {
            m.entries.forEach { e -> add(buildJsonObject { put("trackId", e.trackId); put("path", e.path); put("backup", e.backup) }) }
        })
    }.toString()

    private fun decode(raw: String): BatchManifest? {
        val o = json.parseToJsonElement(raw) as? JsonObject ?: return null
        fun str(x: JsonObject, k: String) = (x[k] as? JsonPrimitive)?.contentOrNull
        val entries = (o["entries"] as? JsonArray).orEmpty().mapNotNull { el ->
            val x = el as? JsonObject ?: return@mapNotNull null
            ManifestEntry(str(x, "trackId") ?: return@mapNotNull null, str(x, "path") ?: return@mapNotNull null, str(x, "backup") ?: return@mapNotNull null)
        }
        return BatchManifest(str(o, "id") ?: return null, (o["createdAtMs"] as? JsonPrimitive)?.longOrNull ?: 0L, entries)
    }
    private val _last = MutableStateFlow<BatchManifest?>(loadLast())
    val lastBatch: StateFlow<BatchManifest?> = _last.asStateFlow()

    private val manifestFile get() = File(backupRoot, "last.json")

    private fun loadLast(): BatchManifest? = runCatching {
        File(backupRoot, "last.json").takeIf { it.isFile }?.let { decode(it.readText()) }
    }.getOrNull()?.takeIf { m -> m.entries.any { File(it.backup).isFile } }

    suspend fun apply(
        items: List<BatchItem>,
        keepBackups: Boolean,
        id3v23: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BatchReport {
        val work = items.filterNot { it.isNoop }
        val prev = _last.value
        if (prev != null && !keepBackups) deleteBatch(prev)
        val id = "b${clockMs()}"
        val dir = File(backupRoot, id)
        val results = ArrayList<ItemResult>()
        val entries = ArrayList<ManifestEntry>()
        work.forEachIndexed { i, item ->
            val backup = File(dir, "%04d-%s".format(i, File(item.path).name))
            val outcome = runCatching {
                writer.write(
                    WriteSpec(
                        path = item.path,
                        fields = item.edits,
                        coverFile = item.coverFile,
                        removeCover = item.removeCover,
                        id3v23 = id3v23,
                        backupPath = backup.absolutePath,
                    ),
                )
            }.getOrElse { WriteOutcome(false, it.message ?: "write failed") }
            if (outcome.ok) {
                entries += ManifestEntry(item.trackId, item.path, backup.absolutePath)
                val read = writer.read(item.path)
                if (read != null) sink.synced(item.trackId, item.path, read, outcome.size, outcome.mtimeMs)
            } else {
                backup.delete()
            }
            results += ItemResult(item, outcome.ok, outcome.error)
            onProgress(i + 1, work.size)
        }
        if (entries.isNotEmpty()) {
            val manifest = BatchManifest(id, clockMs(), entries)
            backupRoot.mkdirs()
            manifestFile.writeText(encode(manifest))
            _last.value = manifest
            prune(keepBackups)
            sink.refresh()
        } else {
            dir.delete()
        }
        return BatchReport(results)
    }

    /** Restores every file of the last batch from its backup. */
    suspend fun undoLast(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): BatchReport {
        val m = _last.value ?: return BatchReport(emptyList(), undo = true)
        val results = ArrayList<ItemResult>()
        val remaining = ArrayList<ManifestEntry>()
        m.entries.forEachIndexed { i, e ->
            val item = BatchItem(e.trackId, e.path, emptyMap())
            val out = runCatching { writer.restore(e.backup, e.path) }.getOrElse { WriteOutcome(false, it.message) }
            if (out.ok) {
                File(e.backup).delete()
                writer.read(e.path)?.let { r ->
                    val f = File(e.path)
                    sink.synced(e.trackId, e.path, r, f.length(), f.lastModified())
                }
            } else {
                remaining += e
            }
            results += ItemResult(item, out.ok, out.error)
            onProgress(i + 1, m.entries.size)
        }
        if (remaining.isEmpty()) {
            File(backupRoot, m.id).deleteRecursively()
            manifestFile.delete()
            _last.value = null
        } else {
            val keep = m.copy(entries = remaining)
            manifestFile.writeText(encode(keep))
            _last.value = keep
        }
        sink.refresh()
        return BatchReport(results, undo = true)
    }

    private fun deleteBatch(m: BatchManifest) {
        File(backupRoot, m.id).deleteRecursively()
        manifestFile.delete()
        _last.value = null
    }

    private fun prune(keepBackups: Boolean) {
        val dirs = backupRoot.listFiles { f -> f.isDirectory && f.name.startsWith("b") }?.sortedBy { it.name }.orEmpty()
        val limit = if (keepBackups) maxBatches else 1
        dirs.dropLast(limit).forEach { it.deleteRecursively() }
    }
}
