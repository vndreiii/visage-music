package com.music.vivi.compose

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * A small splash for the first start only. Until the JVM has written its class-data archive
 * (see `visage.jsa` in build.gradle.kts) the first launch takes several seconds with nothing on screen;
 * after that the archive makes starts fast and no splash is shown.
 */
object StartupSplash {
    private const val W = 360
    private const val H = 220
    private val surface = Color(0x14, 0x16, 0x1A)
    private val accent = Color(0x9E, 0xCA, 0xFF)

    @Volatile private var window: JWindow? = null
    @Volatile private var timer: Timer? = null

    /** True when this start has no class-data archive yet (Windows builds only; elsewhere the property is unset). */
    private fun firstStart(): Boolean =
        System.getProperty("visage.jsa")?.let { !File(it).isFile } ?: false

    fun showIfFirstStart() {
        if (!firstStart() || GraphicsEnvironment.isHeadless()) return
        runCatching {
            val icon = StartupSplash::class.java.classLoader.getResourceAsStream("visage_icon.png")?.use { ImageIO.read(it) }
            SwingUtilities.invokeLater {
                val w = JWindow()
                w.isAlwaysOnTop = true
                w.background = Color(0, 0, 0, 0)
                var phase = 0f
                val panel = object : JPanel() {
                    init { isOpaque = false }
                    override fun paintComponent(g: java.awt.Graphics) {
                        val g2 = g.create() as java.awt.Graphics2D
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                        g2.color = surface
                        g2.fill(RoundRectangle2D.Float(0f, 0f, W.toFloat(), H.toFloat(), 32f, 32f))
                        icon?.let { g2.drawImage(it, (W - 72) / 2, 36, 72, 72, null) }
                        g2.font = Font("Segoe UI", Font.BOLD, 22)
                        g2.color = Color(0xE3, 0xE2, 0xE6)
                        val title = "VIsage"
                        g2.drawString(title, (W - g2.fontMetrics.stringWidth(title)) / 2, 148)
                        g2.font = Font("Segoe UI", Font.PLAIN, 12)
                        g2.color = Color(0x9A, 0x9C, 0xA3)
                        val sub = "First start takes a little longer"
                        g2.drawString(sub, (W - g2.fontMetrics.stringWidth(sub)) / 2, 170)
                        // Indeterminate bar: a pill sliding over a faint track.
                        val trackX = 90f; val trackW = W - 180f; val y = 190f
                        g2.color = Color(0x2A, 0x2D, 0x33)
                        g2.stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        g2.drawLine(trackX.toInt(), y.toInt(), (trackX + trackW).toInt(), y.toInt())
                        val seg = trackW * 0.3f
                        val x = trackX + (trackW - seg) * (0.5f - 0.5f * kotlin.math.cos(phase * 2 * Math.PI.toFloat()))
                        g2.color = accent
                        g2.drawLine(x.toInt(), y.toInt(), (x + seg).toInt(), y.toInt())
                        g2.dispose()
                    }
                }
                w.contentPane = panel
                w.setSize(W, H)
                w.setLocationRelativeTo(null)
                w.isVisible = true
                window = w
                timer = Timer(16) { phase = (phase + 0.012f) % 1f; panel.repaint() }.also { it.start() }
            }
        }
    }

    fun close() {
        val w = window ?: return
        window = null
        SwingUtilities.invokeLater {
            timer?.stop()
            w.dispose()
        }
    }
}
