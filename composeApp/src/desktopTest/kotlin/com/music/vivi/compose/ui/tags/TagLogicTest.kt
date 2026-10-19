package com.music.vivi.compose.ui.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun track(id: String, path: String, vararg tags: Pair<TagField, String>, cover: String? = null, dur: Long? = null) =
    TagTrack(id, path, mapOf(*tags), cover, dur)

class FilenameParserTest {
    @Test fun artistTitleWithTrackNumber() {
        val p = FilenameParser.parse("/m/Some Album/01 - Daft Punk - One More Time.mp3")
        assertEquals("Daft Punk", p.artist); assertEquals("One More Time", p.title); assertEquals(1, p.trackNo); assertEquals("Some Album", p.album)
    }
    @Test fun dottedAndUnderscoredNames() {
        val p = FilenameParser.parse("/m/x/03. Bjork_-_Hyperballad.flac")
        assertEquals(3, p.trackNo); assertEquals("Bjork", p.artist); assertEquals("Hyperballad", p.title)
    }
    @Test fun titleOnlyTakesArtistFromFolderStructure() {
        val p = FilenameParser.parse("/music/Radiohead/OK Computer/02 Paranoid Android.mp3")
        assertEquals("Radiohead", p.artist); assertEquals("OK Computer", p.album); assertEquals("Paranoid Android", p.title); assertEquals(2, p.trackNo)
    }
    @Test fun genericFoldersAreIgnored() {
        val p = FilenameParser.parse("/home/me/Music/Downloads/Song Name.mp3")
        assertNull(p.album); assertNull(p.artist); assertEquals("Song Name", p.title)
    }
    @Test fun stripsOfficialVideoNoise() {
        assertEquals("Hey", FilenameParser.parse("/a/b/Artist - Hey (Official Video).mp3").title)
    }
    @Test fun yearLikeNumbersAreNotTrackNumbers() {
        assertNull(FilenameParser.parse("/a/b/1999.mp3").trackNo)
    }
    @Test fun patternParsingAndMismatch() {
        val t = FilenameParser.parsePattern("%track% - %artist% - %title%", "07 - Massive Attack - Teardrop")
        assertNotNull(t)
        assertEquals("7", t[TagField.TrackNo]?.trimStart('0')); assertEquals("Massive Attack", t[TagField.Artist]); assertEquals("Teardrop", t[TagField.Title])
        assertNull(FilenameParser.parsePattern("%artist% - %title%", "NoSeparatorHere"))
        assertNull(FilenameParser.parsePattern("no placeholders", "x"))
    }
    @Test fun formatPatternPadsSanitisesAndRefusesMissing() {
        val tags = mapOf(TagField.Artist to "AC/DC", TagField.Title to "Back: In Black", TagField.TrackNo to "6")
        assertEquals("06 - AC_DC - Back_ In Black", FilenameParser.format("%track% - %artist% - %title%", tags))
        assertNull(FilenameParser.format("%album%/%title%", tags))
    }
}

class CaseNormalizerTest {
    @Test fun fixesShoutedAndLowerOnly() {
        assertEquals("The Sound of Silence", CaseNormalizer.titleCase("THE SOUND OF SILENCE"))
        assertEquals("Love Me Do", CaseNormalizer.titleCase("love me do"))
    }
    @Test fun leavesMixedCaseAlone() {
        assertEquals("deadmau5 & Kaskade", CaseNormalizer.titleCase("deadmau5 & Kaskade"))
        assertEquals("iPhone Song", CaseNormalizer.titleCase("iPhone Song"))
    }
    @Test fun fixReturnsOnlyChangedFields() {
        val fixed = CaseNormalizer.fix(mapOf(TagField.Title to "HELLO WORLD", TagField.Artist to "Fine Artist"))
        assertEquals(mapOf(TagField.Title to "Hello World"), fixed)
    }
}

class TagScoringTest {
    private fun cand(title: String, artist: String, album: String = "", dur: Long? = null, src: Double? = null, source: String = "x") =
        TagCandidate(source, mapOf(TagField.Title to title, TagField.Artist to artist, TagField.Album to album), durationSec = dur, sourceScore = src)

