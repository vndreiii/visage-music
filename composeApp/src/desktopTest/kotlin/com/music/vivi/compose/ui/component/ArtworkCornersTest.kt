package com.music.vivi.compose.ui.component

import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Color as SkColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkCornersTest {
    private fun png(rounding: Float): ByteArray {
        val s = Surface.makeRasterN32Premul(200, 200)
        val c = s.canvas
        c.clear(SkColor.TRANSPARENT)
        c.drawRRect(RRect.makeXYWH(0f, 0f, 200f, 200f, rounding), Paint().apply { color = SkColor.makeARGB(255, 20, 20, 30); mode = PaintMode.FILL })
        return s.makeImageSnapshot().encodeToData()!!.bytes
    }

    @Test
    fun squareArtNeedsNoZoom() {
        assertEquals(1f, artworkCornerBleed(png(0f)))
    }

    @Test
    fun roundedArtIsZoomedJustEnoughToHideItsCorners() {
        val bleed = artworkCornerBleed(png(40f))
        assertTrue(bleed > 1.05f && bleed < 1.4f, "bleed=$bleed")
        // Bigger baked-in radius → bigger zoom.
        assertTrue(artworkCornerBleed(png(60f)) > bleed)
    }

    @Test
    fun garbageIsIgnored() {
        assertEquals(1f, artworkCornerBleed(byteArrayOf(1, 2, 3)))
    }
}
