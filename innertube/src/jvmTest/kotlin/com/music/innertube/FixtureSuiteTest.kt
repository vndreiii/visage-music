package com.music.innertube

import com.music.innertube.pages.SearchResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Live-network fixture suite for Innertube search.
 *
 * Entry point: [YouTube.search] with [YouTube.SearchFilter.FILTER_SONG]
 * (stable song-shelf results; empty/junk queries fail or return empty items).
 */
@Tag("network")
class FixtureSuiteTest {

    @Test
    fun fixtureSuite() = runTest {
        val fixtures = FixtureLoader.load("fixtures/innertube-cases.json")
        assertTrue(fixtures.cases.isNotEmpty(), "fixture file has no cases")

        for (case in fixtures.cases) {
            val result = YouTube.search(case.query, YouTube.SearchFilter.FILTER_SONG)
            when (case.expect) {
                "success" -> assertTrue(
                    result.isSuccess && hasUsefulPayload(result.getOrThrow()),
                    "${case.id}: expected useful search hits for query=${case.query.debug()}, " +
                        "success=${result.isSuccess}, items=${result.getOrNull()?.items?.size}, " +
                        "err=${result.exceptionOrNull()?.message}",
                )
                "failure" -> assertTrue(
                    result.isFailure || isEmptyUseful(result),
                    "${case.id}: ${case.reason ?: "expected failure/empty"} " +
                        "but got ${result.getOrNull()?.items?.size ?: 0} items " +
                        "(query=${case.query.debug()})",
                )
                else -> error("${case.id}: unknown expect=${case.expect}")
            }
        }
    }

    private fun hasUsefulPayload(result: SearchResult): Boolean =
        result.items.isNotEmpty()

    private fun isEmptyUseful(result: Result<SearchResult>): Boolean =
        result.getOrNull()?.items.isNullOrEmpty()

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
