package com.music.vivi.core.session

import com.music.innertube.auth.YtMusicSession
import com.music.vivi.player.session.SessionSecretStore

interface SessionManager {
    fun isLoggedIn(): Boolean
    fun loadSession(): YtMusicSession?
    fun loadAccount(): SessionSecretStore.StoredAccount?
    fun applyStoredSession(): Boolean

    /** Forgets the account: deletes the stored session and clears it from the live YouTube client. */
    fun signOut()
}

class KeyringSessionManager : SessionManager {
    override fun isLoggedIn(): Boolean =
        SessionSecretStore.loadSession() != null

    override fun loadSession(): YtMusicSession? =
        SessionSecretStore.loadSession()

    override fun loadAccount(): SessionSecretStore.StoredAccount? =
        SessionSecretStore.loadAccount()

    override fun applyStoredSession(): Boolean {
        val session = SessionSecretStore.loadSession() ?: return false
        session.applyToYouTube()
        return true
    }

    override fun signOut() {
        SessionSecretStore.clear()
        com.music.innertube.YouTube.cookie = null
        com.music.innertube.YouTube.visitorData = null
        com.music.innertube.YouTube.dataSyncId = null
    }
}
