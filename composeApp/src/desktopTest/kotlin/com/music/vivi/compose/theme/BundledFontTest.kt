package com.music.vivi.compose.theme

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr

class BundledFontTest {
    @Test fun allBundledWeightsLoadAndCoverUiText() {
        for (name in listOf("Manrope", "Roboto", "DMSans")) for (weight in listOf(400, 500, 600, 700)) {
            val bytes = assertNotNull(javaClass.classLoader.getResourceAsStream("fonts/$name-$weight.ttf")).use { it.readBytes() }
            Data.makeFromBytes(bytes).use { data ->
                assertNotNull(FontMgr.default.makeFromData(data)).use { face ->
                    assertTrue(face.getStringGlyphs("Queue History ADÉLA").all { it.toInt() != 0 })
                }
            }
        }
    }
}
