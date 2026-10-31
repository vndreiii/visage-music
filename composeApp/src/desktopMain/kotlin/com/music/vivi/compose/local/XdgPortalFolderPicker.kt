package com.music.vivi.compose.local

import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.ObjectPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * Native folder picker via XDG Desktop Portal (`org.freedesktop.portal.FileChooser`).
 * Falls back to zenity/kdialog only if the portal call fails.
 */
object XdgPortalFolderPicker {
    suspend fun pickDirectory(title: String = "Choose music folder"): File? = pickDirectories(title).firstOrNull()

    /** One or more folders; empty when the user cancels. */
    suspend fun pickDirectories(title: String = "Choose music folders"): List<File> =
        withContext(Dispatchers.IO) {
            if (com.music.vivi.nativelib.HostOs.isWindows) return@withContext pickViaSwing(title)
            // A successful empty result means the user cancelled. Only failures fall back.
            runCatching { pickViaPortal(title) }.getOrElse {
                runCatching { pickViaZenity(title) }.getOrElse { pickViaKdialog(title) }
            }
        }

    /** One image file via the portal (so it matches the user's own file chooser); null when cancelled. */
    suspend fun pickImage(title: String = "Choose image"): File? = withContext(Dispatchers.IO) {
        val exts = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
        if (com.music.vivi.nativelib.HostOs.isWindows) return@withContext pickImageViaAwt(title, exts)
        // Filtered first; if the portal balks at the filter, the same portal without one, and only then a toolkit dialog.
        runCatching { pickViaPortal(title, directory = false, multiple = false, imageFilter = true).firstOrNull() }.getOrElse {
            println("XDG portal image pick (filtered) failed: ${it.message}")
            runCatching { pickViaPortal(title, directory = false, multiple = false, imageFilter = false).firstOrNull() }.getOrElse {
                runCatching { pickImageViaZenity(title) }.getOrElse { pickImageViaAwt(title, exts) }
            }
        }
    }

    private fun pickImageViaZenity(title: String): File? {
        val proc = ProcessBuilder("zenity", "--file-selection", "--title=$title", "--file-filter=Images | *.png *.jpg *.jpeg *.gif *.bmp *.webp")
            .redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        return if (proc.waitFor() != 0 || out.isBlank()) null else File(out).takeIf { it.isFile }
    }

    private fun pickImageViaAwt(title: String, exts: List<String>): File? = runCatching {
        val dlg = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
        dlg.setFilenameFilter { _, n -> n.lowercase().substringAfterLast('.', "") in exts }
        dlg.isVisible = true
        if (dlg.file != null && dlg.directory != null) File(dlg.directory, dlg.file) else null
    }.getOrNull()

