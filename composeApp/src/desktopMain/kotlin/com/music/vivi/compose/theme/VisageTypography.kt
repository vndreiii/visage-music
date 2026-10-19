package com.music.vivi.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private fun bundledFamily(name: String) = FontFamily(
    Font("fonts/$name-400.ttf", weight = FontWeight.Normal),
    Font("fonts/$name-500.ttf", weight = FontWeight.Medium),
    Font("fonts/$name-600.ttf", weight = FontWeight.SemiBold),
    Font("fonts/$name-700.ttf", weight = FontWeight.Bold),
)
private val manrope by lazy { bundledFamily("Manrope") }
private val roboto by lazy { bundledFamily("Roboto") }
private val dmSans by lazy { bundledFamily("DMSans") }

/** Expressive hierarchy: distinctive emphasized headings, calmer body and action labels. */
internal fun visageTypography(preset: String): Typography {
    val brand = when (preset) {
        "system" -> FontFamily.SansSerif
        "dm-sans" -> dmSans
        "roboto" -> roboto
        else -> manrope
    }
    val plain = if (preset == "system") FontFamily.SansSerif else roboto
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = brand, fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontFamily = brand, fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontFamily = brand, fontWeight = FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = brand, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = brand, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = brand, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = brand, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = plain, fontWeight = FontWeight.Medium),
        titleSmall = t.titleSmall.copy(fontFamily = plain, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = plain),
        bodyMedium = t.bodyMedium.copy(fontFamily = plain),
        bodySmall = t.bodySmall.copy(fontFamily = plain),
        labelLarge = t.labelLarge.copy(fontFamily = plain, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily = plain, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = plain, fontWeight = FontWeight.Medium),
    )
}

/** Bundled font family by file prefix ("Manrope", "DMSans", "Roboto") for per-feature font choices. */
internal fun visageBundledFamily(name: String): FontFamily = when (name) {
    "Manrope" -> manrope
    "DMSans" -> dmSans
    else -> roboto
}
