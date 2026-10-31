@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.music.vivi.compose.ui.popup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PopupLayoutUiTest {
    @Composable
    private fun Sized(w: Int, h: Int) {
        Box(Modifier.size(w.dp, h.dp)) {
            PopupPlayerLayout(
                art = { Box(Modifier.fillMaxSize().testTag("art")) },
                info = { c -> Text(if (c) "info-compact" else "info", Modifier.testTag("info")) },
                controls = { c -> Text(if (c) "controls-compact" else "controls", Modifier.testTag("controls")) },
                lyrics = { Text("lyrics", Modifier.testTag("lyrics-stub")) },
            )
        }
    }

    @Test fun portraitShowsStackWithoutLyrics() = runComposeUiTest {
        setContent { Sized(340, 480) }
        onNodeWithTag("popup-stack").assertIsDisplayed()
        onNodeWithTag("art").assertIsDisplayed()
        onNodeWithTag("controls").assertIsDisplayed()
        onNodeWithTag("popup-landscape").assertDoesNotExist()
        onNodeWithTag("lyrics-stub").assertDoesNotExist()
    }

    @Test fun minimumSizeStillShowsEverything() = runComposeUiTest {
        setContent { Sized(PopupGeometry.MIN_W, PopupGeometry.MIN_H) }
        onNodeWithTag("popup-stack").assertIsDisplayed()
        onNodeWithTag("info").assertIsDisplayed()
        onNodeWithTag("controls").assertIsDisplayed()
        onNodeWithTag("art").assertIsDisplayed()
        onNodeWithTag("info").assertTextEquals("info-compact")
    }

    @Test fun tallWindowIsNotCompact() = runComposeUiTest {
        setContent { Sized(340, 560) }
        onNodeWithTag("info").assertTextEquals("info")
    }

    @Test fun landscapeShowsLyricsBesidePlayer() = runComposeUiTest {
        setContent { Sized(760, 420) }
        onNodeWithTag("popup-landscape").assertIsDisplayed()
        onNodeWithTag("lyrics-stub").assertIsDisplayed()
        onNodeWithTag("art").assertIsDisplayed()
        onNodeWithTag("controls").assertIsDisplayed()
        onNodeWithTag("popup-stack").assertDoesNotExist()
    }

    @Test fun widerButTooSmallStaysStacked() = runComposeUiTest {
        setContent { Sized(480, 340) }
        onNodeWithTag("popup-stack").assertIsDisplayed()
    }

    @Test fun landscapeLyricsFillWholeRightSideAndLeftColumnIsFixed() = runComposeUiTest {
        for (w in listOf(760, 1000)) {
            setContent { Sized(w, 500) }
            val lyrics = onNodeWithTag("popup-lyrics").getBoundsInRoot()
            assertEquals(0f, lyrics.top.value, 0.5f)
            assertEquals(500f, lyrics.bottom.value, 0.5f)
            assertEquals(w.toFloat(), lyrics.right.value, 0.5f)
            // Controls sit under the art in the left column, not under the lyrics.
            val controls = onNodeWithTag("controls").getBoundsInRoot()
            assertTrue(controls.right.value <= lyrics.left.value + 0.5f)
            assertTrue(controls.top.value > onNodeWithTag("art").getBoundsInRoot().bottom.value - 0.5f)
        }
        setContent { Sized(1000, 500) }
        assertEquals(330f, onNodeWithTag("popup-lyrics").getBoundsInRoot().left.value, 0.5f)
    }

    @Test fun landscapeShortWindowKeepsArtVisible() = runComposeUiTest {
        setContent { Sized(520, 340) }
        assertTrue(onNodeWithTag("art").getBoundsInRoot().let { it.bottom.value - it.top.value } > 60f)
    }
}
