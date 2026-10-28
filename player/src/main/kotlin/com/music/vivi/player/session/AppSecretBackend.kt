package com.music.vivi.player.session

import com.music.vivi.nativelib.AppPaths
import java.io.File

/**
 * Secrets for app features other than the YouTube sign-in (API keys), kept the same way as the session: in the desktop
 * keyring when one is reachable, otherwise in an encrypted file ([EncryptedFileBackend]) under the config dir, with the
 * same router (timeouts, retry, moving file entries into the keyring once it is back). Its own vault file, so signing
 * out of YouTube never touches these.
 */
object AppSecretBackend : SecretBackend {
    private val router: SecretStoreRouter by lazy {
        SecretStoreRouter(
            primaryFactory = { SecretServiceBackend.system() },
            file = EncryptedFileBackend.forCurrentUser(File(AppPaths.config(), "app-secrets.vault")),
        )
    }

    /** Where secrets currently live ("Encrypted file (no keyring available)" or the keyring's name). May block briefly. */
    override val description: String get() = router.backendDescription()

    /** True while the entries are in the real keyring (not the encrypted-file fallback). May block briefly. */
    val inKeyring: Boolean get() = !description.startsWith("Encrypted file")

    /**
     * A delete made while only the encrypted file is reachable is stored as this marker, not removed: when the keyring
     * is back the router moves file entries into it, and the marker overwrites the real secret there instead of
     * leaving a revoked key behind. [get] reads the marker as "no entry".
     */
    private const val DELETED = "\u0000deleted"

    override fun get(service: String, account: String): String? =
        router.withBackend(onKeyring = { it.get(service, account) }, onFile = { it.get(service, account) })
            ?.takeIf { it != DELETED }

    override fun put(service: String, account: String, value: String) =
        router.withBackend(onKeyring = { it.put(service, account, value) }, onFile = { it.put(service, account, value) })

    override fun delete(service: String, account: String) =
        router.withBackend(onKeyring = { it.delete(service, account) }, onFile = { it.put(service, account, DELETED) })
}
