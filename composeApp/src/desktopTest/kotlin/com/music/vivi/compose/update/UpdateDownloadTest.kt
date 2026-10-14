package com.music.vivi.compose.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class UpdateDownloadTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    @Test
    fun matchingChecksumAndSizeAreAccepted() {
        val file = File.createTempFile("visage-test", ".part").apply { writeBytes("payload".toByteArray()); deleteOnExit() }
        verifyFile(file, 7L, sha("payload".toByteArray()))
        assertEquals(sha("payload".toByteArray()), sha256Hex(file))
    }

    @Test
    fun checksumMismatchIsRejected() {
        val file = File.createTempFile("visage-test", ".part").apply { writeBytes("payload".toByteArray()); deleteOnExit() }
        assertFailsWith<IOException> { verifyFile(file, 7L, sha("other".toByteArray())) }
    }

    @Test
    fun wrongSizeIsRejected() {
        val file = File.createTempFile("visage-test", ".part").apply { writeBytes("payload".toByteArray()); deleteOnExit() }
        assertFailsWith<IOException> { verifyFile(file, 8L, sha("payload".toByteArray())) }
    }
}
