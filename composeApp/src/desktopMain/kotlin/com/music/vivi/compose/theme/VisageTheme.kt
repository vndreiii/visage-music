package com.music.vivi.compose.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme
import com.music.vivi.compose.prefs.DarkMode
import com.music.vivi.compose.prefs.DefaultThemeColorArgb
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.M3PaletteStyle
import com.music.vivi.compose.prefs.PrefKeys

val DefaultThemeColor = Color(DefaultThemeColorArgb.toLong() and 0xFFFFFFFFL)

data class VisageThemeState(
    val darkMode: DarkMode,
    val pureBlack: Boolean,
    val themeColorArgb: Int,
    val dynamicTheme: Boolean,
    val matugenTheme: Boolean,
    val paletteStyle: M3PaletteStyle,
    /** Material contrastLevel in [-1.0, 1.0]; 0 = standard. */
    val contrastLevel: Double,
    val headingFont: String = "Manrope",
    val bodyFont: String = "Roboto",
) {
    fun useDarkTheme(systemDark: Boolean): Boolean = when (darkMode) {
        DarkMode.ON -> true
        DarkMode.OFF -> false
        DarkMode.AUTO -> systemDark
    }
}

fun DesktopPrefs.readThemeState(): VisageThemeState = VisageThemeState(
    darkMode = runCatching {
        DarkMode.valueOf(getString(PrefKeys.DarkMode, DarkMode.AUTO.name))
    }.getOrDefault(DarkMode.AUTO),
    pureBlack = getBoolean(PrefKeys.PureBlack, false),
    themeColorArgb = getInt(PrefKeys.SelectedThemeColor, DefaultThemeColorArgb),
    dynamicTheme = getBoolean(PrefKeys.DynamicTheme, true),
    matugenTheme = getBoolean(PrefKeys.MatugenTheme, false),
    paletteStyle = runCatching {
        M3PaletteStyle.valueOf(getString(PrefKeys.PaletteStyle, M3PaletteStyle.TONAL_SPOT.name))
    }.getOrDefault(M3PaletteStyle.TONAL_SPOT),
    headingFont = getString(PrefKeys.HeadingFont, "").ifBlank { legacyFontPair(getString(PrefKeys.InterfaceFont, "expressive")).first },
    bodyFont = getString(PrefKeys.BodyFont, "").ifBlank { legacyFontPair(getString(PrefKeys.InterfaceFont, "expressive")).second },
    contrastLevel = getFloat(PrefKeys.ContrastLevel, 0f).toDouble().coerceIn(-1.0, 1.0),
)

@Composable
fun rememberVisageThemeState(prefs: DesktopPrefs): VisageThemeState {
    var state by remember { mutableStateOf(prefs.readThemeState()) }
    DisposableEffect(prefs) {
        val listener = { state = prefs.readThemeState() }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    return state
}

fun ColorScheme.withPureBlack(enabled: Boolean): ColorScheme {
    if (!enabled) return this
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF222222),
    )
}

fun M3PaletteStyle.toKolor(): PaletteStyle = when (this) {
    M3PaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    M3PaletteStyle.NEUTRAL -> PaletteStyle.Neutral
    M3PaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    M3PaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
    M3PaletteStyle.RAINBOW -> PaletteStyle.Rainbow
    M3PaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
    M3PaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
    M3PaletteStyle.FIDELITY -> PaletteStyle.Fidelity
    M3PaletteStyle.CONTENT -> PaletteStyle.Content
}

fun M3PaletteStyle.label(): String = when (this) {
    M3PaletteStyle.TONAL_SPOT -> "Tonal spot"
    M3PaletteStyle.NEUTRAL -> "Neutral"
    M3PaletteStyle.VIBRANT -> "Vibrant"
    M3PaletteStyle.EXPRESSIVE -> "Expressive"
    M3PaletteStyle.RAINBOW -> "Rainbow"
    M3PaletteStyle.FRUIT_SALAD -> "Fruit salad"
    M3PaletteStyle.MONOCHROME -> "Monochrome"
    M3PaletteStyle.FIDELITY -> "Fidelity"
    M3PaletteStyle.CONTENT -> "Content"
}

fun M3PaletteStyle.subtitle(): String = when (this) {
    M3PaletteStyle.TONAL_SPOT -> "Default Material You — pastel, accessible tones"
    M3PaletteStyle.NEUTRAL -> "Near grayscale with a hint of chroma"
    M3PaletteStyle.VIBRANT -> "Max chroma primary; loud accents"
    M3PaletteStyle.EXPRESSIVE -> "Playful — primary hue shifts from the seed"
    M3PaletteStyle.RAINBOW -> "Playful — seed hue does not appear"
    M3PaletteStyle.FRUIT_SALAD -> "Playful dual-hue salad (seed hue avoided)"
    M3PaletteStyle.MONOCHROME -> "Pure grayscale tokens"
    M3PaletteStyle.FIDELITY -> "Palettes match seed; tones adjusted for contrast"
    M3PaletteStyle.CONTENT -> "Like Fidelity — primary container ≈ seed"
}

