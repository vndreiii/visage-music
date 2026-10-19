package com.music.vivi.compose.ui.tags

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagBatchServiceTest {
    private fun tmp(): File = Files.createTempDirectory("tagtest").toFile()

    private fun setup(failOn: Set<String> = emptySet(), ro: Set<String> = emptySet()): Triple<FakeTagWriter, RecordingSink, Pair<File, File>> {
        val music = tmp(); val backups = tmp()
        return Triple(FakeTagWriter(failOn, ro), RecordingSink(), music to backups)
    }

    @Test fun appliesWithBackupSyncsAndUndoes() = runBlocking {
        val (w, sink, dirs) = setup()
        val (music, backups) = dirs
        val f = File(music, "a.mp3"); w.create(f, mapOf(TagField.Title to "old"))
        val svc = TagBatchService(w, backups, sink)
        val r = svc.apply(listOf(BatchItem("1", f.absolutePath, mapOf(TagField.Title to "new", TagField.Genre to "Pop"))), keepBackups = true)
        assertEquals(1, r.succeeded)
        assertEquals("new", w.read(f.absolutePath)!!.tags[TagField.Title])
        assertEquals("new", sink.synced.single().second[TagField.Title]); assertEquals(1, sink.refreshes)
        assertEquals(1, svc.lastBatch.value!!.entries.size)

        val u = svc.undoLast()
        assertTrue(u.undo && u.succeeded == 1)
        assertEquals("old", w.read(f.absolutePath)!!.tags[TagField.Title])
        assertFalse(TagField.Genre in w.read(f.absolutePath)!!.tags)
        assertNull(svc.lastBatch.value)
        assertEquals("old", sink.synced.last().second[TagField.Title])
    }

    @Test fun failuresAreReportedPerTrackAndOthersStillWritten() = runBlocking {
        val (w0, sink, dirs) = setup()
        val (music, backups) = dirs
        val good = File(music, "g.mp3"); val bad = File(music, "b.mp3"); val ro = File(music, "r.mp3")
        val w = FakeTagWriter(setOf(bad.absolutePath), setOf(ro.absolutePath))
        listOf(good, bad, ro).forEach { w.create(it, mapOf(TagField.Title to "t")) }
        val svc = TagBatchService(w, backups, sink)
        val r = svc.apply(listOf(good, bad, ro).mapIndexed { i, f -> BatchItem("$i", f.absolutePath, mapOf(TagField.Album to "X")) }, keepBackups = true)
        assertEquals(1, r.succeeded); assertEquals(2, r.failed.size)
        assertEquals(setOf("boom", "file is read-only"), r.failed.map { it.error }.toSet())
        assertEquals(1, svc.lastBatch.value!!.entries.size)
        assertEquals("X", w.read(good.absolutePath)!!.tags[TagField.Album]); assertFalse(TagField.Album in w.read(bad.absolutePath)!!.tags)
    }

    @Test fun noopItemsNeverTouchFiles() = runBlocking {
        val (w, sink, dirs) = setup()
        val f = File(dirs.first, "a.mp3"); w.create(f, mapOf(TagField.Title to "t"))
        val svc = TagBatchService(w, dirs.second, sink)
        val r = svc.apply(listOf(BatchItem("1", f.absolutePath, emptyMap())), keepBackups = true)
        assertTrue(r.results.isEmpty()); assertTrue(w.writes.isEmpty()); assertNull(svc.lastBatch.value)
    }

    @Test fun backupsOffKeepsOnlyTheLastBatchForUndo() = runBlocking {
        val (w, sink, dirs) = setup()
        val f = File(dirs.first, "a.mp3"); w.create(f, mapOf(TagField.Title to "v0"))
        var now = 1000L
        val svc = TagBatchService(w, dirs.second, sink, clockMs = { now++ })
        svc.apply(listOf(BatchItem("1", f.absolutePath, mapOf(TagField.Title to "v1"))), keepBackups = false)
        svc.apply(listOf(BatchItem("1", f.absolutePath, mapOf(TagField.Title to "v2"))), keepBackups = false)
        val batchDirs = dirs.second.listFiles { x -> x.isDirectory }!!
        assertEquals(1, batchDirs.size)
        svc.undoLast()
        assertEquals("v1", w.read(f.absolutePath)!!.tags[TagField.Title])
    }

    @Test fun keepBackupsPrunesToLimit() = runBlocking {
        val (w, sink, dirs) = setup()
        val f = File(dirs.first, "a.mp3"); w.create(f, mapOf(TagField.Title to "v"))
        var now = 1000L
        val svc = TagBatchService(w, dirs.second, sink, clockMs = { now++ }, maxBatches = 3)
        repeat(6) { i -> svc.apply(listOf(BatchItem("1", f.absolutePath, mapOf(TagField.Title to "v$i"))), keepBackups = true) }
        assertEquals(3, dirs.second.listFiles { x -> x.isDirectory }!!.size)
    }

    @Test fun lastBatchSurvivesRestart() = runBlocking {
        val (w, sink, dirs) = setup()
        val f = File(dirs.first, "a.mp3"); w.create(f, mapOf(TagField.Title to "old"))
        TagBatchService(w, dirs.second, sink).apply(listOf(BatchItem("1", f.absolutePath, mapOf(TagField.Title to "new"))), keepBackups = true)
        val again = TagBatchService(w, dirs.second, sink)
        assertEquals(1, again.lastBatch.value!!.entries.size)
        again.undoLast()
        assertEquals("old", w.read(f.absolutePath)!!.tags[TagField.Title])
    }
}

