package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.theme.visageBundledFamily

/** How the lyric glyphs are painted. */
enum class LyricsTextStyle(val label: String) {
    FILLED("Filled"),
    HOLLOW("Hollow"),
    OUTLINED("Filled + outline"),
    GRADIENT("Gradient"),
}

/** Motion personality of the active line / sung words. */
enum class LyricsAnimation(val label: String) {
    APPLE("Apple"),
    KARAOKE("Karaoke"),
    WAVE("Wave"),
    FADE("Fade"),
    SLIDE("Slide"),
    POP("Pop"),
    MINIMAL("Minimal"),
}

enum class LyricsVisibility(val label: String, val radius: Int) {
    CURRENT("Current line", 0),
    FEW_3("3 lines", 1),
    FEW_5("5 lines", 2),
    FULL("Full lyrics", Int.MAX_VALUE),
}

enum class LyricsBackgroundMode(val label: String) {
    NONE("None"),
    BLURRED_COVER("Blurred cover"),
    TINTED_SCRIM("Tinted scrim"),
    ANIMATED_GRADIENT("Animated gradient"),
    FOLLOW_PLAYER("Player background"),
}

enum class LyricsGapStyle(val label: String) {
    BOUNCING_DOTS("Bouncing dots"),
    FILL_DOTS("Filling dots"),
    RING("Progress ring"),
    BAR("Progress bar"),
    EQUALIZER("Equalizer"),
    MORPH_SHAPE("Morphing shape"),
    COUNTDOWN("Countdown"),
    NONE("None"),
}

enum class LyricsFont(val label: String) {
    APP("App font"),
    MANROPE("Manrope"),
    DM_SANS("DM Sans"),
    ROBOTO("Roboto"),
    SANS("System sans"),
    SERIF("Serif"),
    MONO("Monospace"),
    CURSIVE("Cursive"),
    ;

    fun family(): FontFamily? = when (this) {
        APP -> null
        MANROPE -> visageBundledFamily("Manrope")
        DM_SANS -> visageBundledFamily("DMSans")
        ROBOTO -> visageBundledFamily("Roboto")
        SANS -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONO -> FontFamily.Monospace
        CURSIVE -> FontFamily.Cursive
    }
}

enum class LyricsWeight(val label: String, val weight: FontWeight) {
    REGULAR("Regular", FontWeight.Normal),
    MEDIUM("Medium", FontWeight.Medium),
    SEMI_BOLD("Semibold", FontWeight.SemiBold),
    BOLD("Bold", FontWeight.Bold),
}

enum class LyricsTextCase(val label: String) {
    ORIGINAL("As written"),
    UPPER("UPPERCASE"),
    LOWER("lowercase"),
    TITLE("Title Case"),
    ;

    fun apply(s: String): String = when (this) {
        ORIGINAL -> s
        UPPER -> s.uppercase()
        LOWER -> s.lowercase()
        TITLE -> s.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }
}

enum class LyricsAccent(val label: String) {
    ARTWORK("Cover art"),
    THEME("Theme"),
    WHITE("White"),
}

/** How the sung portion of a unit is revealed. */
enum class SweepMode { SWEEP, FADE, NONE }

