package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.graphics.BitmapFactory
import com.singam.lionlibrary.domain.repository.ArtworkRepository
import com.singam.lionlibrary.util.Constants
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

class ImageCacheManager(private val context: Context, private val client: OkHttpClient) :
    ArtworkRepository {
    private val mutableRevision = MutableStateFlow(0L)
    override val revision = mutableRevision.asStateFlow()

    fun isUsable(path: String?): Boolean {
        if (path == null) return false
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        bounds.inJustDecodeBounds = false
        bounds.inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 256).coerceAtLeast(1)
        val bitmap = BitmapFactory.decodeFile(path, bounds) ?: return false
        bitmap.recycle()
        return true
    }

    private val directory by lazy {
        File(context.filesDir, Constants.IMAGE_CACHE_DIR).also { it.mkdirs() }
    }
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun cachePoster(path: String, name: String) =
        download("${Constants.TMDB_IMAGE_BASE_URL_W500}$path", name)

    suspend fun cacheBackdrop(path: String, name: String) =
        download("${Constants.TMDB_IMAGE_BASE_URL_W1280}$path", name)

    suspend fun cacheLogo(path: String, name: String) =
        download("${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$path", name)

    suspend fun cacheEpisodeStill(path: String, name: String) =
        download("${Constants.TMDB_IMAGE_BASE_URL_W780}$path", name)
            ?: download("${Constants.TMDB_IMAGE_BASE_URL_ORIGINAL}$path", name)

    private suspend fun download(url: String, name: String): String? =
        withContext(Dispatchers.IO) {
            locks
                .getOrPut(name) { Mutex() }
                .withLock {
                    require(File(name).name == name) { "Artwork name must be a basename" }
                    val destination = File(directory, name)
                    if (isUsable(destination.absolutePath)) return@withLock destination.absolutePath
                    var temporary: File? = null
                    try {
                        client.newCall(Request.Builder().url(url).build()).execute().use { response
                            ->
                            if (!response.isSuccessful) return@withLock null
                            val body = response.body ?: return@withLock null
                            val staging = File.createTempFile("art_", ".tmp", directory)
                            temporary = staging
                            body.byteStream().use { input ->
                                staging.outputStream().use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            if (!isUsable(staging.absolutePath)) return@withLock null
                            if (staging.renameTo(destination)) {
                                mutableRevision.update { it + 1 }
                                destination.absolutePath
                            } else null
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        null
                    } catch (_: SecurityException) {
                        null
                    } finally {
                        temporary?.delete()
                    }
                }
        }

    /** Grace period prevents deleting newly downloaded art before its transaction commits. */
    suspend fun pruneOrphans(referenced: Set<String>, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            directory.listFiles()?.forEach { file ->
                currentCoroutineContext().ensureActive()
                if (
                    file.isFile &&
                        file.absolutePath !in referenced &&
                        now - file.lastModified() > 48 * 60 * 60 * 1000L
                )
                    file.delete()
            }
        }
}
