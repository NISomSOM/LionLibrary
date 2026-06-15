package com.example.mediahub.data.scanner

import android.content.Context
import com.example.mediahub.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads and caches TMDB images (posters and backdrops) to local storage.
 * Images are stored in [Context.getFilesDir]/posters/ and never re-downloaded
 * if they already exist locally.
 */
class ImageCacheManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val cacheDir: File by lazy {
        File(context.filesDir, Constants.IMAGE_CACHE_DIR).also { it.mkdirs() }
    }

    /**
     * Downloads a poster image (w500) and caches it locally.
     *
     * @param remotePath The TMDB poster path (e.g., "/abc123.jpg")
     * @param filename A unique filename to save as (e.g., "movie_12345_poster.jpg")
     * @return The absolute local file path, or null on failure.
     */
    suspend fun cachePoster(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_W500}$remotePath"
        return downloadAndCache(url, filename)
    }

    /**
     * Downloads a backdrop image (w1280) and caches it locally.
     *
     * @param remotePath The TMDB backdrop path (e.g., "/xyz789.jpg")
     * @param filename A unique filename to save as (e.g., "movie_12345_backdrop.jpg")
     * @return The absolute local file path, or null on failure.
     */
    suspend fun cacheBackdrop(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_W1280}$remotePath"
        return downloadAndCache(url, filename)
    }

    /**
     * Downloads an episode still (w780 or original) and caches it locally.
     *
     * @param remotePath The TMDB still path (e.g., "/xyz789.jpg")
     * @param filename A unique filename to save as (e.g., "episode_12345_still.jpg")
     * @return The absolute local file path, or null on failure.
     */
    suspend fun cacheEpisodeStill(remotePath: String, filename: String): String? {
        val w780Url = "${Constants.TMDB_IMAGE_BASE_URL_W780}$remotePath"
        val path = downloadAndCache(w780Url, filename)
        if (path != null) return path
        
        // Fallback to original size if w780 is not available for this still
        val originalUrl = "${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$remotePath"
        return downloadAndCache(originalUrl, filename)
    }

    /**
     * Downloads a logo (original) and caches it locally.
     *
     * @param remotePath The TMDB logo path (e.g., "/xyz789.png")
     * @param filename A unique filename to save as (e.g., "tv_12345_logo.png")
     * @return The absolute local file path, or null on failure.
     */
    suspend fun cacheLogo(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$remotePath"
        return downloadAndCache(url, filename)
    }

    /**
     * Downloads the image from [url] and saves it to [cacheDir]/[filename].
     * Returns existing file path if already cached.
     */
    private suspend fun downloadAndCache(url: String, filename: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, filename)
                if (file.exists()) return@withContext file.absolutePath

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext null
                }

                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                file.absolutePath
            } catch (e: Exception) {
                null
            }
        }
}
