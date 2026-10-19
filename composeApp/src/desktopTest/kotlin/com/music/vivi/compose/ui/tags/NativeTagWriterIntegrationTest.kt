package com.music.vivi.compose.ui.tags

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end through JNA and lofty on a generated WAV in a temp folder (never user data).
 * Skipped when the native library isn't built/located (set VISAGE_NATIVE_LIB).
 */
class NativeTagWriterIntegrationTest {
    private fun wav(f: File) {
        val data = ByteArray(16_000)
        val bb = java.nio.ByteBuffer.allocate(44 + data.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(data.size).put(data)
        f.writeBytes(bb.array())
    }

    @Test fun writeReadBackupUndoThroughNative() = runBlocking {
        val writer = NativeTagWriter { Files.createTempDirectory("covers").toFile() }
        if (!writer.available) { println("native lib not available; skipping"); return@runBlocking }
        val dir = Files.createTempDirectory("tagit").toFile()
        val f = File(dir, "a.wav").also(::wav)
        val original = f.readBytes()
        val svc = TagBatchService(writer, File(dir, "bk"), RecordingSink())
        val r = svc.apply(listOf(BatchItem("1", f.absolutePath, mapOf(TagField.Title to "Hello", TagField.Artist to "Me", TagField.Year to "2020"))), keepBackups = true)
        assertTrue(r.results.single().ok, r.results.single().error)
        val read = writer.read(f.absolutePath)!!
        assertEquals("Hello", read.tags[TagField.Title]); assertEquals("2020", read.tags[TagField.Year])
        assertTrue(svc.undoLast().succeeded == 1)
        assertTrue(original.contentEquals(f.readBytes()))
        val bad = writer.write(WriteSpec(File(dir, "missing.wav").absolutePath, mapOf(TagField.Title to "x")))
        assertTrue(!bad.ok)
    }
}
