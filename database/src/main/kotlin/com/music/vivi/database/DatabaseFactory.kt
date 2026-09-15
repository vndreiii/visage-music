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

    fun dataDir(): File = defaultDbFile().parentFile

    fun offlineDir(): File {
        val dir = File(dataDir(), "offline")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun coversDir(): File {
        val dir = File(dataDir(), "covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createDriver(dbFile: File = defaultDbFile()): SqlDriver {
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        // ThreadedConnectionManager → one JDBC connection per thread. Without WAL +
        // busy_timeout, concurrent writers (local scan vs play history) throw SQLITE_BUSY.
        val props = Properties().apply {
            setProperty("journal_mode", "WAL")
            setProperty("synchronous", "NORMAL")
            setProperty("busy_timeout", "30000")
            setProperty("foreign_keys", "true")
        }
        val driver = JdbcSqliteDriver(url, props, ViviDatabase.Schema)
        // Also set on the creating thread's connection (schema/migrate path).
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
        driver.execute(null, "PRAGMA busy_timeout = 30000", 0)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        migrateAdditive(driver)
        return driver
    }

    /** Additive migrations for installs that already have an older vivi.db. */
    private fun migrateAdditive(driver: SqlDriver) {
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
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS downloaded_track (
              video_id TEXT NOT NULL PRIMARY KEY,
              title TEXT NOT NULL,
              artist TEXT NOT NULL DEFAULT '',
              album TEXT,
              duration_sec INTEGER,
              file_path TEXT NOT NULL,
              cover_path TEXT,
              downloaded_at_ms INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(
                null,
                "CREATE INDEX IF NOT EXISTS downloaded_track_by_time ON downloaded_track(downloaded_at_ms DESC)",
                0,
            )
        }
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS local_artist (
              id TEXT NOT NULL PRIMARY KEY,
              name TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS local_album (
              id TEXT NOT NULL PRIMARY KEY,
              title TEXT NOT NULL,
              artist_id TEXT,
              artist_name TEXT NOT NULL DEFAULT '',
              release_year INTEGER,
              cover_path TEXT,
              added_at_ms INTEGER NOT NULL,
              FOREIGN KEY (artist_id) REFERENCES local_artist(id) ON DELETE SET NULL
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_album_by_added ON local_album(added_at_ms DESC)", 0)
        }
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS local_track (
              id TEXT NOT NULL PRIMARY KEY,
              title TEXT NOT NULL,
              album_id TEXT,
              artist_id TEXT,
              artist_name TEXT NOT NULL DEFAULT '',
              album_title TEXT,
              duration_sec INTEGER,
              file_path TEXT NOT NULL UNIQUE,
              track_number INTEGER,
              cover_path TEXT,
              lyrics_path TEXT,
              embedded_lyrics TEXT,
              bitrate INTEGER,
              sample_rate INTEGER,
              bit_depth INTEGER,
              is_lossless INTEGER NOT NULL DEFAULT 0,
              added_at_ms INTEGER NOT NULL,
              last_played_ms INTEGER,
              play_count INTEGER NOT NULL DEFAULT 0,
              FOREIGN KEY (album_id) REFERENCES local_album(id) ON DELETE SET NULL,
              FOREIGN KEY (artist_id) REFERENCES local_artist(id) ON DELETE SET NULL
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_added ON local_track(added_at_ms DESC)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_played ON local_track(last_played_ms DESC)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_count ON local_track(play_count DESC)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_album ON local_track(album_id, track_number)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_artist ON local_track(artist_id)", 0)
        }
    }

    fun create(dbFile: File = defaultDbFile()): ViviDatabase =
        ViviDatabase(createDriver(dbFile))
}
