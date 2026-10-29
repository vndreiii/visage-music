package com.music.vivi.compose.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A slider setting: title and value on one line, slider below, trailing reset icon while the value differs from [default].
 * [onChange] fires while dragging (live preview), [onCommit] on release.
 */
@Composable
internal fun LiveSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    default: Float? = null,
) {
    var last by remember(value) { mutableFloatStateOf(value) }
    SettingsRowFrame(
        icon, title, subtitle, onClick = null,
        belowSubtitle = {
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = { last = it; onChange(it) },
                onValueChangeFinished = { onCommit(last) },
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        extraText = null,
    ) {
        Text(label(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 56.dp))
        if (default != null && kotlin.math.abs(value - default) > 1e-4f) {
            IconButton(onClick = { onChange(default); onCommit(default) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Restore, "Reset to default", modifier = Modifier.size(18.dp))
            }
        }
    }
}
