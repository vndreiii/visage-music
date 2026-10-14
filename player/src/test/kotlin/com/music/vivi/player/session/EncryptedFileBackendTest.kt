package com.music.vivi.player.session

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedFileBackendTest {
    private val dir = createTempDirectory("vsk-test").toFile()
    private val file = File(dir, "session.vault")
    private val key = "machine|user".toByteArray()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun roundTripsAndDeletesTheFileWhenEmpty() {
        val store = EncryptedFileBackend(file, key)
        assertNull(store.get("visage", "yt-music-session"))
        store.put("visage", "yt-music-session", "{\"cookie\":\"secret-cookie\"}")
        store.put("visage", "yt-music-account", "{\"name\":\"Alex\"}")
        assertEquals("{\"cookie\":\"secret-cookie\"}", EncryptedFileBackend(file, key).get("visage", "yt-music-session"))
        assertEquals("{\"name\":\"Alex\"}", store.get("visage", "yt-music-account"))

        val raw = file.readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("secret-cookie"), "plaintext must not appear on disk")

        store.delete("visage", "yt-music-session")
        store.delete("visage", "yt-music-account")
        assertFalse(file.exists(), "an empty vault is removed")
    }

    @Test
    fun fileIsOwnerOnly() {
        EncryptedFileBackend(file, key).put("visage", "a", "b")
        val perms = Files.getPosixFilePermissions(file.toPath())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
    }

    @Test
    fun anyModifiedByteFailsAuthentication() {
        EncryptedFileBackend(file, key).put("visage", "a", "value")
        val original = file.readBytes()
        // Header (magic, version, salt), IV, ciphertext, and the auth tag at the end.
        for (index in listOf(0, 5, 10, 25, original.size - 40, original.size - 1)) {
            val tampered = original.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            file.writeBytes(tampered)
            val store = EncryptedFileBackend(file, key)
            assertFailsWith<SecretBackendException>("byte $index flipped") { store.get("visage", "a") }
            assertTrue(store.lastError != null)
        }
    }

    @Test
    fun anotherMachineOrUserCannotDecrypt() {
        EncryptedFileBackend(file, key).put("visage", "a", "value")
        assertFailsWith<SecretBackendException> { EncryptedFileBackend(file, "other-machine".toByteArray()).get("visage", "a") }
    }

    @Test
    fun putReplacesAnUnreadableVaultInsteadOfFailingForever() {
        file.writeText("garbage")
        val store = EncryptedFileBackend(file, key)
        store.put("visage", "a", "fresh")
        assertNull(store.lastError)
        assertEquals("fresh", store.get("visage", "a"))
    }
}
