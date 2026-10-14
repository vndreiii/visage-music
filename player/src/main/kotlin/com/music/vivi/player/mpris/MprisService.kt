package com.music.vivi.player.mpris

import com.music.vivi.player.NowPlaying
import com.music.vivi.player.PlaybackState
import org.freedesktop.dbus.ObjectPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.LinkedHashMap
import kotlinx.serialization.json.put

/**
 * Exports org.mpris.MediaPlayer2.visage on the session bus.
 *
 * D-Bus interfaces must be **public top-level** types — dbus-java reflects them from
 * another classloader context; private nested interfaces make Play/Pause/Next fail with
 * "cannot access a member of interface … with modifiers public abstract".
 */
class MprisService(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onSeekUs: (Long) -> Unit,
    private val onNext: () -> Unit = {},
    private val onPrevious: () -> Unit = {},
    private val onVolume: (Double) -> Unit = {},
    private val onShuffle: (Boolean) -> Unit = {},
) : AutoCloseable {
    // Preferred: Rust/zbus service (dbus-java never got its well-known name on some setups).
    private val native: NativeMpris? = run {
        MprisDesktopIntegration.ensureInstalled()
        NativeMpris.start(::onNativeCommand)
    }?.also { System.err.println("MPRIS: native service on ${it.busName}") }

    private val connection: DBusConnection? = if (native != null) null else runCatching {
        DBusConnectionBuilder.forSessionBus()
            .withShared(false)
            .build()
            .also { conn ->
                conn.requestBusName(BUS_NAME)
                // Export as public implementation of public ifaces
                conn.exportObject(OBJECT_PATH, Exported())
            }
    }.onFailure { e ->
        System.err.println("MPRIS DBus Connection Failed: ${e.message}")
        e.printStackTrace()
    }.getOrNull()

    @Volatile private var playbackStatus = "Stopped"
    @Volatile private var positionUs = 0L
    @Volatile private var metadata: Map<String, Variant<*>> = emptyMetadata()
    @Volatile private var userRating = 0.0
    @Volatile private var volume = 1.0
    @Volatile private var shuffle = false
    @Volatile private var loopStatus = "None"

    private fun onNativeCommand(cmd: Int, num: Double, text: String?) {
        when (cmd) {
            NativeMpris.PLAY -> onPlay()
            NativeMpris.PAUSE -> onPause()
            NativeMpris.PLAY_PAUSE -> if (playbackStatus == "Playing") onPause() else onPlay()
            NativeMpris.STOP -> onStop()
            NativeMpris.NEXT -> onNext()
            NativeMpris.PREVIOUS -> onPrevious()
            NativeMpris.SEEK_REL_US -> onSeekUs(positionUs + num.toLong())
            NativeMpris.SET_POSITION_US -> onSeekUs(num.toLong())
            NativeMpris.VOLUME -> { volume = num.coerceIn(0.0, 1.0); onVolume(volume) }
            NativeMpris.SHUFFLE -> { shuffle = num > 0.5; onShuffle(shuffle) }
            NativeMpris.LOOP -> loopStatus = text ?: "None"
        }
    }

    private fun pushNative(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        native?.update(kotlinx.serialization.json.buildJsonObject(build).toString())
    }

    private fun pushMetadata(track: NowPlaying?, artFileUrl: String?, rating: Double) = pushNative {
        put("metadata", kotlinx.serialization.json.buildJsonObject {
            put("track_id", track?.videoId ?: "none")
            if (track != null) {
                put("title", track.title)
                put("artist", track.artist)
                track.album?.let { put("album", it) }
                put("length_us", track.durationMs * 1000L)
                artFileUrl?.let { put("art_url", it) }
                put("rating", rating)
            }
        })
    }

    private var lastArtUrl: String? = null
    private var lastTrack: NowPlaying? = null

    fun updatePlaybackState(state: PlaybackState) {
        playbackStatus = when (state) {
            PlaybackState.Playing, PlaybackState.Loading -> "Playing"
            PlaybackState.Paused -> "Paused"
            PlaybackState.Idle, PlaybackState.Ended, PlaybackState.Error -> "Stopped"
        }
        pushNative { put("status", playbackStatus) }
        emitPlayerChanged(mapOf("PlaybackStatus" to Variant(playbackStatus)))
    }

    fun updatePosition(positionMs: Long) {
        positionUs = positionMs * 1000L
        pushNative { put("position_us", positionUs) }
    }

    fun updateVolume(vol: Float) {
        volume = vol.toDouble().coerceIn(0.0, 1.0)
        pushNative { put("volume", volume) }
        emitPlayerChanged(mapOf("Volume" to Variant(volume)))
    }

    fun updateShuffle(enabled: Boolean) {
        shuffle = enabled
        pushNative { put("shuffle", enabled) }
        emitPlayerChanged(mapOf("Shuffle" to Variant(shuffle)))
    }

    fun updateLoopStatus(status: String) {
        loopStatus = status
        pushNative { put("loop_status", status) }
        emitPlayerChanged(mapOf("LoopStatus" to Variant(loopStatus)))
    }

    fun updateNowPlaying(track: NowPlaying?, artFileUrl: String?, favorite: Boolean) {
        userRating = if (favorite) 1.0 else 0.0
        lastTrack = track
        lastArtUrl = artFileUrl
        pushMetadata(track, artFileUrl, userRating)
        metadata = if (track == null) {
            emptyMetadata()
        } else {
            buildMetadata(track, artFileUrl, userRating)
        }
        emitPlayerChanged(mapOf("Metadata" to metadataVariant(metadata)))
    }

    fun updateFavorite(favorite: Boolean) {
        userRating = if (favorite) 1.0 else 0.0
        if (native != null) pushMetadata(lastTrack, lastArtUrl, userRating)
        if (metadata.isNotEmpty()) {
            val next = LinkedHashMap(metadata)
            next["xesam:userRating"] = Variant(userRating)
            metadata = next
            emitPlayerChanged(mapOf("Metadata" to metadataVariant(metadata)))
        }
    }

    private fun metadataVariant(map: Map<String, Variant<*>>): Variant<*> =
        Variant(map, "a{sv}")

    private fun emptyMetadata(): Map<String, Variant<*>> = mapOf(
        "mpris:trackid" to Variant(ObjectPath("/", "$OBJECT_PATH/Track/0")),
    )

    private fun buildMetadata(
        track: NowPlaying,
        artFileUrl: String?,
        rating: Double,
    ): Map<String, Variant<*>> {
        val map = linkedMapOf<String, Variant<*>>(
            "mpris:trackid" to Variant(ObjectPath("/", "$OBJECT_PATH/Track/${track.videoId}")),
            "xesam:title" to Variant(track.title),
            "xesam:artist" to Variant(listOf(track.artist), "as"),
            "mpris:length" to Variant(track.durationMs * 1000L),
            "xesam:userRating" to Variant(rating),
        )
        track.album?.let { map["xesam:album"] = Variant(it) }
        artFileUrl?.let { map["mpris:artUrl"] = Variant(it) }
        return map
    }

    private fun emitPlayerChanged(changed: Map<String, Variant<*>>) {
        val conn = connection ?: return
        runCatching {
            conn.sendMessage(
                Properties.PropertiesChanged(
                    OBJECT_PATH,
                    IFACE_PLAYER,
                    changed,
                    emptyList(),
                ),
            )
        }
    }

    override fun close() {
        native?.close()
        connection?.let { conn ->
            runCatching { conn.unExportObject(OBJECT_PATH) }
            runCatching { conn.releaseBusName(BUS_NAME) }
            runCatching { conn.disconnect() }
        }
    }

    /**
     * Must be a public class — dbus-java invokes methods via reflection against the
     * exported object and its public interface hierarchy.
     */
    inner class Exported : MediaPlayer2, MediaPlayer2Player, Properties {
        override fun getObjectPath(): String = OBJECT_PATH

        override fun Raise() {}
        override fun Quit() {}

        override fun Next() = onNext()
        override fun Previous() = onPrevious()
        override fun Pause() = onPause()
        override fun PlayPause() {
            if (playbackStatus == "Playing") onPause() else onPlay()
        }
        override fun Stop() = onStop()
        override fun Play() = onPlay()
        override fun Seek(Offset: Long) {
            onSeekUs(positionUs + Offset)
        }
        override fun SetPosition(TrackId: ObjectPath, Position: Long) {
            onSeekUs(Position)
        }
        override fun OpenUri(Uri: String) {}

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interface_name: String?, property_name: String?): A {
            val iface = interface_name ?: ""
            val prop = property_name ?: ""
            val value = GetAll(iface)[prop]
                ?: throw DBusException("Unknown property $iface.$prop")
            return value as A
        }

        override fun GetAll(interface_name: String): MutableMap<String, Variant<*>> {
            return when (interface_name) {
                IFACE_ROOT -> linkedMapOf(
                    "CanQuit" to Variant(false),
                    "Fullscreen" to Variant(false),
                    "CanSetFullscreen" to Variant(false),
                    "CanRaise" to Variant(true),
                    "HasTrackList" to Variant(false),
                    "Identity" to Variant("VIsage"),
                    "DesktopEntry" to Variant(MprisDesktopIntegration.DESKTOP_ID),
                    "SupportedUriSchemes" to Variant(listOf("https", "http"), "as"),
                    "SupportedMimeTypes" to Variant(
                        listOf("audio/mpeg", "audio/mp4", "audio/ogg"),
                        "as",
                    ),
                )
                IFACE_PLAYER -> linkedMapOf(
                    "PlaybackStatus" to Variant(playbackStatus),
                    "LoopStatus" to Variant(loopStatus),
                    "Rate" to Variant(1.0),
                    "Shuffle" to Variant(shuffle),
                    "Metadata" to metadataVariant(metadata),
                    "Volume" to Variant(volume),
                    "Position" to Variant(positionUs),
                    "MinimumRate" to Variant(1.0),
                    "MaximumRate" to Variant(1.0),
                    "CanGoNext" to Variant(true),
                    "CanGoPrevious" to Variant(true),
                    "CanPlay" to Variant(true),
                    "CanPause" to Variant(true),
                    "CanSeek" to Variant(true),
                    "CanControl" to Variant(true),
                )
                else -> mutableMapOf()
            }
        }

        override fun <A : Any?> Set(interface_name: String?, property_name: String?, value: A) {
            val iface = interface_name ?: return
            val prop = property_name ?: return
            when {
                iface == IFACE_PLAYER && prop == "Volume" -> {
                    val v = when (value) {
                        is Variant<*> -> (value.value as? Number)?.toDouble()
                        is Number -> value.toDouble()
                        else -> null
                    } ?: return
                    volume = v.coerceIn(0.0, 1.0)
                    onVolume(volume)
                    emitPlayerChanged(mapOf("Volume" to Variant(volume)))
                }
                iface == IFACE_PLAYER && prop == "Shuffle" -> {
                    val s = when (value) {
                        is Variant<*> -> value.value as? Boolean
                        is Boolean -> value
                        else -> null
                    } ?: return
                    shuffle = s
                    onShuffle(s)
                    emitPlayerChanged(mapOf("Shuffle" to Variant(shuffle)))
                }
                iface == IFACE_PLAYER && prop == "LoopStatus" -> {
                    val loop = when (value) {
                        is Variant<*> -> value.value as? String
                        is String -> value
                        else -> "None"
                    } ?: "None"
                    loopStatus = loop
                    emitPlayerChanged(mapOf("LoopStatus" to Variant(loopStatus)))
                }
            }
        }
    }

    companion object {
        val BUS_NAME = "org.mpris.MediaPlayer2.visage.instance${ProcessHandle.current().pid()}"
        const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        private const val IFACE_ROOT = "org.mpris.MediaPlayer2"
        private const val IFACE_PLAYER = "org.mpris.MediaPlayer2.Player"
    }
}
