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
        for (version in 6..14) {
            val name = "migration-$version"
            helper.createDatabase(name, version).apply {
                execSQL(
                    "INSERT INTO media (id,title,mediaType,matchConfidence,isUnidentified,lastUpdated" +
                        (if (version >= 10) ",preferredEngine" else "") +
                        (if (version >= 13) ",addedAt" else "") +
                        ") VALUES (42,'Preserved show','TV_SHOW',1,0,123" +
                        (if (version >= 10) ",'LIBVLC'" else "") +
                        (if (version >= 13) ",123" else "") +
                        ")"
                )
                execSQL(
                    "INSERT INTO seasons(id,showId,seasonNumber,name) VALUES(51,42,1,'Season 1')"
                )
                execSQL(
                    "INSERT INTO episodes(id,showId,seasonNumber,episodeNumber,filePath" +
                        (if (version >= 10) ",preferredEngine" else "") +
                        (if (version >= 13) ",episodeEndNumber" else "") +
                        ") VALUES(61,42,1,1,'content://fixture/episode'" +
                        (if (version >= 10) ",'LIBVLC'" else "") +
                        (if (version >= 13) ",2" else "") +
                        ")"
                )
                if (version >= 14)
                    execSQL("UPDATE media SET originalLanguage='ja', isWatchlisted=1 WHERE id=42")
                execSQL(
                    "INSERT INTO watch_progress(mediaId,episodeId,progress,lastPositionMs,durationMs,lastWatched,completed" +
                        (if (version >= 11) ",isHidden" else "") +
                        ") VALUES(42,61,0.5,500,1000,123,0" +
                        (if (version >= 11) ",1" else "") +
                        ")"
                )
                close()
            }
            helper.runMigrationsAndValidate(name, 15, true, *DatabaseMigrations.all).apply {
                query("SELECT id,addedAt,isWatchlisted,originalLanguage FROM media").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(42L, it.getLong(0))
                    assertEquals(123L, it.getLong(1))
                    if (version >= 14) {
                        assertEquals(1, it.getInt(2))
                        assertEquals("ja", it.getString(3))
                    } else {
                        assertEquals(0, it.getInt(2))
                        // Unknown until the next metadata refresh fetches it.
                        assertTrue(it.isNull(3))
                    }
                }
                query("SELECT episodeEndNumber FROM episodes WHERE id=61").use {
                    assertTrue(it.moveToFirst())
                    if (version >= 13) assertEquals(2, it.getInt(0)) else assertTrue(it.isNull(0))
                }
                query(
                        "SELECT name FROM sqlite_master WHERE type='index' AND " +
                            "name='index_episodes_showId_seasonNumber_episodeNumber'"
                    )
                    .use { assertTrue(it.moveToFirst()) }
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
