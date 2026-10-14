package com.music.vivi.player.login

import com.music.vivi.player.session.SessionSecretStore
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Recover a session from an already open VIsage sign-in browser, without logging secrets. */
fun main() {
    val outcome = runCatching {
        runBlocking {
            val port = requireNotNull(System.getenv("VISAGE_RECOVER_LOGIN_PORT")) { "Set VISAGE_RECOVER_LOGIN_PORT" }.toInt()
            CdpClient(port).use { client ->
                client.reconnectLoop()
                val session = client.captureSession()
                val account = session.validate().getOrThrow()
                SessionSecretStore.save(session, account)
                println("Browser session validated and saved to the system keyring.")
            }
        }
    }
    outcome.exceptionOrNull()?.let { System.err.println("Recovery failed: ${it.message?.substringBefore(". Text:")}") }
    // The keyring/HTTP clients can keep background threads alive in a command-line helper.
    exitProcess(if (outcome.isSuccess) 0 else 1)
}
