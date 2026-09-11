package com.music.lrclib

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Live-network fixture suite for lyrics lookup via LrcLib.
 *
 * Entry point: [LrcLib.getLyrics] with `duration = -1` (title/artist match,
 * no duration gate). Success = non-empty lyrics body; failure = empty/miss
 * or Result.failure without crash.
 */
@Tag("network")
class LyricsFixtureSuiteTest {

    @Test
    fun fixtureSuite() = runTest {
        val fixtures = FixtureLoader.load("fixtures/lyrics-cases.json")
        assertTrue(fixtures.cases.isNotEmpty(), "fixture file has no cases")

        for (case in fixtures.cases) {
            val result = LrcLib.getLyrics(
                title = case.title,
                artist = case.artist,
                duration = -1,
            )
            when (case.expect) {
                "success" -> assertTrue(
                    result.isSuccess && result.getOrThrow().isNotBlank(),
                    "${case.id}: expected non-empty lyrics for " +
                        "artist=${case.artist.debug()} title=${case.title.debug()}, " +
                        "success=${result.isSuccess}, " +
                        "len=${result.getOrNull()?.length}, " +
                        "err=${result.exceptionOrNull()?.message}",
                )
                "failure" -> assertTrue(
                    result.isFailure || result.getOrNull().isNullOrBlank(),
                    "${case.id}: ${case.reason ?: "expected miss/empty"} " +
                        "but got ${result.getOrNull()?.length ?: 0} chars " +
                        "(artist=${case.artist.debug()} title=${case.title.debug()})",
                )
                else -> error("${case.id}: unknown expect=${case.expect}")
            }
        }
    }

    private fun String.debug(): String =
        map { c ->
            when {
                c == '\n' -> "\\n"
                c == '\t' -> "\\t"
                c.code < 32 || c.code == 0xFEFF -> "\\u%04x".format(c.code)
                else -> c.toString()
            }
        }.joinToString("").take(80)
}
