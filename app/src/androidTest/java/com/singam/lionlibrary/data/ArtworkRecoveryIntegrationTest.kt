package com.singam.lionlibrary.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.local.db.entity.*
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.*
import com.singam.lionlibrary.data.repository.TmdbMetadataRepository
import com.singam.lionlibrary.data.scanner.ImageCacheManager
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.presentation.SettingsFake
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException

class ArtworkRecoveryIntegrationTest {
    // Real Room and private files, intercepted HTTP. Fixtures never touch the customer's cache.
    // Break vectors: non-null missing file, corrupt file, valid cached file, remote 404,
    // one failed title before a recoverable title, and history/engine/watchlist preservation.
    @Test
    fun missingAndCorruptImagesRecoverWithoutReidentifyingOrChangingHistory() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val folder =
            File.createTempFile("artwork-test-", "", target.cacheDir).apply {
                delete()
                mkdir()
            }
        val context =
            object : ContextWrapper(target) {
                override fun getFilesDir(): File = folder
            }
        val db = Room.inMemoryDatabaseBuilder(target, MediaDatabase::class.java).build()
        val bytes =
            ByteArrayOutputStream()
                .apply {
                    val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.GREEN)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
                    bitmap.recycle()
                }
                .toByteArray()
        var downloads = 0
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    downloads++
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bytes.toResponseBody("image/png".toMediaType()))
                        .build()
                }
                .build()
        val cache = ImageCacheManager(context, client)
        val settings =
            object : SettingsRepository by SettingsFake() {
                override val tmdbApiKey = flowOf("fixture")
            }
        fun row(id: Long, name: String) =
            MediaEntity(
                id = id,
                tmdbId = id.toInt(),
                title = name,
                originalTitle = null,
                overview = "Keep overview",
                posterPath = File(folder, "absent$id.jpg").path,
                backdropPath = null,
                genres = "Animation,Anime",
                rating = 8f,
                year = 2023,
                mediaType = "MOVIE",
                matchConfidence = 1f,
                isUnidentified = false,
                lastUpdated = 1,
                filePath = "content://fixture/Movies/$id.mkv",
                preferredEngine = "LIBVLC",
                isWatchlisted = true,
                externalSubtitlePath = "content://fixture/subtitle",
            )
        try {
            db.mediaDao().insert(row(1, "A unavailable"))
            db.mediaDao().insert(row(2, "B recoverable"))
            val corrupt = File(folder, "corrupt.png").apply { writeText("not an image") }
            db.mediaDao().update(db.mediaDao().getById(2)!!.copy(logoPath = corrupt.path))
            db.watchProgressDao().upsert(WatchProgressEntity(2, 0, .5f, 500, 1000, 1, false))
            val repo = TmdbMetadataRepository(context, ArtworkApiFake(), db, settings, cache)
            val result = repo.refreshArtwork()
            assertTrue(
                result is OperationResult.Failure
            ) // The remote 404 is reported after other titles recover.
            val media = db.mediaDao().getById(2)!!
            assertTrue(cache.isUsable(media.posterPath))
            assertTrue(cache.isUsable(media.backdropPath))
            assertTrue(cache.isUsable(media.logoPath))
            assertEquals("Keep overview", media.overview)
            assertEquals("MOVIE", media.mediaType)
            assertEquals("LIBVLC", media.preferredEngine)
            assertTrue(media.isWatchlisted)
            assertEquals("content://fixture/subtitle", media.externalSubtitlePath)
            assertEquals(500L, db.watchProgressDao().getProgress(2, 0)!!.lastPositionMs)
            assertTrue(cache.revision.value > 0)
            val firstDownloads = downloads
            repo.refreshArtwork()
            assertEquals(firstDownloads, downloads) // Good images remain local.
        } finally {
            db.close()
            check(folder.canonicalFile.parentFile == target.cacheDir.canonicalFile)
            folder.deleteRecursively()
        }
    }
}

private class ArtworkApiFake : TmdbApiService {
    override suspend fun getMovieDetails(id: Int, key: String): MovieDetailsDto {
        if (id == 1)
            throw HttpException(retrofit2.Response.error<Any>(404, "missing".toResponseBody()))
        return MovieDetailsDto(
            id,
            "Different remote title",
            posterPath = "/poster.png",
            backdropPath = "/backdrop.png",
        )
    }

    override suspend fun getMovieImages(id: Int, key: String) =
        TvImagesResponseDto(
            id,
            logos = listOf(LogoDto(filePath = "/logo.png", iso6391 = "en")),
        )

    override suspend fun validateKey(key: String) = error("unused")

    override suspend fun searchMovie(key: String, query: String, year: Int?) =
        error("must not reidentify")

    override suspend fun searchTv(key: String, query: String) = error("must not reidentify")

    override suspend fun getTvDetails(id: Int, key: String) = error("unused")

    override suspend fun getSeasonDetails(id: Int, season: Int, key: String) = error("unused")

    override suspend fun getTvImages(id: Int, key: String) = error("unused")

    override suspend fun getTvAlternativeTitles(id: Int, key: String) = error("unused")

    override suspend fun getMovieAlternativeTitles(id: Int, key: String) = error("unused")

    override suspend fun getMovieExternalIds(id: Int, key: String) = error("unused")

    override suspend fun getTvExternalIds(id: Int, key: String) = error("unused")
}
