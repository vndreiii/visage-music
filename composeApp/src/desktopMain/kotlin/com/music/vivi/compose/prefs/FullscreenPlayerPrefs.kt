package com.music.vivi.compose.prefs

import com.music.vivi.compose.ui.player.canvas.CanvasSource

/** Minimal read-only view of prefs so resolution logic is testable without files. */
interface PrefReader {
    fun getString(key: String, default: String): String
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
}

fun DesktopPrefs.reader(): PrefReader = object : PrefReader {
    override fun getString(key: String, default: String) = this@reader.getString(key, default)
    override fun getBoolean(key: String, default: Boolean) = this@reader.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = this@reader.getInt(key, default)
}

/** Where the cover sits on the fullscreen player. */
enum class FullscreenLayout { COVER_LEFT, CENTERED, COVER_RIGHT }

enum class FullscreenCoverShape { ROUNDED, SOFT, SQUARE, CIRCLE }

/** Whether the fullscreen player uses canvas video: follow Now Playing, or force on/off. */
enum class FullscreenCanvasMode { FOLLOW, ON, OFF }

const val FullscreenFollow = "FOLLOW"

/** Fullscreen-only preference keys (defaults live in [FullscreenPrefs.defaults]). */
object FullscreenKeys {
    const val Layout = "fsLayout"
    const val CoverScale = "fsCoverScale"
    const val CoverShape = "fsCoverShape"
    const val ShowArtist = "fsShowArtist"
    const val ShowAlbum = "fsShowAlbum"
    const val ShowQueueButton = "fsShowQueueButton"
    const val LyricsByDefault = "fsLyricsByDefault"
    const val ShowVolume = "fsShowVolume"
    const val ShowShuffle = "fsShowShuffle"
    const val ShowRepeat = "fsShowRepeat"
    const val ShowRadio = "fsShowRadio"
    const val ShowTimes = "fsShowTimes"
    const val Background = "fsBackground"
    const val CanvasMode = "fsCanvasMode"
    const val AutoHide = "fsAutoHide"
    const val AutoHideSeconds = "fsAutoHideSeconds"
    const val ShowClock = "fsShowClock"
}

