package com.music.vivi.player.login

/**
 * DATASYNC_ID is delegatedSessionId||userSessionId. Only the first part
 * belongs in onBehalfOfUser; the second part causes HTTP 400 for account_menu.
 *
 * "id||" is a regular (non-delegated) login: the id is the user's own Google
 * identity, not a delegated YouTube one. Sending it as onBehalfOfUser pins
 * requests to that id, which breaks accounts whose YouTube identity differs
 * from it (e.g. ex-Google+ accounts), so it is dropped.
 */
fun normalizeDataSyncId(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    if (!raw.contains("||")) return raw
    val (delegated, user) = raw.split("||", limit = 2)
    return if (user.isEmpty()) "" else delegated
}