/**
 * Official Material 3 Dynamic Color via material-color-utilities (Material Kolor).
 * Replaces the old hand-tint that washed surfaces with the seed and broke contrast.
 */
fun visageColorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    seed: Color,
    style: M3PaletteStyle = M3PaletteStyle.TONAL_SPOT,
    contrastLevel: Double = 0.0,
): ColorScheme {
    val scheme = dynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        isAmoled = false,
        style = style.toKolor(),
        contrastLevel = contrastLevel,
        // 2025 spec: stronger container/on-container contrast (fixes muddy mid-tone pills).
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    return if (darkTheme && pureBlack) scheme.withPureBlack(true) else scheme
}

/**
 * @param artworkPalette when dynamic theme is on and a cover is available, used as seed.
 */
@Composable
fun VisageTheme(
    prefs: DesktopPrefs,
    artworkPalette: ArtworkPalette? = null,
    content: @Composable () -> Unit,
) {
    val theme = rememberVisageThemeState(prefs)
    val systemDark = isSystemInDarkTheme()
    val dark = theme.useDarkTheme(systemDark)
    val matugen = rememberMatugenSchemes(enabled = theme.matugenTheme)
    val prefsSeed = Color(theme.themeColorArgb.toLong() and 0xFFFFFFFFL)

    // Cover art still wins Material seed while playing when dynamic theme is on.
    // Otherwise matugen (system wallpaper) tints the app; idle seed is the fallback.
    val targetSeed = when {
        theme.dynamicTheme && artworkPalette != null -> artworkPalette.seed
        theme.matugenTheme -> matugen?.let { schemeMap ->
            val roles = if (dark) schemeMap.dark else schemeMap.light
            roles["primary"]
        } ?: IdleArtworkPalette.seed
        theme.themeColorArgb == DefaultThemeColorArgb ||
            theme.themeColorArgb == 0xFFED5564.toInt() -> IdleArtworkPalette.seed
        else -> prefsSeed
    }

    // Crossfade seed → regenerates the full Monet scheme fluidly (500–800ms).
    val seed by animateColorAsState(
        targetValue = targetSeed,
        animationSpec = com.music.vivi.compose.ui.motion.VisageMotion.colorShift(),
        label = "themeSeedCrossfade",
    )

    val scheme = remember(
        seed,
        dark,
        theme.pureBlack,
        theme.paletteStyle,
        theme.contrastLevel,
        theme.matugenTheme,
        theme.dynamicTheme,
        artworkPalette?.seed,
        matugen?.modifiedMs,
    ) {
        val base = visageColorScheme(
            darkTheme = dark,
            pureBlack = theme.pureBlack,
            seed = seed,
            style = theme.paletteStyle,
            contrastLevel = theme.contrastLevel,
        )
        val roles = when {
            theme.matugenTheme && matugen != null && !(theme.dynamicTheme && artworkPalette != null) ->
                if (dark) matugen.dark else matugen.light
            else -> null
        }
        val applied = if (roles != null) {
            with(MatugenColors) { base.applyRoles(roles) }
        } else {
            base
        }
        if (theme.pureBlack && dark) applied.withPureBlack(true) else applied
    }

    CompositionLocalProvider(LocalArtworkPalette provides artworkPalette) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = VisageExpressiveShapes,
            typography = remember(theme.headingFont, theme.bodyFont) { visageTypography(theme.headingFont, theme.bodyFont) },
            content = content,
        )
    }
}


/**
 * Material 3 Expressive shape scale: rounder than the baseline across the board
 * (8 / 12 / 16 / 24 / 32 dp instead of 4 / 8 / 12 / 16 / 28).
 */
val VisageExpressiveShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/** The "emphasized" end of the Expressive type scale: heavier weights for headlines, titles and labels. */
val VisageEmphasizedTypography: androidx.compose.material3.Typography = androidx.compose.material3.Typography().let { t ->
    val semi = androidx.compose.ui.text.font.FontWeight.SemiBold
    val medium = androidx.compose.ui.text.font.FontWeight.Medium
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = semi),
        displayMedium = t.displayMedium.copy(fontWeight = semi),
        displaySmall = t.displaySmall.copy(fontWeight = semi),
        headlineLarge = t.headlineLarge.copy(fontWeight = semi),
        headlineMedium = t.headlineMedium.copy(fontWeight = semi),
        headlineSmall = t.headlineSmall.copy(fontWeight = semi),
        titleLarge = t.titleLarge.copy(fontWeight = semi),
        titleMedium = t.titleMedium.copy(fontWeight = semi),
        titleSmall = t.titleSmall.copy(fontWeight = semi),
        labelLarge = t.labelLarge.copy(fontWeight = semi),
        labelMedium = t.labelMedium.copy(fontWeight = medium),
        labelSmall = t.labelSmall.copy(fontWeight = medium),
    )
}
