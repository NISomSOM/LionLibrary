package com.singam.lionlibrary.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.local.db.entity.*
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.*
import com.singam.lionlibrary.data.repository.TmdbMetadataRepository
import com.singam.lionlibrary.data.scanner.ImageCacheManager
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import com.singam.lionlibrary.presentation.SettingsFake
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class MetadataRepairIntegrationTest {
    @Test
    fun groupedRepairPreservesExistingEpisodesAndLeavesAmbiguousFilesForAssignment(): Unit =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).build()
            fun row(id: Long, type: String, tmdb: Int? = null) =
                MediaEntity(
                    id = id,
                    tmdbId = tmdb,
                    title = "Show",
                    mediaType = type,
                    originalTitle = null,
                    overview = null,
                    posterPath = null,
                    backdropPath = null,
                    genres = null,
                    rating = null,
                    year = null,
                    matchConfidence = 1f,
                    isUnidentified = false,
                    lastUpdated = 1,
                )
            try {
                db.mediaDao().insert(row(50, "MOVIE", 123))
                db.mediaDao().insert(row(99, "TV_SHOW", 123))
                db.episodeDao()
                    .insert(
                        EpisodeEntity(
                            id = 20,
                            showId = 99,
                            seasonNumber = 1,
                            episodeNumber = 9,
                            filePath = "content://fixture/old",
                            title = "Existing",
                            overview = null,
                            runtime = null,
                            airDate = null,
                            thumbnailPath = null,
                        )
                    )
                db.watchProgressDao().upsert(WatchProgressEntity(99, 20, 1f, 0, 1000, 1, true))
                for (id in 1L..3L) db.mediaDao()
                    .insert(
                        row(id, "TV_SHOW")
                            .copy(
                                isUnidentified = true,
                                sourceShowTitle = "Show",
                                sourceSeason = 1,
                                sourceEpisode = if (id == 2L) 2 else null,
                                filePath = "content://fixture/$id",
                                externalSubtitlePath =
                                    if (id == 1L) "content://fixture/sub" else null,
                                preferredEngine = "LIBVLC",
                                isWatchlisted = id == 2L,
                            )
                    )
                db.watchProgressDao().upsert(WatchProgressEntity(1, 0, .5f, 500, 1000, 2, false))
                val settings =
                    object : SettingsRepository by SettingsFake() {
                        override val tmdbApiKey = flowOf("fixture")
                    }
                val repo =
                    TmdbMetadataRepository(
                        context,
                        RepairApiFake(),
                        db,
                        settings,
                        ImageCacheManager(context, OkHttpClient()),
                    )
                val result =
                    repo.identify(
                        1,
                        MetadataCandidate(123, "Show", null, MediaType.TV_SHOW),
                        EpisodeAssignment(1, 1),
                    )
                assertTrue(result is OperationResult.Success)
                assertNull(db.mediaDao().getById(1))
                assertNull(db.mediaDao().getById(2))
                assertNotNull(db.mediaDao().getById(3))
                assertNotNull(db.mediaDao().getById(50))
                assertTrue(db.mediaDao().getById(99)!!.isWatchlisted)
                val repaired = db.episodeDao().getByFilePath("content://fixture/1")!!
                assertEquals(99L, repaired.showId)
                assertEquals("LIBVLC", repaired.preferredEngine)
                assertEquals("content://fixture/sub", repaired.externalSubtitlePath)
                assertEquals(
                    500L,
                    db.watchProgressDao().getProgress(99, repaired.id)!!.lastPositionMs,
                )
                assertTrue(db.watchProgressDao().getProgress(99, 20)!!.completed)
                assertEquals(
                    "Existing",
                    db.episodeDao().getByFilePath("content://fixture/old")!!.title,
                )
            } finally {
                db.close()
            }
        }
}

private class RepairApiFake : TmdbApiService {
    override suspend fun getTvDetails(id: Int, key: String) = TvDetailsDto(id, "Show")

    override suspend fun getTvImages(id: Int, key: String) = TvImagesResponseDto(id)

    override suspend fun getSeasonDetails(id: Int, season: Int, key: String) =
        SeasonDetailsDto(id, season)

    override suspend fun validateKey(key: String) = error("unused")

    override suspend fun searchMovie(key: String, query: String, year: Int?) = error("unused")

    override suspend fun searchTv(key: String, query: String) = error("unused")

    override suspend fun getMovieDetails(id: Int, key: String) = error("unused")

    override suspend fun getMovieImages(id: Int, key: String) = error("unused")

    override suspend fun getTvAlternativeTitles(id: Int, key: String) = error("unused")

    override suspend fun getMovieAlternativeTitles(id: Int, key: String) = error("unused")

    override suspend fun getMovieExternalIds(id: Int, key: String) = error("unused")

    override suspend fun getTvExternalIds(id: Int, key: String) = error("unused")
}
