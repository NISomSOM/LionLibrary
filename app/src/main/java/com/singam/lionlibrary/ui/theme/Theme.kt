package com.singam.lionlibrary.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LionLibraryColorScheme =
    darkColorScheme(
        primary = OrangeAccent,
        onPrimary = DarkBackground,
        primaryContainer = Color(0xFF36221B),
        onPrimaryContainer = OrangeAccent,
        secondary = OrangeAccent,
        onSecondary = DarkBackground,
        secondaryContainer = DarkSurfaceVariant,
        onSecondaryContainer = TextPrimary,
        background = DarkBackground,
        onBackground = TextPrimary,
        surface = DarkSurface,
        onSurface = TextSecondary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = TextTertiary,
        outline = DarkSurfaceVariant,
        error = ErrorRed,
        onError = Color.White,
    )

@Composable
fun LionLibraryTheme(artworkRevision: Long = 0L, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = DarkBackground.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        com.singam.lionlibrary.presentation.components.LocalArtworkRevision provides artworkRevision
    ) {
        MaterialTheme(
            colorScheme = LionLibraryColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
