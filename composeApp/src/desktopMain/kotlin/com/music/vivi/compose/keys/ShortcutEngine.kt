package com.music.vivi.compose.keys

import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.text.JTextComponent

/**
 * Turns the AWT key stream into shortcut actions. Pure apart from the injected collaborators, so
 * press/repeat/release/typed bookkeeping is unit-tested without a window.
 *
 *  - A consumed key-down also swallows its auto-repeats (unless the action is repeatable), its
 *    key-up and the matching KEY_TYPED, so a focused button never also "clicks" on Space.
 *  - While [captureSink] is set (the rebinding UI) every key is swallowed and handed to the sink.
 */
class ShortcutEngine(
    private val keymap: Keymap,
    private val textFocused: () -> Boolean,
    private val perform: (ShortcutAction) -> Boolean,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    @Volatile var captureSink: ((KeyInput) -> Unit)? = null

    private val consumed = HashSet<Int>()
    private var typedChar: Char? = null
    private var typedAt = 0L

    fun onPressed(input: KeyInput): Boolean {
        val code = KeyNames.normalize(input.keyCode)
        val sink = captureSink
        if (sink != null) {
            if (code !in KeyNames.modifierCodes) {
                sink(input)
                markConsumed(code, input)
            }
            return true
        }
        val action = keymap.resolve(input, textFocused()) ?: return false
        val repeat = code in consumed
        if (repeat && !action.repeatable) return true
        val handled = perform(action)
        if (handled) markConsumed(code, input)
        return handled
    }

    fun onReleased(input: KeyInput): Boolean = consumed.remove(KeyNames.normalize(input.keyCode))

    fun onTyped(input: KeyInput): Boolean {
        val c = typedChar ?: return false
        if (input.keyChar == c && nanoTime() - typedAt < 300_000_000L) {
            typedChar = null
            return true
        }
        return false
    }

    private fun markConsumed(code: Int, input: KeyInput) {
        consumed += code
        typedChar = input.keyChar.takeIf { it != KeyEvent.CHAR_UNDEFINED }
        typedAt = nanoTime()
    }
}

/** Installs a [ShortcutEngine] on the AWT focus manager so shortcuts work whichever node has focus. */
object ShortcutDispatcher {
    private var installed: KeyEventDispatcher? = null

    fun install(engine: ShortcutEngine) {
        uninstall()
        val d = KeyEventDispatcher { e ->
            val input = e.toInput()
            when (e.id) {
                KeyEvent.KEY_PRESSED -> engine.onPressed(input)
                KeyEvent.KEY_RELEASED -> engine.onReleased(input)
                KeyEvent.KEY_TYPED -> engine.onTyped(input)
                else -> false
            }
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(d)
        installed = d
    }

    fun uninstall() {
        installed?.let { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it) }
        installed = null
    }

    /**
     * True when a text input is focused. Compose Desktop only exposes AWT input-method requests on
     * its canvas while a text field has an active input session, so that is the signal for every
     * Compose text field without each one having to opt in; Swing text components are checked too.
     */
    fun isTextInputFocused(): Boolean {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        return owner is JTextComponent || owner.inputMethodRequests != null
    }

    private fun KeyEvent.toInput(): KeyInput {
        val graph = isAltGraphDown
        return KeyInput(
            keyCode = keyCode,
            keyChar = keyChar,
            ctrl = isControlDown && !graph,
            alt = isAltDown && !graph,
            shift = isShiftDown,
            meta = isMetaDown,
        )
    }
}
