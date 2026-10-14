package com.music.vivi.compose.update

import com.music.vivi.compose.prefs.DesktopPrefs
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.PublicKey
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** A newer signed release that this build can use. */
data class UpdateOffer(
    val version: SemVer,
    val tag: String,
    val notes: String,
    val pubDate: String,
    /** Set when this install can replace its AppImage and the release has a build for this arch. */
    val appImage: ManifestAsset?,
    val appImageName: String?,
)

enum class UpdatePhase { Disabled, Idle, Checking, Downloading, Restarting }

enum class UpdateAction { InstallAndRestart, OpenReleasePage }

data class UpdateStatus(
    val phase: UpdatePhase,
    /** "0.1.0", or "development build". */
    val currentVersion: String,
    /** Why updates are off, or null when they are on. */
    val disabledReason: String?,
    val offer: UpdateOffer?,
    /** Whether the banner should be shown now (not dismissed, not snoozed). */
    val promptVisible: Boolean,
    val progress: Float,
    val message: String?,
    val autoCheck: Boolean,
    val lastCheckedAt: Long,
)

/** Thrown for a release that is present but must not be trusted; shown to the user. */
class UpdateRejected(message: String) : IllegalStateException(message)

/**
 * Checks GitHub Releases for a signed latest.json, shows the offer, and installs AppImage updates.
 * Get the shared instance with [start], which the UI composables also use.
 */
