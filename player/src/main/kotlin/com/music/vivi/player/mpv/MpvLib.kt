package com.music.vivi.player.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.Structure
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference

/**
 * Minimal JNA binding to libmpv (client API).
 */
@Suppress("FunctionName")
interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_command(ctx: Pointer, args: StringArray): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property(
        ctx: Pointer,
        name: String,
        format: Int,
        data: DoubleByReference,
    ): Int

    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: IntByReference): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer?
    fun mpv_error_string(error: Int): String?

    companion object {
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_STRING = 1
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5

        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_PLAYBACK_RESTART = 20
        const val MPV_EVENT_PROPERTY_CHANGE = 22

        /** Natural end of the file — only this should auto-advance the queue. */
        const val MPV_END_FILE_REASON_EOF = 0
        /** Stopped by loadfile replace / stop — must NOT auto-advance. */
        const val MPV_END_FILE_REASON_STOP = 2
        const val MPV_END_FILE_REASON_QUIT = 3
        const val MPV_END_FILE_REASON_ERROR = 4
        const val MPV_END_FILE_REASON_REDIRECT = 5

        val INSTANCE: MpvLib by lazy {
            // mpv requires C numeric locale; force it before first create.
            runCatching {
                CLib.INSTANCE.setlocale(CLib.LC_NUMERIC, "C")
            }
            val candidates = libraryCandidates()
            var last: Throwable? = null
            for (name in candidates) {
                try {
                    return@lazy Native.load(name, MpvLib::class.java)
                } catch (t: Throwable) {
                    last = t
                }
            }
            throw IllegalStateException(
                "Could not load libmpv (install mpv / libmpv). Tried: $candidates",
                last,
            )
        }
    }
}

/** Where to look for libmpv, best first. Shared with the video renderer. */
internal fun libraryCandidates(): List<String> {
    val bundled = com.music.vivi.nativelib.HostOs.resourcesDir()
    return if (com.music.vivi.nativelib.HostOs.isWindows) {
        listOfNotNull(
            com.music.vivi.nativelib.AppEnv.get("LIBMPV"),
            bundled?.let { java.io.File(it, "libmpv-2.dll").path }?.takeIf { java.io.File(it).isFile },
            "libmpv-2",
            "mpv-2",
            "libmpv",
        )
    } else {
        listOfNotNull(
            com.music.vivi.nativelib.AppEnv.get("LIBMPV"),
            "mpv",
            "libmpv.so.2",
            "/usr/lib/libmpv.so.2",
            "/usr/lib/libmpv.so",
        )
    }
}

@Suppress("FunctionName")
private interface CLib : Library {
    fun setlocale(category: Int, locale: String): String?

    companion object {
        const val LC_NUMERIC = 1
        val INSTANCE: CLib = Native.load(if (com.music.vivi.nativelib.HostOs.isWindows) "msvcrt" else "c", CLib::class.java)
    }
}

@Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
open class MpvEvent : Structure {
    @JvmField var event_id: Int = 0
    @JvmField var error: Int = 0
    @JvmField var reply_userdata: Long = 0
    @JvmField var data: Pointer? = null

    constructor() : super()
    constructor(p: Pointer) : super(p)
}

/** mpv_event_end_file — first fields are enough for reason/error. */
@Structure.FieldOrder("reason", "error")
open class MpvEventEndFile : Structure {
    @JvmField var reason: Int = 0
    @JvmField var error: Int = 0

    constructor() : super()
    constructor(p: Pointer) : super(p)
}
