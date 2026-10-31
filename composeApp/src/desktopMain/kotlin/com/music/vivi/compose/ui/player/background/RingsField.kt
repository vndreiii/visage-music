package com.music.vivi.compose.ui.player.background

/**
 * Shared geometry and clock of the Rings background, so the Cuts lyrics can fuse with it: when both are on screen the
 * lyrics are sliced along the background's own rings instead of drawing a second, clashing set.
 */
object RingsField {
    @Volatile var live = false
    /** The background's box in window pixels. */
    @Volatile var left = 0f
    @Volatile var top = 0f
    @Volatile var width = 0f
    @Volatile var height = 0f

    /** The lyric being shown over the rings (window pixels): the rings darken around it so the words stand out. */
    @Volatile var dimLeft = 0f
    @Volatile var dimTop = 0f
    @Volatile var dimRight = 0f
    @Volatile var dimBottom = 0f
    /** [System.nanoTime] of the last update; stale means no lyric is over the rings. */
    @Volatile var dimStamp = 0L
    fun dimActive(): Boolean = System.nanoTime() - dimStamp < 400_000_000L

    private val t0 = System.nanoTime()
    /** One clock for both, so their ripples are in phase. */
    fun seconds(): Float = ((System.nanoTime() - t0) / 1_000_000_000.0).toFloat()
}


/**
 * The music values the rings actually use: [AudioLevels] scaled by the "reaction level" setting and eased by the
 * "smoothing" setting (a kick grows and settles instead of snapping). Shared, so the background and the fused Cuts
 * lyrics always see the same numbers.
 */
object RingsReaction {
    @Volatile var bass = 0f
        private set
    @Volatile var mid = 0f
        private set
    @Volatile var high = 0f
        private set
    @Volatile var kick = 0f
        private set
    private var last = 0f

    /** [level]: 1 = as measured; [smooth]: 0 = none, 1 = very soft. Safe to call from every consumer each frame. */
    @Synchronized
    fun update(level: Float, smooth: Float) {
        val t = RingsField.seconds()
        val dt = t - last
        if (dt < 0.002f) return
        last = t
        val d = dt.coerceAtMost(0.1f)
        val s = smooth.coerceIn(0f, 1f)
        fun ease(current: Float, target: Float): Float {
            if (s < 0.01f) return target
            val tau = if (target > current) s * 0.30f else s * 0.80f
            return current + (target - current) * (1f - kotlin.math.exp(-d / tau.coerceAtLeast(0.001f)))
        }
        bass = ease(bass, (com.music.vivi.player.AudioLevels.bass * level).coerceIn(0f, 1.4f))
        mid = ease(mid, (com.music.vivi.player.AudioLevels.mid * level).coerceIn(0f, 1.4f))
        high = ease(high, (com.music.vivi.player.AudioLevels.high * level).coerceIn(0f, 1.4f))
        kick = ease(kick, (com.music.vivi.player.AudioLevels.kick * level).coerceIn(0f, 1.4f))
    }
}
