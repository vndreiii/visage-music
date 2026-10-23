package com.music.vivi.compose.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowsInstallScriptTest {
    private val script = windowsInstallScript(
        4242,
        File("C:/Users/O'Neil/AppData/Local/visage/updates/VIsage-1.2.0.msi"),
        File("C:/Users/O'Neil/AppData/Local/VIsage/VIsage.exe"),
        File("C:/Users/O'Neil/AppData/Local/visage/updates"),
    )

    @Test
    fun waitsForExitThenInstallsWithALogAndRetriesWhenTheExeIsMissing() {
        val wait = script.indexOf("Wait-Process -Id 4242")
        val stopLeftovers = script.indexOf("Stop-Process -Force")
        val firstInstall = script.indexOf("msiexec")
        val retry = script.indexOf("trying again")
        val launch = script.indexOf("starting VIsage")
        assertTrue(wait in 0 until stopLeftovers && stopLeftovers < firstInstall && firstInstall < retry && retry < launch, script)
        assertTrue("'/l*v'" in script && "msi-install.log" in script)
        assertTrue("VIsage.exe'; } " !in script && "else { Log 'still missing, opening the installer'; Start-Process '" in script, script)
    }

    @Test
    fun quotesApostrophesInPaths() {
        assertTrue("O''Neil" in script, script)
        assertTrue("'O'Neil" !in script)
    }
}
