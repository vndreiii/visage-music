package com.music.vivi.compose.ui.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.ui.motion.VisageMotion

/**
 * Floats over every page while the Tag Manager is working in the background (auto-tagging or writing tags), or has
 * matches waiting for review. Clicking it opens the Tag Manager, which shows the progress or the review.
 */
@Composable
fun TagWorkFab(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val st = TagManagerHost.state
    val autoRunning = st?.autoRunning == true
    val writing = st?.busy == true
    val running = autoRunning || writing
    val waiting = !running && st?.proposals?.isNotEmpty() == true
    val done = if (writing) st?.progressDone ?: 0 else st?.autoDone ?: 0
    val total = if (writing) st?.progressTotal ?: 0 else st?.autoTotal ?: 0
    AnimatedVisibility(
        visible = running || waiting,
        enter = scaleIn(VisageMotion.spatialDefault(), initialScale = 0.6f) + fadeIn(),
        exit = scaleOut(VisageMotion.spatialDefault(), targetScale = 0.6f) + fadeOut(),
        modifier = modifier,
    ) {
        val motion = rememberInfiniteTransition(label = "tagFab")
        val pulse by motion.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
        val wiggle by motion.animateFloat(-12f, 12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "wiggle")
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(20.dp),
            color = if (waiting) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (waiting) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    if (running) {
                        if (total > 0) CircularProgressIndicator(progress = { done.toFloat() / total }, Modifier.size(44.dp), strokeWidth = 3.dp)
                        else CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 3.dp)
                        Icon(Icons.Outlined.AutoFixHigh, "Tag Manager is working", Modifier.size(22.dp).scale(pulse).rotate(wiggle))
                    } else {
                        Icon(Icons.Outlined.TaskAlt, "Matches ready to review", Modifier.size(26.dp))
                    }
                }
                AnimatedVisibility(true, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    Text(
                        when {
                            writing -> "Writing tags $done/$total"
                            autoRunning -> "Auto-tagging $done/$total"
                            else -> "Review matches"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 14.dp),
                    )
                }
            }
        }
    }
}
