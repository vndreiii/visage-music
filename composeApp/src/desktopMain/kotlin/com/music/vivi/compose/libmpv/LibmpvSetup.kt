package com.music.vivi.compose.libmpv

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JProgressBar
import javax.swing.SwingUtilities

/**
 * First-start check for libmpv. If the system has none, the user is asked once to download a copy.
 * The dialogs are plain Swing, because this runs before the app window exists.
 */
object LibmpvSetup {
    /** Returns false when the app cannot start without libmpv and the user did not fix that. */
    fun ensure(): Boolean {
        if (!LibmpvProvisioner.needed()) return true
        val offer = try {
            LibmpvProvisioner.lookup()
        } catch (e: Exception) {
            return fail("VIsage could not find libmpv, and could not look for a download.\n\n${e.message}")
        }
        val megabytes = (offer.asset.size + 999_999) / 1_000_000
        val yes = onEdt {
            JOptionPane.showConfirmDialog(
                null,
                "VIsage needs the mpv audio library (libmpv). It is not installed on this system.\n\n" +
                    "Download a copy from the VIsage release on GitHub now? It is about $megabytes MB and is checked " +
                    "against a signature before it is used.\n\n" +
                    "You can also install libmpv with your package manager (the package is mpv or libmpv2).",
                "VIsage needs libmpv",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
            )
        } == JOptionPane.YES_OPTION
        if (!yes) return fail("VIsage cannot play music without libmpv.\nInstall it with your package manager, or set VISAGE_LIBMPV to the full path of libmpv.so.2.")

        val bar = JProgressBar(0, 100)
        val label = JLabel("Downloading libmpv…")
        val dialog = onEdt {
            JDialog(null as java.awt.Frame?, "VIsage", false).apply {
                val panel = javax.swing.JPanel(BorderLayout(0, 10)).apply {
                    border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
                    add(label, BorderLayout.NORTH)
                    add(bar, BorderLayout.CENTER)
                }
                contentPane.add(panel)
                preferredSize = Dimension(380, 110)
                defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
                pack()
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
        return try {
            LibmpvProvisioner.install(offer) { fraction ->
                SwingUtilities.invokeLater { bar.value = (fraction * 100).toInt() }
            }
            if (com.music.vivi.player.mpv.MpvLib.isAvailable()) true
            else fail("VIsage downloaded libmpv, but it could not be loaded on this system.\nInstall libmpv with your package manager, or set VISAGE_LIBMPV to the full path of libmpv.so.2.")
        } catch (e: Exception) {
            fail("The libmpv download failed.\n\n${e.message}\n\nInstall libmpv with your package manager, or set VISAGE_LIBMPV to the full path of libmpv.so.2.")
        } finally {
            onEdt { dialog.dispose() }
        }
    }

    private fun fail(message: String): Boolean {
        System.err.println("libmpv: $message")
        onEdt { JOptionPane.showMessageDialog(null, message, "VIsage needs libmpv", JOptionPane.ERROR_MESSAGE) }
        return false
    }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
