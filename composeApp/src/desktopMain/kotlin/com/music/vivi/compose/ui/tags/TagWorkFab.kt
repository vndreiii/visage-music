package com.music.vivi.compose.ui.tags

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.runtime.Composable
import com.music.vivi.compose.ui.component.BackgroundTask

/**
 * The Tag Manager's work as a [BackgroundTask] for the shared floating button: auto-tagging, writing tags, or matches
 * waiting for review. Null when there is nothing to show. Opening it goes back to the Tag Manager.
 */
@Composable
fun tagWorkTask(onOpen: () -> Unit): BackgroundTask? {
    val st = TagManagerHost.state
    val autoRunning = st?.autoRunning == true
    val writing = st?.busy == true
    val running = autoRunning || writing
    val waiting = !running && st?.proposals?.isNotEmpty() == true
    if (!running && !waiting) return null
    val done = if (writing) st?.progressDone ?: 0 else st?.autoDone ?: 0
    val total = if (writing) st?.progressTotal ?: 0 else st?.autoTotal ?: 0
    return BackgroundTask(
        id = "tags",
        label = when {
            writing -> "Writing tags $done/$total"
            autoRunning -> "Auto-tagging $done/$total"
            else -> "Review matches"
        },
        icon = Icons.Outlined.AutoFixHigh,
        done = done,
        total = total,
        attention = waiting,
        onOpen = onOpen,
    )
}
