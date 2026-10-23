package com.music.vivi.player.login

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualCookieImportTest {
    @Test
    fun `parses header with prefix`() {
        assertEquals("A=1; B=2", ManualCookieImport.parseCookieHeader("Cookie: A=1; B=2"))
        assertNull(ManualCookieImport.parseCookieHeader("   "))
    }

    @Test
    fun `parses netscape export`() {
        val txt = "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSAPISID\tabc\n#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t0\tSID\txyz\n.example.com\tTRUE\t/\tTRUE\t0\tX\ty"
        assertEquals("SAPISID=abc; SID=xyz", ManualCookieImport.parseCookieHeader(txt))
    }
}
