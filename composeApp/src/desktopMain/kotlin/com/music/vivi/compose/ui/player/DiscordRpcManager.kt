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

    private val applicationId = 839503426907930634L
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
        } else if (enabled) {
            updatePresence()
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

        val buttons = buildList {
            val local = track.localPath != null || track.videoId.startsWith("local:") || track.videoId.startsWith("offline:")
            if (prefs.getBoolean(PrefKeys.DiscordRpcSongButton, false) && !local) {
                add("Listen on YouTube Music" to "https://music.youtube.com/watch?v=${track.videoId}")
            }
            if (prefs.getBoolean(PrefKeys.DiscordRpcVisageButton, false)) {
                add("Get VIsage" to VISAGE_URL)
            }
        }

        try {
            val base = builder.build()
            currentClient.sendRichPresence(
                PresenceWithButtons(base, buttons).also {
                    if (System.getenv("VISAGE_DEBUG_RPC") != null) System.err.println("discord rpc payload: ${it.toJson()}")
                },
                com.jagrosh.discordipc.entities.Callback({ _ -> }, { err -> System.err.println("discord rpc rejected activity: $err") }),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private const val VISAGE_URL = "https://github.com/vndreiii/visage-music"

/** jagrosh's RichPresence has no button support; add the `buttons` array to its payload. */
private class PresenceWithButtons(
    private val base: RichPresence,
    private val buttons: List<Pair<String, String>>,
) : RichPresence(null, null, null, null, null, null, null, null, null, 0, 0, null, null, null, false) {
    // Discord rejects the whole activity when buttons come with party/secrets keys, even empty ones.
    override fun toJson(): org.json.JSONObject = base.toJson().apply {
        remove("secrets")
        remove("party")
        remove("instance")
        put("type", 3) // 3 = Watching ("Playing" is 0, "Listening" 2)
        if (buttons.isNotEmpty()) {
            put("buttons", org.json.JSONArray(buttons.take(2).map { (label, url) -> org.json.JSONObject().put("label", label).put("url", url) }))
        }
    }
}
