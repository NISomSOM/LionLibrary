package com.singam.lionlibrary.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Scrollable content can pass behind navigation and expose its last item above it. */
internal val LocalFloatingNavigationInset = compositionLocalOf { 0.dp }