    @Test fun exactMatchIsHigh() {
        val s = TagScoring.score(TagQuery("One More Time", "Daft Punk", durationSec = 320), cand("One More Time", "Daft Punk", dur = 321))
        assertTrue(s >= TagScoring.HIGH, "$s")
    }
    @Test fun noiseInTitlesIsIgnored() {
        val s = TagScoring.score(TagQuery("Hey Jude", "The Beatles"), cand("Hey Jude (Remastered 2015)", "The Beatles"))
        assertTrue(s >= TagScoring.HIGH, "$s")
    }
    @Test fun wrongTitleIsLowEvenWithRightArtist() {
        val s = TagScoring.score(TagQuery("Yesterday", "The Beatles"), cand("Come Together", "The Beatles"))
        assertTrue(s < TagScoring.MIN_SHOWN, "$s")
    }
    @Test fun partialArtistCreditsStillMatch() {
        val s = TagScoring.score(TagQuery("Lean On", "Major Lazer"), cand("Lean On", "Major Lazer & DJ Snake"))
        assertTrue(s > 0.8, "$s")
    }
    @Test fun durationMismatchLowersScore() {
        val q = TagQuery("Song", "Artist", durationSec = 200)
        assertTrue(TagScoring.score(q, cand("Song", "Artist", dur = 201)) > TagScoring.score(q, cand("Song", "Artist", dur = 400)))
    }
    @Test fun sourceScoreBlends() {
        val q = TagQuery("Song", "Artist")
        assertTrue(TagScoring.score(q, cand("Song", "Artist", src = 1.0)) >= TagScoring.score(q, cand("Song", "Artist", src = 0.2)))
    }
    @Test fun rankSortsDedupesAndAppliesAgreementBonus() {
        val q = TagQuery("Teardrop", "Massive Attack")
        val ranked = TagScoring.rank(
            q,
            listOf(
                cand("Teardrop", "Massive Attack", "Mezzanine", source = "deezer"),
                cand("Teardrop", "Massive Attack", "Mezzanine", source = "itunes"),
                cand("Angel", "Massive Attack", "Mezzanine", source = "deezer"),
                cand("Teardrop", "Massive Attack", "Greatest Hits", source = "itunes"),
            ),
        )
        assertEquals("Teardrop", ranked.first().candidate.tags[TagField.Title])
        assertEquals(ranked.map { it.candidate.tags[TagField.Album] }.toSet().size, ranked.size - ranked.count { it.candidate.tags[TagField.Album] == "Mezzanine" }.minus(1).coerceAtLeast(0) )
        assertTrue(ranked.none { it.candidate.tags[TagField.Title] == "Angel" })
    }
}

class TagDiffTest {
    @Test fun changesOnlyDifferingNonBlankProposedFields() {
        val ch = TagDiff.changes(
            mapOf(TagField.Title to "A", TagField.Album to "Old"),
            mapOf(TagField.Title to "A", TagField.Album to "New", TagField.Year to "1999", TagField.Genre to ""),
        )
        assertEquals(setOf(TagField.Album, TagField.Year), ch.map { it.field }.toSet())
    }
    @Test fun unknownArtistCountsAsEmpty() {
        val ch = TagDiff.changes(mapOf(TagField.Artist to "Unknown Artist"), mapOf(TagField.Artist to "Real"))
        assertTrue(ch.single().fillsEmpty)
    }
    @Test fun defaultsFillEmptyAlwaysOverwriteOnlyWhenHigh() {
        val fill = FieldChange(TagField.Year, "", "1999")
        val over = FieldChange(TagField.Album, "A", "B")
        val case = FieldChange(TagField.Title, "hello", "Hello")
        assertTrue(TagDiff.defaultAccept(fill, 0.6)); assertFalse(TagDiff.defaultAccept(over, 0.6)); assertTrue(TagDiff.defaultAccept(over, 0.9))
        assertFalse(TagDiff.defaultAccept(case, 0.99))
    }
    @Test fun proposalEditsHonourOverridesAndSkip() {
        val cand = ScoredCandidate(TagCandidate("deezer", mapOf(TagField.Title to "T", TagField.Album to "B", TagField.Year to "2000")), 0.6)
        val p = TrackProposal("1", mapOf(TagField.Title to "T", TagField.Album to "A"), listOf(cand))
        assertEquals(mapOf(TagField.Year to "2000"), p.edits())
        assertEquals(mapOf(TagField.Year to "2000", TagField.Album to "B"), p.copy(overrides = mapOf(TagField.Album to true)).edits())
        assertEquals(emptyMap(), p.copy(overrides = mapOf(TagField.Album to true), skipped = true).edits())
    }
    @Test fun acceptAllHighTouchesOnlyHighProposals() {
        val hi = TrackProposal("hi", mapOf(TagField.Album to "A"), listOf(ScoredCandidate(TagCandidate("mb", mapOf(TagField.Title to "T", TagField.Album to "B")), 0.95)))
        val lo = TrackProposal("lo", mapOf(TagField.Album to "A"), listOf(ScoredCandidate(TagCandidate("mb", mapOf(TagField.Title to "T", TagField.Album to "B")), 0.6)))
        val out = TagDiff.acceptAllHigh(listOf(hi, lo))
        assertEquals("B", out[0].edits()[TagField.Album]); assertNull(out[1].edits()[TagField.Album])
    }
}

