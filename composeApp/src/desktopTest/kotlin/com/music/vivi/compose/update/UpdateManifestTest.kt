package com.music.vivi.compose.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateManifestTest {
    private val sha = "a".repeat(64)

    private fun manifest(version: String = "1.2.3", url: String = UpdateUrls.expectedAssetUrl("v1.2.3", "VIsage-x86_64.AppImage"), sha256: String = sha, size: String = "1024") = """
        {"version":"$version","notes":"fixes","pub_date":"2026-10-14T00:00:00Z",
         "assets":{"VIsage-x86_64.AppImage":{"url":"$url","sha256":"$sha256","size":$size}}}
    """.trimIndent().toByteArray()

    @Test
    fun parsesValidManifest() {
        val m = UpdateManifest.parse(manifest(), "v1.2.3")
        assertEquals("1.2.3", m.version.toString())
        assertEquals(1024L, m.assets.getValue("VIsage-x86_64.AppImage").size)
    }

    @Test
    fun versionMustMatchReleaseTag() {
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(), "v1.2.4") }
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(version = "1.2.3"), "1.2.3") }
    }

    @Test
    fun assetMustBeTheExactReleaseLocation() {
        val lookalike = "https://github.com.evil.example/vndreiii/visage-music/releases/download/v1.2.3/VIsage-x86_64.AppImage"
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(url = lookalike), "v1.2.3") }
        val otherTag = UpdateUrls.expectedAssetUrl("v9.9.9", "VIsage-x86_64.AppImage")
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(url = otherTag), "v1.2.3") }
    }

    @Test
    fun rejectsBadChecksumsSizesAndNames() {
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(sha256 = "ABC"), "v1.2.3") }
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(sha256 = "A".repeat(64)), "v1.2.3") }
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(size = "0"), "v1.2.3") }
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse(manifest(size = "9999999999999"), "v1.2.3") }
    }
}
