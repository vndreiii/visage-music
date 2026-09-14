package com.music.vivi.log

/** Portable logger replacing android.util.Log for KMP JVM. */
object ViviLog {
    fun d(tag: String, msg: String) = println("D/$tag: $msg")
    fun w(tag: String, msg: String) = println("W/$tag: $msg")
    fun e(tag: String, msg: String, t: Throwable? = null) {
        println("E/$tag: $msg")
        t?.printStackTrace()
    }
}
