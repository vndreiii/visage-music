package com.music.vivi.log

/**
 * Portable logger replacing Timber / android.util.Log for KMP JVM.
 */
object ViviLog {
    fun d(tag: String, msg: String) {
        println("D/$tag: $msg")
    }

    fun v(tag: String, msg: String) {
        println("V/$tag: $msg")
    }

    fun w(tag: String, msg: String) {
        println("W/$tag: $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        println("E/$tag: $msg")
        t?.printStackTrace()
    }
}
