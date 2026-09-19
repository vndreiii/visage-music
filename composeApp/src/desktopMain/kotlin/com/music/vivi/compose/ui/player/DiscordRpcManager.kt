package com.music.vivi.compose.ui.player

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
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

class DiscordRpcManager(
    private val scope: CoroutineScope
) : KoinComponent {
    private val controller: PlaybackController by inject()
    private val prefs: DesktopPrefs by inject()

    private val rpc = DiscordRPC.INSTANCE
    private var initialized = false
    private var callbackJob: Job? = null
    
    // Use the vivimusic Discord application ID
    private val applicationId = "1411019391843172514"

    fun start() {
        scope.launch {
            prefs.addListener { 
                checkState() 
            }
            checkState()
            
            // Watch playback changes
            launch {
                controller.nowPlaying.collectLatest { updatePresence() }
            }
            launch {
                controller.state.collectLatest { updatePresence() }
            }
        }
    }

    private fun checkState() {
        val enabled = prefs.getBoolean(PrefKeys.DiscordRpcEnabled, true)
        if (enabled && !initialized) {
            initRpc()
        } else if (!enabled && initialized) {
            shutdownRpc()
        }
    }

    private fun initRpc() {
        val handlers = DiscordEventHandlers()
        handlers.ready = { println("Discord RPC Ready") }
        rpc.Discord_Initialize(applicationId, handlers, true, "")
        initialized = true
        
        callbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                rpc.Discord_RunCallbacks()
                delay(2000)
            }
        }
        
        updatePresence()
    }

    private fun shutdownRpc() {
        callbackJob?.cancel()
        callbackJob = null
        if (initialized) {
            rpc.Discord_ClearPresence()
            rpc.Discord_Shutdown()
            initialized = false
        }
    }

    private fun updatePresence() {
        if (!initialized) return
        
        val track = controller.nowPlaying.value
        val state = controller.state.value

        if (track == null) {
            rpc.Discord_ClearPresence()
            return
        }

        val presence = DiscordRichPresence()
        presence.details = track.title
        presence.state = track.artist

        // If you want images, you could set largeImageKey but we need keys registered on Discord app.
        // For java-discord-rpc, we can't easily pass external URLs directly unless they are Discord assets or if Discord started supporting external urls?
        // Wait, Discord API now supports external URLs if prefixed with external? No, Kizzy uses it but maybe club.minnced doesn't expose it or just passes strings.
        // We can just pass the artworkUrl. Discord might ignore it if it doesn't match a registered asset.
        
        if (state == PlaybackState.Playing) {
            val position = controller.positionMs.value
            val duration = controller.durationMs.value.takeIf { it > 0 } ?: track.durationMs
            
            val startTimestamp = System.currentTimeMillis() / 1000 - (position / 1000)
            presence.startTimestamp = startTimestamp
            
            if (duration > 0) {
                presence.endTimestamp = startTimestamp + (duration / 1000)
            }
        }

        rpc.Discord_UpdatePresence(presence)
    }
}
