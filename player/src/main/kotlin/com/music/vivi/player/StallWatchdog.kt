package com.music.vivi.player

import java.util.concurrent.atomic.AtomicLong

/**
 * Leaves a trace when the app hangs: a hung UI thread or track change otherwise writes nothing, so the
 * log just stops. Dumps every thread's stack to stdout (and so to the log file) when stalled.
 */
object StallWatchdog {
    private const val UI_STALL_MS = 10_000L
    private const val MIN_DUMP_GAP_MS = 30_000L

    private val lastDumpMs = AtomicLong(0)
    @Volatile private var started = false

    /** Pings the AWT event queue every 2s; if a ping stays unanswered for 10s the UI thread is wedged. */
    @Synchronized
    fun startUiWatch() {
        if (started) return
        started = true
        val lastAck = AtomicLong(System.currentTimeMillis())
        Thread({
            while (true) {
                try {
                    Thread.sleep(2_000)
                    java.awt.EventQueue.invokeLater { lastAck.set(System.currentTimeMillis()) }
                    val stalled = System.currentTimeMillis() - lastAck.get()
                    if (stalled > UI_STALL_MS) dump("UI thread unresponsive for ${stalled}ms")
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (t: Throwable) {
                    System.err.println("StallWatchdog: ${t.message}")
                }
            }
        }, "stall-watchdog").apply { isDaemon = true }.start()
    }

    /** Print all thread stacks, at most once per [MIN_DUMP_GAP_MS]. */
    fun dump(reason: String) {
        val now = System.currentTimeMillis()
        val prev = lastDumpMs.get()
        if (now - prev < MIN_DUMP_GAP_MS || !lastDumpMs.compareAndSet(prev, now)) return
        val sb = StringBuilder("[stall-watchdog ${java.time.LocalTime.now()}] $reason; thread dump:\n")
        for ((t, frames) in Thread.getAllStackTraces()) {
            sb.append("\"").append(t.name).append("\" ").append(t.state).append('\n')
            frames.forEach { sb.append("\tat ").append(it).append('\n') }
        }
        println(sb)
    }
}
