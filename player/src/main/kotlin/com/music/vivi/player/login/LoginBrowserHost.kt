package com.music.vivi.player.login

/** UI-provided embedded browser. CDP reads HttpOnly cookies without JavaScript bridges. */
interface LoginBrowserHost : AutoCloseable {
    val debugPort: Int
    suspend fun open(url: String)
}
