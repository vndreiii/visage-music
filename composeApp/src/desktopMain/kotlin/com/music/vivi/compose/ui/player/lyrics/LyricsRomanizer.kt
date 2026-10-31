package com.music.vivi.compose.ui.player.lyrics

/**
 * Offline transliteration to Latin letters: Japanese kana (Hepburn), Korean Hangul (Revised Romanization) and
 * Cyrillic. Kanji and Chinese characters need a dictionary, so [needsDictionary] flags those lines for an online
 * provider instead.
 */
internal object LyricsRomanizer {
    private fun Char.isKana() = this in '぀'..'ヿ' || this == 'ー'
    private fun Char.isHangul() = this in '가'..'힣'
    private fun Char.isCyrillic() = this in 'Ѐ'..'ӿ'
    private fun Char.isIdeograph() = this in '一'..'鿿' || this in '㐀'..'䶿'

    /** Whether [text] has anything this object (or a provider) could romanize. */
    fun isNonLatin(text: String): Boolean = text.any { it.isKana() || it.isHangul() || it.isCyrillic() || it.isIdeograph() }

    /** Kanji / hanzi in the line: the readings cannot be derived without a dictionary. */
    fun needsDictionary(text: String): Boolean = text.any { it.isIdeograph() }

    /** The romanization, or null when [text] has no kana/Hangul/Cyrillic to convert. */
    fun romanize(text: String): String? {
        if (!text.any { it.isKana() || it.isHangul() || it.isCyrillic() }) return null
        val out = StringBuilder()
        val s = text
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isKana() -> i = kana(s, i, out)
                c.isHangul() -> i = hangul(s, i, out)
                c.isCyrillic() -> { out.append(CYRILLIC[c] ?: c.toString()); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString().replace(Regex("\\s+"), " ").trim().ifBlank { null }
    }

    // ---- Japanese ----

    private val BASE = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "ゐ" to "i", "ゑ" to "e", "を" to "o", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ゔ" to "vu",
    )
    private val SMALL = mapOf(
        "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo",
        "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o",
    )
    private val COMBO = mapOf(
        "き" to "ky", "ぎ" to "gy", "し" to "sh", "じ" to "j", "ち" to "ch", "ぢ" to "j", "に" to "ny", "ひ" to "hy",
        "び" to "by", "ぴ" to "py", "み" to "my", "り" to "ry", "ふ" to "f", "て" to "t", "で" to "d", "う" to "w",
        "ゔ" to "v",
    )

    private fun toHira(c: Char): Char = if (c in 'ァ'..'ヶ') (c.code - 0x60).toChar() else c

    private fun kana(s: String, start: Int, out: StringBuilder): Int {
        var i = start
        val c = toHira(s[i])
        if (s[i] == 'ー') { // long vowel mark: repeat the previous vowel
            out.lastOrNull { it in "aeiou" }?.let { out.append(it) }
            return i + 1
        }
        if (c == 'っ') { // sokuon doubles the next consonant
            val next = s.getOrNull(i + 1)?.let { toHira(it).toString() }
            val r = next?.let { BASE[it] }
            if (r != null && r[0] !in "aeiou") out.append(if (r.startsWith("ch")) "t" else r[0].toString())
            return i + 1
        }
        val first = c.toString()
        val small = s.getOrNull(i + 1)?.let { SMALL[toHira(it).toString()] }
        val base = BASE[first]
        if (base != null && small != null && first in COMBO && first != "う") {
            val pre = COMBO.getValue(first)
            // きゃ -> kya, しゃ -> sha, じゅ -> ju: the small ya/yu/yo loses its own y after a palatal consonant.
            val tail = if (small.startsWith("y") && (pre.endsWith("y") || pre == "sh" || pre == "ch" || pre == "j")) small.drop(1) else small
            out.append(pre).append(tail)
            return i + 2
        }
        if (first == "て" || first == "で" || first == "ふ" || first == "う") {
            if (small != null && small !in setOf("ya", "yu", "yo") || (first == "ふ" && small != null)) {
                out.append(COMBO.getValue(first)).append(small!!)
                return i + 2
            }
        }
        when {
            base != null -> out.append(base)
            SMALL[first] != null -> out.append(SMALL[first])
            first == "ゝ" || first == "ゞ" -> {}
            else -> out.append(s[i])
        }
        i++
        return i
    }

    // ---- Korean (Revised Romanization, with the common liaison) ----

    private val INITIAL = listOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
    private val VOWEL = listOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
    private val FINAL = listOf("", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lk", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "t")

    /** Final consonant index -> the initial it becomes when the next syllable starts with ㅇ. */
    private val LIAISON = mapOf(1 to "g", 2 to "kk", 4 to "n", 7 to "d", 8 to "r", 16 to "m", 17 to "b", 19 to "s", 20 to "ss", 22 to "j", 23 to "ch", 24 to "k", 25 to "t", 26 to "p")

    private fun hangul(s: String, start: Int, out: StringBuilder): Int {
        val code = s[start].code - 0xAC00
        val initial = code / 588
        val vowel = (code % 588) / 28
        val fin = code % 28
        val next = s.getOrNull(start + 1)
        val nextVowelInitial = next != null && next.isHangul() && (next.code - 0xAC00) / 588 == 11
        out.append(INITIAL[initial]).append(VOWEL[vowel])
        if (fin != 0) {
            if (nextVowelInitial && fin in LIAISON) out.append(LIAISON.getValue(fin)) else out.append(FINAL[fin])
        }
        return start + 1
    }

    // ---- Cyrillic ----

    private val CYRILLIC: Map<Char, String> = buildMap {
        val pairs = "а=a б=b в=v г=g д=d е=e ё=yo ж=zh з=z и=i й=y к=k л=l м=m н=n о=o п=p р=r с=s т=t у=u ф=f х=kh ц=ts ч=ch ш=sh щ=shch ъ= ы=y ь= э=e ю=yu я=ya і=i ї=yi є=ye ґ=g"
        pairs.split(' ').forEach { p ->
            val (k, v) = p.split('=')
            put(k[0], v)
            put(k[0].uppercaseChar(), v.replaceFirstChar { it.uppercase() })
        }
    }
}
