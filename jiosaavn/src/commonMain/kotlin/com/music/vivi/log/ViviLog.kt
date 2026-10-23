package com.music.vivi.log

/**
 * Portable logger replacing android.util.Log for KMP JVM.
 *
 * The lyrics module declares a class with this same name; only one of them ends up on the app classpath,
 * so both must expose the same methods or callers of the other one fail with NoSuchMethodError.
 */
object ViviLog {
    fun d(tag: String, msg: String) = println("D/$tag: $msg")
    fun v(tag: String, msg: String) = println("V/$tag: $msg")
    fun w(tag: String, msg: String) = println("W/$tag: $msg")
    fun e(tag: String, msg: String, t: Throwable? = null) {
        println("E/$tag: $msg")
        t?.printStackTrace()
    }
}
