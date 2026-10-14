package com.music.vivi.player.login

/**
 * DATASYNC_ID is delegatedSessionId||userSessionId. Only the first part
 * belongs in onBehalfOfUser; the second part causes HTTP 400 for account_menu.
 */
fun normalizeDataSyncId(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    if (!raw.contains("||")) return raw
    return raw.substringBefore("||")
}
