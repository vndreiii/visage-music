package com.music.vivi.player.login

/**
 * Upstream DataSyncId helper — keep delegated id after "||" when present.
 */
fun normalizeDataSyncId(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    if (!raw.contains("||")) return raw
    val delegated = raw.substringAfter("||")
    return delegated.ifEmpty { raw.substringBefore("||") }
}
