package com.music.vivi.compose.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateUrlsTest {
    private val good = "https://github.com/vndreiii/visage-music/releases/download/v1.0.0/VIsage-x86_64.AppImage"

    @Test
    fun acceptsOwnReleaseDownloads() {
        assertTrue(UpdateUrls.isTrustedDownload(good))
        assertTrue(UpdateUrls.isTrustedApi(UpdateUrls.RELEASES_API))
    }

    @Test
    fun rejectsLookalikeHostsAndSchemes() {
        val lookalikes = listOf(
            "https://github.com.evil.example/vndreiii/visage-music/releases/download/v1.0.0/a",
            "https://evil.example/github.com/vndreiii/visage-music/releases/download/v1.0.0/a",
            "https://github.com@evil.example/vndreiii/visage-music/releases/download/v1.0.0/a",
            "https://evil.example\\@github.com/vndreiii/visage-music/releases/download/v1.0.0/a",
            "http://github.com/vndreiii/visage-music/releases/download/v1.0.0/a",
            "https://github.com:8443/vndreiii/visage-music/releases/download/v1.0.0/a",
            "https://GITHUB.com.evil.example/vndreiii/visage-music/releases/download/v1.0.0/a",
            "ftp://github.com/vndreiii/visage-music/releases/download/v1.0.0/a",
            "https://github.com/vndreiii/other-repo/releases/download/v1.0.0/a",
            "https://github.com/vndreiii/visage-music/releases/download/v1.0.0/../../../../other/a",
            "https://github.com/vndreiii/visage-music/releases/download/v1.0.0/a?x=1",
            "https://github.com/vndreiii/visage-music/releases/download/v1.0.0/a b",
            "https://objects.githubusercontent.com/vndreiii/visage-music/releases/download/v1.0.0/a",
        )
        for (url in lookalikes) assertFalse(UpdateUrls.isTrustedDownload(url), url)
        assertFalse(UpdateUrls.isTrustedApi("https://api.github.com/repos/vndreiii/other/releases/latest"))
    }

    @Test
    fun redirectsOnlyToGithubHosts() {
        assertTrue(UpdateUrls.isTrustedRedirect("https://objects.githubusercontent.com/x/y"))
        assertTrue(UpdateUrls.isTrustedRedirect("https://release-assets.githubusercontent.com/x/y"))
        assertFalse(UpdateUrls.isTrustedRedirect("https://objects.githubusercontent.com.evil.example/x"))
        assertFalse(UpdateUrls.isTrustedRedirect("http://release-assets.githubusercontent.com/x"))
        assertFalse(UpdateUrls.isTrustedRedirect("https://evil.example/x"))
    }
}
