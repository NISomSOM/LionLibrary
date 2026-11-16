package com.singam.lionlibrary.presentation.details

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.singam.lionlibrary.ui.theme.LionMotion
import java.io.File

/** Darkening over the ambient image; the hero gradient ends in the same tint so they meet. */
internal const val AMBIENT_TINT_ALPHA = .72f

/** Long-side pixels decoded for the ambient copy: blurred anyway, so tiny and cheap to cache. */
private const val AMBIENT_DECODE_PX = 96

/**
 * Page background for Details: a small, heavily blurred and darkened copy of the title's local
 * backdrop that crossfades in. Blur needs Android 12; below that, on low-RAM devices, without a
 * local image or when it fails to load, the flat background shows instead. Never uses a URL.
 */
@Composable
internal fun DetailsAmbientBackdrop(path: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val supported = remember(context) { ambientSupported(context) }
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(background)) {
        if (!supported || path == null) return@Box
        var loaded by remember(path) { mutableStateOf(false) }
        val alpha by
            animateFloatAsState(
                if (loaded) 1f else 0f,
                tween(LionMotion.Slow, easing = LionMotion.EnterEasing),
                label = "ambientFade",
            )
        val request =
            remember(path, context) {
                val file = File(path)
                ImageRequest.Builder(context)
                    .data(file)
                    .size(AMBIENT_DECODE_PX)
                    .memoryCacheKey("ambient:$path:${file.lastModified()}:${file.length()}")
                    .build()
            }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = { loaded = false },
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.blur(40.dp),
        )
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(background.copy(alpha = AMBIENT_TINT_ALPHA))
        )
    }
}

private fun ambientSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
    return !activityManager.isLowRamDevice
}
