package com.singam.lionlibrary.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.SeasonDao
import com.singam.lionlibrary.data.local.db.dao.WatchProgressDao
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import com.singam.lionlibrary.data.local.db.entity.SeasonEntity
import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity

@Database(
    entities = [
        MediaEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        WatchProgressEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        const val DATABASE_NAME = "media_library_db"
    }
}

