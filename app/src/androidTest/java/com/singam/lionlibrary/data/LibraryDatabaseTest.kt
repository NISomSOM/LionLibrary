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
                    db.mediaDao(),
                    db,
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

    @Test
    fun jumpBackInNextUpSkipsCombinedRangeAndMenuWritesNeedAnExistingTarget() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    MediaDatabase::class.java,
                )
                .build()
        try {
            db.mediaDao()
                .insert(
                    MediaEntity(
                        id = 3,
                        tmdbId = null,
                        title = "Show",
                        originalTitle = null,
                        overview = null,
                        posterPath = null,
                        backdropPath = null,
                        genres = null,
                        rating = null,
                        year = null,
                        mediaType = "TV_SHOW",
                        matchConfidence = 1f,
                        isUnidentified = false,
                        lastUpdated = 1,
                    )
                )
            fun episode(id: Long, number: Int, end: Int? = null) =
                EpisodeEntity(
                    id = id,
                    showId = 3,
                    seasonNumber = 1,
                    episodeNumber = number,
                    episodeEndNumber = end,
                    title = null,
                    overview = null,
                    runtime = null,
                    airDate = null,
                    thumbnailPath = null,
                    filePath = "content://fixture/ep$id",
                )
            db.episodeDao().insert(episode(20, 1, end = 2))
            db.episodeDao().insert(episode(22, 3))
            val history =
                com.singam.lionlibrary.data.repository.RoomWatchProgressRepository(
                    db.watchProgressDao(),
                    db.episodeDao(),
                    db.mediaDao(),
                    db,
                )
            history.markAsWatched(3, 20)
            // E1-E2 is one combined file, so next up is E3, not a phantom E2.
            assertEquals(22L, history.getJumpBackInItems().first().single().episodeId)

            history.markAsStarted(3, 22)
            db.watchProgressDao().upsert(WatchProgressEntity(3, 22, .4f, 400, 1000, 5, false))
            history.markAsStarted(3, 22)
            assertEquals(400L, db.watchProgressDao().getProgress(3, 22)!!.lastPositionMs)

            // Writes for a missing show, or an episode of another show, are dropped.
            history.markAsStarted(99, 0)
            history.markAsWatched(99, 22)
            history.markAsUnwatched(3, 777)
            assertNull(db.watchProgressDao().getProgress(99, 0))
            assertNull(db.watchProgressDao().getProgress(99, 22))
            assertNull(db.watchProgressDao().getProgress(3, 777))
        } finally {
            db.close()
        }
    }
}
