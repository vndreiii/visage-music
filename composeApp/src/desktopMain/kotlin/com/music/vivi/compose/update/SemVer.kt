package com.music.vivi.compose.update

/** Semantic version (https://semver.org) with pre-release precedence; build metadata is ignored. */
class SemVer private constructor(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val preRelease: List<String>,
) : Comparable<SemVer> {

    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        // A release outranks any pre-release of the same core version.
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return when {
                preRelease.isEmpty() && other.preRelease.isEmpty() -> 0
                preRelease.isEmpty() -> 1
                else -> -1
            }
        }
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            if (a == b) continue
            val aNum = a.all { it.isDigit() }
            val bNum = b.all { it.isDigit() }
            return when {
                aNum && bNum -> compareValues(a.length, b.length).takeIf { it != 0 } ?: a.compareTo(b)
                aNum -> -1 // numeric identifiers sort before alphanumeric ones
                bNum -> 1
                else -> a.compareTo(b)
            }
        }
        return compareValues(preRelease.size, other.preRelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    companion object {
        private val REGEX = Regex(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)" +
                "(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(?:\\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
        )

        /** Accepts an optional leading "v" (release tags). Returns null for anything else. */
        fun parseOrNull(text: String): SemVer? {
            val match = REGEX.matchEntire(text.removePrefix("v")) ?: return null
            val groups = match.groupValues
            return runCatching {
                SemVer(
                    groups[1].toLong(),
                    groups[2].toLong(),
                    groups[3].toLong(),
                    groups[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList(),
                )
            }.getOrNull()
        }

        fun parse(text: String): SemVer = requireNotNull(parseOrNull(text)) { "Not a semantic version: $text" }
    }
}
