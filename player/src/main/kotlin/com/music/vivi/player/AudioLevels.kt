package com.music.vivi.player

import java.util.concurrent.ConcurrentHashMap

/**
 * What the music is doing right now, measured on the audio that is actually playing: overall loudness plus three
 * frequency bands (bass, mids, highs) and an onset pulse for each, so visuals can behave like a real visualizer.
 *
 * Players report raw dB readings; every band is auto-gained (the quietest and loudest recent moments define 0 and 1),
 * so the full range is used on any song. Plain volatile fields: visuals read them every frame in their draw loops.
 */
object AudioLevels {
    /** Overall loudness 0..1: instant attack, eased release. */
    @Volatile var level: Float = 0f
        private set
    /** Overall onset pulse 0..1 (the strongest of the three bands' hits). */
    @Volatile var beat: Float = 0f
        private set
    @Volatile var bass: Float = 0f
        private set
    @Volatile var mid: Float = 0f
        private set
    @Volatile var high: Float = 0f
        private set
    /** Pulse on bass onsets: kick drums. */
    @Volatile var kick: Float = 0f
        private set

    /** One auto-gained, smoothed band with an onset detector. */
    private class Band(val attack: Float, val release: Float, val gain: Float) {
        var hi = 0.6f
        var lo = 0.2f
        var avg = 0f
        var value = 0f
        var pulse = 0f

        fun step(raw: Float, dt: Float) {
            hi = maxOf(raw, hi - dt * 0.04f)
            lo = minOf(raw, lo + dt * 0.015f)
            val norm = ((raw - lo) / (hi - lo).coerceAtLeast(0.12f)).coerceIn(0f, 1f)
            val k = if (norm > value) 1f - kotlin.math.exp(-dt * attack) else 1f - kotlin.math.exp(-dt * release)
            value += (norm - value) * k
            // An onset is a sudden rise above the recent average: instant attack, quick decay.
            val rise = norm - avg
            avg += (norm - avg) * (1f - kotlin.math.exp(-dt * 3.2f))
            pulse = maxOf(pulse * kotlin.math.exp(-dt * 8f), (rise * gain).coerceIn(0f, 1f))
        }

        fun fade(dt: Float) {
            value += (0f - value) * (1f - kotlin.math.exp(-dt * 8f))
            pulse *= kotlin.math.exp(-dt * 8f)
        }
    }

    private class Reading(val overall: Float, val bands: FloatArray?)

    private val reports = ConcurrentHashMap<Any, Reading>()
    private val all = Band(90f, 9f, 3.6f)
    private val low = Band(90f, 10f, 4.2f)
    private val middle = Band(80f, 11f, 4.0f)
    private val treble = Band(80f, 13f, 4.5f)
    private var lastNanos = 0L

    private fun norm(db: Float, floor: Float, span: Float) = ((db + floor) / span).coerceIn(0f, 1f)

    /** [rmsDb] / [peakDb]: dBFS of everything; [bandDb] (bass, mid, high RMS dBFS) when the band filters are running. */
    fun report(source: Any, rmsDb: Float, peakDb: Float, bandDb: FloatArray? = null) {
        val overall = norm(rmsDb, 52f, 40f) * 0.6f + norm(peakDb, 40f, 38f) * 0.4f
        reports[source] = Reading(overall, bandDb?.let { b -> FloatArray(3) { norm(b[it], 58f, 46f) } })
        mix()
    }

    fun silence(source: Any) {
        reports.remove(source)
        mix()
    }

    @Synchronized
    private fun mix() {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0.004f, 0.2f)
        lastNanos = now
        if (reports.isEmpty()) {
            all.fade(dt); low.fade(dt); middle.fade(dt); treble.fade(dt)
        } else {
            val readings = reports.values
            val overall = readings.maxOf { it.overall }
            all.step(overall, dt)
            val withBands = readings.filter { it.bands != null }
            if (withBands.isNotEmpty()) {
                low.step(withBands.maxOf { it.bands!![0] }, dt)
                middle.step(withBands.maxOf { it.bands!![1] }, dt)
                treble.step(withBands.maxOf { it.bands!![2] }, dt)
            } else {
                // No band filters (e.g. unsupported platform): every band follows the overall loudness.
                low.step(overall, dt); middle.step(overall, dt); treble.step(overall, dt)
            }
        }
        level = all.value
        bass = low.value
        mid = middle.value
        high = treble.value
        kick = low.pulse
        beat = maxOf(low.pulse, middle.pulse * 0.7f, all.pulse * 0.6f)
    }
}
