package com.music.vivi.player.session

import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.BackendNotSupportedException
import java.util.concurrent.TimeUnit
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import com.music.innertube.auth.YtMusicSession
import com.music.innertube.models.AccountInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists YT Music session + account profile in the Freedesktop Secret Service
 * (GNOME Keyring / KWallet via libsecret), not as a plaintext file.
 */
object SessionSecretStore {
    private const val SERVICE = "visage"
    /** Keyring service used before the rename; entries are copied forward on first read. */
    private const val LEGACY_SERVICE = "vivi-desktop"
    private const val ACCOUNT_SESSION = "yt-music-session"
    private const val ACCOUNT_PROFILE = "yt-music-account"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class StoredAccount(
        val name: String,
        val email: String? = null,
        val channelHandle: String? = null,
        val thumbnailUrl: String? = null,
    ) {
        companion object {
            fun from(info: AccountInfo) = StoredAccount(
                name = info.name,
                email = info.email,
                channelHandle = info.channelHandle,
                thumbnailUrl = info.thumbnailUrl,
            )
        }

        fun toAccountInfo() = AccountInfo(
            name = name,
            email = email,
            channelHandle = channelHandle,
            thumbnailUrl = thumbnailUrl,
        )
    }

    fun save(session: YtMusicSession, account: AccountInfo) {
        withKeyring { keyring ->
            keyring.setPassword(SERVICE, ACCOUNT_SESSION, json.encodeToString(session))
            keyring.setPassword(
                SERVICE,
                ACCOUNT_PROFILE,
                json.encodeToString(StoredAccount.from(account)),
            )
        }
    }

    fun loadSession(): YtMusicSession? = readSecret(ACCOUNT_SESSION)?.let { payload ->
        runCatching { json.decodeFromString(YtMusicSession.serializer(), payload) }.getOrNull()
    }

    fun loadAccount(): StoredAccount? = readSecret(ACCOUNT_PROFILE)?.let { payload ->
        runCatching { json.decodeFromString(StoredAccount.serializer(), payload) }.getOrNull()
    }

    fun clear() {
        withKeyring { keyring ->
            // Both services: otherwise the next read would copy the old entry forward and undo the sign-out.
            for (service in listOf(SERVICE, LEGACY_SERVICE)) {
                runCatching { keyring.deletePassword(service, ACCOUNT_SESSION) }
                runCatching { keyring.deletePassword(service, ACCOUNT_PROFILE) }
            }
        }
    }

    private fun readSecret(account: String): String? = try {
        withKeyring { keyring ->
            readWithLegacyFallback(
                account = account,
                get = { service, acct -> keyring.getPassword(service, acct) },
                put = { service, acct, value -> keyring.setPassword(service, acct, value) },
            )
        }
    } catch (_: PasswordAccessException) {
        null
    } catch (_: BackendNotSupportedException) {
        null
    }

    /**
     * Reads from the current service, then from the pre-rename one (copying the value forward so an
     * older build on the same keyring keeps working). `Keyring.getPassword` *throws* when an entry is
     * missing rather than returning null, so each lookup is guarded on its own.
     */
    internal fun readWithLegacyFallback(
        account: String,
        get: (service: String, account: String) -> String?,
        put: (service: String, account: String, value: String) -> Unit,
    ): String? {
        runCatching { get(SERVICE, account) }.getOrNull()?.let { return it }
        val legacy = runCatching { get(LEGACY_SERVICE, account) }.getOrNull() ?: return null
        runCatching { put(SERVICE, account, legacy) }
        return legacy
    }

    private val keyringLock = Any()
    // Cache success or failure: repeated UI reads must not keep activating wallet daemons.
    private val keyringResult by lazy {
        runCatching {
            if (!System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
                Keyring.create()
            } else {
                try {
                    Keyring.create(KeyringStorageType.GNOME_KEYRING)
                } catch (secretServiceUnavailable: BackendNotSupportedException) {
                    // Legacy KDE support without starting an unused KWallet on other desktops.
                    if (walletAlreadyRunning()) Keyring.create(KeyringStorageType.KWALLET)
                    else throw secretServiceUnavailable
                }
            }
        }
    }

    private fun walletAlreadyRunning(): Boolean = listOf("org.kde.kwalletd6", "org.kde.kwalletd5").any { name ->
        runCatching {
            val process = ProcessBuilder("busctl", "--user", "--timeout=1", "call",
                "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                "NameHasOwner", "s", name).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0 && process.inputStream.bufferedReader().use { it.readText().trim() } == "b true"
        }.getOrDefault(false)
    }

    private fun <T> withKeyring(block: (Keyring) -> T): T = synchronized(keyringLock) {
        block(keyringResult.getOrThrow())
    }
}
