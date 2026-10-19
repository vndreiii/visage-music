package com.music.vivi.compose.keys

/** Minimal string storage so the keymap is testable without the real prefs file. */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

enum class ConflictPolicy {
    /** Assign only if free. */
    Reject,
    /** The other action loses the binding. */
    Replace,
    /** The other action receives the binding that this slot used to hold (if any). */
    Swap,
}

/**
 * Resolves key presses to [ShortcutAction]s and stores user overrides.
 *
 * Storage (all in [store]): `kb.enabled` master switch; `kb.<ACTION>` = "" for defaults, "none" for
 * explicitly unbound, or `|`-joined [KeyBinding.serialize] forms; `kb.off.<ACTION>` = "true" to disable.
 * Nothing is cached, so the UI just re-reads after a prefs change.
 */
class Keymap(private val store: KeyValueStore) {

    var masterEnabled: Boolean
        get() = store.get(ENABLED)?.toBooleanStrictOrNull() ?: true
        set(v) = store.set(ENABLED, v.toString())

    fun isEnabled(action: ShortcutAction): Boolean = store.get(OFF + action.id) != "true"
    fun setEnabled(action: ShortcutAction, enabled: Boolean) = store.set(OFF + action.id, if (enabled) "" else "true")

    fun isCustomized(action: ShortcutAction): Boolean = !store.get(KB + action.id).isNullOrEmpty()

    fun bindings(action: ShortcutAction): List<KeyBinding> {
        val raw = store.get(KB + action.id)
        if (raw.isNullOrEmpty()) return action.defaults
        if (raw == NONE) return emptyList()
        return raw.split('|').mapNotNull { KeyBinding.parse(it) }.distinct()
    }

    fun setBindings(action: ShortcutAction, list: List<KeyBinding>) {
        val distinct = list.distinct()
        store.set(KB + action.id, if (distinct == action.defaults) "" else if (distinct.isEmpty()) NONE else distinct.joinToString("|") { it.serialize() })
    }

    fun resetAction(action: ShortcutAction) {
        store.set(KB + action.id, "")
        store.set(OFF + action.id, "")
    }

    fun resetAll() {
        ShortcutAction.entries.forEach { resetAction(it) }
        masterEnabled = true
    }

    /** Enabled actions (other than [excluding]) that already use [binding]. */
    fun conflicts(binding: KeyBinding, excluding: ShortcutAction? = null): List<ShortcutAction> =
        ShortcutAction.entries.filter { it != excluding && isEnabled(it) && binding in bindings(it) }

    /**
     * Puts [binding] on [action] in [slot] (null = append, an existing index = replace that binding).
     * Returns false and changes nothing if it conflicts and [policy] is [ConflictPolicy.Reject].
     */
    fun assign(action: ShortcutAction, slot: Int?, binding: KeyBinding, policy: ConflictPolicy = ConflictPolicy.Reject): Boolean {
        val clashing = conflicts(binding, action)
        if (clashing.isNotEmpty() && policy == ConflictPolicy.Reject) return false
        val mine = bindings(action).toMutableList()
        val displaced = slot?.let { mine.getOrNull(it) }
        if (slot != null && slot in mine.indices) mine[slot] = binding else if (binding !in mine) mine += binding
        clashing.forEach { other ->
            val theirs = bindings(other).toMutableList()
            val at = theirs.indexOf(binding)
            if (at < 0) return@forEach
            if (policy == ConflictPolicy.Swap && displaced != null && displaced != binding && displaced !in theirs) theirs[at] = displaced
            else theirs.removeAt(at)
            setBindings(other, theirs)
        }
        setBindings(action, mine)
        return true
    }

    fun removeBinding(action: ShortcutAction, slot: Int) {
        val mine = bindings(action).toMutableList()
        if (slot in mine.indices) { mine.removeAt(slot); setBindings(action, mine) }
    }

    /**
     * The action [input] triggers, or null. While [textFocused], only chords that cannot be typing or
     * text editing are eligible (Ctrl/Alt/Meta combos that are not caret/clipboard keys).
     */
    fun resolve(input: KeyInput, textFocused: Boolean = false): ShortcutAction? {
        if (!masterEnabled) return null
        for (action in ShortcutAction.entries) {
            if (!isEnabled(action)) continue
            for (b in bindings(action)) {
                if (textFocused && !safeWhileTyping(b)) continue
                if (b.matches(input)) return action
            }
        }
        return null
    }

    companion object {
        private const val KB = "kb."
        private const val OFF = "kb.off."
        private const val ENABLED = "kb.enabled"
        private const val NONE = "none"

        private val editingKeys = setOf(
            java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_UP,
            java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.VK_HOME, java.awt.event.KeyEvent.VK_END,
            java.awt.event.KeyEvent.VK_BACK_SPACE, java.awt.event.KeyEvent.VK_DELETE, java.awt.event.KeyEvent.VK_A,
            java.awt.event.KeyEvent.VK_C, java.awt.event.KeyEvent.VK_V, java.awt.event.KeyEvent.VK_X,
            java.awt.event.KeyEvent.VK_Z, java.awt.event.KeyEvent.VK_Y, java.awt.event.KeyEvent.VK_ENTER,
            java.awt.event.KeyEvent.VK_SPACE,
        )

        /** Whether [b] may fire while a text field is focused. */
        fun safeWhileTyping(b: KeyBinding): Boolean {
            if (!b.hasCommandModifier) return false
            if (b.char != null) return false
            // Alt+Left/Right (history) is not a caret key in text fields; Ctrl/Meta + editing keys are.
            if (b.alt && !b.ctrl && !b.meta) return true
            return b.keyCode !in editingKeys
        }
    }
}
