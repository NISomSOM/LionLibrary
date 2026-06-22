package com.singam.lionlibrary.di

import androidx.room.Room
import com.singam.lionlibrary.data.local.db.MediaDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            MediaDatabase::class.java,
            MediaDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration(true).build()
    }

    single { get<MediaDatabase>().mediaDao() }
    single { get<MediaDatabase>().seasonDao() }
    single { get<MediaDatabase>().episodeDao() }
    single { get<MediaDatabase>().watchProgressDao() }
}

