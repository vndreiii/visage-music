package com.music.vivi.compose.keys

import java.awt.event.KeyEvent as Awt

/** AWT-free snapshot of a key press, so matching/serialising is unit-testable. */
data class KeyInput(
    val keyCode: Int,
    val keyChar: Char = Awt.CHAR_UNDEFINED,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
)

/**
 * One shortcut: a physical key ([keyCode], an AWT VK_ code) or a produced character ([char], layout
 * independent, e.g. '?' wherever it lives on the keyboard) plus modifiers.
 *
 * Character bindings ignore Shift (it is implied by the character) and never require a key code.
 * Key-code bindings require Shift to match exactly.
 */
data class KeyBinding(
    val keyCode: Int = 0,
    val char: Char? = null,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
) {
    init {
        require((keyCode != 0) != (char != null)) { "exactly one of keyCode / char" }
    }

    val hasCommandModifier: Boolean get() = ctrl || alt || meta

    fun matches(input: KeyInput): Boolean {
        if (input.ctrl != ctrl || input.alt != alt || input.meta != meta) return false
        if (char != null) return input.keyChar == char
        if (input.shift != shift) return false
        val code = KeyNames.normalize(input.keyCode)
        if (code == keyCode) return true
        // Non-Latin layouts report no usable VK_ code for letters/digits: fall back to the character.
        val name = KeyNames.nameOf(keyCode)
        return !hasCommandModifier && name.length == 1 && name[0].isLetterOrDigit() &&
            KeyNames.nameOf(code).let { it.startsWith("Code") || it == "Undefined" } &&
            input.keyChar.uppercaseChar() == name[0]
    }

    /** Stable text form, e.g. `Ctrl+Shift+Left`, `Space`, `Char(63)`. */
    fun serialize(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift && char == null) append("Shift+")
        if (meta) append("Meta+")
        append(if (char != null) "Char(${char.code})" else KeyNames.nameOf(keyCode))
    }

    /** Keycap labels, modifiers first, e.g. `["Ctrl", "Shift", "←"]`. */
    fun keycaps(): List<String> = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift && char == null) add("Shift")
        if (meta) add("Meta")
        add(if (char != null) char.toString() else KeyNames.keycapOf(keyCode))
    }

    fun display(): String = keycaps().joinToString(" + ")

    companion object {
        fun parse(text: String): KeyBinding? {
            var rest = text.trim()
            var ctrl = false; var alt = false; var shift = false; var meta = false
            while (true) {
                when {
                    rest.startsWith("Ctrl+") && rest.length > 5 -> { ctrl = true; rest = rest.substring(5) }
                    rest.startsWith("Alt+") && rest.length > 4 -> { alt = true; rest = rest.substring(4) }
                    rest.startsWith("Shift+") && rest.length > 6 -> { shift = true; rest = rest.substring(6) }
                    rest.startsWith("Meta+") && rest.length > 5 -> { meta = true; rest = rest.substring(5) }
                    else -> break
                }
            }
            if (rest.startsWith("Char(") && rest.endsWith(")")) {
                val code = rest.substring(5, rest.length - 1).toIntOrNull() ?: return null
                return KeyBinding(char = code.toChar(), ctrl = ctrl, alt = alt, meta = meta)
            }
            val code = KeyNames.codeOf(rest) ?: return null
            return KeyBinding(keyCode = code, ctrl = ctrl, alt = alt, shift = shift, meta = meta)
        }

        /**
         * Builds a binding from a captured key press, or null for a bare modifier press.
         * Shifted punctuation (`?`, `!`, `:`) becomes a character binding so it survives layouts.
         */
        fun fromInput(input: KeyInput): KeyBinding? {
            val code = KeyNames.normalize(input.keyCode)
            if (code in KeyNames.modifierCodes || code == 0) return null
            val c = input.keyChar
            val command = input.ctrl || input.alt || input.meta
            val printable = c != Awt.CHAR_UNDEFINED && !c.isISOControl() && !c.isWhitespace() && !c.isLetterOrDigit()
            if (input.shift && !command && printable) return KeyBinding(char = c)
            return KeyBinding(keyCode = code, ctrl = input.ctrl, alt = input.alt, shift = input.shift, meta = input.meta)
        }

        fun key(code: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
            KeyBinding(keyCode = code, ctrl = ctrl, alt = alt, shift = shift)

        fun char(c: Char) = KeyBinding(char = c)
    }
}

