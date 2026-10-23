package com.singam.lionlibrary.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.data.local.db.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun populatedUpgradesPreserveEverySupportedSchema() {
        for (version in 6..12) {
            val name = "migration-$version"
            helper.createDatabase(name, version).apply {
                execSQL(
                    "INSERT INTO media (id,title,mediaType,matchConfidence,isUnidentified,lastUpdated" +
                        (if (version >= 10) ",preferredEngine" else "") +
                        ") VALUES (42,'Preserved show','TV_SHOW',1,0,123" +
                        (if (version >= 10) ",'LIBVLC'" else "") +
                        ")"
                )
                execSQL(
                    "INSERT INTO seasons(id,showId,seasonNumber,name) VALUES(51,42,1,'Season 1')"
                )
                execSQL(
                    "INSERT INTO episodes(id,showId,seasonNumber,episodeNumber,filePath" +
                        (if (version >= 10) ",preferredEngine" else "") +
                        ") VALUES(61,42,1,1,'content://fixture/episode'" +
                        (if (version >= 10) ",'LIBVLC'" else "") +
                        ")"
                )
                execSQL(
                    "INSERT INTO watch_progress(mediaId,episodeId,progress,lastPositionMs,durationMs,lastWatched,completed" +
                        (if (version >= 11) ",isHidden" else "") +
                        ") VALUES(42,61,0.5,500,1000,123,0" +
                        (if (version >= 11) ",1" else "") +
                        ")"
                )
                close()
            }
            helper.runMigrationsAndValidate(name, 13, true, *DatabaseMigrations.all).apply {
                query("SELECT id,addedAt,isWatchlisted FROM media").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(42L, it.getLong(0))
                    assertEquals(123L, it.getLong(1))
                    assertEquals(0, it.getInt(2))
                }
                query("SELECT id,showId,preferredEngine FROM episodes").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(61L, it.getLong(0))
                    assertEquals(42L, it.getLong(1))
                    assertEquals(if (version >= 10) "LIBVLC" else "EXOPLAYER", it.getString(2))
                }
                query("SELECT episodeId,lastPositionMs FROM watch_progress").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(61L, it.getLong(0))
                    assertEquals(500L, it.getLong(1))
                }
                query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                close()
            }
        }
    }
}
