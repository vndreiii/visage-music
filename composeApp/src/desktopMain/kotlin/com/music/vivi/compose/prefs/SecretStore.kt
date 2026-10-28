package com.music.vivi.compose.prefs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.vivi.player.session.SecretBackend
import com.music.vivi.player.session.AppSecretBackend
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * App secrets (API keys), never in settings.properties. They live where the YouTube sign-in lives (see
 * [AppSecretBackend]): the operating system's credential store (freedesktop Secret Service / KWallet on Linux,
 * Credential Manager on Windows, large values split by the backend) and, when none is reachable, an encrypted file.
 *
 * - Reads are served from an in-memory cache, so composition and request code never wait on the keyring. The cache is
 *   Compose state ([get] inside a composable recomposes when a value changes) and is filled by [load] on a worker thread.
 * - Writes update the cache first and reach the keyring on a single worker thread, in order.
 * - [status] says where values are kept: [Status.Keyring] or [Status.EncryptedFile]. Only if even that fails
 *   ([Status.Unavailable], e.g. an unwritable config dir) do values stay in memory for this session.
 *
 * Secret values are never logged.
 */
class SecretStore(
    private val backendFactory: () -> SecretBackend = { AppSecretBackend },
    private val service: String = "visage",
    private val executor: Executor = defaultExecutor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryAfterMillis: Long = 30_000,
    private val log: (String) -> Unit = { System.err.println("SecretStore: $it") },
    /** Called after the cache changes without a prefs write (a finished load), so listeners can re-read. */
    private val onChanged: () -> Unit = {},
) {
    enum class Status {
        /** The keyring has not been tried yet. */
        Pending,
        /** Values are saved in the system keyring. */
        Keyring,
        /** No keyring is reachable: values are saved in the encrypted file under the config dir. */
        EncryptedFile,
        /** Nothing could store them: values live in memory until the app closes. */
        Unavailable,
    }

    var status by mutableStateOf(Status.Pending)
        private set

    /** Why the keyring could not be used; no secret content. */
    var unavailableReason by mutableStateOf<String?>(null)
        private set

    private val cache = mutableStateMapOf<String, String>()
    private val lock = Any()
    private var backend: SecretBackend? = null
    private var failedAt: Long? = null

    /** The cached value, "" when there is none. Safe to call from composition and from any thread. */
    fun get(name: String): String = cache[name].orEmpty()

    fun has(name: String): Boolean = get(name).isNotEmpty()

    /** Sets [name] in memory now and in the keyring in the background. A blank value deletes the entry. */
    fun set(name: String, value: String) {
        val v = value.trim()
        if (v.isEmpty()) return delete(name)
        cache[name] = v
        executor.execute { persist(name, v) }
    }

    fun delete(name: String) {
        cache.remove(name)
        executor.execute { persist(name, null) }
    }

    /**
     * Opens the keyring and fills the cache with [names]. Blocking: call from a background thread. Entries already
     * set in this session are not overwritten. Returns whether the keyring was usable.
     */
    fun load(names: Collection<String>): Boolean {
        val b = backendOrNull()
        if (b == null) {
            onChanged()
            return false
        }
        for (name in names) {
            val stored = try {
                b.get(service, name)
            } catch (e: Exception) {
                markFailed(e)
                onChanged()
                return false
            }
            if (!stored.isNullOrEmpty() && name !in cache) cache[name] = stored
        }
        onChanged()
        return true
    }

    /**
     * Stores [value] under [name] in the keyring right now (blocking), for migration, where the caller must know the
     * value is safe before deleting its plain-text copy. Returns false (value only cached) when the keyring is unusable.
     */
    fun putDurably(name: String, value: String): Boolean {
        cache[name] = value
        return persist(name, value)
    }

    /** Whether [name] is saved in the keyring (blocking). */
    fun storedInKeyring(name: String): Boolean {
        val b = backendOrNull() ?: return false
        return try { !b.get(service, name).isNullOrEmpty() } catch (e: Exception) { markFailed(e); false }
    }

    private fun persist(name: String, value: String?): Boolean {
        val b = backendOrNull() ?: return false
        return try {
            if (value == null) b.delete(service, name) else b.put(service, name, value)
            true
        } catch (e: Exception) {
            markFailed(e)
            false
        }
    }

    private fun backendOrNull(): SecretBackend? = synchronized(lock) {
        backend?.let { return it }
        failedAt?.let { if (clock() - it < retryAfterMillis) return null }
        try {
            backendFactory().also {
                backend = it
                unavailableReason = null
                // Fakes in tests carry no router; the real backend reports which store it ended up using.
                status = if (it is AppSecretBackend && !it.inKeyring) Status.EncryptedFile else Status.Keyring
            }
        } catch (e: Exception) {
            markFailed(e)
            null
        }
    }

    private fun markFailed(e: Throwable) = synchronized(lock) {
        backend = null
        failedAt = clock()
        status = Status.Unavailable
        // The message of a keyring exception describes the failure, not the secret; still keep it to one short line.
        unavailableReason = e.message?.lineSequence()?.firstOrNull()?.take(160)
        log("secret store unavailable (${unavailableReason ?: e::class.simpleName}); secrets stay in memory for this session")
    }

    companion object {
        private val defaultExecutor: Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "secret-store").apply { isDaemon = true }
        }
    }
}

