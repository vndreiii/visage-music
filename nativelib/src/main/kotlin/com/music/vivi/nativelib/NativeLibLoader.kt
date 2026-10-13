package com.music.vivi.nativelib

import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Loads `libvivi_native.so` (the Rust helpers in `native/`) for any module that binds part of it.
 *
 * Lookup order: `$VIVI_NATIVE_LIB` (absolute path), the packaged app's resources directory, then the
 * system library path. The working directory is deliberately never searched, and file-based
 * candidates must not be group/world writable. `VIVI_NATIVE_DISABLE=1` forces the pure-JVM fallbacks.
 */
object NativeLibLoader {
    fun <T : Library> load(api: Class<T>): T? {
        if (System.getenv("VIVI_NATIVE_DISABLE") == "1") return null
        val candidates = buildList {
            System.getenv("VIVI_NATIVE_LIB")?.takeIf { it.isNotBlank() }?.let(::add)
            System.getProperty("compose.application.resources.dir")?.let { add(File(it, "libvivi_native.so").path) }
            add("vivi_native")
        }
        for (name in candidates) {
            if (name.contains('/') && !trusted(File(name))) continue
            try {
                return Native.load(name, api)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun trusted(f: File): Boolean {
        if (!f.isAbsolute || !f.isFile) return false
        return runCatching {
            val perms = Files.getPosixFilePermissions(f.toPath())
            PosixFilePermission.GROUP_WRITE !in perms && PosixFilePermission.OTHERS_WRITE !in perms
        }.getOrDefault(false)
    }
}
