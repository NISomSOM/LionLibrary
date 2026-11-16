package com.singam.lionlibrary.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One token set for spacing, shape, opacity, hairlines and motion. Browsing surfaces share this
 * rhythm; values match what the screens used before tokens existed unless a change is recorded in
 * DESIGN.md (secondary text colour, hairline colour, card edge).
 */
internal object LionLayout {
    // Spacing scale.
    val SpaceXxs = 2.dp
    val SpaceXs = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    // Named rhythm (kept from the first token pass).
    val Gutter = 16.dp
    val DetailGutter = 18.dp
    val SectionGap = 24.dp
    val ItemGap = 12.dp
    val CaptionGap = 6.dp
    val PosterWidth = 126.dp
    val BookmarkGlyph = 16.dp
    val BookmarkBadge = 24.dp

    // Shape.
    val RadiusXs = 4.dp
    val RadiusS = 8.dp
    val CardRadius = 12.dp
    val RadiusL = 18.dp
    val RadiusSheet = 28.dp

    /** Width of every hairline border and card edge. */
    val Hairline = 1.dp
}

/** Opacity levels, so scrims and disabled/overlay states use a small, consistent set. */
internal object LionAlpha {
    const val Faint = .06f
    const val Subtle = .14f
    const val Disabled = .35f
    const val Scrim = .4f
    const val ScrimStrong = .5f
    const val Overlay = .7f
    const val OverlayStrong = .85f
}

internal object LionMotion {
    /** Feedback: press, toggles, small fades. */
    const val Fast = 150

    /** Standard transitions. */
    const val Standard = 220

    /** Larger movements (sheets, toolbar slide). */
    const val Medium = 300

    /** Emphasised entrances (shared element, preview lift). */
    const val Emphasized = 400

    /** Slow ambient changes (backdrop crossfade, shimmer half-cycle). */
    const val Slow = 700

    /** Standard easing (unchanged). */
    val Easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Things arriving: decelerate into place. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things leaving: accelerate away. */
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Named colour roles beyond the Material scheme. Brand rating marks keep their own colours in
 * RatingMarks.kt. Read through [LionTheme.colors].
 */
@Immutable
data class LionColors(
    val textPrimary: Color = TextPrimary,
    /** Between primary and muted: metadata, episode synopses, unselected tabs. */
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextTertiary,
    /** Text and icons drawn directly on artwork or video. */
    val onMedia: Color = Color.White,
    /** Hairline borders; lighter than surfaceVariant so card edges read on dark surfaces. */
    val hairline: Color = Hairline,
    /** Episode list dividers. */
    val divider: Color = Color(0xFF333333),
    /** Bottom sheets (episode options). */
    val sheet: Color = Color(0xFF1E1E1E),
    /** Fixed pane behind the two-pane Details layout and its season tabs. */
    val pane: Color = Color(0xFF141414),
    val snackbar: Color = Color(0xFF2D2D2D),
    val ratingStar: Color = Color(0xFFE5B13A),
    /** Base for scrims over artwork and video. */
    val scrim: Color = Color.Black,
    val playerBackground: Color = Color.Black,
    /** Top-lit card edge, fading to transparent toward the bottom. */
    val cardEdgeTop: Color = Color.White.copy(alpha = LionAlpha.Subtle),
    /** Faint sheen across the top of posters. */
    val cardSheen: Color = Color.White.copy(alpha = LionAlpha.Faint),
)

internal val LocalLionColors = staticCompositionLocalOf { LionColors() }

object LionTheme {
    val colors: LionColors
        @Composable @ReadOnlyComposable get() = LocalLionColors.current
}
