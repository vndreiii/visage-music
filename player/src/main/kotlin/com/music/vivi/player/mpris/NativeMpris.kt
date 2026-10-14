package com.music.vivi.player.mpris

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Pointer

@Suppress("FunctionName")
internal interface VisageMprisLib : Library {
    interface CommandCallback : Callback {
        fun invoke(cmd: Int, num: Double, text: String?)
    }

    fun visage_mpris_start(callback: CommandCallback): Long
    fun visage_mpris_name(handle: Long): Pointer?
    fun visage_mpris_update(handle: Long, json: String)
    fun visage_mpris_stop(handle: Long)
    fun visage_free_string(p: Pointer?)
}

/**
 * MPRIS over the Rust helper (`native/visage-native`, zbus). Returns null from [start] when the
 * library is unavailable, in which case [MprisService] falls back to its dbus-java export.
 */
internal class NativeMpris private constructor(
    private val lib: VisageMprisLib,
    private val handle: Long,
    // Referenced so JNA's callback trampoline is never garbage collected.
    @Suppress("unused") private val callback: VisageMprisLib.CommandCallback,
) {
    val busName: String? = lib.visage_mpris_name(handle)?.let { p ->
        try {
            p.getString(0, "UTF-8")
        } finally {
            lib.visage_free_string(p)
        }
    }

    fun update(json: String) = lib.visage_mpris_update(handle, json)

    fun close() = lib.visage_mpris_stop(handle)

    companion object {
        const val PLAY = 1
        const val PAUSE = 2
        const val PLAY_PAUSE = 3
        const val STOP = 4
        const val NEXT = 5
        const val PREVIOUS = 6
        const val SEEK_REL_US = 7
        const val SET_POSITION_US = 8
        const val VOLUME = 9
        const val SHUFFLE = 10
        const val LOOP = 11
        const val RAISE = 12

        private val lib: VisageMprisLib? by lazy { com.music.vivi.nativelib.NativeLibLoader.load(VisageMprisLib::class.java) }

        fun start(onCommand: (cmd: Int, num: Double, text: String?) -> Unit): NativeMpris? {
            val l = lib ?: return null
            val cb = object : VisageMprisLib.CommandCallback {
                override fun invoke(cmd: Int, num: Double, text: String?) {
                    runCatching { onCommand(cmd, num, text) }
                }
            }
            val handle = l.visage_mpris_start(cb)
            return if (handle == 0L) null else NativeMpris(l, handle, cb)
        }
    }
}
