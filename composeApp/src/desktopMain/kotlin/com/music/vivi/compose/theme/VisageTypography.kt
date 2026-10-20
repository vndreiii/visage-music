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

/** Pairs saved before headings and body were chosen separately. */
internal fun legacyFontPair(preset: String): Pair<String, String> = when (preset) {
    "dm-sans" -> "DMSans" to "Roboto"
    "roboto" -> "Roboto" to "Roboto"
    "system" -> "system" to "system"
    else -> "Manrope" to "Roboto"
}

private val installedFamilies = java.util.concurrent.ConcurrentHashMap<String, FontFamily>()

/** Names of every font family installed on this machine (for the font pickers). */
internal fun installedFontFamilies(): List<String> = runCatching {
    val mgr = org.jetbrains.skia.FontMgr.default
    (0 until mgr.familiesCount).map { mgr.getFamilyName(it) }.filter { it.isNotBlank() && !it.startsWith(".") }.distinct().sorted()
}.getOrDefault(emptyList())

/** [key]: "Manrope" / "DMSans" / "Roboto" (bundled), "system" (generic sans-serif) or "sys:<installed family>". */
internal fun fontFamilyFor(key: String): FontFamily = when {
    key == "system" -> FontFamily.SansSerif
    key.startsWith("sys:") -> installedFamilies.getOrPut(key) {
        runCatching {
            val face = org.jetbrains.skia.FontMgr.default.matchFamilyStyle(key.removePrefix("sys:"), org.jetbrains.skia.FontStyle.NORMAL)
            // One typeface per installed family: other weights are synthesised by the text engine.
            if (face != null) FontFamily(androidx.compose.ui.text.platform.Typeface(face, key)) else FontFamily.SansSerif
        }.getOrDefault(FontFamily.SansSerif)
    }
    else -> visageBundledFamily(key)
}

/** Human name of a font key, for the pickers. */
internal fun fontDisplayName(key: String): String = when {
    key == "system" -> "System default"
    key == "DMSans" -> "DM Sans"
    key.startsWith("sys:") -> key.removePrefix("sys:")
    else -> key
}

/** Expressive hierarchy: [heading] for display/headline/title-large, [body] for everything calmer. */
internal fun visageTypography(heading: String, body: String): Typography {
    val brand = fontFamilyFor(heading)
    val plain = fontFamilyFor(body)
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
