package com.music.vivi.desktop.ui

import com.music.vivi.desktop.login.LoginPane
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

/**
 * Reusable desktop shell. Today it hosts YT Music login; later the same
 * [Stage] can show the full Compose / app UI.
 */
class DesktopShell(
    private val stage: Stage,
) {
    private val root = BorderPane()

    init {
        stage.title = "VIVI Desktop"
        stage.scene = Scene(root, 560.0, 280.0)
        stage.minWidth = 480.0
        stage.minHeight = 220.0
    }

    fun showLogin() {
        root.center = LoginPane(
            onLoggedIn = { accountName ->
                stage.title = "VIVI Desktop — $accountName"
            },
        )
        stage.show()
    }
}
