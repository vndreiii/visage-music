package com.music.vivi.compose.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemVerTest {
    private fun v(s: String) = SemVer.parse(s)

    @Test
    fun ordersCoreVersions() {
        assertTrue(v("0.2.0") > v("0.1.9"))
        assertTrue(v("1.0.0") > v("0.99.99"))
        assertTrue(v("0.1.10") > v("0.1.9"))
        assertEquals(0, v("v0.1.0").compareTo(v("0.1.0")))
    }

    @Test
    fun preReleaseSortsBeforeItsRelease() {
        assertTrue(v("1.0.0") > v("1.0.0-rc.2"))
        assertTrue(v("1.0.0-rc.2") > v("1.0.0-rc.1"))
        assertTrue(v("1.0.0-beta") > v("1.0.0-alpha"))
    }

    @Test
    fun followsSemverPreReleaseRules() {
        // Spec example: 1.0.0-alpha < alpha.1 < alpha.beta < beta < beta.2 < beta.11 < rc.1 < 1.0.0
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
            "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        for (i in 0 until ordered.size - 1) assertTrue(v(ordered[i]) < v(ordered[i + 1]), "${ordered[i]} < ${ordered[i + 1]}")
    }

    @Test
    fun buildMetadataIsIgnoredForPrecedence() {
        assertEquals(0, v("1.2.3+build.9").compareTo(v("1.2.3")))
    }

    @Test
    fun rejectsMalformedVersions() {
        assertNull(SemVer.parseOrNull("1.2"))
        assertNull(SemVer.parseOrNull("01.2.3"))
        assertNull(SemVer.parseOrNull("1.2.3-"))
        assertNull(SemVer.parseOrNull("0.0.0-dev.x y"))
        assertNull(SemVer.parseOrNull(""))
    }
}
