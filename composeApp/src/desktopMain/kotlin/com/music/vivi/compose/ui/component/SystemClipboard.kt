package com.music.vivi.compose.ui.component

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.TimeUnit

/**
 * Text clipboard that works on Wayland, where AWT's clipboard is XWayland-only and often doesn't reach native apps:
 * wl-copy first, then xclip/xsel, then AWT. Windows and macOS use AWT, which is native there.
 */
object SystemClipboard {
    fun copy(text: String): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("linux")) return viaAwt(text)
        val wayland = !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
        val tools = buildList {
            if (wayland) add(listOf("wl-copy"))
            add(listOf("xclip", "-selection", "clipboard"))
            add(listOf("xsel", "--clipboard", "--input"))
        }
        for (cmd in tools) if (pipe(cmd, text)) return true
        return viaAwt(text)
    }

    private fun pipe(cmd: List<String>, text: String): Boolean = runCatching {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.use { it.write(text.toByteArray()) }
        // wl-copy and xclip fork into the background to own the selection, so they return quickly.
        if (!p.waitFor(2, TimeUnit.SECONDS)) return@runCatching true
        p.exitValue() == 0
    }.getOrDefault(false)

    private fun viaAwt(text: String): Boolean =
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }.isSuccess
}