class AutoTaggerTest {
    private fun cand(title: String, artist: String, album: String = "", year: String = "", src: String = "deezer", cover: String? = null) =
        TagCandidate(src, buildMap { put(TagField.Title, title); put(TagField.Artist, artist); if (album.isNotBlank()) put(TagField.Album, album); if (year.isNotBlank()) put(TagField.Year, year) }, coverUrl = cover)

    @Test fun usesFilenameWhenTagsAreMissing() = runBlocking {
        var seen: TagQuery? = null
        val lookup = FakeLookup { q -> seen = q; LookupResult(listOf(cand("Teardrop", "Massive Attack", "Mezzanine", "1998", cover = "http://c"))) }
        val t = TagTrack("1", "/m/Massive Attack/Mezzanine/03 Teardrop.mp3", emptyMap(), null)
        val p = AutoTagger(lookup).propose(t)
        assertEquals("Teardrop", seen!!.title); assertEquals("Massive Attack", seen!!.artist)
        val best = p.best!!
        assertTrue(best.high, "${best.score}")
        assertEquals("Mezzanine", p.edits()[TagField.Album]); assertTrue(p.acceptsCover())
    }

    @Test fun offlineStillProposesFilenameTags() = runBlocking {
        val lookup = FakeLookup { LookupResult(emptyList(), listOf("offline")) }
        val t = TagTrack("1", "/m/Artist - Title.mp3", emptyMap(), null)
        val p = AutoTagger(lookup).propose(t, useNetwork = false)
        assertEquals(0, lookup.calls)
        assertEquals("filename", p.best!!.candidate.source)
        assertEquals(mapOf(TagField.Artist to "Artist", TagField.Title to "Title"), p.edits())
    }

    @Test fun lookupFailureDegradesGracefully() = runBlocking {
        val lookup = object : TagLookup { override suspend fun lookup(q: TagQuery, fingerprint: Fingerprint?): LookupResult = error("network down") }
        val t = TagTrack("1", "/m/Artist - Title.mp3", emptyMap(), null)
        val p = AutoTagger(lookup).propose(t)
        assertEquals("filename", p.best!!.candidate.source)
    }

    @Test fun doesNotOverwriteGoodTagsWithLowConfidenceMatches() = runBlocking {
        val lookup = FakeLookup { LookupResult(listOf(cand("Song", "Artist", "Other Album", "1980"))) }
        val t = TagTrack("1", "/m/x/song.mp3", mapOf(TagField.Title to "Song", TagField.Artist to "Artist", TagField.Album to "My Album"), "/c.jpg")
        val p = AutoTagger(lookup).propose(t)
        assertFalse(TagField.Album in p.edits() && !p.best!!.high)
        assertEquals("1980", p.edits()[TagField.Year])
    }
}
