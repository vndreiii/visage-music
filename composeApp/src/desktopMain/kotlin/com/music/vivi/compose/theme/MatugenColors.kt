package com.music.vivi.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Loads wallpaper colours written by matugen into vivi-desktop.colors. */
object MatugenColors {
    val defaultFile: File
        get() = File(System.getProperty("user.home"), ".cache/matugen/vivi-desktop.colors")

    data class Schemes(
        val light: Map<String, Color>,
        val dark: Map<String, Color>,
        val modifiedMs: Long,
    )

    fun load(file: File = defaultFile): Schemes? {
        if (!file.isFile) return null
        val light = mutableMapOf<String, Color>()
        val dark = mutableMapOf<String, Color>()
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEachLine
            val key = line.substring(0, eq).trim()
            val hex = line.substring(eq + 1).trim()
            val color = parseHex(hex) ?: return@forEachLine
            when {
                key.startsWith("light.") -> light[key.removePrefix("light.")] = color
                key.startsWith("dark.") -> dark[key.removePrefix("dark.")] = color
            }
        }
        if (light.isEmpty() && dark.isEmpty()) return null
        return Schemes(light = light, dark = dark, modifiedMs = file.lastModified())
    }

    fun ColorScheme.applyRoles(roles: Map<String, Color>): ColorScheme {
        fun c(name: String) = roles[name]
        return copy(
            primary = c("primary") ?: primary,
            onPrimary = c("onPrimary") ?: onPrimary,
            primaryContainer = c("primaryContainer") ?: primaryContainer,
            onPrimaryContainer = c("onPrimaryContainer") ?: onPrimaryContainer,
            secondary = c("secondary") ?: secondary,
            onSecondary = c("onSecondary") ?: onSecondary,
            secondaryContainer = c("secondaryContainer") ?: secondaryContainer,
            onSecondaryContainer = c("onSecondaryContainer") ?: onSecondaryContainer,
            tertiary = c("tertiary") ?: tertiary,
            onTertiary = c("onTertiary") ?: onTertiary,
            tertiaryContainer = c("tertiaryContainer") ?: tertiaryContainer,
            onTertiaryContainer = c("onTertiaryContainer") ?: onTertiaryContainer,
            error = c("error") ?: error,
            onError = c("onError") ?: onError,
            errorContainer = c("errorContainer") ?: errorContainer,
            onErrorContainer = c("onErrorContainer") ?: onErrorContainer,
            background = c("background") ?: background,
            onBackground = c("onBackground") ?: onBackground,
            surface = c("surface") ?: surface,
            onSurface = c("onSurface") ?: onSurface,
            surfaceVariant = c("surfaceVariant") ?: surfaceVariant,
            onSurfaceVariant = c("onSurfaceVariant") ?: onSurfaceVariant,
            outline = c("outline") ?: outline,
            outlineVariant = c("outlineVariant") ?: outlineVariant,
            inverseSurface = c("inverseSurface") ?: inverseSurface,
            inverseOnSurface = c("inverseOnSurface") ?: inverseOnSurface,
            inversePrimary = c("inversePrimary") ?: inversePrimary,
            surfaceTint = c("surfaceTint") ?: c("primary") ?: surfaceTint,
            scrim = c("scrim") ?: scrim,
            surfaceBright = c("surfaceBright") ?: surfaceBright,
            surfaceDim = c("surfaceDim") ?: surfaceDim,
            surfaceContainerLowest = c("surfaceContainerLowest") ?: surfaceContainerLowest,
            surfaceContainerLow = c("surfaceContainerLow") ?: surfaceContainerLow,
            surfaceContainer = c("surfaceContainer") ?: surfaceContainer,
            surfaceContainerHigh = c("surfaceContainerHigh") ?: surfaceContainerHigh,
            surfaceContainerHighest = c("surfaceContainerHighest") ?: surfaceContainerHighest,
        )
    }

    private fun parseHex(raw: String): Color? {
        val h = raw.removePrefix("#").trim()
        val value = when (h.length) {
            6 -> h.toLongOrNull(16)?.or(0xFF000000L)
            8 -> h.toLongOrNull(16)
            else -> null
        } ?: return null
        return Color(value)
    }
}

/** Polls the matugen colours file so wallpaper regenerations retint the app live. */
@Composable
fun rememberMatugenSchemes(enabled: Boolean): MatugenColors.Schemes? {
    var schemes by remember { mutableStateOf<MatugenColors.Schemes?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(enabled) {
        if (!enabled) {
            schemes = null
            return@DisposableEffect onDispose { }
        }
        schemes = MatugenColors.load()
        val job = scope.launch {
            var lastMs = schemes?.modifiedMs ?: 0L
            while (isActive) {
                delay(1200)
                val file = MatugenColors.defaultFile
                val mod = withContext(Dispatchers.IO) {
                    if (file.isFile) file.lastModified() else 0L
                }
                if (mod != lastMs) {
                    val next = withContext(Dispatchers.IO) { MatugenColors.load() }
                    schemes = next
                    lastMs = next?.modifiedMs ?: mod
                }
            }
        }
        onDispose { job.cancel() }
    }
    return schemes
}
