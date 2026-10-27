package com.music.vivi.compose.ui.artist

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.music.vivi.nativelib.AppPaths
import java.io.File
import java.security.MessageDigest
import java.util.Properties

/**
 * Images the user picked for an artist, which win over anything fetched. The picked file is copied into the app's
 * own folder (so moving or deleting the original does nothing), and the name -> file mapping lives in a small
 * properties file next to the settings. Names are matched the same way the artist lookup matches them.
 */
object ArtistImageOverrides {
    private val dir: File by lazy { File(AppPaths.config(), "artist-images").also { it.mkdirs() } }
    private val index: File by lazy { File(dir, "index.properties") }
    private val map: Properties by lazy {
        Properties().also { p -> runCatching { if (index.isFile) index.inputStream().use(p::load) } }
    }

    /** Bumped on every change so composables reading [file] recompose. */
    private var version by mutableIntStateOf(0)

    fun file(name: String): File? {
        @Suppress("UNUSED_EXPRESSION") version
        val key = ArtistNames.normalize(name).ifEmpty { return null }
        return map.getProperty(key)?.let { File(dir, it) }?.takeIf { it.isFile }
    }

    fun set(name: String, picked: File): Boolean = runCatching {
        val key = ArtistNames.normalize(name).ifEmpty { return false }
        val stored = sha1(key) + "." + picked.extension.lowercase().ifEmpty { "jpg" }
        // Drop the previous file for this artist if it had another extension.
        map.getProperty(key)?.takeIf { it != stored }?.let { File(dir, it).delete() }
        picked.copyTo(File(dir, stored), overwrite = true)
        map.setProperty(key, stored)
        save()
        true
    }.getOrDefault(false)

    fun clear(name: String) {
        val key = ArtistNames.normalize(name).ifEmpty { return }
        map.getProperty(key)?.let { File(dir, it).delete() }
        map.remove(key)
        save()
    }

    private fun save() {
        runCatching { index.outputStream().use { map.store(it, "Artist images chosen by the user") } }
        version++
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}
