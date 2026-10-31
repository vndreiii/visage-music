package com.music.vivi.compose.ui.player.background

import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertTrue

class ScrollShaderTest {
    @Test
    fun everyShaderStillCompilesWithTheScrollFold() {
        for (shader in BackgroundShader.entries) {
            val src = withScroll(shader.sksl)
            assertTrue("foldScroll(fc0)" in src, "${shader.name}: entry point was not rewritten")
            // Throws with the compiler's message when the SkSL is invalid.
            RuntimeEffect.makeForShader(src)
        }
    }
}
