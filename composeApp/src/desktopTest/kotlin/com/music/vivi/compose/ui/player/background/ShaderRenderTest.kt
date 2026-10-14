package com.music.vivi.compose.ui.player.background

import org.jetbrains.skia.Color as SkColor
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compiles every background shader with Skia and renders frames offscreen on the CPU. Frames are
 * written to `$VIVI_SHADER_OUT` when set (for eyeballing); the assertions only check they compile,
 * are not blank, and actually animate.
 */
class ShaderRenderTest {
    private val palette = listOf(
        floatArrayOf(0.95f, 0.35f, 0.55f),
        floatArrayOf(0.55f, 0.35f, 0.95f),
        floatArrayOf(0.25f, 0.75f, 0.85f),
        floatArrayOf(0.98f, 0.70f, 0.30f),
        floatArrayOf(0.20f, 0.25f, 0.55f),
    )

    /** A cover-like test image: coloured blocks with a bright disc. */
    private fun sampleArtwork(edge: Int): Image {
        val s = Surface.makeRasterN32Premul(edge, edge)
        val c = s.canvas
        val p = Paint()
        val cols = intArrayOf(0xFFE0457A.toInt(), 0xFF5B3FD0.toInt(), 0xFF3BB4C8.toInt(), 0xFFF2A640.toInt())
        for (i in 0 until 4) {
            p.color = cols[i]
            c.drawRect(org.jetbrains.skia.Rect.makeXYWH((i % 2) * edge / 2f, (i / 2) * edge / 2f, edge / 2f, edge / 2f), p)
        }
        p.color = SkColor.makeARGB(255, 255, 255, 255)
        c.drawCircle(edge * 0.5f, edge * 0.45f, edge * 0.18f, p)
        return s.makeImageSnapshot()
    }

    private fun render(kind: BackgroundShader, time: Float, w: Int = 640, h: Int = 400): Surface {
        val effect = RuntimeEffect.makeForShader(kind.sksl)
        val b = RuntimeShaderBuilder(effect)
        b.uniform("iResolution", w.toFloat(), h.toFloat())
        b.uniform("iTime", time)
        palette.forEachIndexed { i, c -> b.uniform("c$i", c[0], c[1], c[2]) }
        if (kind.usesImage) {
            val img = sampleArtwork(kind.imageEdge)
            b.child("img", img.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)))
            b.uniform("imgSize", img.width.toFloat(), img.height.toFloat())
        }
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawRect(org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), Paint().apply { shader = b.makeShader() })
        return surface
    }

    private fun pixels(s: Surface): ByteArray {
        val bmp = org.jetbrains.skia.Bitmap()
        bmp.allocN32Pixels(s.width, s.height)
        s.readPixels(bmp, 0, 0)
        return bmp.readPixels()!!
    }

    @Test
    fun everyShaderCompilesRendersAndAnimates() {
        val out = System.getenv("VIVI_SHADER_OUT")?.let { File(it).apply { mkdirs() } }
        for (kind in BackgroundShader.entries) {
            val a = render(kind, 3f)
            val b = render(kind, 9f)
            val pa = pixels(a)
            val pb = pixels(b)
            // Not blank: some channel value above a dark floor, and some variation.
            val max = pa.maxOf { it.toInt() and 0xFF }
            assertTrue(max > 60, "${kind.name} renders too dark/blank (max=$max)")
            assertTrue(pa.toSet().size > 20, "${kind.name} has no variation")
            assertTrue(!pa.contentEquals(pb), "${kind.name} does not animate")
            out?.let {
                File(it, "${kind.name.lowercase()}-t3.png").writeBytes(a.makeImageSnapshot().encodeToData()!!.bytes)
                File(it, "${kind.name.lowercase()}-t9.png").writeBytes(b.makeImageSnapshot().encodeToData()!!.bytes)
            }
        }
        assertEquals(7, BackgroundShader.entries.size)
    }
}
