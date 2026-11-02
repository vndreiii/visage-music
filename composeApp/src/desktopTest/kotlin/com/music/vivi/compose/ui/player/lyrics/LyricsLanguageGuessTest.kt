package com.music.vivi.compose.ui.player.lyrics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsLanguageGuessTest {
    private val englishSong = listOf(
        "I don't know what I'm doing here tonight", "You were the one that I wanted all along",
        "But I can't let it go and you know it", "Oh baby just hold me for a while",
        "We were young and the night was ours", "I got my heart on the line for you",
    )
    private val spanishSong = listOf(
        "No sé lo que hago aquí esta noche", "Tú eras la que quería todo el tiempo",
        "Pero no puedo dejarlo ir y lo sabes", "Ven abrázame un rato más",
        "Éramos jóvenes y la noche era nuestra", "Tengo mi corazón en juego por ti",
    )
    private val japaneseSong = listOf("今夜は眠れない", "あなたのことばかり考えてる", "どうして素直になれないの", "ずっと君を探してた")

    @Test fun englishIsSkippedForEnglishTarget() = assertTrue(LyricsLanguageGuess.alreadyIn(englishSong, "en"))
    @Test fun spanishIsStillTranslatedToEnglish() = assertFalse(LyricsLanguageGuess.alreadyIn(spanishSong, "en"))
    @Test fun japaneseIsStillTranslatedToEnglish() = assertFalse(LyricsLanguageGuess.alreadyIn(japaneseSong, "en"))
    @Test fun japaneseIsSkippedForJapaneseTarget() = assertTrue(LyricsLanguageGuess.alreadyIn(japaneseSong, "ja"))
    @Test fun englishIsTranslatedForOtherTargets() = assertFalse(LyricsLanguageGuess.alreadyIn(englishSong, "de"))
    @Test fun tooLittleTextIsNeverSkipped() = assertFalse(LyricsLanguageGuess.alreadyIn(listOf("la la la"), "en"))
}
