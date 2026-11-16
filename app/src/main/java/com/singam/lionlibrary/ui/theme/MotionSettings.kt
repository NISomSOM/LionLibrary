package com.singam.lionlibrary.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * False when the system "Remove animations" / animator duration scale is 0. Read again on each
 * lifecycle change (returning from system settings resumes the screen), not per recomposition.
 * Decorative motion (parallax, stretch, shared elements, shimmer) checks this; essential state
 * changes still happen, only without movement.
 */
@Composable
fun rememberSystemMotionEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    return remember(context, state) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}
