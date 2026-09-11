package com.music.vivi.desktop

import com.music.vivi.desktop.ui.DesktopShell
import javafx.application.Application
import javafx.stage.Stage

fun main(args: Array<String>) {
    Application.launch(ViviDesktopApp::class.java, *args)
}

class ViviDesktopApp : Application() {
    override fun start(primaryStage: Stage) {
        DesktopShell(primaryStage).showLogin()
    }
}
