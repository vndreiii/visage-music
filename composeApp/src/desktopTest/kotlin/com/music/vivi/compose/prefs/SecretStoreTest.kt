package com.music.vivi.compose.prefs

import com.music.vivi.compose.ui.player.lyrics.LyricsAiProviders
import com.music.vivi.compose.ui.player.lyrics.lyricsTransformOptions
import com.music.vivi.player.session.SecretBackend
import com.music.vivi.player.session.SecretBackendException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SecretStoreTest {
    private class FakeBackend : SecretBackend {
        override val description = "fake"
        val data = mutableMapOf<Pair<String, String>, String>()
        override fun get(service: String, account: String) = data[service to account]
        override fun put(service: String, account: String, value: String) { data[service to account] = value }
        override fun delete(service: String, account: String) { data.remove(service to account) }
    }

    private val direct = java.util.concurrent.Executor { it.run() }

    private fun store(backend: SecretBackend? = FakeBackend(), now: () -> Long = System::currentTimeMillis) = SecretStore(
        backendFactory = { backend ?: throw SecretBackendException("no keyring service found", permanent = true) },
        executor = direct, log = {}, clock = now,
    )

    private fun prefsIn(dir: File, initial: Map<String, String> = emptyMap()): DesktopPrefs {
        val file = File(dir, "settings.properties")
        if (initial.isNotEmpty()) file.writeText(initial.entries.joinToString("\n") { "${it.key}=${it.value}" })
        return DesktopPrefs(file)
    }

    private fun tmp(): File = Files.createTempDirectory("secrets-test").toFile()

    @Test
    fun setGetDeleteGoThroughTheBackend() {
        val backend = FakeBackend()
        val s = store(backend)
        s.set("lyrics-ai.openai.key", " sk-abc ")
        assertEquals("sk-abc", s.get("lyrics-ai.openai.key"))
        assertEquals("sk-abc", backend.data["visage" to "lyrics-ai.openai.key"])
        assertEquals(SecretStore.Status.Keyring, s.status)
        s.delete("lyrics-ai.openai.key")
        assertEquals("", s.get("lyrics-ai.openai.key"))
        assertNull(backend.data["visage" to "lyrics-ai.openai.key"])
    }

    @Test
    fun blankValueDeletes() {
        val backend = FakeBackend()
        val s = store(backend)
        s.set("a", "x")
        s.set("a", "   ")
        assertFalse(s.has("a"))
        assertTrue(backend.data.isEmpty())
    }

    @Test
    fun loadFillsTheCacheWithoutOverwritingNewerSessionValues() {
        val backend = FakeBackend().apply { data["visage" to "a"] = "old"; data["visage" to "b"] = "stored" }
        val s = store(backend)
        s.set("a", "new")
        assertTrue(s.load(listOf("a", "b", "c")))
        assertEquals("new", s.get("a"))
        assertEquals("stored", s.get("b"))
        assertEquals("", s.get("c"))
    }

    @Test
    fun withoutAKeyringSecretsStayInMemoryOnly() {
        val s = store(backend = null)
        s.set("lyrics-ai.openai.key", "sk-secret")
        assertEquals("sk-secret", s.get("lyrics-ai.openai.key"))
        assertEquals(SecretStore.Status.Unavailable, s.status)
        assertFalse(s.load(listOf("x")))
        assertFalse(s.putDurably("k", "v"))
    }

    @Test
    fun savingAKeyNeverTouchesTheSettingsFile() {
        val dir = tmp()
        val prefs = prefsIn(dir)
        val s = store(backend = null) // worst case: no keyring
        val gemini = LyricsAiProviders.byId("gemini")!!
        LyricsAiProviders.saveKey(prefs, gemini, "AIza-very-secret", s)
        LyricsAiProviders.save(prefs, gemini, "model", "gemini-2.5-flash")
        prefs.flush()
        val text = File(dir, "settings.properties").readText()
        assertFalse("AIza-very-secret" in text)
        assertTrue("gemini-2.5-flash" in text)
        assertEquals("AIza-very-secret", LyricsAiProviders.config(prefs, gemini, s).key)
        prefs.setString(PrefKeys.LyricsTranslateProvider, "gemini")
        assertEquals("AIza-very-secret", prefs.lyricsTransformOptions(s).ai.key)
        assertFailsWith<IllegalArgumentException> { LyricsAiProviders.save(prefs, gemini, "key", "nope") }
    }

    @Test
    fun migrationMovesPlainTextKeysIntoTheStoreAndDeletesThem() {
        val dir = tmp()
        val prefs = prefsIn(
            dir,
            mapOf(
                "lyricsAi.openai.key" to "sk-openai",
                "lyricsAi.groq.key" to "gsk-groq",
                "lyricsAi.openai.model" to "gpt-4o-mini",
                PrefKeys.LyricsGeminiKey to "AIza-legacy",
                PrefKeys.TagAcoustidKey to "acoustid-app",
                "lyricsAi.ollama.key" to "",
            ),
        )
        val backend = FakeBackend()
        val s = store(backend)
        SecretMigration.run(prefs, s)
        assertEquals("sk-openai", backend.data["visage" to "lyrics-ai.openai.key"])
        assertEquals("gsk-groq", backend.data["visage" to "lyrics-ai.groq.key"])
        assertEquals("AIza-legacy", backend.data["visage" to "lyrics-ai.gemini.key"])
        assertEquals("acoustid-app", backend.data["visage" to Secrets.AcoustidKey])
        assertEquals("sk-openai", s.get(Secrets.lyricsAiKey("openai")))
        prefs.flush()
        val text = File(dir, "settings.properties").readText()
        for (secret in listOf("sk-openai", "gsk-groq", "AIza-legacy", "acoustid-app")) assertFalse(secret in text, "$secret still on disk")
        assertTrue("gpt-4o-mini" in text, "non-secret settings stay")
        // And a reload from disk sees the removal.
        assertEquals("", DesktopPrefs(File(dir, "settings.properties")).getString("lyricsAi.openai.key", ""))
        assertEquals("", DesktopPrefs(File(dir, "settings.properties")).getString("lyricsAi.ollama.key", ""))
    }

    @Test
    fun migrationKeepsTheNewerStoredKeyOverTheOldFileValue() {
        val dir = tmp()
        val prefs = prefsIn(dir, mapOf("lyricsAi.openai.key" to "old-from-file"))
        val backend = FakeBackend().apply { data["visage" to "lyrics-ai.openai.key"] = "newer" }
        SecretMigration.run(prefs, store(backend))
        assertEquals("newer", backend.data["visage" to "lyrics-ai.openai.key"])
        assertEquals("", prefs.getString("lyricsAi.openai.key", ""))
    }

    @Test
    fun migrationWithoutAKeyringDoesNotLoseTheKeyAndDoesNotCopyItAnywhereElse() {
        val dir = tmp()
        val prefs = prefsIn(dir, mapOf("lyricsAi.openai.key" to "sk-keep"))
        val s = store(backend = null)
        SecretMigration.run(prefs, s)
        // Still usable this session, and the file value is left for a later start when a keyring exists.
        assertEquals("sk-keep", s.get(Secrets.lyricsAiKey("openai")))
        assertEquals("sk-keep", prefs.getString("lyricsAi.openai.key", ""))
        assertEquals(SecretStore.Status.Unavailable, s.status)
    }

    @Test
    fun prefsRemovalIsPersistedAndSurvivesTheMergeWithTheDiskCopy() {
        val dir = tmp()
        val prefs = prefsIn(dir, mapOf("a" to "1", "b" to "2"))
        prefs.remove("a")
        prefs.setString("c", "3")
        prefs.flush()
        val reloaded = DesktopPrefs(File(dir, "settings.properties"))
        assertEquals("", reloaded.getString("a", ""))
        assertEquals("2", reloaded.getString("b", ""))
        assertEquals("3", reloaded.getString("c", ""))
    }
}
