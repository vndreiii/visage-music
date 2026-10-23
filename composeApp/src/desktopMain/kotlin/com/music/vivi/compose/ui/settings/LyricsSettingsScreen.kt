package com.music.vivi.compose.ui.settings

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Blender
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.lyricsync.LyricsParser
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.LyricsProvider
import com.music.vivi.compose.prefs.LyricsTextAlign
import com.music.vivi.compose.prefs.PlayerBackgroundStyle
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.compose.theme.LocalArtworkPalette
import com.music.vivi.compose.ui.component.smoothWheelScroll
import com.music.vivi.compose.ui.player.lyrics.LyricsAccent
import com.music.vivi.compose.ui.player.lyrics.LyricsAnimation
import com.music.vivi.compose.ui.player.lyrics.LyricsBackground
import com.music.vivi.compose.ui.player.lyrics.LyricsBackgroundMode
import com.music.vivi.compose.ui.player.lyrics.LyricsConfig
import com.music.vivi.compose.ui.player.lyrics.LyricsFont
import com.music.vivi.compose.ui.player.lyrics.LyricsGapStyle
import com.music.vivi.compose.ui.player.lyrics.LyricsList
import com.music.vivi.compose.ui.player.lyrics.LyricsPreset
import com.music.vivi.compose.ui.player.lyrics.LyricsTextCase
import com.music.vivi.compose.ui.player.lyrics.LyricsTextStyle
import com.music.vivi.compose.ui.player.lyrics.LyricsVisibility
import com.music.vivi.compose.ui.player.lyrics.LyricsWeight
import com.music.vivi.compose.ui.player.lyrics.lyricsAccent
import com.music.vivi.compose.ui.player.lyrics.lyricsConfig
import com.music.vivi.compose.ui.player.lyrics.saveLyricsConfig
import org.koin.compose.koinInject

/** Sample song for the live preview: word-synced, line-synced (estimated), background vocal, translation, long gap. */
private const val PREVIEW_LRC = """
[00:01.00]<00:01.00>City <00:01.50>lights <00:02.10>are <00:02.50>fading <00:03.30>slow<00:04.00>
[00:04.20]<00:04.20>Hold <00:04.70>the <00:05.00>night <00:05.60>a <00:05.80>little <00:06.50>longer<00:07.40>
[00:07.60]Every heartbeat sings along
[00:07.70]{bg}<00:07.70>(ooh <00:08.30>ooh)<00:09.00>
[00:10.50]Dancing through the afterglow
[00:10.50]Bailando en el resplandor
[00:20.50]<00:20.50>Hold <00:21.10>me <00:21.50>till <00:22.00>the <00:22.30>morning <00:23.30>comes<00:24.20>
"""
private const val PREVIEW_LOOP_MS = 27_000L

@Composable
private fun rememberPreviewClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (((it - start) / 1_000_000L) % PREVIEW_LOOP_MS).toFloat() }
    }
    return t
}

