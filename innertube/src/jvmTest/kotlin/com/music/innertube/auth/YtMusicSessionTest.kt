package com.music.innertube.auth

import com.music.innertube.YouTube
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtMusicSessionTest {

    @Test
    fun applySession_setsYouTubeFields() {
        val session = YtMusicSession(cookie = "a=b; SAPISID=x", visitorData = "Cgt...", dataSyncId = "sync")
        session.applyToYouTube()
        assertEquals(session.cookie, YouTube.cookie)
        assertEquals(session.visitorData, YouTube.visitorData)
        assertEquals(session.dataSyncId, YouTube.dataSyncId)
    }

    @Test
    fun saveAndLoadSessionFromFile_roundTrips(@TempDir tempDir: Path) {
        val path = tempDir.resolve("session.json")
        val session = YtMusicSession(
            cookie = "SID=abc; SAPISID=xyz",
            visitorData = "CgtVisitor",
            dataSyncId = "sync-id",
        )
        saveSessionToFile(path, session)

        val loaded = loadSessionFromFile(path)
        assertEquals(session, loaded)
        assertTrue(Files.exists(path))
        val json = Files.readString(path)
        assertTrue(json.contains("\"cookie\""))
        assertTrue(json.contains("\"visitorData\""))
        assertTrue(json.contains("\"dataSyncId\""))
    }

    @Test
    fun loadSessionFromFile_defaultsEmptyDataSyncId(@TempDir tempDir: Path) {
        val path = tempDir.resolve("partial.json")
        Files.writeString(
            path,
            """{"cookie":"c=1","visitorData":"vd"}""",
        )
        val loaded = loadSessionFromFile(path)
        assertEquals("c=1", loaded.cookie)
        assertEquals("vd", loaded.visitorData)
        assertEquals("", loaded.dataSyncId)
    }

    @Test
    fun loadSessionFromEnv_readsVariables() {
        val env = mapOf(
            "VIVI_YT_COOKIE" to "env-cookie",
            "VIVI_YT_VISITOR_DATA" to "env-visitor",
            "VIVI_YT_DATASYNC_ID" to "env-sync",
        )
        val session = loadSessionFromEnv(env::get)
        assertNotNull(session)
        assertEquals("env-cookie", session.cookie)
        assertEquals("env-visitor", session.visitorData)
        assertEquals("env-sync", session.dataSyncId)
    }

    @Test
    fun loadSessionFromEnv_returnsNullWhenCookieMissing() {
        val env = mapOf("VIVI_YT_VISITOR_DATA" to "env-visitor")
        assertNull(loadSessionFromEnv(env::get))
    }

    @Test
    fun loadSessionFromEnv_returnsNullWhenVisitorDataMissing() {
        val env = mapOf("VIVI_YT_COOKIE" to "env-cookie")
        assertNull(loadSessionFromEnv(env::get))
    }

    @Test
    fun loadSessionFromEnv_defaultsEmptyDataSyncId() {
        val env = mapOf(
            "VIVI_YT_COOKIE" to "env-cookie",
            "VIVI_YT_VISITOR_DATA" to "env-visitor",
        )
        val session = loadSessionFromEnv(env::get)
        assertNotNull(session)
        assertEquals("", session.dataSyncId)
    }

    @Test
    @Tag("network")
    fun validate_withLiveSession_returnsAccountInfo() = runTest {
        val session = loadSessionFromEnv()
            ?: loadSessionFromFileIfPresent(Path.of(System.getProperty("user.home"), ".config", "vivi", "yt-session.json"))
            ?: return@runTest // skip when no credentials configured

        val result = session.validate()
        assertTrue(result.isSuccess, "validate failed: ${result.exceptionOrNull()}")
        assertNotNull(result.getOrNull()?.name)
    }

    private fun loadSessionFromFileIfPresent(path: Path): YtMusicSession? =
        if (Files.isRegularFile(path)) loadSessionFromFile(path) else null
}
