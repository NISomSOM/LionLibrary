package com.singam.lionlibrary.di

import androidx.room.Room
import com.singam.lionlibrary.data.local.db.MediaDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_progress ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
    }
}

val databaseModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            MediaDatabase::class.java,
            MediaDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration(true).addMigrations(MIGRATION_10_11).build()
    }

    single { get<MediaDatabase>().mediaDao() }
    single { get<MediaDatabase>().seasonDao() }
    single { get<MediaDatabase>().episodeDao() }
    single { get<MediaDatabase>().watchProgressDao() }
}

