package com.music.vivi.compose.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MiniJsonTest {
    @Test
    fun parsesNestedValues() {
        val root = MiniJson.parse("""{"a": [1, -2, 3.5, true, null], "b": {"c": "x\"A\n"}}""").asObject()
        assertEquals(listOf<Any?>(1L, -2L, 3.5, true, null), root["a"])
        assertEquals("x\"A\n", root["b"].asObject()["c"])
    }

    @Test
    fun rejectsDuplicateKeysTrailingContentAndControlCharacters() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":1,"a":2}""") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":1} x""") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("\"a\u0001\"") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("01") }
    }

    @Test
    fun rejectsDeepNesting() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("[".repeat(100) + "]".repeat(100)) }
        assertNull(MiniJson.parse("null"))
    }
}