/** Per-animation-preset motion constants (before the user "animation strength" scaling). */
data class AnimationSpec(
    val activeScale: Float,
    val inactiveScale: Float,
    val damping: Float,
    val stiffness: Float,
    val sweep: SweepMode,
    /** Soft edge width of the sweep, in em. */
    val feather: Float,
    /** Rise of a word while sung, in em. */
    val wordLift: Float,
    /** Extra scale of a word while sung. */
    val wordScale: Float,
    /** Slide-in distance of an activating line's words, in em. */
    val slideIn: Float,
    val blurFactor: Float,
    val glowFactor: Float,
) {
    fun <T> spring(): SpringSpec<T> = spring(dampingRatio = damping, stiffness = stiffness)

    companion object {
        fun of(a: LyricsAnimation): AnimationSpec = when (a) {
            LyricsAnimation.APPLE -> AnimationSpec(1.0f, 0.86f, 0.78f, 320f, SweepMode.SWEEP, 0.45f, 0f, 0f, 0f, 1f, 0.8f)
            LyricsAnimation.KARAOKE -> AnimationSpec(1.04f, 0.94f, 0.82f, 420f, SweepMode.SWEEP, 0.12f, 0.05f, 0f, 0f, 0.5f, 1.3f)
            LyricsAnimation.WAVE -> AnimationSpec(1.02f, 0.92f, 0.75f, 380f, SweepMode.SWEEP, 0.3f, 0.24f, 0.04f, 0f, 0.6f, 0.9f)
            LyricsAnimation.FADE -> AnimationSpec(1.0f, 0.97f, 1.0f, 220f, SweepMode.FADE, 0f, 0f, 0f, 0f, 0.6f, 0.6f)
            LyricsAnimation.SLIDE -> AnimationSpec(1.0f, 0.95f, 0.9f, 300f, SweepMode.SWEEP, 0.3f, 0f, 0f, 0.55f, 0.5f, 0.7f)
            LyricsAnimation.POP -> AnimationSpec(1.08f, 0.9f, 0.45f, 520f, SweepMode.SWEEP, 0.18f, 0.02f, 0.16f, 0f, 0.4f, 1.1f)
            LyricsAnimation.MINIMAL -> AnimationSpec(1.0f, 1.0f, 1.0f, 600f, SweepMode.NONE, 0f, 0f, 0f, 0f, 0f, 0f)
        }
    }
}

/** Everything the lyrics renderer needs, read from [DesktopPrefs]. */
data class LyricsConfig(
    val textStyle: LyricsTextStyle = LyricsTextStyle.FILLED,
    val animation: LyricsAnimation = LyricsAnimation.APPLE,
    val font: LyricsFont = LyricsFont.APP,
    val weight: LyricsWeight = LyricsWeight.SEMI_BOLD,
    val fontSizeSp: Float = 28f,
    val lineSpacingDp: Float = 12f,
    val textCase: LyricsTextCase = LyricsTextCase.ORIGINAL,
    val align: LyricsTextAlign = LyricsTextAlign.CENTER,
    val visibility: LyricsVisibility = LyricsVisibility.FULL,
    val fadeEdges: Boolean = true,
    /** Vertical position of the active line, 0 (top) .. 1 (bottom). */
    val anchor: Float = 0.5f,
    val wordSync: Boolean = true,
    val estimateWords: Boolean = true,
    /** 0 disables the glow. */
    val glow: Float = 0.6f,
    /** 0 disables distance blur. */
    val blur: Float = 0.5f,
    /** Alpha of the line next to the active one (further lines decay from it). */
    val inactiveBrightness: Float = 0.5f,
    /** Scales scale/lift/pop amounts of the chosen animation. */
    val animStrength: Float = 1f,
    val accent: LyricsAccent = LyricsAccent.ARTWORK,
    val showTranslation: Boolean = true,
    val showBackgroundVocals: Boolean = true,
    val tapToSeek: Boolean = true,
    val autoResume: Boolean = true,
    val syncOffsetMs: Int = 0,
    val gapStyle: LyricsGapStyle = LyricsGapStyle.FILL_DOTS,
    val background: LyricsBackgroundMode = LyricsBackgroundMode.NONE,
    val backgroundIntensity: Float = 0.6f,
) {
    val spec: AnimationSpec get() = AnimationSpec.of(animation)

    /** Fields that change text measurement; the layout cache is keyed on this. */
    val layoutKey: Int
        get() = listOf(font, weight, fontSizeSp, textCase, align, showTranslation, textStyle == LyricsTextStyle.OUTLINED).hashCode()

    companion object {
        val Default = LyricsConfig()
    }
}

private inline fun <reified T : Enum<T>> DesktopPrefs.enumOr(key: String, default: T): T =
    runCatching { enumValueOf<T>(getString(key, default.name)) }.getOrDefault(default)

