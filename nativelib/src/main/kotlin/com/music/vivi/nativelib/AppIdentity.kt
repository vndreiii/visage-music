package com.music.vivi.nativelib

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The app's names and where its files live. The app was called "VIVI Desktop" before it became
 * **VIsage**: existing users' data is migrated on first use, and old environment variables keep working.
 */
object AppIdentity {
    const val NAME = "VIsage"
    /** Lowercase id: directory names, desktop entry, MPRIS bus name suffix, keyring service. */
    const val ID = "visage"
    const val LEGACY_ID = "vivi-desktop"
}

/** `VISAGE_<NAME>`, falling back to the legacy `VIVI_<NAME>`. */
object AppEnv {
    fun get(name: String): String? =
        System.getenv("VISAGE_$name")?.takeIf { it.isNotEmpty() } ?: System.getenv("VIVI_$name")?.takeIf { it.isNotEmpty() }

    fun flag(name: String): Boolean = get(name) == "1"
}

/**
 * XDG directories for the app. Each accessor moves a pre-rename directory (`…/vivi-desktop`) to
 * `…/visage` the first time it is asked for, then leaves a symlink at the old path so a still-running
 * older build (or an unmigrated tool) keeps working. A rename is atomic and instant on one filesystem,
 * which matters: the data directory holds the library database and offline downloads.
 */
object AppPaths {
    enum class Kind { Data, Cache, Config, State }

    private fun base(kind: Kind): File {
        val home = System.getProperty("user.home")
        val (env, fallback) = when (kind) {
            Kind.Data -> "XDG_DATA_HOME" to ".local/share"
            Kind.Cache -> "XDG_CACHE_HOME" to ".cache"
            Kind.Config -> "XDG_CONFIG_HOME" to ".config"
            Kind.State -> "XDG_STATE_HOME" to ".local/state"
        }
        return File(System.getenv(env)?.takeIf { it.isNotBlank() } ?: File(home, fallback).path)
    }

    private val resolved = java.util.concurrent.ConcurrentHashMap<Kind, File>()

    fun dir(kind: Kind): File = resolved.getOrPut(kind) { migrateDir(base(kind)) }

    /** Returns `<parent>/visage`, first moving a pre-rename `<parent>/vivi-desktop` there if needed. */
    fun migrateDir(parent: File): File {
        val current = File(parent, AppIdentity.ID)
        val legacy = File(parent, AppIdentity.LEGACY_ID)
        runCatching {
            parent.mkdirs()
            if (!current.exists() && legacy.exists() && !Files.isSymbolicLink(legacy.toPath())) {
                Files.move(legacy.toPath(), current.toPath(), StandardCopyOption.ATOMIC_MOVE)
                runCatching { Files.createSymbolicLink(legacy.toPath(), current.toPath()) }
            }
        }.onFailure { System.err.println("AppPaths: could not migrate $legacy → $current: ${it.message}") }
        current.mkdirs()
        return current
    }

    fun data(): File = dir(Kind.Data)
    fun cache(): File = dir(Kind.Cache)
    fun config(): File = dir(Kind.Config)
    fun state(): File = dir(Kind.State)

    /**
     * Renames a file inside [dir] from its pre-rename name to [current] (with SQLite's -wal/-shm
     * side files) if only the old one exists. Returns the file to use.
     */
    fun migratedFile(dir: File, current: String, legacy: String): File {
        val target = File(dir, current)
        val old = File(dir, legacy)
        if (!target.exists() && old.exists()) {
            runCatching {
                for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                    val from = File(dir, legacy + suffix)
                    if (from.exists()) Files.move(from.toPath(), File(dir, current + suffix).toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
            }.onFailure { System.err.println("AppPaths: could not rename $old → $target: ${it.message}") }
        }
        return target
    }
}
