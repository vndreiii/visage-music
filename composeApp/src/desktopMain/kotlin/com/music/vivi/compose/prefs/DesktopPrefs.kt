package com.music.vivi.compose.prefs

import java.io.File
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Desktop prefs using the same string keys as upstream [PreferenceKeys]
 * so cherry-picks stay easy. Stored at ~/.config/vivi-desktop/settings.properties
 */
class DesktopPrefs(
    private val file: File = defaultFile(),
) {
    private val props = Properties()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    init {
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun getString(key: String, default: String): String =
        props.getProperty(key) ?: default

    fun setString(key: String, value: String) {
        props.setProperty(key, value)
        persist()
        listeners.forEach { it() }
    }

    fun getBoolean(key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    fun setBoolean(key: String, value: Boolean) {
        setString(key, value.toString())
    }

    fun getInt(key: String, default: Int): Int =
        props.getProperty(key)?.toIntOrNull() ?: default

    fun setInt(key: String, value: Int) {
        setString(key, value.toString())
    }

    fun getFloat(key: String, default: Float): Float =
        props.getProperty(key)?.toFloatOrNull() ?: default

    fun setFloat(key: String, value: Float) {
        setString(key, value.toString())
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "vivi-desktop settings") }
    }

    companion object {
        fun defaultFile(): File {
            val home = System.getenv("XDG_CONFIG_HOME")
                ?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.config")
            return File(home, "vivi-desktop/settings.properties")
        }
    }
}

/** Key names mirrored from upstream PreferenceKeys. */
object PrefKeys {
    const val DarkMode = "darkMode"
    const val PureBlack = "pureBlack"
    const val PureBlackMiniPlayer = "pureBlackMiniPlayer"
    const val DynamicTheme = "dynamicTheme"
    const val MatugenTheme = "matugenTheme"
    const val PaletteStyle = "paletteStyle"
    const val ContrastLevel = "contrastLevel"
    const val SelectedThemeColor = "selectedThemeColor"
    const val UseNewPlayerDesign = "useNewPlayerDesign"
    const val UsePlayerV2 = "usePlayerV2"
    const val UseNewMiniPlayerDesign = "useNewMiniPlayerDesign"
    const val UseAppleMiniPlayer = "useAppleMiniPlayer"
    /** Canonical mini architecture: FULL_WIDTH_BAR | FLOATING_PILL | COMPACT_EDGE */
    const val MiniPlayerStyle = "miniPlayerStyle"
    const val SliderStyle = "sliderStyle"
    const val SquigglySlider = "squigglySlider"
    const val PlayerButtonsStyle = "player_buttons_style"
    const val PlayerBackgroundStyle = "playerBackgroundStyle"
    const val MiniPlayerBackgroundStyle = "miniPlayerBackgroundStyle"
    const val QueuePanelMode = "queuePanelMode"
    const val SettingsDropdown = "settingsDropdown"
    const val TopNavStyle = "topNavStyle"
    const val CanvasSource = "canvasSource"
    const val EnableCanvas = "enableCanvas"
    const val HidePlayerThumbnail = "hidePlayerThumbnail"
    const val AudioQuality = "audioQuality"
    const val PersistentQueue = "persistentQueue"
    const val SaavnEnabled = "saavnEnabled"
    const val PlaybackVolume = "playbackVolume"
    const val LyricsTextAlign = "lyricsTextAlign"
    const val LyricsProvider = "lyricsProvider"
    const val DownloadPath = "downloadPath"
    const val DownloadFormat = "downloadFormat"
    const val DownloadQuality = "downloadQuality"
    const val DownloadEmbedMetadata = "downloadEmbedMetadata"
    /** Soft volume ramp when skipping tracks (single-player sequential fade). */
    const val CrossfadeEnabled = "crossfadeEnabled"
    const val CrossfadeSeconds = "crossfadeSeconds"
    const val PlayPauseFadeEnabled = "playPauseFadeEnabled"
    const val PlayPauseFadeSeconds = "playPauseFadeSeconds"
}

enum class DarkMode { ON, OFF, AUTO }

/**
 * Material 3 Dynamic Color scheme variants (material-color-utilities / Material Kolor).
 * Maps 1:1 to [com.materialkolor.PaletteStyle].
 */
enum class M3PaletteStyle {
    TONAL_SPOT,
    NEUTRAL,
    VIBRANT,
    EXPRESSIVE,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
    FIDELITY,
    CONTENT,
}

/** Center top-bar destinations: icon only (default), icon+label, or text only. */
enum class TopNavStyle { ICON_ONLY, ICON_AND_TEXT, TEXT_ONLY }

enum class SliderStyle { DEFAULT, WAVY, SLIM, EXPRESSIVE }

enum class PlayerButtonsStyle { DEFAULT, PRIMARY, TERTIARY }

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR,
    GLOW_ANIMATED,
    APPLE_MUSIC,
    LIVE_MESH,
    /** Classic horizontal cover→black gradient (former mini default look). */
    LEGACY,
}

/** Mini-player chrome architecture (Appearance → Mini-player / Apple mini). */
enum class MiniPlayerStyle {
    /** Docked full-bleed bar along the bottom edge. */
    FULL_WIDTH_BAR,
    /** Floating M3 pill with inset, shadow, and rounded ends. */
    FLOATING_PILL,
    /** Compact floating card anchored to the bottom-trailing corner. */
    COMPACT_EDGE,
}

/** Queue UI: fullscreen sheet (default), right rail, or up-next under the NP transport. */
enum class QueuePanelMode {
    FULLSCREEN,
    PINNED_RIGHT,
    UNDER_PLAYBAR,
}

enum class AudioQuality { AUTO, HIGH, LOW }

/** Now-playing lyrics column text alignment (Appearance → Lyrics). Default: CENTER. */
enum class LyricsTextAlign { LEFT, CENTER, RIGHT }

/** Active lyrics backend for Now Playing. Default: LRCLIB. */
enum class LyricsProvider {
    LRCLIB,
    KUGOU,
    MUSIXMATCH,
    BETTER_LYRICS,
    YOULY_PLUS,
    UNISON,
    PAXSENIX,
    ;

    val label: String
        get() = when (this) {
            LRCLIB -> "LRCLIB"
            KUGOU -> "KuGou"
            MUSIXMATCH -> "Musixmatch"
            BETTER_LYRICS -> "Better Lyrics"
            YOULY_PLUS -> "YouLy+"
            UNISON -> "Unison"
            PAXSENIX -> "Paxsenix"
        }
}

/** yt-dlp extract format for Downloads. Default: OPUS. */
enum class DownloadAudioFormat {
    OPUS,
    M4A,
    MP3,
    BEST,
    ;

    val label: String
        get() = when (this) {
            OPUS -> "Opus"
            M4A -> "M4A"
            MP3 -> "MP3"
            BEST -> "Best (as downloaded)"
        }
}

/** yt-dlp audio quality selector. Default: BEST. */
enum class DownloadAudioQuality {
    BEST,
    HIGH,
    MEDIUM,
    ;

    val label: String
        get() = when (this) {
            BEST -> "Best"
            HIGH -> "High (≤192 kbps)"
            MEDIUM -> "Medium (≤128 kbps)"
        }
}

/** Default seed — soft blue-gray (idle / no artwork), not coral. */
const val DefaultThemeColorArgb: Int = 0xFF8BA3B8.toInt()
