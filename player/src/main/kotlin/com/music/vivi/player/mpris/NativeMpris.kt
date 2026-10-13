package com.music.vivi.player.mpris

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

@Suppress("FunctionName")
internal interface ViviMprisLib : Library {
    interface CommandCallback : Callback {
        fun invoke(cmd: Int, num: Double, text: String?)
    }

    fun vivi_mpris_start(callback: CommandCallback): Long
    fun vivi_mpris_name(handle: Long): Pointer?
    fun vivi_mpris_update(handle: Long, json: String)
    fun vivi_mpris_stop(handle: Long)
    fun vivi_free_string(p: Pointer?)
}

/**
 * MPRIS over the Rust helper (`native/vivi-native`, zbus). Returns null from [start] when the
 * library is unavailable, in which case [MprisService] falls back to its dbus-java export.
 */
internal class NativeMpris private constructor(
    private val lib: ViviMprisLib,
    private val handle: Long,
    // Referenced so JNA's callback trampoline is never garbage collected.
    @Suppress("unused") private val callback: ViviMprisLib.CommandCallback,
) {
    val busName: String? = lib.vivi_mpris_name(handle)?.let { p ->
        try {
            p.getString(0, "UTF-8")
        } finally {
            lib.vivi_free_string(p)
        }
    }

    fun update(json: String) = lib.vivi_mpris_update(handle, json)

    fun close() = lib.vivi_mpris_stop(handle)

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

        private val lib: ViviMprisLib? by lazy {
            if (System.getenv("VIVI_NATIVE_DISABLE") == "1") return@lazy null
            val candidates = buildList {
                System.getenv("VIVI_NATIVE_LIB")?.takeIf { it.isNotBlank() }?.let(::add)
                System.getProperty("compose.application.resources.dir")?.let { add(File(it, "libvivi_native.so").path) }
                add("vivi_native")
            }
            for (name in candidates) {
                if (name.contains('/') && !trusted(File(name))) continue
                try {
                    return@lazy Native.load(name, ViviMprisLib::class.java)
                } catch (_: Throwable) {
                }
            }
            null
        }

        /** Never load a library other users could have replaced. */
        private fun trusted(f: File): Boolean {
            if (!f.isAbsolute || !f.isFile) return false
            return runCatching {
                val perms = Files.getPosixFilePermissions(f.toPath())
                PosixFilePermission.GROUP_WRITE !in perms && PosixFilePermission.OTHERS_WRITE !in perms
            }.getOrDefault(false)
        }

        fun start(onCommand: (cmd: Int, num: Double, text: String?) -> Unit): NativeMpris? {
            val l = lib ?: return null
            val cb = object : ViviMprisLib.CommandCallback {
                override fun invoke(cmd: Int, num: Double, text: String?) {
                    runCatching { onCommand(cmd, num, text) }
                }
            }
            val handle = l.vivi_mpris_start(cb)
            return if (handle == 0L) null else NativeMpris(l, handle, cb)
        }
    }
}
