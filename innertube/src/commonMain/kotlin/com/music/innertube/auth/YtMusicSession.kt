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
     * Applies this session to [YouTube] and validates via [YouTube.accountInfo],
     * mirroring the upstream login confirm path.
     */
    suspend fun validate(): Result<AccountInfo> {
        applyToYouTube()
        return YouTube.accountInfo()
    }
}
