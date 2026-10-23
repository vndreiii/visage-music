package com.music.vivi.player.session

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkedSecretsTest {
    /** A keyring that, like Windows Credential Manager, rejects any entry over the limit. */
    private class LimitedStore(val limit: Int) {
        val entries = HashMap<String, String>()
        val secrets = ChunkedSecrets(
            900,
            rawGet = { entries[it] },
            rawPut = { account, v -> if (v.length > limit) error("1783 bad data") else entries[account] = v },
            rawDelete = { entries.remove(it) },
        )
    }

    @Test
    fun aSessionFarOverTheLimitRoundTrips() {
        val store = LimitedStore(1280)
        val session = "{\"cookie\":\"" + "SID=abcdef; ".repeat(600) + "\"}"
        store.secrets.put("session", session)
        assertTrue(store.entries.values.all { it.length <= 1280 })
        assertEquals(session, store.secrets.get("session"))
    }

    @Test
    fun shortValuesAreStoredAsIsAndOldEntriesStillRead() {
        val store = LimitedStore(1280)
        store.secrets.put("profile", "{\"name\":\"x\"}")
        assertEquals(mapOf("profile" to "{\"name\":\"x\"}"), store.entries)
        assertNull(store.secrets.get("missing"))
    }

    @Test
    fun shrinkingAndDeletingLeaveNoOrphanPieces() {
        val store = LimitedStore(1280)
        store.secrets.put("s", "x".repeat(3000))
        store.secrets.put("s", "small")
        assertEquals(mapOf("s" to "small"), store.entries)
        store.secrets.put("s", "y".repeat(2000))
        store.secrets.delete("s")
        assertTrue(store.entries.isEmpty(), store.entries.keys.toString())
    }

    @Test
    fun aDamagedChunkedEntryReadsAsMissing() {
        val store = LimitedStore(1280)
        store.secrets.put("s", "z".repeat(2500))
        store.entries.remove("s#1")
        assertNull(store.secrets.get("s"))
    }
}
