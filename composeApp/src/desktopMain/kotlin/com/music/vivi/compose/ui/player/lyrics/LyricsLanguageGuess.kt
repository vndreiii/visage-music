package com.music.vivi.compose.ui.player.lyrics

/**
 * Offline guess of whether a song is already in the translation target language, so a provider (Google, an AI model)
 * is never asked to "translate" English lyrics into English. Conservative: only answers true when sure; anything
 * unclear is sent as before. Whole-song, not per line, so an English song with a foreign phrase is still skipped.
 */
internal object LyricsLanguageGuess {
    private val english = setOf(
        "the", "and", "you", "your", "i", "i'm", "i'll", "i've", "it's", "of", "to", "is", "that", "with", "for", "don't",
        "can't", "my", "we", "they", "this", "be", "on", "all", "just", "but", "are", "was", "like", "when", "what", "know",
        "got", "baby", "oh", "yeah", "up", "out", "so", "if", "not", "have", "do", "she", "he", "her", "his", "me",
    )

    fun alreadyIn(lines: List<String>, targetLang: String): Boolean {
        val text = lines.joinToString(" ")
        val letters = text.filter { it.isLetter() }
        if (letters.length < 20) return false
        return when (targetLang.lowercase().substringBefore('-')) {
            "en" -> {
                if (letters.count { it.code > 0x24F } > letters.length * 0.1) return false // not Latin script at all
                val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
                words.size >= 12 && words.count { it in english }.toDouble() / words.size >= 0.28
            }
            "ja" -> letters.count { it in '぀'..'ヿ' } > letters.length * 0.3
            "ko" -> letters.count { it in '가'..'힯' } > letters.length * 0.5
            "ru" -> letters.count { it in 'Ѐ'..'ӿ' } > letters.length * 0.6
            else -> false
        }
    }
}
