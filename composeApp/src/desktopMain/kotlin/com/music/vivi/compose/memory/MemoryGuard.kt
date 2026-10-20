package com.music.vivi.compose.memory

import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryType
import javax.management.NotificationEmitter
import javax.management.NotificationListener

/**
 * Frees caches before the heap runs out, instead of letting the app hit an OutOfMemoryError: when the long-lived
 * heap is still over [THRESHOLD] full right after a garbage collection, [onPressure] runs (drop image and video
 * caches). Everything it drops is refetched on demand, so the only cost is a little extra loading.
 */
object MemoryGuard {
    private const val THRESHOLD = 0.8
    private const val MIN_GAP_MS = 20_000L
    @Volatile private var last = 0L

    private interface LibC : com.sun.jna.Library {
        fun malloc_trim(pad: Long): Int
    }

    /**
     * Linux: every minute, ask glibc to hand freed native memory back to the OS. Skia, mpv and ffmpeg free a lot of
     * memory that malloc would otherwise keep, which is most of the app's resident size outside the Java heap.
     */
    private fun startNativeTrim() {
        if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return
        runCatching {
            val libc = com.sun.jna.Native.load("c", LibC::class.java)
            java.util.Timer("native-trim", true).scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() { runCatching { libc.malloc_trim(0) } }
            }, 60_000L, 60_000L)
        }
    }

    fun install(onPressure: () -> Unit) {
        startNativeTrim()
        runCatching {
            // Only the long-lived pool matters: young pools are emptied constantly by design.
            val old = ManagementFactory.getMemoryPoolMXBeans().firstOrNull {
                it.type == MemoryType.HEAP && it.isCollectionUsageThresholdSupported &&
                    (it.name.contains("Old", ignoreCase = true) || it.name.contains("Tenured", ignoreCase = true))
            } ?: return
            val max = old.usage.max.takeIf { it > 0 } ?: Runtime.getRuntime().maxMemory()
            old.collectionUsageThreshold = (max * THRESHOLD).toLong()
            val listener = NotificationListener { n, _ ->
                if (n.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED) {
                    val now = System.currentTimeMillis()
                    if (now - last < MIN_GAP_MS) return@NotificationListener
                    last = now
                    runCatching(onPressure)
                    // Let the next collection reclaim what the caches released.
                    System.gc()
                }
            }
            (ManagementFactory.getMemoryMXBean() as NotificationEmitter).addNotificationListener(listener, null, null)
        }
    }
}
