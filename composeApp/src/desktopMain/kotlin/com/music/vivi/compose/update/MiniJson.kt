package com.music.vivi.compose.update

/**
 * A small, strict JSON reader for update metadata. composeApp does not have kotlinx-serialization
 * on its compile classpath, and the updater only needs objects, arrays, strings, numbers and
 * literals. Duplicate keys, trailing content and deep nesting are rejected.
 *
 * Result types: Map<String, Any?>, List<Any?>, String, Long (integral), Double, Boolean, null.
 */
internal object MiniJson {
    private const val MAX_DEPTH = 32
    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue(0)
        reader.skipWhitespace()
        require(reader.atEnd()) { "Unexpected content after JSON value" }
        return value
    }

    private class Reader(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\t' || s[pos] == '\n' || s[pos] == '\r')) pos++
        }

        private fun peek(): Char {
            require(pos < s.length) { "Unexpected end of JSON" }
            return s[pos]
        }

        private fun expect(c: Char) {
            require(peek() == c) { "Expected '$c' at offset $pos" }
            pos++
        }

        fun readValue(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "JSON nested too deeply" }
            skipWhitespace()
            return when (val c = peek()) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> {
                    require(c == '-' || c in '0'..'9') { "Unexpected character at offset $pos" }
                    readNumber()
                }
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, pos)) { "Invalid literal at offset $pos" }
            pos += word.length
            return value
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                require(!out.containsKey(key)) { "Duplicate JSON key '$key'" }
                out[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return out
                    }
                    else -> fail("Expected ',' or '}' at offset $pos")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return out
            }
            while (true) {
                out += readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return out
                    }
                    else -> fail("Expected ',' or ']' at offset $pos")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = peek()
                pos++
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        val e = peek()
                        pos++
                        when (e) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(pos + 4 <= s.length) { "Truncated unicode escape" }
                                val hex = s.substring(pos, pos + 4)
                                require(hex.all { it.isHexDigit() }) { "Invalid unicode escape" }
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> fail("Invalid escape at offset $pos")
                        }
                    }
                    c < ' ' -> fail("Control character in string at offset $pos")
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] in ".eE+-")) pos++
            val raw = s.substring(start, pos)
            require(NUMBER.matches(raw)) { "Invalid number '$raw'" }
            return if (raw.any { it == '.' || it == 'e' || it == 'E' }) raw.toDouble() else raw.toLong()
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

        private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
