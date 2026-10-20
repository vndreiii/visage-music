package com.music.vivi.compose.prefs

import java.io.File
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Desktop prefs using the same string keys as upstream [PreferenceKeys]
 * so cherry-picks stay easy. Stored at ~/.config/visage/settings.properties
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

    @Synchronized
    fun setString(key: String, value: String) {
        if (props.getProperty(key) == value) return
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
        props.getProperty(key)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: default

    fun setFloat(key: String, value: Float) {
        setString(key, value.toString())
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val target = file.absoluteFile
        val temporary = File.createTempFile("settings-", ".tmp", target.parentFile)
        try {
            temporary.outputStream().use { props.store(it, "VIsage settings") }
            try {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    companion object {
        fun defaultFile(): File = File(com.music.vivi.nativelib.AppPaths.config(), "settings.properties")
    }
}

/** Key names mirrored from upstream PreferenceKeys. */
object PrefKeys {
    /** Tag Manager: copy originals to a backup folder before rewriting tags (default on). */
    const val TagBackupEnabled = "tagBackupEnabled"
    /** Tag Manager: write ID3v2.3 instead of 2.4 in MP3/WAV (default off). */
    const val TagId3v23 = "tagId3v23"
    /** Optional AcoustID application key; fingerprint lookup runs only with this and `fpcalc`. */
    const val TagAcoustidKey = "tagAcoustidKey"
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
    const val VolumeControlStyle = "volumeControlStyle"
    const val SquigglySlider = "squigglySlider"
    const val PlayerButtonsStyle = "player_buttons_style"
    const val PlayerBackgroundStyle = "playerBackgroundStyle"
    const val MiniPlayerBackgroundStyle = "miniPlayerBackgroundStyle"
    const val QueuePanelMode = "queuePanelMode"
    const val SettingsDropdown = "settingsDropdown"
    const val TopNavStyle = "topNavStyle"
    const val CanvasSource = "canvasSource"
    const val EnableCanvas = "enableCanvas"
    /** Now Playing: cover (and canvas) with square corners instead of rounded ones. */
    const val SquareCover = "nowPlayingSquareCover"
    const val PlayerVideoEnabled = "playerVideoEnabled"
    /** Legacy boolean (true = fill); superseded by [PlayerVideoFraming], still read for migration. */
    const val PlayerVideoFill = "playerVideoFill"
    /** Fit | Fill | Original (see VideoFraming). */
    const val PlayerVideoFraming = "playerVideoFraming"
    const val PlayerVideoQuality = "playerVideoQuality"
    const val CanvasUseGpu = "canvasUseGpu"
    const val MiniPlayerBackgroundDarkness = "miniPlayerBackgroundDarkness"
    const val PlayerBackgroundDarkness = "playerBackgroundDarkness"
    /** Legacy single preset ("expressive" / "dm-sans" / "roboto" / "system"); read only to migrate to the two below. */
    const val InterfaceFont = "interfaceFont"
    /** Font for headings and titles: "Manrope", "DMSans", "Roboto", "system" or "sys:<installed family name>". */
    const val HeadingFont = "headingFont"
    /** Font for body text and labels, same values as [HeadingFont]. */
    const val BodyFont = "bodyFont"
    const val AdwaitaInspired = "adwaitaInspired"
    /** "Material 3 Expressive Blur": translucent, blurred surfaces over content. */
    const val GlassBlur = "glassBlur"
    /** With the blur on, every page gets a cover-tinted background in this style / darkness. */
    const val PageBackgroundStyle = "pageBackgroundStyle"
    const val PageBackgroundDarkness = "pageBackgroundDarkness"
    const val LimitPlayerControlsWidth = "limitPlayerControlsWidth"
    const val PlayerControlsWidth = "playerControlsWidth"
    const val PlayPauseHeight = "playPauseHeight"
    const val CanvasPingPong = "canvasPingPong"
    const val DiscordRpcEnabled = "discordRpcEnabled"
    const val DiscordRpcSongButton = "discordRpcSongButton"
    const val DiscordRpcVisageButton = "discordRpcVisageButton"
    const val HidePlayerThumbnail = "hidePlayerThumbnail"
    const val AudioQuality = "audioQuality"
    const val PersistentQueue = "persistentQueue"
    const val SaavnEnabled = "saavnEnabled"
    const val PlaybackVolume = "playbackVolume"
    const val LyricsTextAlign = "lyricsTextAlign"
    const val LyricsProvider = "lyricsProvider"
    // Lyrics experience (see ui/player/lyrics/LyricsConfig.kt for defaults / ranges)
    const val LyricsTextStyle = "lyricsTextStyle"
    const val LyricsAnimation = "lyricsAnimation"
    const val LyricsFont = "lyricsFont"
    const val LyricsFontWeight = "lyricsFontWeight"
    const val LyricsFontSize = "lyricsFontSize"
    const val LyricsLineSpacing = "lyricsLineSpacing"
    const val LyricsTextCase = "lyricsTextCase"
    const val LyricsVisibility = "lyricsVisibility"
    const val LyricsFadeEdges = "lyricsFadeEdges"
    /** v2: the old key held 0.38–0.45, which put the active line above the centre; start fresh at 0.5. */
    const val LyricsAnchor = "lyricsAnchorV2"
    const val LyricsWordSync = "lyricsWordSync"
    const val LyricsEstimateWords = "lyricsEstimateWords"
    const val LyricsGlow = "lyricsGlow"
    const val LyricsBlur = "lyricsBlur"
    const val LyricsInactiveBrightness = "lyricsInactiveBrightness"
    const val LyricsAnimStrength = "lyricsAnimStrength"
    const val LyricsAccent = "lyricsAccent"
    const val LyricsShowTranslation = "lyricsShowTranslation"
    const val LyricsShowBackgroundVocals = "lyricsShowBackgroundVocals"
    const val LyricsTapToSeek = "lyricsTapToSeek"
    const val LyricsAutoResume = "lyricsAutoResume"
    const val LyricsSyncOffsetMs = "lyricsSyncOffsetMs"
    const val LyricsGapStyle = "lyricsGapStyle"
    const val LyricsBackground = "lyricsBackground"
    const val LyricsBackgroundIntensity = "lyricsBackgroundIntensity"
    const val DownloadPath = "downloadPath"
    const val DownloadFormat = "downloadFormat"
    const val DownloadQuality = "downloadQuality"
    const val DownloadEmbedMetadata = "downloadEmbedMetadata"
    /** Concurrent crossfade between two decoded audio streams. */
    const val CrossfadeEnabled = "crossfadeEnabled"
    const val CrossfadeSeconds = "crossfadeSeconds"
    const val PlayPauseFadeEnabled = "playPauseFadeEnabled"
    const val PlayPauseFadeSeconds = "playPauseFadeSeconds"
    /** Pipe-separated absolute paths of local music folders. */
    const val LocalMusicFolders = "localMusicFolders"
    const val LocalAutoScanOnStartup = "localAutoScanOnStartup"
    const val PreferLocalMetadata = "preferLocalMetadata"
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
enum class VolumeControlStyle { SLIDER, COMPACT_PILL }

enum class TopNavStyle { ICON_ONLY, ICON_AND_TEXT, TEXT_ONLY }

/** A stored "DEFAULT" (removed style) fails valueOf and falls back to [M3_EXPRESSIVE] everywhere it is read. */
enum class SliderStyle { M3_EXPRESSIVE, WAVY, SLIM, EXPRESSIVE }

enum class PlayerButtonsStyle { DEFAULT, PRIMARY, TERTIARY }

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR,
    GLOW_ANIMATED,
    APPLE_MUSIC,
    LIVE_MESH,
    FLUID,
    WAVE,
    XMB,
    DISTORT,
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
