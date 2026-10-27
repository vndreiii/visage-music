package com.music.vivi.compose.prefs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LookMigrationTest {
    private fun existingFile(vararg lines: String): File =
        File.createTempFile("look", ".properties").also { it.writeText(lines.joinToString("\n") + "\nsomething=1\n") }

    @Test
    fun untouchedLookIsOfferedBackAndRestoredExactly() {
        val prefs = DesktopPrefs(existingFile("playerBackgroundDarkness=30"))
        assertTrue(LookMigration.pending(prefs))
        // The user's own choice stays; only options they never set take the new defaults.
        assertEquals(30, prefs.getInt(PrefKeys.PlayerBackgroundDarkness, LookDefaults.PlayerDarkness))
        assertEquals(LookDefaults.PlayerStyle.name, prefs.getString(PrefKeys.PlayerBackgroundStyle, LookDefaults.PlayerStyle.name))
        LookMigration.restorePrevious(prefs)
        assertFalse(LookMigration.pending(prefs))
        assertEquals(LookDefaults.OldPlayerStyle.name, prefs.getString(PrefKeys.PlayerBackgroundStyle, LookDefaults.PlayerStyle.name))
        assertEquals(false, prefs.getBoolean(PrefKeys.GlassBlur, LookDefaults.GlassBlur))
        assertEquals(50, prefs.getInt(PrefKeys.PageBackgroundDarkness, LookDefaults.PageDarkness))
        assertEquals(30, prefs.getInt(PrefKeys.PlayerBackgroundDarkness, LookDefaults.PlayerDarkness))
    }

    @Test
    fun userWhoSetEverythingIsNotBothered() {
        val prefs = DesktopPrefs(
            existingFile(
                "glassBlur=false", "playerBackgroundStyle=BLUR", "playerBackgroundDarkness=10",
                "pageBackgroundStyle=BLUR", "pageBackgroundDarkness=20",
            ),
        )
        assertFalse(LookMigration.pending(prefs))
    }

    @Test
    fun freshInstallHasNothingToRevert() {
        val f = File.createTempFile("fresh", ".properties").also { it.delete() }
        assertFalse(LookMigration.pending(DesktopPrefs(f)))
    }
}
