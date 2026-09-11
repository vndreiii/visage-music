package com.music.innertube.auth

import com.music.innertube.YouTube
import com.music.innertube.models.AccountInfo
import kotlinx.serialization.Serializable

/**
 * Cookie-login session fields matching upstream YouTube Music login confirm
 * (cookie + visitorData + dataSyncId) without a WebView UI.
 */
@Serializable
data class YtMusicSession(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String = "",
) {
    fun applyToYouTube() {
        YouTube.cookie = cookie
        YouTube.visitorData = visitorData
        YouTube.dataSyncId = dataSyncId
    }

    /**
     * Applies this session to [YouTube] and validates via [accountInfo],
     * mirroring the upstream login confirm path.
     *
     * On failure, restores the prior [YouTube] cookie / visitorData / dataSyncId
     * so a bad session does not leave the singleton corrupted.
     *
     * @param accountInfo injectable for offline tests; defaults to [YouTube.accountInfo].
     */
    suspend fun validate(
        accountInfo: suspend () -> Result<AccountInfo> = { YouTube.accountInfo() },
    ): Result<AccountInfo> {
        val previousCookie = YouTube.cookie
        val previousVisitorData = YouTube.visitorData
        val previousDataSyncId = YouTube.dataSyncId
        applyToYouTube()
        val result = accountInfo()
        if (result.isFailure) {
            YouTube.cookie = previousCookie
            YouTube.visitorData = previousVisitorData
            YouTube.dataSyncId = previousDataSyncId
        }
        return result
    }
}
