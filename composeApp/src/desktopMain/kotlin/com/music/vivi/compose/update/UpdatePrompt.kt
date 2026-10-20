package com.music.vivi.compose.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.ui.expressive.ExpressiveButton
import com.music.vivi.compose.ui.expressive.ExpressiveSize
import com.music.vivi.compose.ui.expressive.ExpressiveStyle
import com.music.vivi.compose.ui.motion.VisageMotion
import org.koin.compose.koinInject

/**
 * "New update available" toast, bottom-left. Drop it as the last child of a full-size Box
 * over the app content; it does not block input when hidden.
 */
@Composable
fun UpdatePrompt() {
    val prefs = koinInject<DesktopPrefs>()
    val service = remember(prefs) { UpdateService.start(prefs) }
    val status by service.status.collectAsState()
    val offer = status.offer
    val busy = status.phase == UpdatePhase.Downloading || status.phase == UpdatePhase.Restarting

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
        AnimatedVisibility(
            visible = status.promptVisible && offer != null,
            enter = fadeIn(VisageMotion.fadeInSpec()) + slideInHorizontally(VisageMotion.settle()) { -it / 3 },
            exit = fadeOut(VisageMotion.fadeOutSpec<Float>()),
        ) {
            if (offer == null) return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(max = 380.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "New update available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "VIsage ${offer.version} is ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (offer.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            offer.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (busy) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (status.phase == UpdatePhase.Restarting) "Restarting…"
                            else "Downloading ${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    status.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveButton(
                            text = "Skip",
                            onClick = service::dismiss,
                            size = ExpressiveSize.XSmall,
                            style = ExpressiveStyle.Text,
                            enabled = !busy,
                        )
                        Spacer(Modifier.width(4.dp))
                        ExpressiveButton(
                            text = "Later",
                            onClick = service::remindLater,
                            size = ExpressiveSize.XSmall,
                            style = ExpressiveStyle.Text,
                            enabled = !busy,
                        )
                        Spacer(Modifier.width(8.dp))
                        val installs = service.primaryAction(offer) == UpdateAction.InstallAndRestart
                        ExpressiveButton(
                            text = if (installs) "Update now" else "Open release page",
                            onClick = service::install,
                            size = ExpressiveSize.Small,
                            enabled = !busy,
                        )
                    }
                }
            }
        }
    }
}
