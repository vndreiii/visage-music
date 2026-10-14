package com.music.vivi.compose.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class UpdateChoicesTest {
    private class MemoryStore : UpdateKeyValues {
        val map = HashMap<String, String>()
        override fun getString(key: String) = map[key]
        override fun setString(key: String, value: String) { map[key] = value }
    }

    @Test
    fun dismissHidesOnlyThatVersion() {
        val choices = UpdateChoices(MemoryStore())
        choices.dismiss("1.2.3")
        assertNull(choices.promptDelay("1.2.3", now = 0))
        assertEquals(0L, choices.promptDelay("1.2.4", now = 0))
    }

    @Test
    fun laterHidesForTwentyFourHours() {
        val choices = UpdateChoices(MemoryStore())
        val t0 = 1_000_000L
        choices.remindLater("2.0.0", now = t0)
        assertEquals(UpdateChoices.LATER_MS, choices.promptDelay("2.0.0", now = t0))
        assertEquals(0L, choices.promptDelay("2.0.0", now = t0 + UpdateChoices.LATER_MS))
    }

    @Test
    fun autoCheckDefaultsOn() {
        val choices = UpdateChoices(MemoryStore())
        assertTrue(choices.autoCheck())
        choices.setAutoCheck(false)
        assertFalse(choices.autoCheck())
    }

    @Test
    fun installModeFollowsEnvironment() {
        assertEquals(InstallMode.FlatpakManaged, InstallMode.detect(mapOf("FLATPAK_ID" to "app.visage")))
        assertEquals(InstallMode.ReleasePageOnly, InstallMode.detect(emptyMap()))
        val image = File("/opt/VIsage.AppImage")
        assertEquals(InstallMode.AppImage(image.absoluteFile), InstallMode.detect(mapOf("APPIMAGE" to image.path), flatpakInfoExists = false) { true })
        assertEquals(InstallMode.ReleasePageOnly, InstallMode.detect(mapOf("APPIMAGE" to image.path), flatpakInfoExists = false) { false })
    }
}
