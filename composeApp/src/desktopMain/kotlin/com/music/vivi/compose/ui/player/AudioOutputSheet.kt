package com.music.vivi.compose.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import com.music.vivi.compose.ui.player.PlayerSeekBar
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.compose.audio.AudioOutputDevice
import com.music.vivi.compose.audio.PipeWireAudio
import com.music.vivi.compose.prefs.AudioQuality
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Audio output menu anchored to the mini-player speaker control.
 *
 * Uses [Popup] (not [Dialog] / Material DropdownMenu) — DropdownMenu crashes on
 * Compose Desktop with unbounded max-height; Dialog was a centered sheet.
 */
@Composable
fun AudioOutputSheet(onDismiss: () -> Unit) {
    val controller = LocalPlaybackController.current
    val prefs = koinInject<DesktopPrefs>()
    val scope = rememberCoroutineScope()
    val volume by controller.volume.collectAsState()
    var scrubVol by remember { mutableFloatStateOf(volume) }
    var scrubbing by remember { mutableStateOf(false) }
    val displayVol = if (scrubbing) scrubVol else volume

    var devices by remember { mutableStateOf<List<AudioOutputDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var quality by remember {
        mutableStateOf(
            runCatching {
                AudioQuality.valueOf(prefs.getString(PrefKeys.AudioQuality, AudioQuality.AUTO.name))
            }.getOrDefault(AudioQuality.AUTO),
        )
    }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            devices = PipeWireAudio.listSinks()
            if (devices.isEmpty()) error = "No PipeWire / Pulse sinks found (is pactl available?)"
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val current = devices.firstOrNull { it.isDefault } ?: devices.firstOrNull()

    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, -8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                sinkIcon(current?.iconName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current?.description ?: if (loading) "Looking for devices…" else "No output",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (current != null) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    "Connected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Volume", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            "${(displayVol * 100).toInt()}%",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                PlayerSeekBar(
                    progress = displayVol,
                    onScrub = {
                        scrubbing = true
                        scrubVol = it
                        controller.setVolume(it)
                    },
                    onScrubFinished = {
                        scrubbing = false
                        prefs.setFloat(PrefKeys.PlaybackVolume, scrubVol)
                    },
                    style = com.music.vivi.compose.prefs.SliderStyle.M3_EXPRESSIVE,
                    squiggly = false,
                    compact = true,
                )

                Spacer(Modifier.height(14.dp))
                Text("Audio Quality", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { q ->
                        val selected = quality == q
                        Surface(
                            onClick = {
                                quality = q
                                prefs.setString(PrefKeys.AudioQuality, q.name)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Text(
                                q.name.lowercase().replaceFirstChar { it.titlecase() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Outputs", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                devices.forEach { device ->
                    Surface(
                        onClick = {
                            scope.launch {
                                if (PipeWireAudio.setDefaultSink(device.name)) reload()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                sinkIcon(device.iconName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    device.state.lowercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (device.isDefault) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = "Default",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                RadioButton(selected = false, onClick = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sinkIcon(iconName: String?): ImageVector {
    val n = iconName.orEmpty().lowercase()
    return when {
        "headphone" in n || "headset" in n -> Icons.Outlined.Headphones
        else -> Icons.Outlined.Speaker
    }
}
