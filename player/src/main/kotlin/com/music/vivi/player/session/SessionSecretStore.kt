package com.music.vivi.player.session

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
            keyring.getPassword(SERVICE, account) ?: keyring.getPassword(LEGACY_SERVICE, account)?.also { legacy ->
                // Copy (don't move) so an older build on the same keyring keeps working.
                runCatching { keyring.setPassword(SERVICE, account, legacy) }
            }
        }
    } catch (_: PasswordAccessException) {
        null
    }

    private inline fun <T> withKeyring(block: (Keyring) -> T): T {
        Keyring.create().use { keyring ->
            return block(keyring)
        }
    }
}
