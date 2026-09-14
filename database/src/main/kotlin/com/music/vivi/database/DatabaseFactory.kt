package com.music.vivi.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

object DatabaseFactory {
    /** XDG data dir: ~/.local/share/vivi-desktop/vivi.db */
    fun defaultDbFile(): File {
        val xdg = System.getenv("XDG_DATA_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), ".local/share").absolutePath
        val dir = File(xdg, "vivi-desktop")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "vivi.db")
    }

    fun createDriver(dbFile: File = defaultDbFile()): SqlDriver {
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        val driver = JdbcSqliteDriver(url, Properties(), ViviDatabase.Schema)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        // Additive migration for play history (existing installs).
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS play_event (
              id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
              song_id TEXT NOT NULL,
              title TEXT NOT NULL,
              artists TEXT NOT NULL DEFAULT '',
              thumbnail TEXT,
              duration_sec INTEGER,
              played_at_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(null, "CREATE INDEX IF NOT EXISTS play_event_by_time ON play_event(played_at_ms DESC)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS play_event_by_song ON play_event(song_id, played_at_ms DESC)", 0)
        }
        return driver
    }

    fun create(dbFile: File = defaultDbFile()): ViviDatabase =
        ViviDatabase(createDriver(dbFile))
}
