package com.music.vivi.compose.keys

import java.awt.event.KeyEvent as K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemStore(val m: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
    override fun get(key: String) = m[key]?.ifEmpty { null }
    override fun set(key: String, value: String) { m[key] = value }
}

private fun press(code: Int, c: Char = K.CHAR_UNDEFINED, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
    KeyInput(code, c, ctrl, alt, shift)

class KeyBindingTest {
    @Test fun serializeRoundTrips() {
        val all = ShortcutAction.entries.flatMap { it.defaults } + KeyBinding.char('|') + KeyBinding.key(K.VK_PLUS + 0, ctrl = true, shift = true, alt = true)
        for (b in all) assertEquals(b, KeyBinding.parse(b.serialize()), b.serialize())
    }

    @Test fun parseRejectsGarbage() {
        assertNull(KeyBinding.parse(""))
        assertNull(KeyBinding.parse("Ctrl+"))
        assertNull(KeyBinding.parse("Frobnicate"))
        assertNull(KeyBinding.parse("Char(x)"))
    }

    @Test fun modifiersMustMatchExactly() {
        val b = KeyBinding.key(K.VK_RIGHT, ctrl = true)
        assertTrue(b.matches(press(K.VK_RIGHT, ctrl = true)))
        assertFalse(b.matches(press(K.VK_RIGHT)))
        assertFalse(b.matches(press(K.VK_RIGHT, ctrl = true, shift = true)))
        assertFalse(b.matches(press(K.VK_RIGHT, ctrl = true, alt = true)))
    }

    @Test fun numpadDigitsCountAsDigits() {
        assertTrue(KeyBinding.key(K.VK_5).matches(press(K.VK_NUMPAD5, '5')))
    }

    @Test fun questionMarkMatchesOnAnyLayout() {
        val b = ShortcutAction.SHOW_SHORTCUTS.defaults.first()
        assertEquals('?', b.char)
        // US: Shift+/ ; other layouts put '?' on a different key code.
        assertTrue(b.matches(press(K.VK_SLASH, '?', shift = true)))
        assertTrue(b.matches(press(K.VK_UNDEFINED + 77, '?', shift = true)))
        assertFalse(b.matches(press(K.VK_SLASH, '/')))
    }

    @Test fun capturingShiftedPunctuationGivesCharBinding() {
        assertEquals(KeyBinding.char('?'), KeyBinding.fromInput(press(K.VK_SLASH, '?', shift = true)))
        // Ctrl chords and letters stay key-code bindings.
        assertEquals(KeyBinding.key(K.VK_K, ctrl = true), KeyBinding.fromInput(press(K.VK_K, '\u000b', ctrl = true)))
        assertEquals(KeyBinding.key(K.VK_R, shift = true), KeyBinding.fromInput(press(K.VK_R, 'R', shift = true)))
        assertNull(KeyBinding.fromInput(press(K.VK_SHIFT, shift = true)))
    }

    @Test fun nonLatinLayoutFallsBackToCharacter() {
        val b = KeyBinding.key(K.VK_S)
        assertTrue(b.matches(press(K.VK_UNDEFINED, 's')))
        assertFalse(KeyBinding.key(K.VK_S, ctrl = true).matches(press(K.VK_UNDEFINED, 's', ctrl = true)))
    }

    @Test fun keycaps() {
        assertEquals(listOf("Ctrl", "Shift", "←"), KeyBinding.key(K.VK_LEFT, ctrl = true, shift = true).keycaps())
        assertEquals(listOf("?"), KeyBinding.char('?').keycaps())
    }
}

class KeymapTest {
    private fun km() = Keymap(MemStore())

    @Test fun defaultsHaveNoConflicts() {
        val seen = HashMap<KeyBinding, ShortcutAction>()
        for (a in ShortcutAction.entries) for (b in a.defaults) {
            val prev = seen.put(b, a)
            assertNull(prev, "$b is bound to both $prev and $a")
        }
    }

    @Test fun resolvesDefaults() {
        val km = km()
        assertEquals(ShortcutAction.PLAY_PAUSE, km.resolve(press(K.VK_SPACE, ' ')))
        assertEquals(ShortcutAction.NEXT, km.resolve(press(K.VK_RIGHT, ctrl = true)))
        assertEquals(ShortcutAction.SEEK_FORWARD_LONG, km.resolve(press(K.VK_RIGHT, shift = true)))
        assertEquals(ShortcutAction.SEEK_50, km.resolve(press(K.VK_5, '5')))
        assertEquals(ShortcutAction.FOCUS_SEARCH, km.resolve(press(K.VK_SLASH, '/')))
        assertNull(km.resolve(press(K.VK_J, 'j')))
    }

    @Test fun masterSwitchAndPerActionDisable() {
        val km = km()
        km.setEnabled(ShortcutAction.PLAY_PAUSE, false)
        assertNull(km.resolve(press(K.VK_SPACE, ' ')))
        km.setEnabled(ShortcutAction.PLAY_PAUSE, true)
        assertNotNull(km.resolve(press(K.VK_SPACE, ' ')))
        km.masterEnabled = false
        assertNull(km.resolve(press(K.VK_SPACE, ' ')))
    }

    @Test fun typingSuspendsPlainButNotCommandChords() {
        val km = km()
        assertNull(km.resolve(press(K.VK_SPACE, ' '), textFocused = true))
        assertNull(km.resolve(press(K.VK_S, 's'), textFocused = true))
        assertNull(km.resolve(press(K.VK_R, 'R', shift = true), textFocused = true))
        assertNull(km.resolve(press(K.VK_ESCAPE), textFocused = true))
        assertEquals(ShortcutAction.FOCUS_SEARCH, km.resolve(press(K.VK_K, ctrl = true), textFocused = true))
        assertEquals(ShortcutAction.GO_HOME, km.resolve(press(K.VK_1, ctrl = true), textFocused = true))
        // Ctrl+Left/Right move the caret by word in a text field.
        assertNull(km.resolve(press(K.VK_RIGHT, ctrl = true), textFocused = true))
        assertEquals(ShortcutAction.BACK, km.resolve(press(K.VK_LEFT, alt = true), textFocused = true))
    }

    @Test fun rebindPersistsAndFallsBackToDefaults() {
        val store = MemStore()
        val km = Keymap(store)
        assertTrue(km.assign(ShortcutAction.MUTE, 0, KeyBinding.key(K.VK_X)))
        assertEquals(listOf(KeyBinding.key(K.VK_X)), Keymap(store).bindings(ShortcutAction.MUTE))
        assertTrue(Keymap(store).isCustomized(ShortcutAction.MUTE))
        assertNull(km.resolve(press(K.VK_M, 'm')))
        km.resetAction(ShortcutAction.MUTE)
        assertEquals(ShortcutAction.MUTE.defaults, km.bindings(ShortcutAction.MUTE))
        assertFalse(km.isCustomized(ShortcutAction.MUTE))
    }

    @Test fun clearedActionStaysUnboundAcrossReload() {
        val store = MemStore()
        val km = Keymap(store)
        km.removeBinding(ShortcutAction.MUTE, 0)
        assertEquals(emptyList(), Keymap(store).bindings(ShortcutAction.MUTE))
    }

    @Test fun secondBindingAppends() {
        val km = km()
        km.assign(ShortcutAction.MUTE, null, KeyBinding.key(K.VK_X))
        assertEquals(listOf(KeyBinding.key(K.VK_M), KeyBinding.key(K.VK_X)), km.bindings(ShortcutAction.MUTE))
    }

    @Test fun conflictDetectionAndPolicies() {
        val km = km()
        val space = KeyBinding.key(K.VK_SPACE)
        assertEquals(listOf(ShortcutAction.PLAY_PAUSE), km.conflicts(space, ShortcutAction.MUTE))
        assertFalse(km.assign(ShortcutAction.MUTE, 0, space, ConflictPolicy.Reject))
        assertEquals(ShortcutAction.MUTE.defaults, km.bindings(ShortcutAction.MUTE))

        // Replace: Play/pause loses Space.
        assertTrue(km.assign(ShortcutAction.MUTE, 0, space, ConflictPolicy.Replace))
        assertEquals(listOf(space), km.bindings(ShortcutAction.MUTE))
        assertEquals(emptyList(), km.bindings(ShortcutAction.PLAY_PAUSE))
        assertEquals(ShortcutAction.MUTE, km.resolve(press(K.VK_SPACE, ' ')))
    }

    @Test fun swapGivesTheOtherActionTheDisplacedKey() {
        val km = km()
        val space = KeyBinding.key(K.VK_SPACE)
        val m = KeyBinding.key(K.VK_M)
        assertTrue(km.assign(ShortcutAction.MUTE, 0, space, ConflictPolicy.Swap))
        assertEquals(listOf(space), km.bindings(ShortcutAction.MUTE))
        assertEquals(listOf(m), km.bindings(ShortcutAction.PLAY_PAUSE))
    }

    @Test fun disabledActionsDoNotConflict() {
        val km = km()
        km.setEnabled(ShortcutAction.PLAY_PAUSE, false)
        assertTrue(km.conflicts(KeyBinding.key(K.VK_SPACE), ShortcutAction.MUTE).isEmpty())
    }

    @Test fun resetAllRestoresEverything() {
        val km = km()
        km.assign(ShortcutAction.MUTE, 0, KeyBinding.key(K.VK_X))
        km.setEnabled(ShortcutAction.NEXT, false)
        km.masterEnabled = false
        km.resetAll()
        assertTrue(km.masterEnabled)
        assertTrue(km.isEnabled(ShortcutAction.NEXT))
        assertEquals(ShortcutAction.MUTE.defaults, km.bindings(ShortcutAction.MUTE))
    }

    @Test fun corruptStoredValueIsIgnored() {
        val store = MemStore(mutableMapOf("kb.MUTE" to "Nonsense|Ctrl+"))
        assertEquals(emptyList(), Keymap(store).bindings(ShortcutAction.MUTE))
    }
}

class ShortcutEngineTest {
    private var now = 0L
    private var text = false
    private val performed = mutableListOf<ShortcutAction>()
    private var handled = true
    private val engine = Keymap(MemStore()).let { km ->
        ShortcutEngine(km, { text }, { performed += it; handled }, { now })
    }
    private val space = press(K.VK_SPACE, ' ')

    @Test fun spaceIsConsumedAcrossPressTypedAndRelease() {
        assertTrue(engine.onPressed(space))
        assertTrue(engine.onTyped(space))
        assertTrue(engine.onReleased(space))
        assertEquals(listOf(ShortcutAction.PLAY_PAUSE), performed)
        assertFalse(engine.onReleased(space))
    }

    @Test fun holdingSpaceDoesNotRetoggle() {
        engine.onPressed(space)
        assertTrue(engine.onPressed(space))
        assertTrue(engine.onPressed(space))
        assertEquals(1, performed.size)
    }

    @Test fun repeatableActionsFireWhileHeld() {
        val right = press(K.VK_RIGHT)
        repeat(3) { engine.onPressed(right) }
        assertEquals(List(3) { ShortcutAction.SEEK_FORWARD }, performed)
    }

    @Test fun textFocusLetsSpaceThrough() {
        text = true
        assertFalse(engine.onPressed(space))
        assertFalse(engine.onReleased(space))
        assertTrue(performed.isEmpty())
    }

    @Test fun unhandledActionIsNotConsumed() {
        handled = false
        assertFalse(engine.onPressed(press(K.VK_ESCAPE)))
        assertFalse(engine.onReleased(press(K.VK_ESCAPE)))
    }

    @Test fun staleTypedCharIsNotSwallowed() {
        engine.onPressed(space)
        now += 1_000_000_000L
        assertFalse(engine.onTyped(space))
    }

    @Test fun captureSwallowsEverythingAndReportsNonModifierKeys() {
        val got = mutableListOf<KeyInput>()
        engine.captureSink = { got += it }
        assertTrue(engine.onPressed(press(K.VK_SHIFT, shift = true)))
        assertTrue(engine.onPressed(press(K.VK_SPACE, ' ')))
        assertEquals(1, got.size)
        assertTrue(performed.isEmpty())
        assertTrue(engine.onReleased(space))
        engine.captureSink = null
        assertTrue(engine.onPressed(space))
        assertEquals(listOf(ShortcutAction.PLAY_PAUSE), performed)
    }
}
