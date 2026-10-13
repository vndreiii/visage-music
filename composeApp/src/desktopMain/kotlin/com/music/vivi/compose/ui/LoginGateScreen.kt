package com.music.vivi.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.vivi.player.login.YtLoginFlow

/** Full-screen sign-in prompt. */
@Composable
fun LoginGateScreen(onLoggedIn: (String) -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Not signed in", style = MaterialTheme.typography.headlineMedium)
        YtLoginPanel(onLoggedIn = onLoggedIn, modifier = Modifier.padding(top = 16.dp))
    }
}

/** Opens a Chromium sign-in window and saves the YouTube Music session to the keyring. */
@Composable
fun YtLoginPanel(modifier: Modifier = Modifier, onLoggedIn: (String) -> Unit = {}) {
    val flow = remember { YtLoginFlow(onLoggedIn) }
    DisposableEffect(flow) { onDispose { flow.close() } }
    val state by flow.state.collectAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Google blocks embedded browsers, so sign-in happens in a Chromium window; " +
                "your session is stored in the system keyring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.status, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = flow::open, enabled = !state.busy) {
                Text(if (state.browserOpen) "Reopen sign-in window" else "Open sign-in window")
            }
            Button(onClick = flow::confirm, enabled = state.canConfirm && !state.busy) { Text("Confirm") }
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
