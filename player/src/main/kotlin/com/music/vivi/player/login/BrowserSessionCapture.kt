package com.music.vivi.player.login

import com.music.innertube.auth.YtMusicSession

/** Reads the app's own sign-in browser; session values never go to logs or files. */
suspend fun CdpClient.captureSession(): YtMusicSession {
    val visitor = evaluateString("(window.ytcfg && window.ytcfg.get('VISITOR_DATA')) || (window.yt && window.yt.config_ && window.yt.config_.VISITOR_DATA) || ''")
    val delegated = evaluateString("(window.ytcfg && window.ytcfg.get('DELEGATED_SESSION_ID')) || ''")
    val raw = if (delegated.isBlank()) evaluateString("(window.ytcfg && window.ytcfg.get('DATASYNC_ID')) || (window.yt && window.yt.config_ && window.yt.config_.DATASYNC_ID) || ''") else delegated
    return YtMusicSession(cookieHeader(), visitor, normalizeDataSyncId(raw))
}
