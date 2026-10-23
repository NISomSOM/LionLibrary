package com.singam.lionlibrary.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.local.db.entity.*
import com.singam.lionlibrary.data.repository.RoomMediaRepository
import com.singam.lionlibrary.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryDatabaseTest {
    @Test
    fun filtersAndStableUpdates() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    MediaDatabase::class.java,
                )
                .build()
        try {
            val dao = db.mediaDao()
            val repo = RoomMediaRepository(dao, db.seasonDao(), db.episodeDao())
            fun item(
                id: Long,
                title: String,
                type: String,
                genres: String? = null,
                rating: Float? = null,
            ) =
                MediaEntity(
                    tmdbId = null,
                    originalTitle = null,
                    overview = null,
                    posterPath = null,
                    backdropPath = null,
                    year = null,
                    id = id,
                    title = title,
                    mediaType = type,
                    matchConfidence = 1f,
                    isUnidentified = false,
                    lastUpdated = 1,
                    genres = genres,
                    rating = rating,
                    filePath = "content://fixture/$id",
                )
            dao.insert(item(1, "100%_real", "MOVIE", "Anime", 8f))
            dao.insert(item(2, "Other", "MOVIE", rating = null))
            dao.insert(item(3, "Show", "TV_SHOW"))
            db.seasonDao().insert(SeasonEntity(10, 3, 1, "Season 1", null))
            db.episodeDao()
                .insert(
                    EpisodeEntity(
                        title = null,
                        overview = null,
                        runtime = null,
                        airDate = null,
                        thumbnailPath = null,
                        id = 20,
                        showId = 3,
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "content://fixture/ep1",
                    )
                )
            db.episodeDao()
                .insert(
                    EpisodeEntity(
                        title = null,
                        overview = null,
                        runtime = null,
                        airDate = null,
                        thumbnailPath = null,
                        id = 21,
                        showId = 3,
                        seasonNumber = 1,
                        episodeNumber = 2,
                        filePath = "content://fixture/ep2",
                    )
                )
            db.watchProgressDao().upsert(WatchProgressEntity(3, 20, 1f, 0, 100, 1, true))
            // A completed episode must not lend its captured timing to the next episode's badge.
            val history =
                com.singam.lionlibrary.data.repository.RoomWatchProgressRepository(
                    db.watchProgressDao(),
                    db.episodeDao(),
                )
            val nextUp = history.getJumpBackInItems().first().single()
            assertEquals(21L, nextUp.episodeId)
            assertTrue(nextUp.isNextUp)
            assertEquals(0L, nextUp.durationMs)
            assertEquals(0L, nextUp.positionMs)
            db.watchProgressDao()
                .upsert(WatchProgressEntity(3, 21, .5f, 600_000, 3_600_000, 2, false))
            val resumed = history.getJumpBackInItems().first().single()
            assertEquals(3_600_000L, resumed.durationMs)
            assertEquals(600_000L, resumed.positionMs)

            assertEquals(listOf(1L), repo.browse(LibraryQuery(text = "%_")).first().map { it.id })
            assertEquals(
                listOf(1L),
                repo.browse(LibraryQuery(type = MediaFilter.ANIME)).first().map { it.id },
            )
            assertEquals(
                listOf(3L),
                repo.browse(LibraryQuery(watch = WatchFilter.IN_PROGRESS)).first().map { it.id },
            )
            dao.toggleWatchlist(1)
            assertEquals(
                listOf(1L),
                repo.browse(LibraryQuery(watchlistOnly = true)).first().map { it.id },
            )
            assertEquals(
                1L,
                repo.browse(LibraryQuery(sort = LibrarySort.RATING)).first().first().id,
            )
            // Movies and Anime are overlapping views, with stable movie playback IDs.
            assertEquals(
                listOf(1L, 2L),
                repo.browse(LibraryQuery(type = MediaFilter.MOVIES)).first().map { it.id },
            )
            dao.insert(item(4, "Pending", "ANIME").copy(isUnidentified = true))
            assertEquals(listOf(4L), repo.search("Pending", MediaFilter.ALL).first().map { it.id })
            assertTrue(repo.getById(4)!!.isSingleFile)
            val playback =
                com.singam.lionlibrary.data.repository.RoomPlaybackRepository(
                    dao,
                    db.episodeDao(),
                    db.watchProgressDao(),
                    db,
                )
            assertEquals("content://fixture/4", playback.target(MediaType.MOVIE, 4)!!.filePath)
            db.watchProgressDao().upsert(WatchProgressEntity(4, 0, 1f, 100, 100, 2, true))
            assertEquals(
                listOf(4L),
                repo.browse(LibraryQuery(watch = WatchFilter.WATCHED)).first().map { it.id },
            )
            dao.insert(dao.getById(3)!!.copy(title = "Corrected show"))
            assertNotNull(db.episodeDao().getById(20))
            assertEquals(1, db.seasonDao().getSeasonsForShow(3).first().size)
            assertNotNull(db.watchProgressDao().getProgress(3, 20))
        } finally {
            db.close()
        }
    }
}
