package com.music.vivi.player.session

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Chooses between the desktop keyring and the encrypted file, and keeps keyring calls from hanging the app.
 *
 * - Every keyring call runs on a daemon thread and is abandoned after [timeoutMillis] (a locked wallet or a
 *   hung D-Bus must not freeze startup). The abandoned call may still finish later; its result is discarded.
 * - A failed keyring is remembered for [retryAfterMillis] (or forever, when the service doesn't exist), so
 *   repeated UI reads don't re-activate the daemon or spam the log.
 * - While the keyring is up, any entries left in the file by an earlier outage are moved into it, and the
 *   file is deleted. File entries win over keyring entries: they were written later, during the outage.
 */
internal class SecretStoreRouter(
    private val primaryFactory: () -> SecretBackend,
    val file: EncryptedFileBackend,
    private val timeoutMillis: Long = 5_000,
    private val retryAfterMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { System.err.println("SessionSecretStore: $it") },
) {
    private val lock = Any()
    private var keyring: SecretBackend? = null
    private var downUntil = 0L
    private var lastKeyringError: String? = null

    /**
     * Runs [keyring] against the keyring when it is usable, otherwise [file] against the encrypted file.
     * A keyring failure during [keyring] falls through to [file] as well. Exceptions from [file] propagate.
     */
    fun <T> withBackend(onKeyring: (SecretBackend) -> T, onFile: (EncryptedFileBackend) -> T): T {
        synchronized(lock) {
            val backend = activeKeyring()
            if (backend != null) {
                try {
                    return boundedCall { migrateFileInto(backend); onKeyring(backend) }
                } catch (e: SecretBackendException) {
                    markDown(e)
                }
            }
        }
        return onFile(file)
    }

    /** What the Diagnostics screen should show. May block up to [timeoutMillis] if the keyring has not been probed yet. */
    fun backendDescription(): String = synchronized(lock) {
        activeKeyring()?.let { return it.description }
        buildString {
            append("Encrypted file (no keyring available)")
            lastKeyringError?.let { append(" - $it") }
            if (file.lastError != null) append("; file unreadable")
        }
    }

    private fun activeKeyring(): SecretBackend? {
        keyring?.let { return it }
        if (clock() < downUntil) return null
        return try {
            boundedCall(primaryFactory).also {
                keyring = it
                lastKeyringError = null
            }
        } catch (e: SecretBackendException) {
            markDown(e)
            null
        }
    }

    private fun markDown(e: SecretBackendException) {
        keyring = null
        downUntil = if (e.permanent) Long.MAX_VALUE else clock() + retryAfterMillis
        lastKeyringError = e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "unknown error"
        log("keyring unavailable (${lastKeyringError}); using the encrypted file" +
            if (e.permanent) "" else ", retrying in ${retryAfterMillis / 1000}s")
    }

    private fun migrateFileInto(backend: SecretBackend) {
        // A corrupt file is left alone here; its error is reported through file.lastError.
        val entries = try { file.readAll() } catch (_: SecretBackendException) { return }
        if (entries.isEmpty()) return
        for ((key, value) in entries) backend.put(key.first, key.second, value)
        file.deleteFile()
        log("moved ${entries.size} entries from the encrypted file into the keyring")
    }

    private fun <T> boundedCall(block: () -> T): T {
        val future: Future<T> = executor.submit(Callable { block() })
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            throw SecretBackendException("keyring did not answer within ${timeoutMillis / 1000}s (locked or hung?)")
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            throw cause as? SecretBackendException ?: SecretBackendException(cause.message ?: cause::class.simpleName ?: "error", cause)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SecretBackendException("interrupted while waiting for the keyring")
        }
    }

    private companion object {
        // Cached and daemon: a hung call must not block the next attempt, and must not keep the JVM alive.
        val executor: ExecutorService = Executors.newCachedThreadPool { r ->
            Thread(r, "visage-keyring").apply { isDaemon = true }
        }
    }
}
