package com.music.vivi.compose.ui.tags

import java.io.File

/** File-backed fake: a "track" is a text file of `field=value` lines, so backup/undo can be tested with real files. */
class FakeTagWriter(private val failOn: Set<String> = emptySet(), private val readonlyPaths: Set<String> = emptySet()) : TagWriter {
    val writes = mutableListOf<WriteSpec>()

    fun create(file: File, tags: Tags) { file.parentFile.mkdirs(); file.writeText(encode(tags)) }

    private fun encode(t: Tags) = t.entries.joinToString("\n") { "${it.key.key}=${it.value}" }
    private fun decode(s: String): Tags = s.lines().filter { '=' in it }.associate { l ->
        TagField.entries.first { it.key == l.substringBefore('=') } to l.substringAfter('=')
    }

    override fun read(path: String): TagReadResult? =
        File(path).takeIf { it.isFile }?.let { TagReadResult(path, decode(it.readText())) }

    override fun write(spec: WriteSpec): WriteOutcome {
        writes += spec
        val f = File(spec.path)
        if (spec.path in readonlyPaths) return WriteOutcome(false, "file is read-only")
        if (spec.path in failOn) return WriteOutcome(false, "boom")
        spec.backupPath?.let { File(it).parentFile.mkdirs(); f.copyTo(File(it), overwrite = true) }
        val merged = decode(f.readText()).toMutableMap()
        spec.fields.forEach { (k, v) -> if (v.isBlank()) merged.remove(k) else merged[k] = v }
        f.writeText(encode(merged))
        return WriteOutcome(true, null, f.length(), f.lastModified())
    }

    override fun restore(backup: String, dest: String): WriteOutcome {
        File(backup).copyTo(File(dest), overwrite = true)
        return WriteOutcome(true)
    }
}

class RecordingSink : LibrarySink {
    val synced = mutableListOf<Pair<String, Tags>>()
    var refreshes = 0
    override fun synced(trackId: String, path: String, read: TagReadResult, size: Long, mtimeMs: Long) { synced += trackId to read.tags }
    override suspend fun refresh() { refreshes++ }
}

class FakeLookup(private val result: (TagQuery) -> LookupResult) : TagLookup {
    var calls = 0
    override suspend fun lookup(q: TagQuery, fingerprint: Fingerprint?): LookupResult { calls++; return result(q) }
}
