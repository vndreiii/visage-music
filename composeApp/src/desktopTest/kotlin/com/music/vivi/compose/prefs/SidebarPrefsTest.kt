package com.music.vivi.compose.prefs

import com.music.vivi.compose.navigation.Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidebarPrefsTest {
    private fun reader(vararg pairs: Pair<String, String>) = object : PrefReader {
        private val m = pairs.toMap()
        override fun getString(key: String, default: String) = m[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = m[key]?.toBooleanStrictOrNull() ?: default
        override fun getInt(key: String, default: Int) = m[key]?.toIntOrNull() ?: default
    }

    @Test fun `defaults put the library on the right and the queue on the right`() {
        val c = SidebarConfig.resolve(reader())
        assertTrue(c.libraryEnabled)
        assertEquals(SidebarSide.RIGHT, c.librarySide)
        assertEquals(LibrarySidebarState.EXPANDED, c.libraryState)
        assertEquals(SidebarSide.RIGHT, c.queueSide)
        assertFalse(c.queueOnHome)
        assertEquals(SidebarConfig.DefaultLibraryWidth, c.libraryWidthDp)
    }

    @Test fun `remembered state wins over default and bad values fall back`() {
        val c = SidebarConfig.resolve(
            reader(
                SidebarKeys.LibraryDefaultState to "RAIL",
                SidebarKeys.LibraryState to "HIDDEN",
                SidebarKeys.LibrarySide to "nonsense",
                SidebarKeys.LibraryWidth to "9999",
            ),
        )
        assertEquals(LibrarySidebarState.RAIL, c.libraryDefaultState)
        assertEquals(LibrarySidebarState.HIDDEN, c.libraryState)
        assertEquals(SidebarSide.RIGHT, c.librarySide)
        assertEquals(SidebarConfig.MaxLibraryWidth, c.libraryWidthDp)
        assertEquals(LibrarySidebarState.RAIL, SidebarConfig.resolve(reader(SidebarKeys.LibraryDefaultState to "RAIL")).libraryState)
    }

    @Test fun `library is on every page except Now Playing and Settings by default`() {
        val c = SidebarConfig.resolve(reader())
        assertEquals(LibrarySidebarPages.ALL_EXCEPT_PLAYER_SETTINGS, c.libraryPages)
        listOf(Routes.HOME, Routes.LIBRARY, Routes.CHARTS, Routes.MOOD_AND_GENRES, Routes.NEW_RELEASE, "search/abc", "album/x", "artist/x", "online_playlist/x", "local_playlist/x", Routes.TAG_MANAGER)
            .forEach { assertTrue(Sidebars.libraryAllowedOn(it, c), it) }
        listOf(Routes.NOW_PLAYING, Routes.NOW_PLAYING_FULLSCREEN, Routes.SETTINGS, "settings/appearance/theme", Routes.SETTINGS_SIDEBARS)
            .forEach { assertFalse(Sidebars.libraryAllowedOn(it, c), it) }
        assertFalse(Sidebars.libraryAllowedOn(null, c))
    }

    @Test fun `every Routes constant is visible unless it is a player or settings route`() {
        val routes = Routes::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.apply { isAccessible = true }.get(null) as String }
        assertTrue(routes.size > 20)
        val c = SidebarConfig.resolve(reader())
        routes.forEach { r ->
            val hidden = r == Routes.NOW_PLAYING || r == Routes.NOW_PLAYING_FULLSCREEN || r == "settings" || r.startsWith("settings/")
            assertEquals(!hidden, Sidebars.libraryAllowedOn(r, c), r)
        }
    }

    @Test fun `home only option and legacy stored values`() {
        val home = SidebarConfig.resolve(reader(SidebarKeys.LibraryPages to "HOME_ONLY"))
        assertTrue(Sidebars.libraryAllowedOn(Routes.HOME, home))
        assertFalse(Sidebars.libraryAllowedOn(Routes.LIBRARY, home))
        val legacy = SidebarConfig.resolve(reader(SidebarKeys.LibraryPages to "ALL_MAIN"))
        assertEquals(LibrarySidebarPages.ALL_EXCEPT_PLAYER_SETTINGS, legacy.libraryPages)
        val off = SidebarConfig.resolve(reader(SidebarKeys.LibraryEnabled to "false"))
        assertFalse(Sidebars.libraryAllowedOn(Routes.HOME, off))
    }

    @Test fun `queue auto-opens on Home only when enabled and not dismissed`() {
        val on = SidebarConfig.resolve(reader(SidebarKeys.QueueOnHome to "true"))
        assertTrue(Sidebars.queueAutoOpenOnHome(Routes.HOME, on, false))
        assertFalse(Sidebars.queueAutoOpenOnHome(Routes.HOME, on, true))
        assertFalse(Sidebars.queueAutoOpenOnHome(Routes.LIBRARY, on, false))
        assertFalse(Sidebars.queueAutoOpenOnHome(Routes.HOME, SidebarConfig.resolve(reader()), false))
    }

    @Test fun `toggle hides when shown and restores the preferred state`() {
        assertEquals(LibrarySidebarState.HIDDEN, Sidebars.toggled(LibrarySidebarState.EXPANDED, LibrarySidebarState.EXPANDED))
        assertEquals(LibrarySidebarState.HIDDEN, Sidebars.toggled(LibrarySidebarState.RAIL, LibrarySidebarState.EXPANDED))
        assertEquals(LibrarySidebarState.RAIL, Sidebars.toggled(LibrarySidebarState.HIDDEN, LibrarySidebarState.RAIL))
        assertEquals(LibrarySidebarState.EXPANDED, Sidebars.toggled(LibrarySidebarState.HIDDEN, LibrarySidebarState.HIDDEN))
    }

    @Test fun `library width shrinks to leave room for the content and the queue`() {
        assertEquals(340, Sidebars.libraryWidthFor(1600, 0, 340))
        assertEquals(340, Sidebars.libraryWidthFor(1600, 360, 340))
        assertEquals(320, Sidebars.libraryWidthFor(1200, 360, 400))
        assertEquals(240, Sidebars.libraryWidthFor(700, 360, 340))
    }
}