class UpdateService internal constructor(
    private val choices: UpdateChoices,
    private val current: SemVer?,
    val installMode: InstallMode,
    private val publicKey: PublicKey?,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onRestart: () -> Unit,
) {
    private val work = Mutex()

    private val disabledReason: String? = when {
        current == null -> "Updates are off in development builds."
        installMode == InstallMode.FlatpakManaged -> "Flatpak installs are updated by flatpak."
        publicKey == null -> "This build has no update signing key, so updates are off."
        else -> null
    }

    private val _status = MutableStateFlow(
        UpdateStatus(
            phase = if (disabledReason == null) UpdatePhase.Idle else UpdatePhase.Disabled,
            currentVersion = current?.toString() ?: "development build",
            disabledReason = disabledReason,
            offer = null,
            promptVisible = false,
            progress = 0f,
            message = null,
            autoCheck = choices.autoCheck(),
            lastCheckedAt = choices.lastChecked(),
        ),
    )
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    internal fun startScheduler() {
        if (disabledReason != null) return
        scope.launch {
            delay(STARTUP_DELAY_MS)
            while (isActive) {
                if (choices.autoCheck()) runCheck(manual = false)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun primaryAction(offer: UpdateOffer): UpdateAction =
        if (installMode is InstallMode.AppImage && offer.appImage != null) UpdateAction.InstallAndRestart
        else UpdateAction.OpenReleasePage

    fun checkNow() {
        if (disabledReason != null) return
        scope.launch { runCheck(manual = true) }
    }

    fun setAutoCheck(enabled: Boolean) {
        choices.setAutoCheck(enabled)
        _status.update { it.copy(autoCheck = enabled) }
    }

    fun remindLater() {
        val offer = _status.value.offer ?: return
        choices.remindLater(offer.version.toString(), now())
        _status.update { it.copy(promptVisible = false) }
    }

    fun dismiss() {
        val offer = _status.value.offer ?: return
        choices.dismiss(offer.version.toString())
        _status.update { it.copy(promptVisible = false) }
    }

    /** Runs the primary action for the current offer: install the AppImage, or open the release page. */
    fun install() {
        val offer = _status.value.offer ?: return
        when (primaryAction(offer)) {
            UpdateAction.OpenReleasePage -> openReleasePage()
            UpdateAction.InstallAndRestart -> {
                val mode = installMode as? InstallMode.AppImage ?: return
                scope.launch { installAppImage(offer, mode) }
            }
        }
    }

    fun openReleasePage() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val uri = URI(UpdateUrls.RELEASE_PAGE)
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri)
                } else {
                    ProcessBuilder("xdg-open", uri.toString()).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                }
            }.onFailure { System.err.println("[update] could not open release page: ${it.message}") }
        }
    }

    private suspend fun runCheck(manual: Boolean) {
        if (!work.tryLock()) return
        try {
            _status.update {
                it.copy(phase = UpdatePhase.Checking, message = if (manual) "Checking for updates…" else null)
            }
            val offer = withContext(Dispatchers.IO) { fetchOffer() }
            val at = now()
            choices.setLastChecked(at)
            if (offer == null) {
                _status.update {
                    it.copy(
                        phase = UpdatePhase.Idle, offer = null, promptVisible = false, lastCheckedAt = at,
                        message = if (manual) "You're on the latest version." else null,
                    )
                }
            } else {
                val delay = choices.promptDelay(offer.version.toString(), at)
                _status.update {
                    it.copy(
                        phase = UpdatePhase.Idle, offer = offer, promptVisible = delay == 0L, lastCheckedAt = at,
                        message = if (manual) "VIsage ${offer.version} is available." else null,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateRejected) {
            System.err.println("[update] ${e.message}")
            _status.update { it.copy(phase = UpdatePhase.Idle, promptVisible = false, message = e.message) }
        } catch (e: Exception) {
            System.err.println("[update] check failed: ${e.message}")
            _status.update {
                it.copy(
                    phase = UpdatePhase.Idle,
                    message = if (manual) "Couldn't check for updates. Try again when you're online." else null,
                )
            }
        } finally {
            work.unlock()
        }
    }

    /** Returns the newer release, or null when there is none or the release server has none. */
    private fun fetchOffer(): UpdateOffer? {
        val key = publicKey ?: throw UpdateRejected("This build has no update signing key.")
        val installed = current ?: return null
        val body = UpdateHttp.readBounded(
            start = UpdateUrls.RELEASES_API,
            accept = "application/vnd.github+json",
            maxBytes = UpdateLimits.MAX_METADATA_BYTES,
            trustedStart = UpdateUrls::isTrustedApi,
            allowMissing = true,
        ) ?: return null
        val release = GitHubRelease.parse(body)
        if (release.draft || release.prerelease) return null
        val version = SemVer.parseOrNull(release.tag) ?: return null
        if (version <= installed) return null

        val manifestUrl = release.trustedAssetUrl(UpdateAssets.MANIFEST) ?: return null
        val signatureUrl = release.trustedAssetUrl(UpdateAssets.SIGNATURE) ?: return null
        val manifestBytes = fetchTrusted(manifestUrl, UpdateLimits.MAX_METADATA_BYTES, release.tag, UpdateAssets.MANIFEST)
        val signature = fetchTrusted(signatureUrl, UpdateLimits.MAX_SIGNATURE_BYTES, release.tag, UpdateAssets.SIGNATURE)
            .toString(Charsets.UTF_8)
        if (!UpdateSignature.verify(manifestBytes, signature, key)) {
            throw UpdateRejected("Release ${release.tag} failed signature verification and was ignored.")
        }
        val manifest = UpdateManifest.parse(manifestBytes, release.tag)
        val arch = UpdateAssets.appImageName(System.getProperty("os.arch").orEmpty())
        return UpdateOffer(
            version = manifest.version,
            tag = release.tag,
            notes = manifest.notes,
            pubDate = manifest.pubDate,
            appImage = arch?.let { manifest.assets[it] },
            appImageName = arch,
        )
    }

    private fun fetchTrusted(url: String, maxBytes: Int, tag: String, name: String): ByteArray =
        UpdateHttp.readBounded(
            start = url,
            accept = "application/octet-stream",
            maxBytes = maxBytes,
            trustedStart = { it == UpdateUrls.expectedAssetUrl(tag, name) },
        ) ?: throw UpdateRejected("Release $tag is missing $name")

    private suspend fun installAppImage(offer: UpdateOffer, mode: InstallMode.AppImage) {
        val asset = offer.appImage ?: return
        val name = offer.appImageName ?: return
        if (!work.tryLock()) return
        var partial: File? = null
        try {
            _status.update { it.copy(phase = UpdatePhase.Downloading, progress = 0f, message = null) }
            withContext(Dispatchers.IO) {
                // Same directory as the AppImage, so the final rename is atomic.
                val part = File.createTempFile(".VIsage-update-", ".part", mode.file.parentFile)
                partial = part
                UpdateHttp.download(asset, offer.tag, name, part) { progress ->
                    _status.update { it.copy(progress = progress) }
                }
                check(part.length() == asset.size)
                part.setReadable(true, false)
                part.setExecutable(true, false)
                Files.move(part.toPath(), mode.file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                partial = null
            }
            _status.update { it.copy(phase = UpdatePhase.Restarting, message = "Restarting VIsage…") }
            // The new file is in place. Start it once this process has exited, so two copies never overlap.
            if (!spawnRelaunchAfterExit(mode.file)) {
                _status.update { it.copy(phase = UpdatePhase.Idle, message = "Update installed. Restart VIsage to finish.") }
                return
            }
            onRestart()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("[update] install failed: ${e.message}")
            _status.update {
                it.copy(phase = UpdatePhase.Idle, progress = 0f, message = "Couldn't install the update. Try again.")
            }
        } finally {
            partial?.delete()
            work.unlock()
        }
    }

    private fun spawnRelaunchAfterExit(file: File): Boolean = runCatching {
        val pid = ProcessHandle.current().pid().toString()
        val builder = ProcessBuilder(
            "sh", "-c",
            "while kill -0 \"\$1\" 2>/dev/null; do sleep 0.2; done; exec \"\$2\"",
            "visage-relaunch", pid, file.absolutePath,
        )
        // Let the new AppImage set up its own mount rather than inheriting the old one.
        builder.environment().keys.removeIf { it == "APPIMAGE" || it == "APPDIR" || it == "ARGV0" || it == "OWD" }
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        builder.redirectError(ProcessBuilder.Redirect.DISCARD)
        builder.start()
        true
    }.getOrElse {
        System.err.println("[update] could not schedule restart: ${it.message}")
        false
    }

    companion object {
        const val DEV_VERSION = "0.0.0-dev"
        const val STARTUP_DELAY_MS = 15_000L
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        private val lock = Any()
        @Volatile private var instance: UpdateService? = null

        /** Creates the shared service once and starts its schedule. Safe to call repeatedly. */
        fun start(
            prefs: DesktopPrefs,
            onRestart: () -> Unit = { exitProcess(0) },
        ): UpdateService = synchronized(lock) {
            instance ?: create(prefs, onRestart).also {
                instance = it
                it.startScheduler()
            }
        }

        private fun create(prefs: DesktopPrefs, onRestart: () -> Unit): UpdateService {
            val raw = System.getProperty("visage.version")?.trim().orEmpty()
            val current = if (raw.isEmpty() || raw == DEV_VERSION) null else SemVer.parseOrNull(raw)
            val store = object : UpdateKeyValues {
                override fun getString(key: String): String? = prefs.getString(key, "").takeIf { it.isNotEmpty() }
                override fun setString(key: String, value: String) = prefs.setString(key, value)
            }
            return UpdateService(
                choices = UpdateChoices(store),
                current = current,
                installMode = InstallMode.detect(System.getenv()),
                publicKey = UpdateSignature.embeddedPublicKey(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                now = System::currentTimeMillis,
                onRestart = onRestart,
            )
        }
    }
}
