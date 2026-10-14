package com.music.vivi.player.login

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DataSyncIdTest {
    @Test
    fun `delegation uses brand identity rather than user identity`() {
        assertEquals("brand", normalizeDataSyncId("brand||user"))
        assertEquals("", normalizeDataSyncId("||user"))
        assertEquals("brand", normalizeDataSyncId("brand||"))
        assertEquals("brand", normalizeDataSyncId("brand"))
        assertEquals("", normalizeDataSyncId(null))
    }
}
