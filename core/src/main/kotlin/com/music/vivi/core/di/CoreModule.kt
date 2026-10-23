package com.music.vivi.core.di

import com.music.vivi.core.session.KeyringSessionManager
import com.music.vivi.core.session.SessionManager
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.DownloadedTrackRepository
import com.music.vivi.database.LibraryRepository
import com.music.vivi.database.LocalMusicRepository
import com.music.vivi.database.LocalMusicScanner
import com.music.vivi.database.LyricsCacheRepository
import com.music.vivi.database.PlayHistoryRepository
import com.music.vivi.database.VisageDatabase
import org.koin.dsl.module

val coreModule = module {
    single<SessionManager> { KeyringSessionManager() }
    single { YouTubeMusicService(get()) }
    single<app.cash.sqldelight.db.SqlDriver> { DatabaseFactory.createDriver() }
    single<VisageDatabase> { VisageDatabase(get()) }
    single { LibraryRepository(get()) }
    single { PlayHistoryRepository(get()) }
    single { DownloadedTrackRepository(get()) }
    single { LyricsCacheRepository(get()) }
    single { LocalMusicRepository(get(), get()) }
    single { LocalMusicScanner(get()) }
}
