package com.music.vivi.core.di

import com.music.vivi.core.session.KeyringSessionManager
import com.music.vivi.core.session.SessionManager
import com.music.vivi.core.youtube.YouTubeMusicService
import com.music.vivi.database.DatabaseFactory
import com.music.vivi.database.LibraryRepository
import com.music.vivi.database.PlayHistoryRepository
import com.music.vivi.database.ViviDatabase
import org.koin.dsl.module

val coreModule = module {
    single<SessionManager> { KeyringSessionManager() }
    single { YouTubeMusicService(get()) }
    single<ViviDatabase> { DatabaseFactory.create() }
    single { LibraryRepository(get()) }
    single { PlayHistoryRepository(get()) }
}