/** Names of the secrets the app keeps, and the process-wide store. */
object Secrets {
    const val AcoustidKey = "acoustid.key"
    fun lyricsAiKey(providerId: String) = "lyrics-ai.$providerId.key"

    @Volatile
    private var instance: SecretStore? = null

    /** The app's store. Created on first use; call [start] at launch so the cache is filled before it is needed. */
    val store: SecretStore
        get() = instance ?: synchronized(this) {
            instance ?: SecretStore(onChanged = { notifier() }).also { instance = it }
        }

    @Volatile
    private var notifier: () -> Unit = {}

    /** Migrates plain-text keys and fills the cache, on a background thread. */
    fun start(prefs: DesktopPrefs, providerIds: List<String>) {
        notifier = { prefs.notifyChanged() }
        Thread({
            runCatching {
                SecretMigration.run(prefs, store)
                store.load(providerIds.map(::lyricsAiKey) + AcoustidKey)
            }.onFailure { System.err.println("SecretStore: startup failed (${it::class.simpleName})") }
        }, "secret-store-start").apply { isDaemon = true }.start()
    }
}

/**
 * Moves API keys that earlier builds wrote into settings.properties into the secret store and deletes the plain-text
 * copies. A key is deleted from the file only after the keyring confirmed it; with no keyring the file value stays
 * (so the key is not lost) and is used from memory, and the next start tries again.
 */
object SecretMigration {
    /** Legacy pref key to secret name. */
    private fun legacyEntries(prefs: DesktopPrefs): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        // lyricsAi.<id>.key, then the very first Gemini-only key (the per-provider entry wins if both exist).
        for (k in prefs.keysWithPrefix("lyricsAi.").filter { it.endsWith(".key") }) {
            val id = k.removePrefix("lyricsAi.").removeSuffix(".key")
            if (id.isNotEmpty()) out += k to Secrets.lyricsAiKey(id)
        }
        out += PrefKeys.LyricsGeminiKey to Secrets.lyricsAiKey("gemini")
        out += PrefKeys.TagAcoustidKey to Secrets.AcoustidKey
        return out
    }

    fun run(prefs: DesktopPrefs, store: SecretStore) {
        for ((prefKey, name) in legacyEntries(prefs)) {
            val legacy = prefs.getString(prefKey, "").trim()
            if (legacy.isEmpty() && prefs.getString(prefKey, "x") == "x") continue // absent
            if (legacy.isEmpty()) {
                prefs.remove(prefKey)
                continue
            }
            // A key already in the store (set via the new UI, or migrated from a sibling entry) is newer than the file's.
            val safe = store.storedInKeyring(name) || store.putDurably(name, legacy)
            if (safe) prefs.remove(prefKey)
        }
    }
}
