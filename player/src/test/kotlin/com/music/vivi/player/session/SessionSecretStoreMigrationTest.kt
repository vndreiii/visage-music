package com.music.vivi.player.session

import com.github.javakeyring.PasswordAccessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionSecretStoreMigrationTest {
    /** Behaves like java-keyring: a missing entry throws instead of returning null. */
    private class FakeKeyring(val data: MutableMap<Pair<String, String>, String> = mutableMapOf()) {
        fun get(service: String, account: String): String? =
            data[service to account] ?: throw PasswordAccessException("not found: $service/$account")

        fun put(service: String, account: String, value: String) {
            data[service to account] = value
        }
    }

    private fun read(k: FakeKeyring, account: String = "yt-music-session") =
        SessionSecretStore.readWithLegacyFallback(account, k::get, k::put)

    @Test
    fun readsTheCurrentEntryFirst() {
        val k = FakeKeyring(mutableMapOf(("visage" to "yt-music-session") to "new", ("vivi-desktop" to "yt-music-session") to "old"))
        assertEquals("new", read(k))
    }

    @Test
    fun fallsBackToThePreRenameEntryEvenThoughTheNewOneThrows() {
        val k = FakeKeyring(mutableMapOf(("vivi-desktop" to "yt-music-session") to "old-session"))
        assertEquals("old-session", read(k))
        // Copied forward, and the old entry is left in place for an older build.
        assertEquals("old-session", k.data["visage" to "yt-music-session"])
        assertEquals("old-session", k.data["vivi-desktop" to "yt-music-session"])
    }

    @Test
    fun nothingAnywhereIsJustNull() {
        assertNull(read(FakeKeyring()))
    }
}