@OptIn(ExperimentalLayoutApi::class)
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

    val adwaita = prefs.getBoolean(PrefKeys.AdwaitaInspired, false)
    val maxW = if (adwaita) 600.dp else 760.dp
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = maxW).fillMaxWidth().padding(horizontal = if (adwaita) 12.dp else 24.dp)) {
            SettingsBackHeader(title = "Lyrics", onBack = { navController.navigateUp() })
            LyricsPreview(live, Modifier.fillMaxWidth().height(230.dp).padding(top = 4.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().smoothWheelScroll(scroll).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = maxW).fillMaxWidth()
                    .padding(horizontal = if (adwaita) 12.dp else 24.dp).padding(bottom = 120.dp),
            ) {
                SettingsSection("Quick looks") {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LyricsPreset.entries.forEach { preset ->
                            AssistChip(
                                onClick = { commit(preset.applyTo(live)) },
                                label = { Text(preset.label) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        TextButton(onClick = {
                            commit(LyricsConfig.Default.copy(align = live.align, syncOffsetMs = live.syncOffsetMs))
                        }) {
                            androidx.compose.material3.Icon(Icons.Outlined.Restore, null, Modifier.padding(end = 6.dp))
                            Text("Reset to defaults")
                        }
                        val lyricsCache = koinInject<com.music.vivi.database.LyricsCacheRepository>()
                        TextButton(onClick = { Thread { runCatching { lyricsCache.purgeLyrics() } }.start() }) {
                            Text("Clear lyrics cache")
                        }
                    }
                }

                SettingsSection("Text") {
                    ChipChoiceRow(Icons.Outlined.TextFields, "Text style", LyricsTextStyle.entries.map { it to it.label }, live.textStyle) {
                        commit(live.copy(textStyle = it))
                    }
                    SettingsDivider()
                    ChipChoiceRow(Icons.Outlined.FormatSize, "Font", LyricsFont.entries.map { it to it.label }, live.font) {
                        commit(live.copy(font = it))
                    }
                    SettingsDivider()
                    ChoiceRow(null, "Weight", LyricsWeight.entries.map { it to it.label }, live.weight) { commit(live.copy(weight = it)) }
                    SettingsDivider()
                    ChoiceRow(null, "Case", LyricsTextCase.entries.map { it to it.label }, live.textCase) { commit(live.copy(textCase = it)) }
                    SettingsDivider()
                    ChoiceRow(
                        Icons.Outlined.FormatAlignCenter, "Alignment",
                        listOf(LyricsTextAlign.LEFT to "Left", LyricsTextAlign.CENTER to "Center", LyricsTextAlign.RIGHT to "Right"),
                        live.align,
                    ) { commit(live.copy(align = it)) }
                    LiveSlider("Size", live.fontSizeSp, 16f..56f, 39, { "${it.toInt()} sp" }, { live = live.copy(fontSizeSp = it) }) { commit(live.copy(fontSizeSp = it)) }
                    LiveSlider("Line spacing", live.lineSpacingDp, 0f..40f, 19, { "${it.toInt()} dp" }, { live = live.copy(lineSpacingDp = it) }) { commit(live.copy(lineSpacingDp = it)) }
                }

                SettingsSection("Animation") {
                    ChipChoiceRow(Icons.Outlined.AutoAwesome, "Preset", LyricsAnimation.entries.map { it to it.label }, live.animation) {
                        commit(live.copy(animation = it))
                    }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.GraphicEq, "Word-by-word highlight", "Sweep each word as it is sung", live.wordSync) { commit(live.copy(wordSync = it)) }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.Speed, "Estimate word timing", "For lyrics with only line times (e.g. LRCLIB): spread the line over its words", live.estimateWords) {
                        commit(live.copy(estimateWords = it))
                    }
                    SettingsDivider()
                    LiveSlider("Glow", live.glow, 0f..1f, 19, { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" }, { live = live.copy(glow = it) }) { commit(live.copy(glow = it)) }
                    LiveSlider("Blur by distance", live.blur, 0f..1f, 19, { if (it <= 0f) "Off" else "${(it * 100).toInt()}%" }, { live = live.copy(blur = it) }) { commit(live.copy(blur = it)) }
                    LiveSlider("Other lines", live.inactiveBrightness, 0.1f..0.95f, 16, { "${(it * 100).toInt()}%" }, { live = live.copy(inactiveBrightness = it) }) { commit(live.copy(inactiveBrightness = it)) }
                    LiveSlider("Motion strength", live.animStrength, 0f..2f, 19, { "${(it * 100).toInt()}%" }, { live = live.copy(animStrength = it) }) { commit(live.copy(animStrength = it)) }
                    SettingsDivider()
                    ChoiceRow(Icons.Outlined.Palette, "Accent", LyricsAccent.entries.map { it to it.label }, live.accent) { commit(live.copy(accent = it)) }
                }

                SettingsSection("Layout & behaviour") {
                    ChoiceRow(Icons.Outlined.ViewDay, "Lines shown", LyricsVisibility.entries.map { it to it.label }, live.visibility) {
                        commit(live.copy(visibility = it))
                    }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.BlurOn, "Fade edges", "Soft fade at the top and bottom", live.fadeEdges) { commit(live.copy(fadeEdges = it)) }
                    LiveSlider("Active line position", live.anchor, 0.15f..0.7f, 10, { "${(live.anchor * 100).toInt()}%" }, { live = live.copy(anchor = it) }) { commit(live.copy(anchor = it)) }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.TouchApp, "Tap a line to seek", null, live.tapToSeek) { commit(live.copy(tapToSeek = it)) }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.Speed, "Resume following automatically", "After scrolling by hand, return to the active line after a few seconds", live.autoResume) {
                        commit(live.copy(autoResume = it))
                    }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.Translate, "Translations", "Show the translation / romanisation line when the lyrics include one", live.showTranslation) {
                        commit(live.copy(showTranslation = it))
                    }
                    SettingsDivider()
                    SwitchItem(Icons.Outlined.Lyrics, "Background vocals", "Show backing / echo vocals in smaller italics", live.showBackgroundVocals) {
                        commit(live.copy(showBackgroundVocals = it))
                    }
                    LiveSlider("Sync offset", live.syncOffsetMs.toFloat(), -1500f..1500f, 59, { "${it.toInt()} ms" }, { live = live.copy(syncOffsetMs = it.toInt()) }) { commit(live.copy(syncOffsetMs = it.toInt())) }
                }

                SettingsSection("Instrumental break") {
                    ChipChoiceRow(Icons.Outlined.GraphicEq, "Indicator", LyricsGapStyle.entries.map { it to it.label }, live.gapStyle) {
                        commit(live.copy(gapStyle = it))
                    }
                }

                SettingsSection("Background") {
                    ChipChoiceRow(Icons.Outlined.Wallpaper, "Panel background", LyricsBackgroundMode.entries.map { it to it.label }, live.background) {
                        commit(live.copy(background = it))
                    }
                    if (live.background != LyricsBackgroundMode.NONE) {
                        LiveSlider("Intensity", live.backgroundIntensity, 0f..1f, 19, { "${(it * 100).toInt()}%" }, { live = live.copy(backgroundIntensity = it) }) { commit(live.copy(backgroundIntensity = it)) }
                    }
                }

                SettingsSection("Source") {
                    ChipChoiceRow(
                        Icons.Outlined.AutoAwesome, "Provider",
                        LyricsProvider.entries.map { it to it.label },
                        prefs.enumPref(PrefKeys.LyricsProvider, LyricsProvider.LRCLIB),
                    ) { prefs.setString(PrefKeys.LyricsProvider, it.name) }
                    Text(
                        "Word-level timing: Better Lyrics (TTML), Paxsenix, YouLy+ and Musixmatch. LRCLIB, KuGou and Unison are line-level; enable \"Estimate word timing\" for those.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsPreview(cfg: LyricsConfig, modifier: Modifier = Modifier) {
    val parsed = remember { LyricsParser.parse(PREVIEW_LRC.trim()) }
    val timeline = remember(parsed, cfg.estimateWords, cfg.showBackgroundVocals, cfg.gapStyle) {
        LyricsTimeline.build(
            parsed, cfg.estimateWords,
            if (cfg.gapStyle == LyricsGapStyle.NONE) Long.MAX_VALUE / 4 else LyricsTimeline.DEFAULT_MIN_GAP_MS,
            cfg.showBackgroundVocals,
        )
    }
    val clock = rememberPreviewClock()
    val accent = lyricsAccent(cfg.accent)
    val prefs = koinInject<DesktopPrefs>()
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF0F1115))) {
        LyricsBackground(
            mode = cfg.background,
            intensity = cfg.backgroundIntensity,
            artworkUrl = null,
            palette = LocalArtworkPalette.current,
            playerBackgroundStyle = prefs.enumPref(PrefKeys.PlayerBackgroundStyle, PlayerBackgroundStyle.GRADIENT),
            corner = 24,
        )
        LyricsList(timeline = timeline, cfg = cfg, clock = clock, accent = accent, onSeek = null, sizeScale = 0.62f)
    }
}

@Composable
private fun LiveSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    var last by remember(value) { mutableFloatStateOf(value) }
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 96.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { last = it; onChange(it) },
            onValueChangeFinished = { onCommit(last) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(label(value), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 56.dp))
    }
}
