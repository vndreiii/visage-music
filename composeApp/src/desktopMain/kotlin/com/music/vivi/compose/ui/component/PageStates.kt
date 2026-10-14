package com.music.vivi.compose.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Reserve the page's structure while loading, with one gentle shared pulse. */
@Composable
fun PageLoadingState(title: String = "Loading music", detail: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "pageLoading")
    val alpha by transition.animateFloat(
        0.35f, 0.75f,
        infiniteRepeatable(com.music.vivi.compose.ui.motion.VisageMotion.pulse(), RepeatMode.Reverse),
        label = "placeholderPulse",
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column(
        Modifier.fillMaxSize().padding(24.dp).clip(RoundedCornerShape(24.dp)).semantics { contentDescription = title },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VisageLoadingIndicator(modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (detail) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(156.dp).background(color, RoundedCornerShape(28.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth(.65f).height(28.dp).background(color, RoundedCornerShape(14.dp)))
                    Box(Modifier.fillMaxWidth(.4f).height(18.dp).background(color, RoundedCornerShape(9.dp)))
                    Box(Modifier.width(140.dp).height(48.dp).background(color, RoundedCornerShape(24.dp)))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { Box(Modifier.weight(1f).height(172.dp).background(color, RoundedCornerShape(24.dp))) }
            }
        }
        repeat(5) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(color, RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(.55f).height(16.dp).background(color, RoundedCornerShape(8.dp)))
                    Box(Modifier.fillMaxWidth(.3f).height(12.dp).background(color, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

@Composable
fun PageEmptyState(title: String, message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Outlined.LibraryMusic, null, Modifier.padding(24.dp).size(32.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
