package com.music.vivi.compose.ui.player

import com.jagrosh.discordipc.IPCClient
import com.jagrosh.discordipc.IPCListener
import com.jagrosh.discordipc.entities.RichPresence
import com.jagrosh.discordipc.entities.pipe.PipeStatus
import com.music.vivi.compose.prefs.DesktopPrefs
import com.music.vivi.compose.prefs.PrefKeys
import com.music.vivi.player.PlaybackController
import com.music.vivi.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DiscordRpcManager(
    private val scope: CoroutineScope
) : KoinComponent {
    private val controller: PlaybackController by inject()
    private val prefs: DesktopPrefs by inject()

    private val applicationId = 1411019391843172514L
    private var client: IPCClient? = null
    private var isConnected = false

    fun start() {
        scope.launch {
            prefs.addListener { checkState() }
            checkState()
            
            launch { controller.nowPlaying.collectLatest { updatePresence() } }
            launch { controller.state.collectLatest { updatePresence() } }
        }
    }

    private fun checkState() {
        val enabled = prefs.getBoolean(PrefKeys.DiscordRpcEnabled, true)
        if (enabled && client == null) {
            initRpc()
        } else if (!enabled && client != null) {
            shutdownRpc()
        }
    }

    private fun initRpc() {
        client = IPCClient(applicationId)
        client?.setListener(object : IPCListener {
            override fun onReady(client: IPCClient?) {
                isConnected = true
                updatePresence()
            }
            override fun onClose(client: IPCClient?, json: org.json.JSONObject?) {
                isConnected = false
            }
            override fun onDisconnect(client: IPCClient?, t: Throwable?) {
                isConnected = false
            }
        })
        try {
            client?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            // Ignore connection errors, it will retry or stay disconnected
        }
    }

    private fun shutdownRpc() {
        try {
            if (isConnected) {
                client?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        client = null
        isConnected = false
    }

    private fun updatePresence() {
        if (!isConnected) return
        val currentClient = client ?: return
        
        val track = controller.nowPlaying.value
        val state = controller.state.value

        if (track == null) {
            currentClient.sendRichPresence(null)
            return
        }

        val builder = RichPresence.Builder()
        builder.setDetails(track.title)
        builder.setState(track.artist)
        
        // Jagrosh supports arbitrary image URLs if Discord allows them
        track.artworkUrl?.let { url ->
            builder.setLargeImage(url, track.title)
        }

        if (state == PlaybackState.Playing) {
            val position = controller.positionMs.value
            val duration = controller.durationMs.value.takeIf { it > 0 } ?: track.durationMs
            
            val startTimestamp = System.currentTimeMillis() / 1000 - (position / 1000)
            builder.setStartTimestamp(OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(startTimestamp), ZoneOffset.UTC))
            
            if (duration > 0) {
                val endTimestamp = startTimestamp + (duration / 1000)
                builder.setEndTimestamp(OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(endTimestamp), ZoneOffset.UTC))
            }
        }

        try {
            currentClient.sendRichPresence(builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
