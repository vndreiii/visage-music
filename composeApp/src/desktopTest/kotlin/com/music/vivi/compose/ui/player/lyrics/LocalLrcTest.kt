package com.music.vivi.compose.ui.player.lyrics

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalLrcTest {
    private val dir: File = Files.createTempDirectory("lrc").toFile().also { it.deleteOnExit() }
    private fun file(name: String, text: String = "[00:01.00]hi") = File(dir, name).also { it.parentFile.mkdirs(); it.writeText(text) }

    @Test
    fun exactThenCaseThenLooseNames() {
        val audio = file("01 Song.flac")
        file("Other.lrc")
        assertNull(LocalLrc.find(audio, null, "Song Title"))
        val loose = file("1 - Song.LRC")
        assertEquals(loose, LocalLrc.find(audio, null, null))
        val exact = file("01 Song.lrc")
        assertEquals(exact, LocalLrc.find(audio, null, null))
    }

    @Test
    fun artistDashTitleAndLyricsFolderAndStoredPath() {
        val audio = file("track07.mp3")
        val byTitle = file("Some Artist - Magnetic.lrc")
        assertEquals(byTitle, LocalLrc.find(audio, null, "Magnetic"))

        val audio2 = file("sub/a.flac")
        val inFolder = file("sub/Lyrics/a.lrc")
        assertEquals(inFolder, LocalLrc.find(audio2, null, null))

        val stored = file("elsewhere/x.lrc")
        assertEquals(stored, LocalLrc.find(audio2, stored.absolutePath, null))
    }

    @Test
    fun readsBomAndLegacyEncodings() {
        val bom = File(dir, "bom.lrc").apply { writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01.00]añb".toByteArray()) }
        assertEquals("[00:01.00]añb", LocalLrc.read(bom))
        val legacy = File(dir, "old.lrc").apply { writeBytes("[00:01.00]ca".toByteArray() + byteArrayOf(0xE9.toByte())) }
        assertEquals("[00:01.00]caé", LocalLrc.read(legacy))
    }
}
