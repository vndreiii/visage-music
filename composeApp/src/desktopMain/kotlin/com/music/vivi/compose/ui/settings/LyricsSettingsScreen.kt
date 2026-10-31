package com.music.vivi.compose.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurLinear
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterTiltShift
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.lyricsync.LyricsParser
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.ui.player.lyrics.LyricsAccent
import com.music.vivi.compose.ui.player.lyrics.LyricsAnimation
import com.music.vivi.compose.ui.player.lyrics.LyricsBackground
import com.music.vivi.compose.ui.player.lyrics.LyricsBackgroundMode
import com.music.vivi.compose.ui.player.lyrics.LyricsConfig
import com.music.vivi.compose.ui.player.lyrics.LyricsCutsScene
import com.music.vivi.compose.ui.player.lyrics.Lyrics3DScene
import com.music.vivi.compose.ui.player.lyrics.LyricsFont
import com.music.vivi.compose.ui.player.lyrics.LyricsGapStyle
import com.music.vivi.compose.ui.player.lyrics.LyricsList
import com.music.vivi.compose.ui.player.lyrics.LyricsPreset
import com.music.vivi.compose.ui.player.lyrics.LyricsRomanizer
import com.music.vivi.compose.ui.player.lyrics.LyricsScene
import com.music.vivi.compose.ui.player.lyrics.LyricsTextCase
import com.music.vivi.compose.ui.player.lyrics.LyricsTextStyle
import com.music.vivi.compose.ui.player.lyrics.LyricsVisibility
import com.music.vivi.compose.ui.player.lyrics.LyricsWeight
import com.music.vivi.compose.ui.player.lyrics.lyricsAccent
import com.music.vivi.compose.ui.player.lyrics.lyricsConfig
import com.music.vivi.compose.ui.player.lyrics.saveLyricsConfig
import com.music.vivi.compose.ui.settings.preview.SettingsPreviewFrame
import org.koin.compose.koinInject

/** Sample song for the live preview: word-synced, line-synced (estimated), background vocal, translation, kana line, long gap. */
internal const val PREVIEW_LRC = """
[00:01.00]<00:01.00>City <00:01.50>lights <00:02.10>are <00:02.50>fading <00:03.30>slow<00:04.00>
[00:04.20]<00:04.20>Hold <00:04.70>the <00:05.00>night <00:05.60>a <00:05.80>little <00:06.50>longer<00:07.40>
[00:07.60]Every heartbeat sings along
[00:07.70]{bg}<00:07.70>(ooh <00:08.30>ooh)<00:09.00>
[00:10.50]Dancing through the afterglow
[00:10.50]Bailando en el resplandor
[00:13.50]ひかりのなかで
[00:20.50]<00:20.50>Hold <00:21.10>me <00:21.50>till <00:22.00>the <00:22.30>morning <00:23.30>comes<00:24.20>
"""
private const val PREVIEW_LOOP_MS = 27_000L

@Composable
internal fun rememberPreviewClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (((it - start) / 1_000_000L) % PREVIEW_LOOP_MS).toFloat() }
    }
    return t
}

/** A small generated cover saved once as a file, so "Blurred cover" has something to blur in the preview. */
internal val previewCoverUrl: String? by lazy {
    runCatching {
        val img = java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(0xFF5E7C), 256f, 256f, java.awt.Color(0x3A2BD6))
        g.fillRect(0, 0, 256, 256)
        g.paint = java.awt.Color(0xFFC857)
        g.fillOval(120, 30, 150, 150)
        g.dispose()
        val f = java.io.File.createTempFile("visage-lyrics-preview", ".png").apply { deleteOnExit() }
        javax.imageio.ImageIO.write(img, "png", f)
        f.toURI().toString()
    }.getOrNull()
}

private val WORD_LEVEL = setOf(LyricsProvider.BETTER_LYRICS, LyricsProvider.PAXSENIX, LyricsProvider.YOULY_PLUS, LyricsProvider.MUSIXMATCH)