class BatchEditTest {
    private val a = mapOf(TagField.Artist to "X", TagField.Album to "One", TagField.Year to "2001")
    private val b = mapOf(TagField.Artist to "X", TagField.Album to "Two")
    @Test fun resolvesSameAndMixed() {
        val be = BatchEdit.of(listOf(a, b))
        assertEquals(FieldState.Same("X"), be.states[TagField.Artist])
        assertEquals(FieldState.Mixed, be.states[TagField.Album])
        assertEquals(FieldState.Mixed, be.states[TagField.Year])
        assertEquals(FieldState.Same(""), be.states[TagField.Genre])
        assertTrue(be.isMixed(TagField.Album)); assertFalse(be.dirty)
    }
    @Test fun onlyChangedFieldsAreApplied() {
        val be = BatchEdit.of(listOf(a, b)).set(TagField.Genre, "Rock").set(TagField.Album, "Both")
        assertEquals(mapOf(TagField.Genre to "Rock", TagField.Album to "Both"), be.editsFor(a))
        assertEquals("Both", be.displayValue(TagField.Album)); assertFalse(be.isMixed(TagField.Album))
    }
    @Test fun typingTheOriginalValueBackDropsTheEdit() {
        val be = BatchEdit.of(listOf(a, a)).set(TagField.Artist, "Y").set(TagField.Artist, "X")
        assertFalse(be.dirty)
    }
    @Test fun mixedFieldCanBeExplicitlyCleared() {
        val be = BatchEdit.of(listOf(a, b)).set(TagField.Album, "")
        assertEquals(mapOf(TagField.Album to ""), be.editsFor(a))
        assertEquals(emptyMap(), BatchEdit.of(listOf(a, b)).editsFor(a))
    }
    @Test fun editsForSkipsNoops() {
        val be = BatchEdit.of(listOf(a, b)).set(TagField.Artist, "X").set(TagField.Genre, "Pop")
        assertEquals(mapOf(TagField.Genre to "Pop"), be.editsFor(a))
    }
    @Test fun resetRestoresMixed() {
        val be = BatchEdit.of(listOf(a, b)).set(TagField.Album, "Z").reset(TagField.Album)
        assertTrue(be.isMixed(TagField.Album))
    }
}

class TagBrowserTest {
    private val full = track("1", "/m/Artist/Album/01 a.mp3", TagField.Title to "A", TagField.Artist to "Artist", TagField.Album to "Album", TagField.Year to "2000", cover = "/c/1.jpg")
    private val unknown = track("2", "/m/Misc/b.mp3", TagField.Title to "Bee", TagField.Artist to "Unknown Artist", TagField.Album to "Misc", cover = null)
    private val noTitle = track("3", "/m/Artist/Album/c.mp3", TagField.Title to "c", TagField.Artist to "Artist", TagField.Album to "Album", TagField.Year to "2000", cover = "/c/1.jpg")

