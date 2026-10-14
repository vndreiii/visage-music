package com.music.vivi.player.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Fallback store for when no keyring is reachable: one AES-256-GCM encrypted file, mode 0600.
 *
 * Honest threat model: the key is derived (HKDF-SHA256, per-file random salt) from `/etc/machine-id`
 * and the user name. Anyone who can read this file *and* the machine id, as the same user or as root,
 * can decrypt it. That is obfuscation-grade protection against copying the file off the disk or out
 * of a backup, not a substitute for a real keyring. The file exists so that sign-in still works on
 * minimal setups without a Secret Service; it is replaced by the keyring as soon as one is available.
 *
 * File layout: magic "VSK1" | version (1) | salt (16) | iv (12) | GCM ciphertext+tag. The header is
 * bound as AAD, so tampering with any byte fails authentication.
 */
class EncryptedFileBackend(
    private val file: File,
    private val keyMaterial: ByteArray,
    private val random: SecureRandom = SecureRandom(),
) : SecretBackend {

    override val description: String = "Encrypted file (no keyring available)"

    /** Why the last file operation failed (e.g. the file was tampered with). Null when it is healthy. */
    @Volatile
    var lastError: String? = null
        private set

    private val lock = Any()

    override fun get(service: String, account: String): String? = synchronized(lock) {
        readAll()[service to account]
    }

    override fun put(service: String, account: String, value: String) = synchronized(lock) {
        // An unreadable vault is replaced rather than blocking every future save; its error stays visible via lastError until then.
        val current = try { readAll() } catch (_: SecretBackendException) { emptyMap() }
        write(current + ((service to account) to value))
    }

    override fun delete(service: String, account: String) = synchronized(lock) {
        val current = try { readAll() } catch (_: SecretBackendException) { return@synchronized }
        if (current.containsKey(service to account)) write(current - (service to account))
    }

    /** All entries, empty when there is no file. Throws [SecretBackendException] if the file fails authentication. */
    fun readAll(): Map<Pair<String, String>, String> = synchronized(lock) {
        if (!file.exists()) {
            lastError = null
            return@synchronized emptyMap()
        }
        try {
            val vault = json.decodeFromString(Vault.serializer(), String(decrypt(file.readBytes()), Charsets.UTF_8))
            lastError = null
            vault.entries.associate { (it.service to it.account) to it.value }
        } catch (e: SecretBackendException) {
            lastError = e.message
            throw e
        } catch (e: Exception) {
            lastError = "unreadable vault: ${e::class.simpleName}"
            throw SecretBackendException(lastError!!, e)
        }
    }

    fun deleteFile() = synchronized(lock) {
        Files.deleteIfExists(file.toPath())
        lastError = null
    }

    private fun write(entries: Map<Pair<String, String>, String>) {
        if (entries.isEmpty()) {
            deleteFile()
            return
        }
        val vault = Vault(entries = entries.map { (key, value) -> Entry(key.first, key.second, value) })
        val bytes = encrypt(json.encodeToString(Vault.serializer(), vault).toByteArray(Charsets.UTF_8))
        val dir = file.absoluteFile.parentFile
        dir.mkdirs()
        // Create the temp file already 0600, so the secret never exists with wider permissions.
        val owner = PosixFilePermissions.asFileAttribute(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        val tmp = try {
            Files.createTempFile(dir.toPath(), ".session-vault", ".tmp", owner)
        } catch (_: UnsupportedOperationException) {
            Files.createTempFile(dir.toPath(), ".session-vault", ".tmp")
        }
        try {
            Files.write(tmp, bytes)
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
        // Tighten a file that was created by something else with wider permissions.
        runCatching { Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
        lastError = null
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(salt), "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + iv + cipher.doFinal(plain)
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        val headerSize = MAGIC.size + 1 + SALT_BYTES
        if (bytes.size < headerSize + IV_BYTES + TAG_BYTES || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || bytes[MAGIC.size] != VERSION) {
            throw SecretBackendException("encrypted store has an unknown format")
        }
        val header = bytes.copyOfRange(0, headerSize)
        val salt = bytes.copyOfRange(headerSize - SALT_BYTES, headerSize)
        val iv = bytes.copyOfRange(headerSize, headerSize + IV_BYTES)
        val body = bytes.copyOfRange(headerSize + IV_BYTES, bytes.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(salt), "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            throw SecretBackendException("encrypted store failed authentication (modified, or written by another user or machine)", e)
        }
    }

    private fun deriveKey(salt: ByteArray): ByteArray = hkdfSha256(keyMaterial, salt, INFO, 32)

    @Serializable
    private data class Entry(val service: String, val account: String, val value: String)

    @Serializable
    private data class Vault(val version: Int = 1, val entries: List<Entry> = emptyList())

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val MAGIC = "VSK1".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val TAG_BITS = 128
        private val INFO = "visage session vault v1".toByteArray(Charsets.US_ASCII)

        /** Store bound to this user on this machine (see the class comment for what that does and does not protect). */
        fun forCurrentUser(file: File): EncryptedFileBackend = EncryptedFileBackend(file, currentUserKeyMaterial())

        internal fun currentUserKeyMaterial(): ByteArray {
            val machineId = listOf("/etc/machine-id", "/var/lib/dbus/machine-id")
                .firstNotNullOfOrNull { path -> runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } }
                ?: "no-machine-id"
            return "visage|$machineId|${System.getProperty("user.name")}|${System.getProperty("user.home")}".toByteArray(Charsets.UTF_8)
        }

        /** RFC 5869 HKDF with HMAC-SHA256 (the JDK only gained a KDF API after Java 21). */
        internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var previous = ByteArray(0)
            var pos = 0
            var counter = 1
            while (pos < length) {
                mac.update(previous)
                mac.update(info)
                mac.update(counter.toByte())
                previous = mac.doFinal()
                val n = minOf(previous.size, length - pos)
                previous.copyInto(out, pos, 0, n)
                pos += n
                counter++
            }
            return out
        }
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray = this.copyOf(this.size + other.size).also {
    other.copyInto(it, this.size)
}
