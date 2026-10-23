package com.singam.lionlibrary.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/** Shared rhythm for browsing surfaces; player controls and episode rows keep their own layout. */
internal object LionLayout {
    val Gutter = 16.dp
    val DetailGutter = 18.dp
    val SectionGap = 24.dp
    val ItemGap = 12.dp
    val CaptionGap = 6.dp
    val CardRadius = 12.dp
    val PosterWidth = 126.dp
    val BookmarkGlyph = 16.dp
    val BookmarkBadge = 24.dp
}

internal object LionMotion {
    const val Fast = 150
    const val Standard = 220
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
