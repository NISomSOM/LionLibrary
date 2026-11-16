package com.singam.lionlibrary.presentation.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled
import com.singam.lionlibrary.util.ImageFocus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Focus per file signature; a few hundred floats at most, process lifetime, no disk/Room. */
private val focusCache = LruCache<String, Float>(256)

/** Longest side decoded for the estimate. */
private const val FOCUS_SAMPLE_PX = 64

/**
 * Crop alignment for a tall hero showing a wide local backdrop: biased toward the estimated subject
 * instead of the exact centre. Starts centred, computes off the main thread, caches in memory, and
 * eases to the new bias (snaps when system animations are off). Any failure keeps the centre.
 */
@Composable
fun rememberBackdropAlignment(path: String?): Alignment {
    val motion = rememberSystemMotionEnabled()
    val key = remember(path) { path?.let { signature(it) } }
    var focus by remember(key) { mutableFloatStateOf(key?.let { focusCache.get(it) } ?: .5f) }
    LaunchedEffect(key) {
        if (key == null || focusCache.get(key) != null) return@LaunchedEffect
        val computed =
            withContext(Dispatchers.Default) { estimate(path!!) } ?: return@LaunchedEffect
        focusCache.put(key, computed)
        focus = computed
    }
    val bias by
        animateFloatAsState(
            focus * 2f - 1f,
            if (motion) tween(LionMotion.Slow, easing = LionMotion.Easing) else snap(),
            label = "backdropFocus",
        )
    return BiasAlignment(bias, 0f)
}

private fun signature(path: String): String {
    val file = File(path)
    return "$path:${file.lastModified()}:${file.length()}"
}

private fun estimate(path: String): Float? =
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
        else {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize =
                        (maxOf(bounds.outWidth, bounds.outHeight) / FOCUS_SAMPLE_PX).coerceAtLeast(
                            1
                        )
                }
            BitmapFactory.decodeFile(path, options)?.let { bitmap ->
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    ImageFocus.horizontalFocus(pixels, bitmap.width, bitmap.height)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
