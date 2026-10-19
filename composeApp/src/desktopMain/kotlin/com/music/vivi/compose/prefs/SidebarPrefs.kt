package com.music.vivi.compose.prefs

import com.music.vivi.compose.navigation.Routes

/** Which edge a sidebar docks to. */
enum class SidebarSide { LEFT, RIGHT }

/** Library sidebar presentation: full panel, thin icon rail of covers, or fully hidden. */
enum class LibrarySidebarState { EXPANDED, RAIL, HIDDEN }

/** Where the library sidebar appears. */
enum class LibrarySidebarPages {
    /** Every page except Now Playing (both routes) and Settings (all sub-pages). The default. */
    ALL_EXCEPT_PLAYER_SETTINGS,
    HOME_ONLY,
}

enum class LibraryViewMode { LIST, GRID }

object SidebarKeys {
    const val LibraryEnabled = "libSidebarEnabled"
    const val LibrarySide = "libSidebarSide"
    const val LibraryDefaultState = "libSidebarDefaultState"
    /** Last state the user left it in (expanded / rail / hidden); falls back to the default state. */
    const val LibraryState = "libSidebarState"
    const val LibraryPages = "libSidebarPages"
    const val LibraryWidth = "libSidebarWidth"
    const val LibraryFilter = "libSidebarFilter"
    const val LibrarySort = "libSidebarSort"
    const val LibraryView = "libSidebarView"
    const val LibraryFoldersFirst = "libSidebarFoldersFirst"
    const val LibraryShowHidden = "libSidebarShowHidden"
    const val QueueSide = "queueSidebarSide"
    const val QueueOnHome = "queueSidebarOnHome"
}

data class SidebarConfig(
    val libraryEnabled: Boolean,
    val librarySide: SidebarSide,
    val libraryDefaultState: LibrarySidebarState,
    /** Effective current state (remembered state, else [libraryDefaultState]). */
    val libraryState: LibrarySidebarState,
    val libraryPages: LibrarySidebarPages,
    val libraryWidthDp: Int,
    val queueSide: SidebarSide,
    val queueOnHome: Boolean,
) {
    companion object {
        const val MinLibraryWidth = 260
        const val MaxLibraryWidth = 480
        const val DefaultLibraryWidth = 340
        const val RailWidth = 76
        const val QueueWidth = 360

        private inline fun <reified T : Enum<T>> PrefReader.enum(key: String, default: T): T =
            runCatching { enumValueOf<T>(getString(key, default.name)) }.getOrDefault(default)

        fun resolve(p: PrefReader): SidebarConfig {
            val default = p.enum(SidebarKeys.LibraryDefaultState, LibrarySidebarState.EXPANDED)
            return SidebarConfig(
                libraryEnabled = p.getBoolean(SidebarKeys.LibraryEnabled, true),
                librarySide = p.enum(SidebarKeys.LibrarySide, SidebarSide.RIGHT),
                libraryDefaultState = default,
                libraryState = p.enum(SidebarKeys.LibraryState, default),
                libraryPages = when (val raw = p.getString(SidebarKeys.LibraryPages, "")) {
                    // Legacy "All main pages" is a subset of the new default, so it maps onto it.
                    "HOME_ONLY" -> LibrarySidebarPages.HOME_ONLY
                    else -> runCatching { enumValueOf<LibrarySidebarPages>(raw) }
                        .getOrDefault(LibrarySidebarPages.ALL_EXCEPT_PLAYER_SETTINGS)
                },
                libraryWidthDp = p.getInt(SidebarKeys.LibraryWidth, DefaultLibraryWidth)
                    .coerceIn(MinLibraryWidth, MaxLibraryWidth),
                queueSide = p.enum(SidebarKeys.QueueSide, SidebarSide.RIGHT),
                queueOnHome = p.getBoolean(SidebarKeys.QueueOnHome, false),
            )
        }
    }
}

object Sidebars {
    /** Routes where the library sidebar never appears: the player pages and every Settings page. */
    fun isPlayerOrSettings(route: String): Boolean =
        route == Routes.NOW_PLAYING || route == Routes.NOW_PLAYING_FULLSCREEN ||
            route == Routes.SETTINGS || route.startsWith("settings/")

    /** Whether the library sidebar belongs on [route] (ignoring its collapsed/hidden state). Pure over the route. */
    fun libraryAllowedOn(route: String?, cfg: SidebarConfig): Boolean = when {
        !cfg.libraryEnabled || route == null -> false
        isPlayerOrSettings(route) -> false
        cfg.libraryPages == LibrarySidebarPages.HOME_ONLY -> route == Routes.HOME
        else -> true
    }

    /** The queue rail is auto-shown on Home when the setting is on and the user hasn't dismissed it there. */
    fun queueAutoOpenOnHome(route: String?, cfg: SidebarConfig, dismissedOnHome: Boolean): Boolean =
        cfg.queueOnHome && route == Routes.HOME && !dismissedOnHome

    /**
     * Expanded library width that still leaves [minMain] for the content next to a [queueWidth]
     * queue rail (0 when closed). Never below [floor], never above the user's [desired] width.
     */
    fun libraryWidthFor(available: Int, queueWidth: Int, desired: Int, minMain: Int = 520, floor: Int = 240): Int =
        (available - queueWidth - minMain).coerceAtLeast(floor).coerceAtMost(desired.coerceAtLeast(floor))

    /** What the toggle button / shortcut does: hide when shown, otherwise bring back the preferred shown state. */
    fun toggled(current: LibrarySidebarState, default: LibrarySidebarState): LibrarySidebarState = when {
        current != LibrarySidebarState.HIDDEN -> LibrarySidebarState.HIDDEN
        default == LibrarySidebarState.HIDDEN -> LibrarySidebarState.EXPANDED
        else -> default
    }
}
