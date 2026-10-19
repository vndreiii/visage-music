package com.music.vivi.compose.keys

import com.music.vivi.compose.keys.KeyBinding.Companion.char
import com.music.vivi.compose.keys.KeyBinding.Companion.key
import java.awt.event.KeyEvent as K

enum class ShortcutCategory(val label: String) {
    Playback("Playback"),
    Player("Player views"),
    Navigation("Navigation"),
    Library("Library"),
    Seek("Seek to position"),
    General("General"),
}

/**
 * Every rebindable action. [repeatable] actions fire again while the key is held (seek, volume);
 * the rest fire once per physical press.
 */
enum class ShortcutAction(
    val label: String,
    val category: ShortcutCategory,
    val defaults: List<KeyBinding>,
    val repeatable: Boolean = false,
    val hint: String? = null,
) {
    PLAY_PAUSE("Play / pause", ShortcutCategory.Playback, listOf(key(K.VK_SPACE))),
    NEXT("Next track", ShortcutCategory.Playback, listOf(key(K.VK_RIGHT, ctrl = true), key(K.VK_N))),
    PREVIOUS("Previous track", ShortcutCategory.Playback, listOf(key(K.VK_LEFT, ctrl = true), key(K.VK_P))),
    SEEK_FORWARD("Skip forward 5 s", ShortcutCategory.Playback, listOf(key(K.VK_RIGHT)), repeatable = true),
    SEEK_BACKWARD("Skip back 5 s", ShortcutCategory.Playback, listOf(key(K.VK_LEFT)), repeatable = true),
    SEEK_FORWARD_LONG("Skip forward 15 s", ShortcutCategory.Playback, listOf(key(K.VK_RIGHT, shift = true)), repeatable = true),
    SEEK_BACKWARD_LONG("Skip back 15 s", ShortcutCategory.Playback, listOf(key(K.VK_LEFT, shift = true)), repeatable = true),
    VOLUME_UP("Volume up", ShortcutCategory.Playback, listOf(key(K.VK_UP, ctrl = true)), repeatable = true),
    VOLUME_DOWN("Volume down", ShortcutCategory.Playback, listOf(key(K.VK_DOWN, ctrl = true)), repeatable = true),
    MUTE("Mute / unmute", ShortcutCategory.Playback, listOf(key(K.VK_M))),
    SHUFFLE("Toggle shuffle", ShortcutCategory.Playback, listOf(key(K.VK_S))),
    REPEAT("Cycle repeat mode", ShortcutCategory.Playback, listOf(key(K.VK_R))),
    LIKE("Like / unlike track", ShortcutCategory.Playback, listOf(key(K.VK_L))),
    START_RADIO("Start radio from this track", ShortcutCategory.Playback, listOf(key(K.VK_R, shift = true))),

    TOGGLE_LYRICS("Show / hide lyrics", ShortcutCategory.Player, listOf(key(K.VK_Y))),
    TOGGLE_QUEUE("Show / hide queue", ShortcutCategory.Player, listOf(key(K.VK_Q))),
    TOGGLE_FULLSCREEN("Fullscreen player", ShortcutCategory.Player, listOf(key(K.VK_F), key(K.VK_F11))),
    OPEN_PLAYER("Open Now Playing", ShortcutCategory.Player, listOf(key(K.VK_ENTER, ctrl = true))),
    TRACK_ACTIONS("Track actions (add to playlist, share, …)", ShortcutCategory.Player, listOf(char('.'))),
    LEAVE_FULLSCREEN("Leave fullscreen / close overlay", ShortcutCategory.Player, listOf(key(K.VK_ESCAPE)),
        hint = "Only acts when there is something to leave or close"),

    BACK("Go back", ShortcutCategory.Navigation, listOf(key(K.VK_LEFT, alt = true))),
    FOCUS_SEARCH("Search", ShortcutCategory.Navigation, listOf(char('/'), key(K.VK_K, ctrl = true), key(K.VK_F, ctrl = true))),
    GO_HOME("Go to Home", ShortcutCategory.Navigation, listOf(key(K.VK_1, ctrl = true))),
    GO_SEARCH("Go to Search", ShortcutCategory.Navigation, listOf(key(K.VK_2, ctrl = true))),
    GO_LIBRARY("Go to Library", ShortcutCategory.Navigation, listOf(key(K.VK_3, ctrl = true))),
    GO_SETTINGS("Go to Settings", ShortcutCategory.Navigation, listOf(key(K.VK_4, ctrl = true), key(K.VK_COMMA, ctrl = true))),

    TOGGLE_LIBRARY_SIDEBAR("Show / hide library sidebar", ShortcutCategory.Library, listOf(key(K.VK_L, ctrl = true, shift = true))),

    SHOW_SHORTCUTS("Show keyboard shortcuts", ShortcutCategory.General, listOf(char('?'), key(K.VK_F1))),

    SEEK_0("Jump to 0%", ShortcutCategory.Seek, listOf(key(K.VK_0))),
    SEEK_10("Jump to 10%", ShortcutCategory.Seek, listOf(key(K.VK_1))),
    SEEK_20("Jump to 20%", ShortcutCategory.Seek, listOf(key(K.VK_2))),
    SEEK_30("Jump to 30%", ShortcutCategory.Seek, listOf(key(K.VK_3))),
    SEEK_40("Jump to 40%", ShortcutCategory.Seek, listOf(key(K.VK_4))),
    SEEK_50("Jump to 50%", ShortcutCategory.Seek, listOf(key(K.VK_5))),
    SEEK_60("Jump to 60%", ShortcutCategory.Seek, listOf(key(K.VK_6))),
    SEEK_70("Jump to 70%", ShortcutCategory.Seek, listOf(key(K.VK_7))),
    SEEK_80("Jump to 80%", ShortcutCategory.Seek, listOf(key(K.VK_8))),
    SEEK_90("Jump to 90%", ShortcutCategory.Seek, listOf(key(K.VK_9)));

    /** Stable persistence id (the enum name). */
    val id: String get() = name

    /** 0..9 for the "jump to N0%" actions. */
    val seekTenths: Int? get() = if (category == ShortcutCategory.Seek) ordinal - SEEK_0.ordinal else null

    companion object {
        fun fromId(id: String): ShortcutAction? = entries.firstOrNull { it.name == id }
    }
}
