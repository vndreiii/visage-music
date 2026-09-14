package com.music.vivi.desktop.ui

import com.music.vivi.desktop.login.LoginPane
import com.music.vivi.player.session.SessionSecretStore
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

/**
 * Reusable desktop shell. Hosts YT Music login, then a playback smoke pane.
 */
class DesktopShell(
    private val stage: Stage,
) {
    private val root = BorderPane()
    private var playbackPane: PlaybackPane? = null

    init {
        stage.title = "VIVI Desktop"
        stage.scene = Scene(root, 560.0, 280.0)
        stage.minWidth = 480.0
        stage.minHeight = 220.0
        stage.setOnCloseRequest {
            playbackPane?.dispose()
        }
    }

    fun showLogin() {
        val existing = SessionSecretStore.loadAccount()
        if (existing != null && SessionSecretStore.loadSession() != null) {
            showPlayback(existing.name)
            return
        }
        root.center = LoginPane(
            onLoggedIn = { accountName ->
                showPlayback(accountName)
            },
        )
        stage.show()
    }

    private fun showPlayback(accountName: String) {
        stage.title = "VIVI Desktop — $accountName"
        stage.width = 640.0
        stage.height = 320.0
        playbackPane?.dispose()
        playbackPane = PlaybackPane(accountName).also { root.center = it }
        stage.show()
    }
}