fun DesktopPrefs.lyricsConfig(): LyricsConfig {
    val d = LyricsConfig.Default
    return LyricsConfig(
        textStyle = enumOr(PrefKeys.LyricsTextStyle, d.textStyle),
        animation = enumOr(PrefKeys.LyricsAnimation, d.animation),
        font = enumOr(PrefKeys.LyricsFont, d.font),
        weight = enumOr(PrefKeys.LyricsFontWeight, d.weight),
        fontSizeSp = getFloat(PrefKeys.LyricsFontSize, d.fontSizeSp).coerceIn(16f, 56f),
        lineSpacingDp = getFloat(PrefKeys.LyricsLineSpacing, d.lineSpacingDp).coerceIn(0f, 40f),
        textCase = enumOr(PrefKeys.LyricsTextCase, d.textCase),
        align = enumOr(PrefKeys.LyricsTextAlign, d.align),
        visibility = enumOr(PrefKeys.LyricsVisibility, d.visibility),
        fadeEdges = getBoolean(PrefKeys.LyricsFadeEdges, d.fadeEdges),
        anchor = getFloat(PrefKeys.LyricsAnchor, d.anchor).coerceIn(0.15f, 0.7f),
        wordSync = getBoolean(PrefKeys.LyricsWordSync, d.wordSync),
        estimateWords = getBoolean(PrefKeys.LyricsEstimateWords, d.estimateWords),
        glow = getFloat(PrefKeys.LyricsGlow, d.glow).coerceIn(0f, 1f),
        blur = getFloat(PrefKeys.LyricsBlur, d.blur).coerceIn(0f, 1f),
        inactiveBrightness = getFloat(PrefKeys.LyricsInactiveBrightness, d.inactiveBrightness).coerceIn(0.1f, 0.95f),
        animStrength = getFloat(PrefKeys.LyricsAnimStrength, d.animStrength).coerceIn(0f, 2f),
        accent = enumOr(PrefKeys.LyricsAccent, d.accent),
        showTranslation = getBoolean(PrefKeys.LyricsShowTranslation, d.showTranslation),
        showBackgroundVocals = getBoolean(PrefKeys.LyricsShowBackgroundVocals, d.showBackgroundVocals),
        tapToSeek = getBoolean(PrefKeys.LyricsTapToSeek, d.tapToSeek),
        autoResume = getBoolean(PrefKeys.LyricsAutoResume, d.autoResume),
        syncOffsetMs = getInt(PrefKeys.LyricsSyncOffsetMs, d.syncOffsetMs).coerceIn(-1500, 1500),
        gapStyle = enumOr(PrefKeys.LyricsGapStyle, d.gapStyle),
        background = enumOr(PrefKeys.LyricsBackground, d.background),
        backgroundIntensity = getFloat(PrefKeys.LyricsBackgroundIntensity, d.backgroundIntensity).coerceIn(0f, 1f),
    )
}

fun DesktopPrefs.saveLyricsConfig(c: LyricsConfig) {
    setString(PrefKeys.LyricsTextStyle, c.textStyle.name)
    setString(PrefKeys.LyricsAnimation, c.animation.name)
    setString(PrefKeys.LyricsFont, c.font.name)
    setString(PrefKeys.LyricsFontWeight, c.weight.name)
    setFloat(PrefKeys.LyricsFontSize, c.fontSizeSp)
    setFloat(PrefKeys.LyricsLineSpacing, c.lineSpacingDp)
    setString(PrefKeys.LyricsTextCase, c.textCase.name)
    setString(PrefKeys.LyricsTextAlign, c.align.name)
    setString(PrefKeys.LyricsVisibility, c.visibility.name)
    setBoolean(PrefKeys.LyricsFadeEdges, c.fadeEdges)
    setFloat(PrefKeys.LyricsAnchor, c.anchor)
    setBoolean(PrefKeys.LyricsWordSync, c.wordSync)
    setBoolean(PrefKeys.LyricsEstimateWords, c.estimateWords)
    setFloat(PrefKeys.LyricsGlow, c.glow)
    setFloat(PrefKeys.LyricsBlur, c.blur)
    setFloat(PrefKeys.LyricsInactiveBrightness, c.inactiveBrightness)
    setFloat(PrefKeys.LyricsAnimStrength, c.animStrength)
    setString(PrefKeys.LyricsAccent, c.accent.name)
    setBoolean(PrefKeys.LyricsShowTranslation, c.showTranslation)
    setBoolean(PrefKeys.LyricsShowBackgroundVocals, c.showBackgroundVocals)
    setBoolean(PrefKeys.LyricsTapToSeek, c.tapToSeek)
    setBoolean(PrefKeys.LyricsAutoResume, c.autoResume)
    setInt(PrefKeys.LyricsSyncOffsetMs, c.syncOffsetMs)
    setString(PrefKeys.LyricsGapStyle, c.gapStyle.name)
    setString(PrefKeys.LyricsBackground, c.background.name)
    setFloat(PrefKeys.LyricsBackgroundIntensity, c.backgroundIntensity)
}

