package com.music.vivi.database

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpusCoverExtractor {
    /**
     * Extracts METADATA_BLOCK_PICTURE from Ogg Opus files, falling back to ffmpeg if available.
     * Returns the absolute path of the cached image file, or null if no cover was found.
     */
    suspend fun extractCover(audioPath: String, coversRoot: File): String? = withContext(Dispatchers.IO) {
        val file = File(audioPath)
        if (!file.exists()) return@withContext null
        
        try {
            val imageData = extractFromOggOpus(file)
            if (imageData != null) {
                return@withContext saveToCache(imageData, coversRoot)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Fallback to ffmpeg
        try {
            val md = MessageDigest.getInstance("SHA-1")
            val hash = md.digest(audioPath.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
            val out = File(coversRoot, "ffmpeg_$hash.jpg")
            if (out.exists()) return@withContext out.absolutePath
            
            val process = ProcessBuilder(
                "ffmpeg", "-v", "error", "-i", audioPath, "-an", "-c:v", "copy", "-frames:v", "1", "-y", out.absolutePath
            ).start()
            val exitCode = process.waitFor()
            if (exitCode == 0 && out.exists() && out.length() > 0) {
                return@withContext out.absolutePath
            }
        } catch (e: Exception) {
            // ffmpeg not found or failed
        }
        
        null
    }
    
    private fun saveToCache(imageData: ByteArray, coversRoot: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(imageData).joinToString("") { "%02x".format(it) }.take(24)
        val out = File(coversRoot, "$hash.jpg")
        if (!out.exists() || out.length() != imageData.size.toLong()) {
            out.writeBytes(imageData)
        }
        return out.absolutePath
    }

    private fun extractFromOggOpus(file: File): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(4)
            raf.readFully(header)
            if (String(header) != "OggS") return null
            
            // Ogg files have pages. We need to find the OpusTags page.
            // Usually it's the second page. Let's just scan for "OpusTags" signature.
            val buf = ByteArray(1024 * 128) // 128KB should be enough to find tags
            raf.seek(0)
            val read = raf.read(buf)
            if (read <= 0) return null
            
            val tagsMagic = "OpusTags".toByteArray()
            var tagsOffset = -1
            for (i in 0 until read - tagsMagic.size) {
                var match = true
                for (j in tagsMagic.indices) {
                    if (buf[i + j] != tagsMagic[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    tagsOffset = i
                    break
                }
            }
            if (tagsOffset == -1) return null
            
            // OpusTags structure:
            // 8 bytes: "OpusTags"
            // 4 bytes (LE): Vendor string length
            // N bytes: Vendor string
            // 4 bytes (LE): User comment list length
            // Loop for comments...
            
            var pos = tagsOffset + 8
            if (pos + 4 > read) return null
            
            val vendorLen = readIntLE(buf, pos)
            pos += 4 + vendorLen
            if (pos + 4 > read) return null
            
            val commentsCount = readIntLE(buf, pos)
            pos += 4
            
            for (i in 0 until commentsCount) {
                if (pos + 4 > read) return null
                val commentLen = readIntLE(buf, pos)
                pos += 4
                if (pos + commentLen > read) return null
                
                val commentStr = String(buf, pos, commentLen, Charsets.UTF_8)
                pos += commentLen
                
                if (commentStr.startsWith("METADATA_BLOCK_PICTURE=", ignoreCase = true)) {
                    val base64Str = commentStr.substring("METADATA_BLOCK_PICTURE=".length)
                    return parseFlacPicture(base64Str)
                }
            }
        }
        return null
    }
    
    private fun readIntLE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
               ((buf[offset + 1].toInt() and 0xFF) shl 8) or
               ((buf[offset + 2].toInt() and 0xFF) shl 16) or
               ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }
    
    private fun parseFlacPicture(base64: String): ByteArray? {
        try {
            val bytes = Base64.getDecoder().decode(base64)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            val type = buffer.int
            val mimeLen = buffer.int
            val mime = ByteArray(mimeLen)
            buffer.get(mime)
            
            val descLen = buffer.int
            val desc = ByteArray(descLen)
            buffer.get(desc)
            
            val width = buffer.int
            val height = buffer.int
            val colorDepth = buffer.int
            val indexedColors = buffer.int
            
            val dataLen = buffer.int
            val data = ByteArray(dataLen)
            buffer.get(data)
            
            return data
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
