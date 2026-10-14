package com.music.vivi.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.awt.SwingPanel
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

/** Embeds Chromium sign-in and saves the YouTube Music session to the keyring. */
@Composable
fun YtLoginPanel(modifier: Modifier = Modifier, onLoggedIn: (String) -> Unit = {}) {
    val browser = remember { EmbeddedLoginBrowser() }
    val flow = remember { YtLoginFlow(browser, onLoggedIn) }
    DisposableEffect(flow) { onDispose { flow.close() } }
    val state by flow.state.collectAsState()
    val panel by browser.panel.collectAsState()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Sign in below using the browser embedded in VIsage; " +
                "your session is stored in the system keyring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.status, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = flow::open, enabled = !state.busy) {
                Text(if (state.browserOpen) "Reload sign-in" else "Sign in with Google")
            }
            Button(onClick = flow::confirm, enabled = state.canConfirm && !state.busy) { Text("Confirm") }
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        panel?.let { component ->
            SwingPanel(factory = { component }, modifier = Modifier.fillMaxWidth().height(520.dp))
        }
    }
}
