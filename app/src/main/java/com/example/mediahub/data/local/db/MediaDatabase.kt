package com.example.mediahub.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mediahub.data.local.db.dao.EpisodeDao
import com.example.mediahub.data.local.db.dao.MediaDao
import com.example.mediahub.data.local.db.dao.SeasonDao
import com.example.mediahub.data.local.db.dao.WatchProgressDao
import com.example.mediahub.data.local.db.entity.EpisodeEntity
import com.example.mediahub.data.local.db.entity.MediaEntity
import com.example.mediahub.data.local.db.entity.SeasonEntity
import com.example.mediahub.data.local.db.entity.WatchProgressEntity

@Database(
    entities = [
        MediaEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        WatchProgressEntity::class
    ],
    version = 6,
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
