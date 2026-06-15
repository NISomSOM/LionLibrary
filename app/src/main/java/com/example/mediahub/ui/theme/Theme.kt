package com.example.mediahub.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LionLibraryColorScheme = darkColorScheme(
    primary = OrangeAccent,
    onPrimary = Color.White,
    primaryContainer = DarkOrangeAccent,
    onPrimaryContainer = Color.White,
    secondary = DarkSurfaceVariant,
    onSecondary = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextSecondary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextTertiary,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun LionLibraryTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = LionLibraryColorScheme,
        typography = Typography,
        content = content
    )
}