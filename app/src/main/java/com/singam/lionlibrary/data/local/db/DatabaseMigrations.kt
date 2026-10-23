package com.singam.lionlibrary.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuilds only schema structure; stable primary keys and customer data are copied intact. */
object DatabaseMigrations {
    val all: Array<Migration> =
        (6..12)
            .map { source ->
                object : Migration(source, 13) {
                    override fun migrate(db: SupportSQLiteDatabase) = upgrade(db)
                }
            }
            .toTypedArray()

    private fun upgrade(db: SupportSQLiteDatabase) {
        rebuild(
            db,
            "media",
            "CREATE TABLE IF NOT EXISTS `media_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tmdbId` INTEGER, `title` TEXT NOT NULL, `originalTitle` TEXT, `overview` TEXT, `posterPath` TEXT, `backdropPath` TEXT, `logoPath` TEXT, `genres` TEXT, `rating` REAL, `year` INTEGER, `mediaType` TEXT NOT NULL, `matchConfidence` REAL NOT NULL, `isUnidentified` INTEGER NOT NULL, `duration` INTEGER, `certification` TEXT, `lastUpdated` INTEGER NOT NULL, `filePath` TEXT, `externalSubtitlePath` TEXT, `preferredEngine` TEXT NOT NULL, `letterboxdRating` REAL, `letterboxdUpdatedAt` INTEGER, `isWatchlisted` INTEGER NOT NULL DEFAULT 0, `addedAt` INTEGER NOT NULL DEFAULT 0, `sourceShowTitle` TEXT, `sourceSeason` INTEGER, `sourceEpisode` INTEGER, `sourceEpisodeEnd` INTEGER, `identificationFailure` TEXT)",
            linkedMapOf(
                "id" to "0",
                "tmdbId" to "NULL",
                "title" to "''",
                "originalTitle" to "NULL",
                "overview" to "NULL",
                "posterPath" to "NULL",
                "backdropPath" to "NULL",
                "logoPath" to "NULL",
                "genres" to "NULL",
                "rating" to "NULL",
                "year" to "NULL",
                "mediaType" to "''",
                "matchConfidence" to "0",
                "isUnidentified" to "0",
                "duration" to "NULL",
                "certification" to "NULL",
                "lastUpdated" to "0",
                "filePath" to "NULL",
                "externalSubtitlePath" to "NULL",
                "preferredEngine" to "'EXOPLAYER'",
                "letterboxdRating" to "NULL",
                "letterboxdUpdatedAt" to "NULL",
                "isWatchlisted" to "0",
                "addedAt" to "lastUpdated",
                "sourceShowTitle" to "NULL",
                "sourceSeason" to "NULL",
                "sourceEpisode" to "NULL",
                "sourceEpisodeEnd" to "NULL",
                "identificationFailure" to "NULL",
            ),
        )
        rebuild(
            db,
            "seasons",
            "CREATE TABLE IF NOT EXISTS `seasons_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `showId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `name` TEXT, `posterPath` TEXT, FOREIGN KEY(`showId`) REFERENCES `media_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            linkedMapOf(
                "id" to "0",
                "showId" to "0",
                "seasonNumber" to "0",
                "name" to "NULL",
                "posterPath" to "NULL",
            ),
        )
        rebuild(
            db,
            "episodes",
            "CREATE TABLE IF NOT EXISTS `episodes_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `showId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `title` TEXT, `overview` TEXT, `runtime` INTEGER, `airDate` TEXT, `thumbnailPath` TEXT, `filePath` TEXT NOT NULL, `externalSubtitlePath` TEXT, `preferredEngine` TEXT NOT NULL, `episodeEndNumber` INTEGER, FOREIGN KEY(`showId`) REFERENCES `media_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            linkedMapOf(
                "id" to "0",
                "showId" to "0",
                "seasonNumber" to "0",
                "episodeNumber" to "0",
                "title" to "NULL",
                "overview" to "NULL",
                "runtime" to "NULL",
                "airDate" to "NULL",
                "thumbnailPath" to "NULL",
                "filePath" to "''",
                "externalSubtitlePath" to "NULL",
                "preferredEngine" to "'EXOPLAYER'",
                "episodeEndNumber" to "NULL",
            ),
        )
        rebuild(
            db,
            "watch_progress",
            "CREATE TABLE IF NOT EXISTS `watch_progress_new` (`mediaId` INTEGER NOT NULL, `episodeId` INTEGER NOT NULL, `progress` REAL NOT NULL, `lastPositionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `lastWatched` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, PRIMARY KEY(`mediaId`, `episodeId`))",
            linkedMapOf(
                "mediaId" to "0",
                "episodeId" to "0",
                "progress" to "0",
                "lastPositionMs" to "0",
                "durationMs" to "0",
                "lastWatched" to "0",
                "completed" to "0",
                "isHidden" to "0",
            ),
        )
        db.execSQL("DROP TABLE episodes")
        db.execSQL("DROP TABLE seasons")
        db.execSQL("DROP TABLE watch_progress")
        db.execSQL("DROP TABLE media")
        db.execSQL("ALTER TABLE media_new RENAME TO media")
        // Android SQLite versions/legacy ALTER mode do not consistently rewrite child FKs.
        // Create the final children explicitly rather than depending on rename propagation.
        db.execSQL(
            "CREATE TABLE `episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `showId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `title` TEXT, `overview` TEXT, `runtime` INTEGER, `airDate` TEXT, `thumbnailPath` TEXT, `filePath` TEXT NOT NULL, `externalSubtitlePath` TEXT, `preferredEngine` TEXT NOT NULL, `episodeEndNumber` INTEGER, FOREIGN KEY(`showId`) REFERENCES `media`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("INSERT INTO `episodes` SELECT * FROM `episodes_new`")
        db.execSQL(
            "CREATE TABLE `seasons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `showId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `name` TEXT, `posterPath` TEXT, FOREIGN KEY(`showId`) REFERENCES `media`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("INSERT INTO `seasons` SELECT * FROM `seasons_new`")
        db.execSQL("DROP TABLE episodes_new")
        db.execSQL("DROP TABLE seasons_new")
        db.execSQL("ALTER TABLE watch_progress_new RENAME TO watch_progress")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_mediaType` ON `media` (`mediaType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_title` ON `media` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_tmdbId` ON `media` (`tmdbId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_filePath` ON `media` (`filePath`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_seasons_showId_seasonNumber` ON `seasons` (`showId`, `seasonNumber`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_showId` ON `episodes` (`showId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_filePath` ON `episodes` (`filePath`)"
        )
    }

    private fun rebuild(
        db: SupportSQLiteDatabase,
        table: String,
        createSql: String,
        defaults: Map<String, String>,
    ) {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val index = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(index)
        }
        db.execSQL(createSql)
        val names = defaults.keys.joinToString { "`$it`" }
        val values =
            defaults
                .map { (name, fallback) ->
                    if (name in columns) "`$name`"
                    else if (fallback in columns) "`$fallback`" else fallback
                }
                .joinToString()
        db.execSQL("INSERT INTO `${table}_new` ($names) SELECT $values FROM `$table`")
    }
}