/** Fully resolved fullscreen configuration: fullscreen overrides applied over Now Playing prefs. */
data class FullscreenSettings(
    val layout: FullscreenLayout,
    val coverScale: Float,
    val coverShape: FullscreenCoverShape,
    val showArtist: Boolean,
    val showAlbum: Boolean,
    val showQueueButton: Boolean,
    val lyricsByDefault: Boolean,
    val showVolume: Boolean,
    val showShuffle: Boolean,
    val showRepeat: Boolean,
    val showRadio: Boolean,
    val showTimes: Boolean,
    val autoHide: Boolean,
    val autoHideSeconds: Int,
    val showClock: Boolean,
    // Resolved against Now Playing:
    val backgroundStyle: PlayerBackgroundStyle,
    val canvasEnabled: Boolean,
    val canvasSource: CanvasSource,
    val sliderStyle: SliderStyle,
    val squiggly: Boolean,
    val buttonsStyle: PlayerButtonsStyle,
    val playPauseHeightDp: Int,
    val limitControlsWidth: Boolean,
    val controlsMaxWidthDp: Int,
    val framedControls: Boolean,
    val hideThumbnail: Boolean,
    val volumeStyle: VolumeControlStyle,
) {
    companion object {
        val defaults: Map<String, String> = mapOf(
            FullscreenKeys.Layout to FullscreenLayout.COVER_LEFT.name,
            FullscreenKeys.CoverScale to "100",
            FullscreenKeys.CoverShape to FullscreenCoverShape.ROUNDED.name,
            FullscreenKeys.ShowArtist to "true",
            FullscreenKeys.ShowAlbum to "true",
            FullscreenKeys.ShowQueueButton to "true",
            FullscreenKeys.LyricsByDefault to "false",
            FullscreenKeys.ShowVolume to "true",
            FullscreenKeys.ShowShuffle to "true",
            FullscreenKeys.ShowRepeat to "true",
            FullscreenKeys.ShowRadio to "false",
            FullscreenKeys.ShowTimes to "true",
            FullscreenKeys.Background to FullscreenFollow,
            FullscreenKeys.CanvasMode to FullscreenCanvasMode.FOLLOW.name,
            FullscreenKeys.AutoHide to "false",
            FullscreenKeys.AutoHideSeconds to "5",
            FullscreenKeys.ShowClock to "false",
        )

        /** Background style: [override] is [FullscreenFollow] or a style name; unknown values follow. */
        fun resolveBackground(override: String, nowPlaying: PlayerBackgroundStyle): PlayerBackgroundStyle =
            if (override == FullscreenFollow) nowPlaying
            else runCatching { PlayerBackgroundStyle.valueOf(override) }.getOrDefault(nowPlaying)

        fun resolveCanvas(mode: FullscreenCanvasMode, nowPlayingEnabled: Boolean): Boolean = when (mode) {
            FullscreenCanvasMode.FOLLOW -> nowPlayingEnabled
            FullscreenCanvasMode.ON -> true
            FullscreenCanvasMode.OFF -> false
        }

        fun resolve(p: PrefReader): FullscreenSettings {
            fun str(k: String) = p.getString(k, defaults.getValue(k))
            fun bool(k: String) = p.getBoolean(k, defaults.getValue(k).toBoolean())
            fun <T : Enum<T>> en(k: String, parse: (String) -> T, fallback: T): T =
                runCatching { parse(str(k)) }.getOrDefault(fallback)

            val npBg = runCatching {
                PlayerBackgroundStyle.valueOf(p.getString(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT.name))
            }.getOrDefault(PlayerBackgroundStyle.GRADIENT)
            val canvasMode = en(FullscreenKeys.CanvasMode, { FullscreenCanvasMode.valueOf(it) }, FullscreenCanvasMode.FOLLOW)
            return FullscreenSettings(
                layout = en(FullscreenKeys.Layout, { FullscreenLayout.valueOf(it) }, FullscreenLayout.COVER_LEFT),
                coverScale = p.getInt(FullscreenKeys.CoverScale, 100).coerceIn(50, 100) / 100f,
                coverShape = en(FullscreenKeys.CoverShape, { FullscreenCoverShape.valueOf(it) }, FullscreenCoverShape.ROUNDED),
                showArtist = bool(FullscreenKeys.ShowArtist),
                showAlbum = bool(FullscreenKeys.ShowAlbum),
                showQueueButton = bool(FullscreenKeys.ShowQueueButton),
                lyricsByDefault = bool(FullscreenKeys.LyricsByDefault),
                showVolume = bool(FullscreenKeys.ShowVolume),
                showShuffle = bool(FullscreenKeys.ShowShuffle),
                showRepeat = bool(FullscreenKeys.ShowRepeat),
                showRadio = bool(FullscreenKeys.ShowRadio),
                showTimes = bool(FullscreenKeys.ShowTimes),
                autoHide = bool(FullscreenKeys.AutoHide),
                autoHideSeconds = p.getInt(FullscreenKeys.AutoHideSeconds, 5).coerceIn(2, 30),
                showClock = bool(FullscreenKeys.ShowClock),
                backgroundStyle = resolveBackground(str(FullscreenKeys.Background), npBg),
                canvasEnabled = resolveCanvas(canvasMode, p.getBoolean(PrefKeys.EnableCanvas, true)),
                canvasSource = runCatching {
                    CanvasSource.valueOf(p.getString(PrefKeys.CanvasSource, CanvasSource.AUTO.name))
                }.getOrDefault(CanvasSource.AUTO),
                sliderStyle = runCatching {
                    SliderStyle.valueOf(p.getString(PrefKeys.SliderStyle, SliderStyle.M3_EXPRESSIVE.name))
                }.getOrDefault(SliderStyle.M3_EXPRESSIVE),
                squiggly = p.getBoolean(PrefKeys.SquigglySlider, false),
                buttonsStyle = runCatching {
                    PlayerButtonsStyle.valueOf(p.getString(PrefKeys.PlayerButtonsStyle, PlayerButtonsStyle.DEFAULT.name))
                }.getOrDefault(PlayerButtonsStyle.DEFAULT),
                playPauseHeightDp = p.getInt(PrefKeys.PlayPauseHeight, 56).coerceIn(48, 96),
                limitControlsWidth = p.getBoolean(
                    PrefKeys.LimitPlayerControlsWidth,
                    p.getBoolean(PrefKeys.AdwaitaInspired, false),
                ),
                controlsMaxWidthDp = p.getInt(PrefKeys.PlayerControlsWidth, 600).coerceIn(400, 600),
                framedControls = p.getBoolean(PrefKeys.UsePlayerV2, false),
                hideThumbnail = p.getBoolean(PrefKeys.HidePlayerThumbnail, false),
                volumeStyle = runCatching {
                    VolumeControlStyle.valueOf(p.getString(PrefKeys.VolumeControlStyle, VolumeControlStyle.SLIDER.name))
                }.getOrDefault(VolumeControlStyle.SLIDER),
            )
        }
    }
}

/** Restores every fullscreen-only pref to its default so the page follows Now Playing again. */
fun DesktopPrefs.resetFullscreenPrefs() {
    FullscreenSettings.defaults.forEach { (k, v) -> setString(k, v) }
}
