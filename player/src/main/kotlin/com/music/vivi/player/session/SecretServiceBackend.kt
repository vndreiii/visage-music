package com.music.vivi.player.session

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException
import java.util.concurrent.TimeUnit

/** The desktop keyring (GNOME Keyring / KWallet via the freedesktop Secret Service), through java-keyring. */
class SecretServiceBackend private constructor(
    private val keyring: Keyring,
    override val description: String,
) : SecretBackend {

    override fun get(service: String, account: String): String? = try {
        keyring.getPassword(service, account)
    } catch (_: PasswordAccessException) {
        // java-keyring reports a missing entry this way (and some denied accesses; both mean "nothing usable").
        null
    } catch (e: Exception) {
        throw SecretBackendException("keyring read failed: ${describe(e)}", e)
    }

    override fun put(service: String, account: String, value: String) {
        try {
            keyring.setPassword(service, account, value)
        } catch (e: Exception) {
            throw SecretBackendException("keyring write failed: ${describe(e)}", e)
        }
    }

    override fun delete(service: String, account: String) {
        try {
            keyring.deletePassword(service, account)
        } catch (_: PasswordAccessException) {
            // Already absent.
        } catch (e: Exception) {
            throw SecretBackendException("keyring delete failed: ${describe(e)}", e)
        }
    }

    companion object {
        /**
         * Opens the system keyring. Blocking (D-Bus); callers should go through [SecretStoreRouter],
         * which bounds it with a timeout. Throws [SecretBackendException].
         */
        fun system(): SecretBackend = try {
            open()
        } catch (e: BackendNotSupportedException) {
            throw SecretBackendException("no keyring service found: ${e.message}", e, permanent = true)
        } catch (e: SecretBackendException) {
            throw e
        } catch (e: Exception) {
            throw SecretBackendException("could not open keyring: ${describe(e)}", e)
        }

        private fun open(): SecretBackend {
            if (!System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
                return SecretServiceBackend(Keyring.create(), "System keyring")
            }
            return try {
                SecretServiceBackend(Keyring.create(KeyringStorageType.GNOME_KEYRING), "Secret Service (GNOME Keyring)")
            } catch (secretServiceUnavailable: BackendNotSupportedException) {
                // KWallet only when it is already running: starting an unused wallet daemon is a side effect.
                if (walletAlreadyRunning()) {
                    SecretServiceBackend(Keyring.create(KeyringStorageType.KWALLET), "KWallet")
                } else {
                    throw secretServiceUnavailable
                }
            }
        }

        private fun describe(e: Throwable): String {
            val base = e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e::class.simpleName ?: "unknown error"
            // Flatpak apps can't reach the Secret Service unless the manifest grants it.
            return if (System.getenv("FLATPAK_ID") != null && e !is PasswordAccessException) {
                "$base (Flatpak: grant --talk-name=org.freedesktop.secrets)"
            } else base
        }

        private fun walletAlreadyRunning(): Boolean = listOf("org.kde.kwalletd6", "org.kde.kwalletd5").any { name ->
            runCatching {
                val process = ProcessBuilder(
                    "busctl", "--user", "--timeout=1", "call",
                    "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                    "NameHasOwner", "s", name,
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0 && process.inputStream.bufferedReader().use { it.readText().trim() } == "b true"
                }
            }.getOrDefault(false)
        }
    }
}
