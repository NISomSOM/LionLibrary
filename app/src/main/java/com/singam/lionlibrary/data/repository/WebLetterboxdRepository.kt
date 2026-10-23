package com.singam.lionlibrary.data.repository

import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.mapper.toMediaItem
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.repository.LetterboxdRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Letterboxd has no public API; the rating is read from the film page's meta tags.
class WebLetterboxdRepository(okHttpClient: OkHttpClient, private val mediaDao: MediaDao) :
    LetterboxdRepository {

    private val client = okHttpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()

    override suspend fun refreshRating(mediaId: Long): MediaItem? =
        withContext(Dispatchers.IO) {
            try {
                val entity = mediaDao.getById(mediaId) ?: return@withContext null
                val tmdbId = entity.tmdbId ?: return@withContext null
                if (entity.isUnidentified || entity.mediaType != MediaType.MOVIE.name)
                    return@withContext null

                val updatedAt = entity.letterboxdUpdatedAt
                if (updatedAt != null && System.currentTimeMillis() - updatedAt < CACHE_TTL_MS) {
                    return@withContext entity.toMediaItem()
                }

                val request =
                    Request.Builder().url(filmUrl(tmdbId)).header("User-Agent", USER_AGENT).build()

                val rating: Float? =
                    client.newCall(request).execute().use { response ->
                        when {
                            response.isSuccessful -> parseLetterboxdRating(response.body.string())
                            // Film not on Letterboxd: cache the miss.
                            response.code == 404 -> null
                            // Blocked or server error: keep old values, retry next time.
                            else -> return@withContext entity.toMediaItem()
                        }
                    }

                mediaDao.updateLetterboxdRating(mediaId, rating, System.currentTimeMillis())
                entity.toMediaItem().copy(letterboxdRating = rating)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                // Offline or unexpected markup: fall back to whatever is cached.
                try {
                    mediaDao.getById(mediaId)?.toMediaItem()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }

    // Letterboxd redirects /tmdb/{id}/ to the film's page.
    override fun filmUrl(tmdbId: Int): String = "https://letterboxd.com/tmdb/$tmdbId/"

    companion object {
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

private val twitterRatingPattern =
    Regex(
        """<meta\s+name="twitter:data2"\s+content="(\d+(?:\.\d+)?)\s+out of 5"""",
        RegexOption.IGNORE_CASE,
    )
private val jsonLdRatingPattern = Regex(""""ratingValue"\s*:\s*"?(\d+(?:\.\d+)?)""")

fun parseLetterboxdRating(html: String): Float? {
    val raw =
        twitterRatingPattern.find(html)?.groupValues?.get(1)
            ?: jsonLdRatingPattern.find(html)?.groupValues?.get(1)
    return raw?.toFloatOrNull()?.takeIf { it in 0f..5f }
}
