package com.singam.lionlibrary.data.scanner

import android.content.Context
import com.singam.lionlibrary.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

// Caches TMDB images locally so we don't redownload them.
class ImageCacheManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val cacheDir: File by lazy {
        File(context.filesDir, Constants.IMAGE_CACHE_DIR).also { it.mkdirs() }
    }

    // Caches a poster (w500). Returns local path or null.
    suspend fun cachePoster(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_W500}$remotePath"
        return downloadAndCache(url, filename)
    }

    // Caches a backdrop (w1280).
    suspend fun cacheBackdrop(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_W1280}$remotePath"
        return downloadAndCache(url, filename)
    }

    // Caches an episode still (tries w780 then original).
    suspend fun cacheEpisodeStill(remotePath: String, filename: String): String? {
        val w780Url = "${Constants.TMDB_IMAGE_BASE_URL_W780}$remotePath"
        val path = downloadAndCache(w780Url, filename)
        if (path != null) return path
        
        // Fallback to original size if w780 is not available for this still
        val originalUrl = "${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$remotePath"
        return downloadAndCache(originalUrl, filename)
    }

    // Caches a logo.
    suspend fun cacheLogo(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$remotePath"
        return downloadAndCache(url, filename)
    }

    // Actual download logic. Returns existing path if already cached.
    private suspend fun downloadAndCache(url: String, filename: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, filename)
                if (file.exists()) return@withContext file.absolutePath

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                response.use { resp ->
                    if (!resp.isSuccessful) return@withContext null

                    val tempFile = File(cacheDir, "$filename.tmp")
                    try {
                        resp.body?.byteStream()?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.renameTo(file)
                        file.absolutePath
                    } catch (e: Exception) {
                        tempFile.delete()
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
}