internal object KeyNames {
    val modifierCodes = setOf(Awt.VK_SHIFT, Awt.VK_CONTROL, Awt.VK_ALT, Awt.VK_META, Awt.VK_ALT_GRAPH, Awt.VK_CAPS_LOCK, Awt.VK_WINDOWS)

    private val named: Map<Int, String> = buildMap {
        for (c in 'A'..'Z') put(c.code, c.toString())
        for (c in '0'..'9') put(c.code, c.toString())
        for (i in 1..12) put(Awt.VK_F1 + i - 1, "F$i")
        put(Awt.VK_SPACE, "Space"); put(Awt.VK_ENTER, "Enter"); put(Awt.VK_ESCAPE, "Escape")
        put(Awt.VK_BACK_SPACE, "Backspace"); put(Awt.VK_DELETE, "Delete"); put(Awt.VK_TAB, "Tab")
        put(Awt.VK_LEFT, "Left"); put(Awt.VK_RIGHT, "Right"); put(Awt.VK_UP, "Up"); put(Awt.VK_DOWN, "Down")
        put(Awt.VK_HOME, "Home"); put(Awt.VK_END, "End"); put(Awt.VK_PAGE_UP, "PageUp"); put(Awt.VK_PAGE_DOWN, "PageDown")
        put(Awt.VK_INSERT, "Insert")
        put(Awt.VK_SLASH, "Slash"); put(Awt.VK_BACK_SLASH, "Backslash"); put(Awt.VK_COMMA, "Comma")
        put(Awt.VK_PERIOD, "Period"); put(Awt.VK_SEMICOLON, "Semicolon"); put(Awt.VK_QUOTE, "Quote")
        put(Awt.VK_MINUS, "Minus"); put(Awt.VK_EQUALS, "Equals"); put(Awt.VK_OPEN_BRACKET, "OpenBracket")
        put(Awt.VK_CLOSE_BRACKET, "CloseBracket"); put(Awt.VK_BACK_QUOTE, "BackQuote")
    }
    private val byName: Map<String, Int> = named.entries.associate { (k, v) -> v to k }

    private val keycaps = mapOf(
        Awt.VK_LEFT to "←", Awt.VK_RIGHT to "→", Awt.VK_UP to "↑", Awt.VK_DOWN to "↓",
        Awt.VK_SLASH to "/", Awt.VK_BACK_SLASH to "\\", Awt.VK_COMMA to ",", Awt.VK_PERIOD to ".",
        Awt.VK_SEMICOLON to ";", Awt.VK_QUOTE to "'", Awt.VK_MINUS to "-", Awt.VK_EQUALS to "=",
        Awt.VK_OPEN_BRACKET to "[", Awt.VK_CLOSE_BRACKET to "]", Awt.VK_BACK_QUOTE to "`",
        Awt.VK_ESCAPE to "Esc", Awt.VK_BACK_SPACE to "Backspace", Awt.VK_PAGE_UP to "PgUp", Awt.VK_PAGE_DOWN to "PgDn",
    )

    /** Numpad digits count as the top-row digits. */
    fun normalize(code: Int): Int =
        if (code in Awt.VK_NUMPAD0..Awt.VK_NUMPAD9) '0'.code + (code - Awt.VK_NUMPAD0) else code

    fun nameOf(code: Int): String = named[code] ?: if (code == 0 || code == Awt.VK_UNDEFINED) "Undefined" else "Code$code"
    fun codeOf(name: String): Int? = byName[name] ?: name.removePrefix("Code").toIntOrNull()?.takeIf { name.startsWith("Code") }
    fun keycapOf(code: Int): String = keycaps[code] ?: nameOf(code)
}