/** Quick picks: whole looks that overwrite the style knobs (provider/alignment/offset are kept). */
enum class LyricsPreset(val label: String) {
    APPLE_MUSIC("Apple Music"),
    KARAOKE("Karaoke"),
    NEON("Neon outline"),
    CINEMA("Cinema"),
    MINIMAL("Minimal"),
    ;

    fun applyTo(base: LyricsConfig): LyricsConfig = when (this) {
        APPLE_MUSIC -> base.copy(
            textStyle = LyricsTextStyle.FILLED, animation = LyricsAnimation.APPLE, font = LyricsFont.APP,
            weight = LyricsWeight.BOLD, fontSizeSp = 30f, lineSpacingDp = 14f, glow = 0.45f, blur = 0.6f,
            visibility = LyricsVisibility.FULL, fadeEdges = true, anchor = 0.5f, inactiveBrightness = 0.45f,
            textCase = LyricsTextCase.ORIGINAL, background = LyricsBackgroundMode.NONE,
        )
        KARAOKE -> base.copy(
            textStyle = LyricsTextStyle.GRADIENT, animation = LyricsAnimation.KARAOKE, weight = LyricsWeight.BOLD,
            fontSizeSp = 32f, glow = 0.9f, blur = 0.25f, visibility = LyricsVisibility.FEW_5, fadeEdges = true,
            anchor = 0.5f, inactiveBrightness = 0.5f, background = LyricsBackgroundMode.ANIMATED_GRADIENT,
            backgroundIntensity = 0.55f,
        )
        NEON -> base.copy(
            textStyle = LyricsTextStyle.HOLLOW, animation = LyricsAnimation.POP, font = LyricsFont.MANROPE,
            weight = LyricsWeight.BOLD, fontSizeSp = 34f, glow = 1f, blur = 0.3f, textCase = LyricsTextCase.UPPER,
            visibility = LyricsVisibility.FEW_3, inactiveBrightness = 0.55f, background = LyricsBackgroundMode.TINTED_SCRIM,
            backgroundIntensity = 0.7f,
        )
        CINEMA -> base.copy(
            textStyle = LyricsTextStyle.FILLED, animation = LyricsAnimation.FADE, font = LyricsFont.SERIF,
            weight = LyricsWeight.MEDIUM, fontSizeSp = 30f, glow = 0.25f, blur = 0.2f, visibility = LyricsVisibility.CURRENT,
            fadeEdges = true, anchor = 0.5f, background = LyricsBackgroundMode.BLURRED_COVER, backgroundIntensity = 0.7f,
        )
        MINIMAL -> base.copy(
            textStyle = LyricsTextStyle.FILLED, animation = LyricsAnimation.MINIMAL, font = LyricsFont.APP,
            weight = LyricsWeight.MEDIUM, fontSizeSp = 24f, lineSpacingDp = 8f, glow = 0f, blur = 0f,
            visibility = LyricsVisibility.FULL, fadeEdges = false, anchor = 0.5f, inactiveBrightness = 0.4f,
            background = LyricsBackgroundMode.NONE,
        )
    }
}