@Composable
fun LyricsSettingsScreen(navController: NavController) {
    val prefs = koinInject<DesktopPrefs>()
    val version = rememberPrefsVersion(prefs)
    val saved = remember(version) { prefs.lyricsConfig() }
    // Sliders update `live` while dragging (instant preview) and persist on release.
    var live by remember(saved) { mutableStateOf(saved) }
    fun commit(c: LyricsConfig) {
        live = c
        prefs.saveLyricsConfig(c)
    }
    var confirm by remember { mutableStateOf<String?>(null) }

    SettingsPage(title = "Lyrics", onBack = { navController.navigateUp() }) {
        SettingsPreviewFrame(
            pageKey = "lyrics",
            caption = "Sample song",
            virtualWidth = 640.dp,
            virtualHeight = 250.dp,
        ) { LyricsPreview(live, Modifier.fillMaxSize()) }

        SettingsSection("Source") {
            val provider = prefs.enumPref(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB)
            SettingsDropdownRow(
                Icons.Outlined.Lyrics, "Lyrics source",
                options = LyricsProvider.entries.map {
                    val word = it in WORD_LEVEL
                    DropdownOption(
                        it, it.label,
                        hint = if (it == LyricsProvider.BETTER_LYRICS) "word-level timing (TTML)" else if (word) "word-level timing" else "line-level timing",
                        group = if (word) "Word-level" else "Line-level",
                    )
                }.sortedBy { it.group != "Word-level" },
                selected = provider,
            ) { prefs.setString(PrefKeys.LyricsProvider, it.name) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Speed, "Estimate word timing", "For line-level lyrics: spread each line over its words", live.estimateWords) {
                commit(live.copy(estimateWords = it))
            }
            SettingsDivider()
            LiveSlider(
                "Sync offset", live.syncOffsetMs.toFloat(), -1500f..1500f, 59, { "${it.toInt()} ms" },
                { live = live.copy(syncOffsetMs = it.toInt()) }, { commit(live.copy(syncOffsetMs = it.toInt())) },
                icon = Icons.Outlined.Timer, subtitle = "Positive shows lyrics later", default = 0f,
            )
            SettingsDivider()
            SettingsRowFrame(Icons.Outlined.DeleteSweep, "Lyrics cache", "Downloaded lyrics are kept so songs open instantly", onClick = { confirm = "cache" }) {
                Text("Clear", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        SettingsSection("Look") {
            SettingsDropdownRow(
                Icons.Outlined.Style, "Preset",
                options = LyricsPreset.entries.map { DropdownOption<LyricsPreset?>(it, it.label) },
                selected = null,
                subtitle = "Apply a whole look; the settings below then adjust it",
            ) { p -> if (p != null) commit(p.applyTo(live)) }
            SettingsDivider()
            SettingsRowFrame(Icons.Outlined.Restore, "Reset look", "Back to the default look; alignment and sync offset stay", onClick = { confirm = "reset" })
        }

        SettingsSection("Text") {
            SettingsDropdownRow(
                Icons.Outlined.TextFields, "Font",
                options = LyricsFont.entries.map { DropdownOption(it, it.label) }, selected = live.font,
            ) { commit(live.copy(font = it)) }
            SettingsDivider()
            SettingsDropdownRow(
                Icons.Outlined.Brush, "Text style",
                options = LyricsTextStyle.entries.map { DropdownOption(it, it.label) }, selected = live.textStyle,
            ) { commit(live.copy(textStyle = it)) }
            SettingsDivider()
            ChoiceRow(Icons.Outlined.FormatBold, "Weight", LyricsWeight.entries.map { it to it.label }, live.weight) { commit(live.copy(weight = it)) }
            SettingsDivider()
            SettingsDropdownRow(
                Icons.Outlined.TextFormat, "Case",
                options = LyricsTextCase.entries.map { DropdownOption(it, it.label) }, selected = live.textCase,
            ) { commit(live.copy(textCase = it)) }
            SettingsDivider()
            ChoiceRow(
                Icons.Outlined.FormatAlignCenter, "Alignment",
                listOf(LyricsTextAlign.LEFT to "Left", LyricsTextAlign.CENTER to "Center", LyricsTextAlign.RIGHT to "Right"),
                live.align,
            ) { commit(live.copy(align = it)) }
            SettingsDivider()
            LiveSlider(
                "Size", live.fontSizeSp, 16f..56f, 39, { "${it.toInt()} sp" },
                { live = live.copy(fontSizeSp = it) }, { commit(live.copy(fontSizeSp = it)) },
                icon = Icons.Outlined.FormatSize, subtitle = "Translation and romanization follow", default = LyricsConfig().fontSizeSp,
            )
            SettingsDivider()
            LiveSlider(
                "Line spacing", live.lineSpacingDp, 0f..40f, 19, { "${it.toInt()} dp" },
                { live = live.copy(lineSpacingDp = it) }, { commit(live.copy(lineSpacingDp = it)) },
                icon = Icons.Outlined.FormatLineSpacing, default = LyricsConfig().lineSpacingDp,
            )
        }

        SettingsSection("Lines") {
            SettingsDropdownRow(
                Icons.Outlined.ViewDay, "Lines shown",
                options = LyricsVisibility.entries.map { DropdownOption(it, it.label) }, selected = live.visibility,
            ) { commit(live.copy(visibility = it)) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.BlurOn, "Fade edges", "Soft fade at the top and bottom", live.fadeEdges) { commit(live.copy(fadeEdges = it)) }
            SettingsDivider()
            LiveSlider(
                "Active line position", live.anchor, 0.15f..0.7f, 10, { "${(it * 100).toInt()}%" },
                { live = live.copy(anchor = it) }, { commit(live.copy(anchor = it)) },
                icon = Icons.Outlined.VerticalAlignCenter, default = LyricsConfig().anchor,
            )
            SettingsDivider()
            SwitchItem(Icons.Outlined.Lyrics, "Background vocals", "Backing and echo vocals in smaller italics", live.showBackgroundVocals) {
                commit(live.copy(showBackgroundVocals = it))
            }
            SettingsDivider()
            SettingsDropdownRow(
                Icons.Outlined.GraphicEq, "Instrumental break",
                options = LyricsGapStyle.entries.map { DropdownOption(it, it.label) }, selected = live.gapStyle,
            ) { commit(live.copy(gapStyle = it)) }
        }

        SettingsSection("Animation") {
            SettingsDropdownRow(
                Icons.Outlined.AutoAwesome, "Animation style",
                options = LyricsAnimation.entries.map { DropdownOption(it, it.label) }, selected = live.animation,
            ) { commit(live.copy(animation = it)) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.GraphicEq, "Word-by-word highlight", "Sweep each word as it is sung", live.wordSync) { commit(live.copy(wordSync = it)) }
            SettingsDivider()
            ChoiceRow(Icons.Outlined.Palette, "Accent", LyricsAccent.entries.map { it to it.label }, live.accent) { commit(live.copy(accent = it)) }
            SettingsDivider()
            LiveSlider(
                "Motion strength", live.animStrength, 0f..2f, 19, { "${(it * 100).toInt()}%" },
                { live = live.copy(animStrength = it) }, { commit(live.copy(animStrength = it)) },
                icon = Icons.Outlined.Speed, default = LyricsConfig().animStrength,
            )
            SettingsDivider()
            LiveSlider(
                "Glow", live.glow, 0f..1f, 19, { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" },
                { live = live.copy(glow = it) }, { commit(live.copy(glow = it)) },
                icon = Icons.Outlined.WbSunny, default = LyricsConfig().glow,
            )
            SettingsDivider()
            LiveSlider(
                "Blur by distance", live.blur, 0f..1f, 19, { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" },
                { live = live.copy(blur = it) }, { commit(live.copy(blur = it)) },
                icon = Icons.Outlined.BlurLinear, default = LyricsConfig().blur,
            )
            SettingsDivider()
            LiveSlider(
                "Other lines", live.inactiveBrightness, 0.1f..0.95f, 16, { "${(it * 100).toInt()}%" },
                { live = live.copy(inactiveBrightness = it) }, { commit(live.copy(inactiveBrightness = it)) },
                icon = Icons.Outlined.Contrast, subtitle = "Brightness of lines that are not active", default = LyricsConfig().inactiveBrightness,
            )
        }

        SettingsSection("Background") {
            SettingsDropdownRow(
                Icons.Outlined.Wallpaper, "Panel background",
                options = LyricsBackgroundMode.entries.map { DropdownOption(it, it.label) }, selected = live.background,
            ) { commit(live.copy(background = it)) }
            AnimatedVisibility(live.background != LyricsBackgroundMode.NONE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    LiveSlider(
                        "Intensity", live.backgroundIntensity, 0f..1f, 19, { "${(it * 100).toInt()}%" },
                        { live = live.copy(backgroundIntensity = it) }, { commit(live.copy(backgroundIntensity = it)) },
                        icon = Icons.Outlined.Opacity, default = LyricsConfig().backgroundIntensity,
                    )
                }
            }
        }

        SettingsSection("Scene") {
            ChoiceRow(Icons.Outlined.ViewInAr, "Scene", LyricsScene.entries.map { it to it.label }, live.scene) { commit(live.copy(scene = it)) }
            AnimatedVisibility(live.scene == LyricsScene.ROW, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    LiveSlider(
                        "Close-up fade", live.sceneDissolve, 0.55f..0.95f, 16, { "${(it * 100).toInt()}%" },
                        { live = live.copy(sceneDissolve = it) }, { commit(live.copy(sceneDissolve = it)) },
                        icon = Icons.Outlined.FilterTiltShift, default = LyricsConfig().sceneDissolve,
                    )
                    SettingsDivider()
                    LiveSlider(
                        "Linger time", live.sceneLingerSec, 0.5f..8f, 15, { "${((it * 10).toInt()) / 10f} s" },
                        { live = live.copy(sceneLingerSec = it) }, { commit(live.copy(sceneLingerSec = it)) },
                        icon = Icons.Outlined.Timer, subtitle = "How long a line stays after its last word", default = LyricsConfig().sceneLingerSec,
                    )
                }
            }
            AnimatedVisibility(live.scene == LyricsScene.CUTS, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.ContentCut, "Rings cut the lyrics", "The rings slice the lettering; off keeps the text whole", live.cutsSliceText) {
                        commit(live.copy(cutsSliceText = it))
                    }
                }
            }
            AnimatedVisibility(live.scene != LyricsScene.NONE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    AdvancedExpander(Icons.Outlined.Tune, "Advanced", "Overshoot, frequency and damping of the camera spring") {
                        LiveSlider(
                            "Inertial overshoot", live.bounceAmp, 0f..2.5f, 25, { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" },
                            { live = live.copy(bounceAmp = it) }, { commit(live.copy(bounceAmp = it)) },
                            icon = Icons.Outlined.Vibration, default = LyricsConfig().bounceAmp,
                        )
                        SettingsDivider()
                        LiveSlider(
                            "Bounce frequency", live.bounceFreq, 1.0f..4.0f, 15, { "${((it * 10).toInt()) / 10f} Hz" },
                            { live = live.copy(bounceFreq = it) }, { commit(live.copy(bounceFreq = it)) },
                            icon = Icons.Outlined.Waves, default = LyricsConfig().bounceFreq,
                        )
                        SettingsDivider()
                        LiveSlider(
                            "Bounce damping", live.bounceDecay, 1.5f..8.0f, 13, { "${((it * 10).toInt()) / 10f}" },
                            { live = live.copy(bounceDecay = it) }, { commit(live.copy(bounceDecay = it)) },
                            icon = Icons.AutoMirrored.Outlined.TrendingDown, default = LyricsConfig().bounceDecay,
                        )
                    }
                }
            }
        }

        SettingsSection("Translation and romanization") {
            SwitchItem(Icons.Outlined.Translate, "Translation line", "Show the translation or romanization under a line when there is one", live.showTranslation) {
                commit(live.copy(showTranslation = it))
            }
            SettingsDivider()
            val romanize = prefs.getBoolean(PrefKeys.LyricsRomanize, false)
            val auto = prefs.getBoolean(PrefKeys.LyricsAutoTranslate, false)
            SwitchItem(
                Icons.Outlined.SortByAlpha, "Romanize lyrics",
                "Japanese, Korean and Cyrillic in Latin letters; kanji uses the provider below", romanize,
            ) {
                prefs.setBoolean(PrefKeys.LyricsRomanize, it)
                if (it) commit(live.copy(showTranslation = true))
            }
            SettingsDivider()
            SwitchItem(
                Icons.Outlined.GTranslate, "Auto-translate lyrics",
                "Translate lines that have no translation of their own", auto,
            ) {
                prefs.setBoolean(PrefKeys.LyricsAutoTranslate, it)
                if (it) commit(live.copy(showTranslation = true))
            }
            AnimatedVisibility(auto, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    TranslateLanguageRow(prefs)
                }
            }
            AnimatedVisibility(auto || romanize, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    SettingsDivider()
                    LyricsAiProviderSettings(prefs)
                }
            }
        }

        SettingsSection("Interaction") {
            SwitchItem(Icons.Outlined.TouchApp, "Tap a line to seek", "Jump to a line by clicking it", live.tapToSeek) { commit(live.copy(tapToSeek = it)) }
            SettingsDivider()
            SwitchItem(Icons.Outlined.Sync, "Resume following", "After scrolling by hand, return to the active line after a few seconds", live.autoResume) {
                commit(live.copy(autoResume = it))
            }
        }
    }

    val what = confirm
    if (what != null) {
        val lyricsCache = koinInject<com.music.vivi.database.LyricsCacheRepository>()
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "cache") "Clear the lyrics cache?" else "Reset the lyrics look?") },
            text = {
                Text(
                    if (what == "cache") "Cached lyrics are deleted and downloaded again the next time a song plays."
                    else "Every text, animation, background and scene setting goes back to its default. Alignment and sync offset stay.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "cache") Thread { runCatching { lyricsCache.purgeLyrics() } }.start()
                    else commit(LyricsConfig.Default.copy(align = live.align, syncOffsetMs = live.syncOffsetMs))
                    confirm = null
                }) { Text(if (what == "cache") "Clear" else "Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LyricsPreview(cfg: LyricsConfig, modifier: Modifier = Modifier) {
    val prefs = koinInject<DesktopPrefs>()
    // Observe prefs here too: this composable takes the config as a parameter, so without its own listener a change that
    // is not part of `cfg` (romanization, player background style) would not reach it until the page was left.
    val version = rememberPrefsVersion(prefs)
    val romanize = remember(version) { prefs.getBoolean(PrefKeys.LyricsRomanize, false) }
    val bgStyle = remember(version) { prefs.enumPref(PrefKeys.PlayerBackgroundStyle, com.music.vivi.compose.prefs.LookDefaults.PlayerStyle) }
    val parsed = remember(romanize) {
        val p = LyricsParser.parse(PREVIEW_LRC.trim())
        if (!romanize) p else p.copy(
            lines = p.lines.map { l ->
                if (l.isBackground || !l.translation.isNullOrBlank()) l
                else LyricsRomanizer.romanize(l.text)?.takeUnless { it.equals(l.text, ignoreCase = true) }?.let { l.copy(translation = it) } ?: l
            },
        )
    }
    val timeline = remember(parsed, cfg.estimateWords, cfg.showBackgroundVocals, cfg.gapStyle) {
        LyricsTimeline.build(
            parsed, cfg.estimateWords,
            if (cfg.gapStyle == LyricsGapStyle.NONE) Long.MAX_VALUE / 4 else LyricsTimeline.DEFAULT_MIN_GAP_MS,
            cfg.showBackgroundVocals,
        )
    }
    val base = rememberPreviewClock()
    val offset by rememberUpdatedState(cfg.syncOffsetMs)
    val clock = remember { derivedStateOf { base.value + offset } }
    val accent = lyricsAccent(cfg.accent)
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF0F1115))) {
        LyricsBackground(
            mode = cfg.background,
            intensity = cfg.backgroundIntensity,
            artworkUrl = previewCoverUrl,
            palette = LocalArtworkPalette.current,
            playerBackgroundStyle = bgStyle,
            corner = 18,
        )
        when {
            cfg.scene == LyricsScene.ROW && timeline.isSynced -> {
                Lyrics3DScene(timeline = timeline, cfg = cfg, clock = clock, accent = accent, sizeScale = 0.62f)
            }
            cfg.scene == LyricsScene.CUTS -> {
                LyricsCutsScene(timeline = timeline, cfg = cfg, clock = clock, accent = accent, palette = LocalArtworkPalette.current, sizeScale = 0.62f)
            }
            else -> {
                LyricsList(timeline = timeline, cfg = cfg, clock = clock, accent = accent, onSeek = null, sizeScale = 0.62f)
            }
        }
    }
}
