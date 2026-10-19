package com.music.vivi.compose.keys

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.motion.VisageMotion

/** A single physical-looking key. */
@Composable
fun KeyCap(label: String, modifier: Modifier = Modifier, emphasized: Boolean = false, dimmed: Boolean = false) {
    val bg by animateColorAsState(
        if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        VisageMotion.effectsDefault(), label = "capBg",
    )
    val fg by animateColorAsState(
        if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        VisageMotion.effectsDefault(), label = "capFg",
    )
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.alpha(if (dimmed) 0.5f else 1f).heightIn(min = 28.dp).widthIn(min = 28.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(PaddingValues(horizontal = 9.dp, vertical = 4.dp)),
        )
    }
}

/** All keys of a binding as keycaps joined by "+" gaps. */
@Composable
fun BindingCaps(binding: KeyBinding, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        binding.keycaps().forEach { KeyCap(it, dimmed = dimmed) }
    }
}

/** The "Press a key…" state: a keycap-shaped slot that breathes while waiting for input. */
@Composable
fun CapturePrompt(text: String = "Press a key…", modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "capture")
    val a by pulse.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "captureAlpha",
    )
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.alpha(a),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}
