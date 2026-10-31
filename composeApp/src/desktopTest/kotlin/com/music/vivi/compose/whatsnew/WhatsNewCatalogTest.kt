package com.music.vivi.compose.whatsnew

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsNewCatalogTest {
    private val v130 get() = WhatsNewCatalog.releases.first { it.version == "1.3.0" }

    @Test
    fun slugsAreUniqueAndNonBlank() {
        val slugs = v130.features.map { it.slug }
        assertTrue(slugs.all { it.isNotBlank() })
        assertEquals(slugs.size, slugs.toSet().size)
    }

    @Test
    fun everyFeatureExplainsItself() {
        v130.features.forEach {
            assertTrue(it.title.isNotBlank() && it.summary.isNotBlank() && it.howTo.isNotBlank(), it.slug)
        }
    }

    @Test
    fun responsiveAndPopoutAreInTheTour() {
        val slugs = v130.features.map { it.slug }
        assertTrue("responsive-layouts" in slugs)
        assertTrue("popout-player" in slugs)
    }

    @Test
    fun noWaylandClaims() {
        // Native Wayland is not part of this release; do not advertise it.
        val all = v130.features.flatMap { listOf(it.summary, it.howTo) } + v130.changes.map { it.text }
        assertTrue(all.none { it.contains("native wayland", ignoreCase = true) })
    }
}
