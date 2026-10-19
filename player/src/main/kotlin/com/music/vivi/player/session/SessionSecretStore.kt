package com.music.vivi.player.session

import com.music.innertube.auth.YtMusicSession
import com.music.innertube.models.AccountInfo
import com.music.vivi.nativelib.AppPaths
import com.github.javakeyring.PasswordAccessException
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the YT Music session + account profile, not as a plaintext file: in the desktop keyring
 * (GNOME Keyring / KWallet via the Secret Service) when one is reachable, otherwise in an encrypted
 * file under the config dir. See [EncryptedFileBackend] for what that fallback does and does not protect.
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

    private val router: SecretStoreRouter by lazy {
        SecretStoreRouter(
            primaryFactory = { SecretServiceBackend.system() },
            file = EncryptedFileBackend.forCurrentUser(File(AppPaths.config(), "session.vault")),
        )
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

    /** Where secrets currently live, for the Diagnostics screen. */
    fun backendDescription(): String = router.backendDescription()

    fun save(session: YtMusicSession, account: AccountInfo) {
        val sessionJson = json.encodeToString(session)
        val accountJson = json.encodeToString(StoredAccount.from(account))
        router.withBackend(
            onKeyring = { keyring ->
                keyring.put(SERVICE, ACCOUNT_SESSION, sessionJson)
                keyring.put(SERVICE, ACCOUNT_PROFILE, accountJson)
            },
            onFile = { file ->
                file.put(SERVICE, ACCOUNT_SESSION, sessionJson)
                file.put(SERVICE, ACCOUNT_PROFILE, accountJson)
            },
        )
    }

    fun loadSession(): YtMusicSession? = readSecret(ACCOUNT_SESSION)?.let { payload ->
        runCatching { json.decodeFromString(YtMusicSession.serializer(), payload) }.getOrNull()
    }

    fun loadAccount(): StoredAccount? = readSecret(ACCOUNT_PROFILE)?.let { payload ->
        runCatching { json.decodeFromString(StoredAccount.serializer(), payload) }.getOrNull()
    }

    fun clear() {
        router.withBackend(
            onKeyring = { keyring ->
                // Both services: otherwise the next read would copy the old entry forward and undo the sign-out.
                for (service in listOf(SERVICE, LEGACY_SERVICE)) {
                    for (account in listOf(ACCOUNT_SESSION, ACCOUNT_PROFILE)) {
                        runCatching { keyring.delete(service, account) }
                            .onFailure { System.err.println("[session] could not delete $service/$account from the keyring: ${it.message}") }
                    }
                }
            },
            onFile = { file -> file.deleteFile() },
        )
        // Also remove the encrypted fallback file, so an unreachable keyring cannot leave a copy that a later fallback read would use.
        runCatching { File(AppPaths.config(), "session.vault").delete() }
    }

    /**
     * Reads the entry, or null when it is missing or unreadable. An unreadable store is logged (not thrown),
     * so the app treats it as signed out instead of crashing; the reason is visible in [backendDescription].
     */
    private fun readSecret(account: String): String? = try {
        router.withBackend(
            onKeyring = { keyring -> readWithLegacyFallback(account, keyring::get, keyring::put) },
            onFile = { file -> file.get(SERVICE, account) },
        )
    } catch (e: SecretBackendException) {
        System.err.println("SessionSecretStore: could not read $account: ${e.message}")
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
        missingAsNull { get(SERVICE, account) }?.let { return it }
        val legacy = missingAsNull { get(LEGACY_SERVICE, account) } ?: return null
        runCatching { put(SERVICE, account, legacy) }
        return legacy
    }

    /** "No such entry" is null (java-keyring throws for it); any other failure propagates as unavailability. */
    private fun missingAsNull(read: () -> String?): String? = try {
        read()
    } catch (_: PasswordAccessException) {
        null
    }
}
