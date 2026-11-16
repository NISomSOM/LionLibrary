package com.singam.lionlibrary.presentation.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Window snapshots need PixelCopy on a window (Android 8.0+). */
internal val windowSnapshotSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

/**
 * Copies what [view]'s window is showing right now into a bitmap at [scale] and hands it to
 * [onResult] on the main thread. Used as a still picture of the page a Details zoom grows over, so
 * that page does not have to be kept alive by navigation. Does nothing when a copy is not possible.
 */
internal fun captureWindow(view: View, scale: Float, onResult: (ImageBitmap) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val window = view.context.findActivity()?.window ?: return
    val width = (view.rootView.width * scale).toInt()
    val height = (view.rootView.height * scale).toInt()
    if (width <= 0 || height <= 0) return
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            window,
            bitmap,
            { result -> if (result == PixelCopy.SUCCESS) onResult(bitmap.asImageBitmap()) },
            Handler(Looper.getMainLooper()),
        )
    } catch (_: IllegalArgumentException) {
        // The window has no surface yet or any more: the zoom simply runs without the picture.
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
