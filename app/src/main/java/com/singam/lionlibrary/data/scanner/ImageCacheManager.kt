package com.singam.lionlibrary.data.scanner

import android.content.Context
import com.singam.lionlibrary.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

// Local image cache.
class ImageCacheManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val cacheDir: File by lazy {
        File(context.filesDir, Constants.IMAGE_CACHE_DIR).also { it.mkdirs() }
    }

    // Cache poster (w500).
    suspend fun cachePoster(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_W500}$remotePath"
        return downloadAndCache(url, filename)
    }

    // Cache backdrop (w1280).
    suspend fun cacheBackdrop(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_W1280}$remotePath"
        return downloadAndCache(url, filename)
    }

    // Cache episode still.
    suspend fun cacheEpisodeStill(remotePath: String, filename: String): String? {
        val w780Url = "${Constants.TMDB_IMAGE_BASE_URL_W780}$remotePath"
        val path = downloadAndCache(w780Url, filename)
        if (path != null) return path
        
        val originalUrl = "${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$remotePath"
        return downloadAndCache(originalUrl, filename)
    }

    // Cache logo.
    suspend fun cacheLogo(remotePath: String, filename: String): String? {
        val url = "${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$remotePath"
        return downloadAndCache(url, filename)
    }

    // Download or return cached path.
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

