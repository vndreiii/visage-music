@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.music.vivi.compose.ui.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.music.lyricsync.LyricsParser
import com.music.lyricsync.LyricsTimeline
import com.music.vivi.compose.prefs.DesktopPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test

/**
 * Offscreen timing harness (ImageComposeScene, CPU raster, no audio/window). Prints numbers only when
 * LYRICS_BENCH=1 so it is a no-op in normal test runs.
 */
class LyricsPerfBench {
    private val bench = System.getenv("LYRICS_BENCH") == "1"

    private fun sample(n: Int) = buildString {
        for (i in 0 until n) {
            val t = 1000 + i * 3000
            fun ts(ms: Int) = "%02d:%02d.%02d".format(ms / 60000, ms / 1000 % 60, ms % 1000 / 10)
            append("[${ts(t)}]")
            val words = listOf("City", "lights", "are", "fading", "slowly", "tonight", "over", "the", "river", "bend")
            words.forEachIndexed { j, w -> append("<${ts(t + j * 250)}>$w ") }
            append("<${ts(t + 2600)}>\n")
        }
    }

    private fun ms(a: Long) = a / 1_000_000.0

    @Test
    fun listOpenAndScroll() {
        if (!bench) return
        val cfg = LyricsConfig()
        val timeline = LyricsTimeline.build(LyricsParser.parse(sample(60)), cfg.estimateWords)
        runBlocking(Dispatchers.Swing) {
            val clock = mutableFloatStateOf(1500f)
            fun openScene() = ImageComposeScene(width = 640, height = 400, coroutineContext = coroutineContext) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF14161A))) {
                        LyricsList(timeline, cfg, clock, accent = Color(0xFFFF6FA5), onSeek = null)
                    }
                }
            }
            var t = 0L
            fun frame(sc: ImageComposeScene): Long {
                val s = System.nanoTime(); sc.render(t).close(); t += 16_000_000; return System.nanoTime() - s
            }
            // warm the JIT / fonts with a throwaway scene
            openScene().also { repeat(5) { _ -> frame(it) }; it.close() }
            val results = StringBuilder()
            for (round in 1..3) {
                clock.floatValue = 1500f
                val sc = openScene()
                var open = 0L
                repeat(3) { open += frame(sc) }
                var scrollTotal = 0L; var worst = 0L
                for (line in 0 until 60) {
                    clock.floatValue = (1000 + line * 3000 + 800).toFloat()
                    repeat(12) { val d = frame(sc); scrollTotal += d; worst = maxOf(worst, d) }
                }
                // scroll back to the top and down again: lines that left the viewport come back
                var back = 0L
                for (line in 59 downTo 0 step 1) {
                    clock.floatValue = (1000 + line * 3000 + 800).toFloat()
                    repeat(6) { back += frame(sc) }
                }
                sc.close()
                // reopen the panel (the 'y' key twice)
                clock.floatValue = 1500f
                val sc2 = openScene()
                var reopen = 0L
                repeat(3) { reopen += frame(sc2) }
                sc2.close()
                results.append("round $round: open3=%.1fms scrollDown(60 lines x12 frames)=%.1fms worstFrame=%.1fms scrollBack=%.1fms reopen3=%.1fms\n"
                    .format(ms(open), ms(scrollTotal), ms(worst), ms(back), ms(reopen)))
            }
            println("LYRICS_BENCH_LIST\n$results")
        }
    }

    @Test
    fun cutsFrames() {
        if (!bench) return
        val dir = File(System.getProperty("java.io.tmpdir"), "lyrics-bench-${System.nanoTime()}").apply { mkdirs() }
        val prefs = DesktopPrefs(File(dir, "s.properties"))
        startKoin { modules(module { single { prefs } }) }
        try {
            val cfg = LyricsConfig(scene = LyricsScene.CUTS)
            val timeline = LyricsTimeline.build(LyricsParser.parse(sample(20)), cfg.estimateWords)
            runBlocking(Dispatchers.Swing) {
                val clock = mutableFloatStateOf(1500f)
                val sc = ImageComposeScene(width = 1280, height = 720, coroutineContext = coroutineContext) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF14161A))) {
                            LyricsCutsScene(timeline, cfg, clock, accent = Color(0xFFFF6FA5))
                        }
                    }
                }
                var t = 0L
                repeat(20) { sc.render(t).close(); t += 16_000_000 }
                val frames = ArrayList<Long>()
                repeat(240) { i ->
                    clock.floatValue = 1500f + i * 16f
                    val s = System.nanoTime(); sc.render(t).close(); frames += System.nanoTime() - s; t += 16_000_000
                }
                sc.close()
                val sorted = frames.sorted()
                println("LYRICS_BENCH_CUTS mean=%.2fms median=%.2fms p95=%.2fms (240 frames, 1280x720, same line mostly)"
                    .format(ms(frames.sum()) / frames.size, ms(sorted[frames.size / 2]), ms(sorted[(frames.size * 0.95).toInt()])))
            }
        } finally {
            stopKoin(); dir.deleteRecursively()
        }
    }

    /** Isolates the shaping cost: 60 lines built directly (what the measure lambda ran) vs served by LineLayoutCache. */
    @Test
    fun layoutBuildVsCache() {
        if (!bench) return
        val cfg = LyricsConfig()
        val timeline = LyricsTimeline.build(LyricsParser.parse(sample(60)), cfg.estimateWords)
        runBlocking(Dispatchers.Swing) {
            var out = ""
            val sc = ImageComposeScene(width = 100, height = 100, coroutineContext = coroutineContext) {
                val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 48)
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    fun raw(l: com.music.lyricsync.LyricsLine) = buildLineLayout(measurer, l, cfg, 1f, false, 560, density)
                    timeline.lines.take(5).forEach { raw(it) } // warm up
                    val t0 = System.nanoTime()
                    // fresh per-line measurers like the old code: every call shapes from scratch
                    timeline.lines.forEach { raw(it) }
                    val cold = System.nanoTime() - t0
                    fun cached(l: com.music.lyricsync.LyricsLine) = LineLayoutCache.getOrBuild(
                        LineLayoutKey(l, cfg.font, cfg.weight, cfg.fontSizeSp, cfg.textCase, cfg.align, cfg.wordSync, cfg.showTranslation, 1f, false, 560, density.density, density.fontScale),
                    ) { raw(l) }
                    LineLayoutCache.clear()
                    timeline.lines.forEach { cached(it) }
                    val t1 = System.nanoTime()
                    timeline.lines.forEach { cached(it) }
                    val hit = System.nanoTime() - t1
                    out = "LYRICS_BENCH_LAYOUT 60 lines: build=%.2fms (%.3fms/line) cacheHit=%.3fms".format(ms(cold), ms(cold) / 60, ms(hit))
                }
            }
            repeat(5) { sc.render(it * 16_000_000L).close(); kotlinx.coroutines.delay(50) }
            sc.close()
            println(out)
        }
    }
}
