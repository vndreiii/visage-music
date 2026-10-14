package com.music.vivi.compose.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Network access for the updater. Requests never carry credentials. Redirects are followed by
 * hand, and each hop must be an https URL on a GitHub host.
 */
internal object UpdateHttp {
    private const val MAX_REDIRECTS = 5
    private const val USER_AGENT = "VIsage-updater"

    /** Opens [start] (already checked by [trustedStart]) and follows trusted redirects. */
    fun open(start: String, accept: String, trustedStart: (String) -> Boolean): HttpURLConnection {
        var target = start
        for (hop in 0..MAX_REDIRECTS) {
            val allowed = if (hop == 0) trustedStart(target) else UpdateUrls.isTrustedRedirect(target)
            if (!allowed) throw IOException("Untrusted update location")
            val connection = (URI(target).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location == null) throw IOException("Redirect without location")
                target = URI(target).resolve(location).toString()
                continue
            }
            return connection
        }
        throw IOException("Too many redirects")
    }

    /** Reads at most [maxBytes]; a 404 on [allowMissing] returns null. */
    fun readBounded(
        start: String,
        accept: String,
        maxBytes: Int,
        trustedStart: (String) -> Boolean,
        allowMissing: Boolean = false,
    ): ByteArray? {
        val connection = open(start, accept, trustedStart)
        try {
            if (allowMissing && connection.responseCode == 404) return null
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > maxBytes) throw IOException("Response too large")
            return connection.inputStream.use { readAtMost(it, maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Streams an asset into [destination], checking size and SHA-256 as it goes. The caller
     * deletes [destination] if this throws.
     */
    fun download(
        asset: ManifestAsset,
        tag: String,
        name: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ) {
        val connection = open(asset.url, "application/octet-stream", trustedStart = { url ->
            url == UpdateUrls.expectedAssetUrl(tag, name)
        })
        try {
            if (connection.responseCode != 200) throw IOException("Download failed: HTTP ${connection.responseCode}")
            val reported = connection.contentLengthLong
            if (reported >= 0 && reported != asset.size) throw IOException("Unexpected download size")
            var copied = 0L
            var lastReported = -1
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > asset.size) throw IOException("Download larger than expected")
                        output.write(buffer, 0, count)
                        val percent = (copied * 100 / asset.size).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(copied.toFloat() / asset.size)
                        }
                    }
                }
            }
            verifyFile(destination, asset.size, asset.sha256)
        } finally {
            connection.disconnect()
        }
    }

    internal fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (out.size() + count > maxBytes) throw IOException("Response too large")
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}

/** Throws unless [file] has exactly [size] bytes and SHA-256 [sha256] (lower-case hex). */
internal fun verifyFile(file: File, size: Long, sha256: String) {
    if (file.length() != size) throw IOException("Incomplete download")
    if (sha256Hex(file) != sha256) throw IOException("Checksum mismatch")
}

/** Lower-case hex, the form used by latest.json. */
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** SHA-256 of a file, lower-case hex. */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}
