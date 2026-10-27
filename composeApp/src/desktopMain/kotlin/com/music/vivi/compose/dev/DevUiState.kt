package com.music.vivi.compose.dev

/**
 * Dev-only UI state forcing: `VISAGE_UI_STATE=<name>[,<name>...]` makes the app show that state even when
 * the real condition is false (e.g. the Home login state while logged in). Read once at startup. Unset means
 * nothing changes. Forced states never write to prefs; see docs/dev-ui-states.md for the names.
 */
object DevUiState {
    private val forced: Set<String> by lazy {
        System.getenv("VISAGE_UI_STATE").orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** True when the named state is forced on for this run. */
    fun has(name: String): Boolean = name.lowercase() in forced
}
