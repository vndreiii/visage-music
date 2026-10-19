package com.music.vivi.compose.libmpv

import com.music.vivi.compose.update.ManifestAsset
import com.music.vivi.compose.update.MiniJson
import com.music.vivi.compose.update.SemVer
import com.music.vivi.compose.update.UpdateAssets
import com.music.vivi.compose.update.UpdateHttp
import com.music.vivi.compose.update.UpdateLimits
import com.music.vivi.compose.update.UpdateManifest
import com.music.vivi.compose.update.UpdateSignature
import com.music.vivi.compose.update.UpdateUrls
import com.music.vivi.compose.update.asObject
import com.music.vivi.compose.update.string
import com.music.vivi.nativelib.AppEnv
import com.music.vivi.nativelib.HostOs
import com.music.vivi.player.mpv.LibmpvBundle
import com.music.vivi.player.mpv.MpvLib
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

/**
 * Gets libmpv when the system has none (Linux; Windows ships its own).
 *
 * The copy comes from the newest VIsage release. Its checksum is in `latest.json`, which carries an Ed25519
 * signature that is checked with the key built into the app, like an update. The download is therefore
 * verified before it is unpacked.
 */
object LibmpvProvisioner {
    class Offer(val tag: String, val name: String, val asset: ManifestAsset)

    /** Whether the app must offer a download: Linux, no libmpv found, and the user has not turned it off. */
    fun needed(): Boolean =
        HostOs.isLinux && AppEnv.get("NO_LIBMPV_DOWNLOAD") != "1" && !MpvLib.isAvailable()

    /** Looks up the libmpv bundle of the newest release. Throws [IOException] when there is none. */
    fun lookup(): Offer {
        val name = UpdateAssets.libmpvName(System.getProperty("os.arch").orEmpty())
            ?: throw IOException("No libmpv download for this CPU (${System.getProperty("os.arch")}).")
        val key = UpdateSignature.embeddedPublicKey() ?: throw IOException("This build has no signing key.")
        val manifestUrl = UpdateUrls.latestAsset(UpdateAssets.MANIFEST)
        val signatureUrl = UpdateUrls.latestAsset(UpdateAssets.SIGNATURE)
        val manifestBytes = UpdateHttp.readBounded(manifestUrl, "application/octet-stream", UpdateLimits.MAX_METADATA_BYTES, { it == manifestUrl })
            ?: throw IOException("The latest release has no manifest.")
        val signature = UpdateHttp.readBounded(signatureUrl, "application/octet-stream", UpdateLimits.MAX_SIGNATURE_BYTES, { it == signatureUrl })
            ?.toString(Charsets.UTF_8) ?: throw IOException("The latest release has no signature.")
        if (!UpdateSignature.verify(manifestBytes, signature, key)) throw IOException("The release signature is not valid.")
        val version = SemVer.parseOrNull(MiniJson.parse(manifestBytes.toString(Charsets.UTF_8)).asObject().string("version").orEmpty())
            ?: throw IOException("The release version is not valid.")
        val tag = "v$version"
        val asset = UpdateManifest.parse(manifestBytes, tag).assets[name]
            ?: throw IOException("The latest release (VIsage $version) has no $name.")
        return Offer(tag, name, asset)
    }

    /** Downloads, checks and unpacks [offer] into [LibmpvBundle.dir]. [onProgress] gets 0..1 for the download. */
    fun install(offer: Offer, onProgress: (Float) -> Unit) {
        val target = LibmpvBundle.dir()
        val parent = target.parentFile.apply { mkdirs() }
        val archive = File.createTempFile("libmpv-", ".tar.gz", parent)
        val staging = Files.createTempDirectory(parent.toPath(), "libmpv-new-").toFile()
        try {
            UpdateHttp.download(offer.asset, offer.tag, offer.name, archive, onProgress)
            extractTarGz(archive, staging, rootFolder = "libmpv")
            if (!File(staging, "libmpv.so.2").isFile) throw IOException("The download does not contain libmpv.so.2.")
            target.deleteRecursively()
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            archive.delete()
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    /**
     * Unpacks regular files and links of a .tar.gz into [dest]. Only entries inside [rootFolder] are accepted, and
     * the folder name is dropped, so `libmpv/libmpv.so.2` becomes `dest/libmpv.so.2`. Anything that would land
     * outside [dest] (a `..` part, an absolute name, a link that leaves the folder) is rejected.
     */
    internal fun extractTarGz(archive: File, dest: File, rootFolder: String) {
        val root = dest.canonicalFile
        GZIPInputStream(archive.inputStream().buffered()).use { input ->
            val header = ByteArray(512)
            var pendingLongName: String? = null
            while (true) {
                if (!readFully(input, header)) break
                if (header.all { it == 0.toByte() }) break
                var name = pendingLongName ?: (field(header, 345, 155).let { if (it.isEmpty()) "" else "$it/" } + field(header, 0, 100))
                pendingLongName = null
                val size = field(header, 124, 12).trim().takeIf { it.isNotEmpty() }?.toLong(8) ?: 0L
                val type = header[156].toInt().toChar()
                val blocks = (size + 511) / 512 * 512
                when (type) {
                    'L' -> { // GNU long name: the data is the name of the next entry
                        val data = ByteArray(size.toInt()); readFully(input, data); skip(input, blocks - size)
                        pendingLongName = String(data, Charsets.UTF_8).trimEnd('\u0000')
                    }
                    '0', '\u0000' -> {
                        val relative = relativeName(name, rootFolder)
                        if (relative == null) { skip(input, blocks); continue }
                        val out = safeChild(root, relative)
                        out.parentFile.mkdirs()
                        out.outputStream().use { o ->
                            var left = size
                            val buf = ByteArray(64 * 1024)
                            while (left > 0) {
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) throw IOException("The archive ends early.")
                                o.write(buf, 0, n); left -= n
                            }
                        }
                        skip(input, blocks - size)
                    }
                    '2' -> { // symlink, such as libmpv.so -> libmpv.so.2: only a plain sibling name is accepted
                        val relative = relativeName(name, rootFolder)
                        val linkTo = field(header, 157, 100)
                        if (relative != null && !linkTo.contains('/') && linkTo != ".." && linkTo.isNotEmpty()) {
                            val link = safeChild(root, relative)
                            link.parentFile.mkdirs()
                            Files.deleteIfExists(link.toPath())
                            Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(linkTo))
                        }
                        skip(input, blocks)
                    }
                    else -> skip(input, blocks) // directories, pax headers and anything else
                }
            }
        }
    }

    private fun relativeName(name: String, rootFolder: String): String? {
        val clean = name.removePrefix("./")
        if (!clean.startsWith("$rootFolder/")) return null
        return clean.removePrefix("$rootFolder/").takeIf { it.isNotEmpty() && !it.endsWith("/") }
    }

    private fun safeChild(root: File, relative: String): File {
        if (relative.startsWith("/") || relative.split('/').any { it == ".." }) throw IOException("Unsafe name in the archive: $relative")
        val child = File(root, relative).canonicalFile
        if (child.parentFile != root && !child.path.startsWith(root.path + File.separator)) throw IOException("Unsafe name in the archive: $relative")
        return child
    }

    private fun field(header: ByteArray, offset: Int, length: Int): String =
        String(header, offset, length, Charsets.UTF_8).trimEnd('\u0000', ' ')

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return if (read == 0) false else throw IOException("The archive ends early.")
            read += n
        }
        return true
    }

    private fun skip(input: java.io.InputStream, count: Long) {
        var left = count
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("The archive ends early.")
            left -= n
        }
    }
}
