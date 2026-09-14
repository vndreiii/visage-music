package com.music.vivi.core.session

import com.music.innertube.auth.YtMusicSession
import com.music.vivi.player.session.SessionSecretStore

interface SessionManager {
    fun isLoggedIn(): Boolean
    fun loadSession(): YtMusicSession?
    fun loadAccount(): SessionSecretStore.StoredAccount?
    fun applyStoredSession(): Boolean
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
}
