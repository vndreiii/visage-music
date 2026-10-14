package com.music.vivi.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

object DatabaseFactory {
    /** XDG data dir: ~/.local/share/visage/visage.db (migrated from vivi-desktop/vivi.db on first use). */
    fun defaultDbFile(): File {
        val dir = com.music.vivi.nativelib.AppPaths.data()
        // The scan cache moved into the database; drop the leftover file from older builds.
        runCatching { File(dir, "scan-cache.json").delete() }
        return com.music.vivi.nativelib.AppPaths.migratedFile(dir, "visage.db", "vivi.db")
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
        val driver = JdbcSqliteDriver(url, props, VisageDatabase.Schema)
        // Also set on the creating thread's connection (schema/migrate path).
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
        driver.execute(null, "PRAGMA busy_timeout = 30000", 0)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        migrateAdditive(driver)
        return driver
    }

    /** Additive migrations for installs that already have an older database. */
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
        migrateLocalLibrary(driver)
    }

    private fun columnsOf(driver: SqlDriver, table: String): Set<String> =
        driver.executeQuery(
            null,
            "PRAGMA table_info($table)",
            { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.next().value) cursor.getString(1)?.let(names::add)
                app.cash.sqldelight.db.QueryResult.Value(names)
            },
            0,
        ).value

    /** Richer track metadata, multi-artist credits and the full-text index (installs from before v2). */
    private fun migrateLocalLibrary(driver: SqlDriver) {
        val have = columnsOf(driver, "local_track")
        mapOf(
            "album_artist" to "TEXT", "genre" to "TEXT", "composer" to "TEXT", "year" to "INTEGER",
            "disc_number" to "INTEGER", "file_size" to "INTEGER", "file_mtime_ms" to "INTEGER",
        ).forEach { (col, type) ->
            if (col !in have) driver.execute(null, "ALTER TABLE local_track ADD COLUMN $col $type", 0)
        }
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS local_track_artist (
              track_id TEXT NOT NULL,
              artist_id TEXT NOT NULL,
              position INTEGER NOT NULL,
              PRIMARY KEY (track_id, artist_id),
              FOREIGN KEY (track_id) REFERENCES local_track(id) ON DELETE CASCADE,
              FOREIGN KEY (artist_id) REFERENCES local_artist(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            0,
        )
        runCatching {
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_artist_by_artist ON local_track_artist(artist_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_genre ON local_track(genre COLLATE NOCASE)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS local_track_by_year ON local_track(year)", 0)
        }
        // FTS5 can't be declared in .sq files (SQLDelight can't parse virtual tables), so it lives here.
        val fresh = columnsOf(driver, "local_track_fts").isEmpty()
        if (fresh) {
            runCatching {
                driver.execute(
                    null,
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS local_track_fts USING fts5(
                      title, artist_name, album_title, genre,
                      content='local_track', content_rowid='rowid',
                      tokenize='unicode61 remove_diacritics 2'
                    )
                    """.trimIndent(),
                    0,
                )
                driver.execute(null, "DROP TRIGGER IF EXISTS local_track_fts_ai", 0)
                driver.execute(null, "DROP TRIGGER IF EXISTS local_track_fts_ad", 0)
                driver.execute(null, "DROP TRIGGER IF EXISTS local_track_fts_au", 0)
                driver.execute(
                    null,
                    """
                    CREATE TRIGGER local_track_fts_ai AFTER INSERT ON local_track BEGIN
                      INSERT INTO local_track_fts(rowid, title, artist_name, album_title, genre)
                      VALUES (new.rowid, new.title, new.artist_name, new.album_title, new.genre);
                    END
                    """.trimIndent(),
                    0,
                )
                driver.execute(
                    null,
                    """
                    CREATE TRIGGER local_track_fts_ad AFTER DELETE ON local_track BEGIN
                      INSERT INTO local_track_fts(local_track_fts, rowid, title, artist_name, album_title, genre)
                      VALUES ('delete', old.rowid, old.title, old.artist_name, old.album_title, old.genre);
                    END
                    """.trimIndent(),
                    0,
                )
                driver.execute(
                    null,
                    """
                    CREATE TRIGGER local_track_fts_au AFTER UPDATE ON local_track BEGIN
                      INSERT INTO local_track_fts(local_track_fts, rowid, title, artist_name, album_title, genre)
                      VALUES ('delete', old.rowid, old.title, old.artist_name, old.album_title, old.genre);
                      INSERT INTO local_track_fts(rowid, title, artist_name, album_title, genre)
                      VALUES (new.rowid, new.title, new.artist_name, new.album_title, new.genre);
                    END
                    """.trimIndent(),
                    0,
                )
                driver.execute(null, "INSERT INTO local_track_fts(local_track_fts) VALUES ('rebuild')", 0)
            }.onFailure { System.err.println("FTS5 unavailable, local search falls back to LIKE: ${it.message}") }
        }
    }

    fun create(dbFile: File = defaultDbFile()): VisageDatabase =
        VisageDatabase(createDriver(dbFile))
}
