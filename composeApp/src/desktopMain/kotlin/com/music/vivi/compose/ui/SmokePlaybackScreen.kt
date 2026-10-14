package com.music.vivi.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.music.vivi.compose.LocalPlaybackController
import com.music.vivi.core.session.SessionManager
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Smoke screen proving Compose ↔ PlaybackController ↔ session wiring
 * before the full upstream UI port.
 */
@Composable
fun SmokePlaybackScreen() {
    val controller = LocalPlaybackController.current
    val sessionManager = koinInject<SessionManager>()
    val scope = rememberCoroutineScope()

    val state by controller.state.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val positionMs by controller.positionMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val favorite by controller.isFavorite.collectAsState()
    val error by controller.lastError.collectAsState()

    var videoId by remember { mutableStateOf("dQw4w9WgXcQ") }
    val account = remember { sessionManager.loadAccount()?.name }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("VIsage", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (sessionManager.isLoggedIn()) {
                "Signed in as ${account ?: "unknown"}"
            } else {
                "Not signed in — sign in from Settings → Account"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(nowPlaying?.let { "${it.title} — ${it.artist}" } ?: "—")
        Text(
            buildString {
                append("State: $state")
                if (durationMs > 0) append("  ${positionMs / 1000}s / ${durationMs / 1000}s")
                error?.let { append("  ($it)") }
            },
        )
        OutlinedTextField(
            value = videoId,
            onValueChange = { videoId = it },
            label = { Text("YouTube video id") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val id = videoId.trim()
                    if (id.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) { controller.playVideo(id) }
                    }
                },
            ) { Text("Play") }
            Button(
                onClick = {
                    if (state == PlaybackState.Playing) controller.pause() else controller.play()
                },
            ) { Text(if (state == PlaybackState.Playing) "Pause" else "Resume") }
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { controller.toggleFavorite() }
                    }
                },
            ) { Text(if (favorite) "Unlike" else "Favourite") }
        }
        Text("MPRIS: org.mpris.MediaPlayer2.vivi", style = MaterialTheme.typography.labelSmall)
    }
}
