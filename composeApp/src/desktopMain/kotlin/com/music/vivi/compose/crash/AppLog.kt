package com.music.vivi.compose.crash

import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Tees everything printed to stdout/stderr into a log file and an in-memory tail, so a crash report
 * can include what led up to it. The original streams keep working.
 */
object AppLog {
    private const val MAX_LINES = 2_000
    private const val KEEP_FILES = 5

    private val tail = ArrayDeque<String>(MAX_LINES)
    private val lock = Any()
    @Volatile var logFile: File? = null
        private set

    fun install() {
        if (logFile != null) return
        val dir = logDir()
        val file = runCatching {
            dir.mkdirs()
            File(dir, "visage-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.log").also { pruneOld(dir) }
        }.getOrNull()
        logFile = file
        val writer = file?.let { runCatching { it.bufferedWriter() }.getOrNull() }
        System.setOut(PrintStream(Tee(System.out, writer, stderr = false), true, Charsets.UTF_8))
        System.setErr(PrintStream(Tee(System.err, writer, stderr = true), true, Charsets.UTF_8))
    }

    fun logDir(): File = File(com.music.vivi.nativelib.AppPaths.state(), "logs")

    /** The most recent [lines] log lines, oldest first. */
    fun snapshot(lines: Int = 300): String = synchronized(lock) { tail.toList().takeLast(lines).joinToString("\n") }

    private fun pruneOld(dir: File) {
        dir.listFiles { f -> (f.name.startsWith("visage-") || f.name.startsWith("vivi-")) && f.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_FILES - 1)
            ?.forEach { runCatching { it.delete() } }
    }

    private class Tee(
        private val original: OutputStream,
        private val file: java.io.BufferedWriter?,
        private val stderr: Boolean,
    ) : OutputStream() {
        private val line = StringBuilder()

        override fun write(b: Int) {
            original.write(b)
            synchronized(lock) {
                if (b == '\n'.code) flushLine() else if (b != '\r'.code) line.append(b.toChar())
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            original.write(b, off, len)
            synchronized(lock) {
                String(b, off, len, Charsets.UTF_8).forEach { c ->
                    if (c == '\n') flushLine() else if (c != '\r') line.append(c)
                }
            }
        }

        private fun flushLine() {
            val text = (if (stderr) "E " else "  ") + line
            line.setLength(0)
            if (tail.size >= MAX_LINES) tail.removeFirst()
            tail.addLast(text)
            runCatching { file?.apply { write(text); newLine(); flush() } }
        }

        override fun flush() = original.flush()
    }
}