    private fun pickViaPortal(title: String, directory: Boolean = true, multiple: Boolean = true, imageFilter: Boolean = false): List<File> {
        val connection = try {
            DBusConnectionBuilder.forSessionBus().withShared(false).build()
        } catch (e: DBusException) {
            println("XDG portal: no session bus (${e.message})")
            throw e
        }
        return try {
            val portal = connection.getRemoteObject(
                "org.freedesktop.portal.Desktop",
                "/org/freedesktop/portal/desktop",
                PortalFileChooser::class.java,
            )
            val token = "vivi_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val options = linkedMapOf<String, Variant<*>>(
                "handle_token" to Variant(token),
                "directory" to Variant(directory),
                "multiple" to Variant(multiple),
                "modal" to Variant(true),
            )
            if (imageFilter) {
                val patterns = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp").map { PortalPattern(UInt32(0), "*.$it") }
                options["filters"] = Variant(listOf(PortalFilter("Images", patterns)), "a(sa(us))")
            }
            // Subscribe before opening; a fast portal response must not be lost.
            val handlePath = "/org/freedesktop/portal/desktop/request/${connection.uniqueName.removePrefix(":").replace('.', '_')}/$token"
            val future = CompletableFuture<List<File>>()
            val handler = DBusSigHandler<PortalRequest.Response> { signal ->
                if (signal.path != handlePath) return@DBusSigHandler
                if (signal.response.toInt() == 0) {
                    @Suppress("UNCHECKED_CAST")
                    val uris = signal.results["uris"]?.value as? List<*>
                    future.complete(uris.orEmpty().mapNotNull { it?.toString()?.let(::uriToFile) })
                } else {
                    future.complete(emptyList())
                }
            }
            connection.addSigHandler(PortalRequest.Response::class.java, handler)
            try {
                portal.OpenFile("", title, options)
                future.get(120, TimeUnit.SECONDS)
            } finally {
                runCatching {
                    connection.removeSigHandler(PortalRequest.Response::class.java, handler)
                }
            }
        } catch (e: Exception) {
            println("XDG portal folder pick failed: ${e.message}")
            throw e
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun uriToFile(uri: String): File? = runCatching {
        val path = URI(uri).path ?: return null
        // percent-decode path segments if needed
        val decoded = URI(null, null, path, null).path
        File(decoded).absoluteFile
    }.getOrNull()

    /** Windows (and any host without a portal): the Swing chooser in the system look and feel. */
    private fun pickViaSwing(title: String): List<File> {
        var picked: List<File> = emptyList()
        javax.swing.SwingUtilities.invokeAndWait {
            runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
            val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
                dialogTitle = title
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
                isMultiSelectionEnabled = true
            }
            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                picked = chooser.selectedFiles.map { it.absoluteFile }
            }
        }
        return picked.filter { it.isDirectory }
    }

    private fun pickViaZenity(title: String): List<File> {
        val proc = ProcessBuilder(
            "zenity", "--file-selection", "--directory", "--multiple", "--separator=\n", "--title=$title",
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        return if (proc.waitFor() != 0 || out.isBlank()) emptyList()
        else out.lines().map { File(it.trim()).absoluteFile }.filter { it.isDirectory }
    }

    private fun pickViaKdialog(title: String): List<File> = runCatching {
        val proc = ProcessBuilder(
            "kdialog", "--getexistingdirectory", System.getProperty("user.home"), "--title", title,
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() != 0 || out.isBlank()) emptyList()
        else listOfNotNull(File(out).absoluteFile.takeIf { it.isDirectory })
    }.getOrDefault(emptyList())
}

@DBusInterfaceName("org.freedesktop.portal.FileChooser")
interface PortalFileChooser : DBusInterface {
    fun OpenFile(
        parentWindow: String,
        title: String,
        // Kotlin's `Map<K, out V>` becomes `? extends Variant<?>` in Java, which dbus-java turns into the broken signature
        // `a{s}` (the bus then drops the connection: "Underlying transport returned -1"). It must be exactly a{sv}.
        options: @JvmSuppressWildcards Map<String, Variant<*>>,
    ): ObjectPath
}

/**
 * The portal's request object. dbus-java wants a signal class to be a member of the interface that declares it
 * (a top-level class gives "Signals must be declared as a member of a class implementing DBusInterface"), so
 * `Response` lives inside this interface and its name is the D-Bus signal name.
 */
@DBusInterfaceName("org.freedesktop.portal.Request")
interface PortalRequest : DBusInterface {
    class Response @Throws(DBusException::class) constructor(
        path: String,
        val response: UInt32,
        val results: Map<String, Variant<*>>,
    ) : DBusSignal(path, response, results)
}

class PortalPattern(
    @JvmField @field:org.freedesktop.dbus.annotations.Position(0) val kind: UInt32,
    @JvmField @field:org.freedesktop.dbus.annotations.Position(1) val pattern: String,
) : org.freedesktop.dbus.Struct()

class PortalFilter(
    @JvmField @field:org.freedesktop.dbus.annotations.Position(0) val name: String,
    @JvmField @field:org.freedesktop.dbus.annotations.Position(1) val patterns: List<PortalPattern>,
) : org.freedesktop.dbus.Struct()