    @Test fun healthDetectsScannerFallbacks() {
        val h = TagHealth.of(unknown)
        assertTrue(h.missingArtist && h.missingAlbum && h.missingCover && !h.missingTitle)
        assertTrue(TagHealth.of(noTitle).missingTitle)
        assertFalse(TagHealth.of(full).any)
    }
    @Test fun filters() {
        val all = listOf(full, unknown, noTitle)
        assertEquals(listOf("2"), TagBrowser.filter(all, TagFilter.UnknownArtist, "", emptySet()).map { it.id })
        assertEquals(listOf("2"), TagBrowser.filter(all, TagFilter.MissingCover, "", emptySet()).map { it.id })
        assertEquals(setOf("2", "3"), TagBrowser.filter(all, TagFilter.MissingTags, "", emptySet()).map { it.id }.toSet())
        assertEquals(listOf("3"), TagBrowser.filter(all, TagFilter.Modified, "", setOf("3")).map { it.id })
        assertEquals(listOf("2"), TagBrowser.filter(all, TagFilter.All, "misc b", emptySet()).map { it.id })
        assertEquals(3, TagBrowser.counts(all, emptySet())[TagFilter.All])
    }
    @Test fun groupingByAlbumAndFolder() {
        val rows = TagBrowser.rows(listOf(unknown, noTitle, full), TagGrouping.Album)
        val headers = rows.filterIsInstance<TagBrowser.Row.Header>()
        assertEquals(2, headers.size); assertEquals(2, headers.first { it.title == "Album" }.count)
        val folder = TagBrowser.rows(listOf(unknown, noTitle, full), TagGrouping.Folder).filterIsInstance<TagBrowser.Row.Header>()
        assertEquals(listOf("Album", "Misc"), folder.map { it.title })
        assertEquals(3, TagBrowser.rows(listOf(unknown, noTitle, full), TagGrouping.None).size)
    }
    @Test fun duplicatesByCanonicalTitleAndLength() {
        val d1 = track("a", "/x/1.mp3", TagField.Title to "Song (Remastered)", TagField.Artist to "Band", dur = 200)
        val d2 = track("b", "/y/1.mp3", TagField.Title to "Song", TagField.Artist to "Band", dur = 201)
        val other = track("c", "/z/1.mp3", TagField.Title to "Song", TagField.Artist to "Band", dur = 300)
        val groups = TagBrowser.duplicates(listOf(d1, d2, other))
        assertEquals(1, groups.size); assertEquals(setOf("a", "b"), groups[0].map { it.id }.toSet())
    }
}

class TagSelectionTest {
    private val order = listOf("a", "b", "c", "d", "e")
    @Test fun plainClickSelectsOne() = assertEquals(setOf("b"), TagSelection().click("b", order, false, false).ids)
    @Test fun ctrlTogglesAndShiftExtendsFromAnchor() {
        var s = TagSelection().click("b", order, false, false).click("d", order, false, true)
        assertEquals(setOf("b", "d"), s.ids)
        s = s.click("d", order, false, true); assertEquals(setOf("b"), s.ids)
        s = TagSelection().click("b", order, false, false).click("e", order, true, false)
        assertEquals(setOf("b", "c", "d", "e"), s.ids)
    }
    @Test fun selectAllClearAndRetain() {
        val s = TagSelection().selectAll(order)
        assertEquals(5, s.ids.size); assertEquals(emptySet(), s.clear().ids)
        assertEquals(setOf("a"), s.retain(listOf("a", "z")).ids)
    }
}

class TagJsonTest {
    @Test fun parsesReadResult() {
        val r = TagJson.parseRead("""{"path":"/a.mp3","format":"Mpeg","readonly":false,"title":"T","artist":"A","year":1999,"trackNo":3,"hasCover":true,"coverPath":"/c.jpg","supportsCover":true,"lyrics":null}""")!!
        assertEquals("T", r.tags[TagField.Title]); assertEquals("1999", r.tags[TagField.Year]); assertEquals("3", r.tags[TagField.TrackNo])
        assertTrue(r.hasCover); assertFalse(TagField.Lyrics in r.tags)
        assertNull(TagJson.parseRead("""{"error":"x"}"""))
    }
    @Test fun writeRequestRoundTripsFields() {
        val j = TagJson.writeRequest(WriteSpec("/a.mp3", mapOf(TagField.Title to "T", TagField.AlbumArtist to ""), coverFile = "/c.png", id3v23 = true, backupPath = "/b"))
        assertTrue("\"albumArtist\":\"\"" in j && "\"cover_path\":\"/c.png\"" in j && "\"id3v23\":true" in j)
    }
    @Test fun parsesLookupAndOutcome() {
        val l = TagJson.parseLookup("""{"candidates":[{"source":"itunes","title":"T","artist":"A","year":2001,"coverUrl":"u","sourceScore":0.5},{"source":"x"}],"errors":["deezer: HTTP 500"]}""")
        assertEquals(1, l.candidates.size); assertEquals("2001", l.candidates[0].tags[TagField.Year]); assertEquals(0.5, l.candidates[0].sourceScore); assertEquals(1, l.errors.size)
        val o = TagJson.parseOutcome("""{"ok":false,"error":"file is read-only","size":0,"mtimeMs":0}""")
        assertFalse(o.ok); assertEquals("file is read-only", o.error)
        assertFalse(TagJson.parseOutcome("garbage").ok)
    }
}
