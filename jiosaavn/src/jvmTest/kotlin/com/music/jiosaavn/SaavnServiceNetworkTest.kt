package com.music.jiosaavn

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

class SaavnServiceNetworkTest {
    @Test
    @Tag("network")
    fun searchSongs_returnsResults() = runTest {
        val songs = SaavnService.searchSongs("Never Gonna Give You Up Rick Astley").getOrThrow()
        assertTrue(songs.isNotEmpty())
        val first = songs.first()
        assertTrue(first.name.isNotBlank())
        // download URLs should decrypt for non-pro tracks
        val nonPro = songs.firstOrNull { !it.isProOnly && it.downloadUrl.isNotEmpty() }
        if (nonPro != null) {
            val url = SaavnService.selectBestUrl(nonPro.downloadUrl, "320kbps")
            assertTrue(!url.isNullOrBlank(), "expected stream url for ${nonPro.name}")
        }
    }
}
