/*
 * The scope locals, the shared element key, the shared poster with its animated overlay clip and
 * the bottom bar drawn in the overlay follow the Jetsnack sample (android/compose-samples,
 * JetsnackApp.kt, Snacks.kt and SnackDetail.kt), Copyright 2020 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
 * Adapted for LionLibrary.
 */
package com.singam.lionlibrary.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.ArcMode
import androidx.compose.animation.core.ExperimentalAnimationSpecApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled

/** The destination's own enter/exit scope, provided inside each nav `composable {}`. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The `SharedTransitionLayout` around the navigation host and the floating bar. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Pairs one poster card with the hero of the Details page it opened. The same title sits on several
 * shelves, so a card is identified by title and [origin] (its shelf or grid).
 */
data class PosterSharedElementKey(val mediaId: Long, val origin: String)

/**
 * What a Details page needs to share its hero with the card that opened it: the [key] and the
 * poster file, known before the page has loaded anything. [closesToCard] is false while the page is
 * leaving forward (to the player or another title) rather than going back to that card.
 */
data class PosterTransition(
    val key: PosterSharedElementKey,
    val posterPath: String,
    val closesToCard: Boolean = true,
)

/**
 * Carries the tapped card's shelf and poster to the navigation call made in the same click, so the
 * Details route can name them. Details opened any other way (hero carousel, long-press preview)
 * finds nothing here and opens with a plain fade.
 */
@Stable
class PosterTapOrigin {
    private var pending: Pair<String, String>? = null

    // Per shelf or grid: the part of the screen its cards can show in (root coordinates), and the
    // size of its cards' artwork. Plain maps: written on layout, read only while a poster flies.
    private val viewports = HashMap<String, Rect>()
    private val cardSizes = HashMap<String, Size>()

    // Where each card's artwork last sat (root coordinates), for a page shrinking back into it, and
    // the pages that will shrink rather than fly their poster when they close.
    private val cardBounds = HashMap<PosterSharedElementKey, Rect>()
    private val shrinkingPages = HashSet<PosterSharedElementKey>()

    fun onCardTapped(origin: String, posterPath: String, cardSize: Size? = null) {
        pending = origin to posterPath
        if (cardSize != null) cardSizes[origin] = cardSize
    }

    /** Shelf and poster of the card tapped in this click, once. */
    fun consume(): Pair<String, String>? = pending.also { pending = null }

    /**
     * A scrolling grid or list whose cards can sit partly under a fixed bar above it (Library's
     * search and filters): a poster flying to or from one of its cards is cut off at [bounds]' top,
     * as the card itself is.
     */
    fun onViewport(origin: String, bounds: Rect) {
        viewports[origin] = bounds
    }

    internal fun viewportOf(origin: String): Rect? = viewports[origin]

    internal fun cardSizeOf(origin: String): Size? = cardSizes[origin]

    fun onCardPlaced(key: PosterSharedElementKey, bounds: Rect) {
        cardBounds[key] = bounds
    }

    internal fun cardBoundsOf(key: PosterSharedElementKey): Rect? = cardBounds[key]

    /** Whether the Details page of [key] closes by shrinking into its card (see `pageShrink`). */
    internal fun setShrinksOnClose(key: PosterSharedElementKey, shrinks: Boolean) {
        if (shrinks) shrinkingPages += key else shrinkingPages -= key
    }

    internal fun shrinksOnClose(key: PosterSharedElementKey): Boolean = key in shrinkingPages
}

val LocalPosterTapOrigin = staticCompositionLocalOf<PosterTapOrigin?> { null }

/** The Poster animation setting: false gives plain fades and a banner hero on Details. */
val LocalPosterTransitions = staticCompositionLocalOf { true }

/** The Blurred background setting: false puts Details on solid black. */
val LocalDetailsBlur = staticCompositionLocalOf { true }

/**
 * Shared bounds for a poster card's artwork: the poster leaves the card and flies to the Details
 * hero, and comes back the same way. The card's picture is drawn underneath the hero's copy of the
 * same poster: it fades away as the poster leaves and is there at once on the way back.
 *
 * Returns an empty modifier outside a shared transition layout, for a card with no [origin] and
 * with system animations off.
 */
@Composable
fun posterCardSharedBounds(mediaId: Long, origin: String?): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    if (origin == null || sharedTransitionScope == null || animatedVisibilityScope == null) {
        return Modifier
    }
    if (!rememberSystemMotionEnabled()) return Modifier
    val tapOrigin = LocalPosterTapOrigin.current
    val density = LocalDensity.current
    val heroHeightPx = with(density) { posterHeroHeight().toPx() }
    with(sharedTransitionScope) {
        // Fixed corners: an animated shape would recompose every card on every frame.
        val cornerPx = with(density) { LionLayout.CardRadius.toPx() }
        val clip =
            remember(tapOrigin, origin, heroHeightPx) {
                PosterOverlayClip({ cornerPx }, tapOrigin, origin, heroHeightPx)
            }
        return Modifier.sharedBounds(
            rememberSharedContentState(PosterSharedElementKey(mediaId, origin)),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            // The hero's copy of the poster covers this one from the first frame. Left in place,
            // it would show through the hero's bottom fade until the spring had fully settled.
            exit = fadeOut(tween(CARD_COPY_FADE_MS, CARD_COPY_HOLD_MS, easing = LinearEasing)),
            boundsTransform = PosterBoundsTransform,
            clipInOverlayDuringTransition = clip,
        )
    }
}

/**
 * Shared bounds for the Details hero poster, the other end of [posterCardSharedBounds]. Its corners
 * are rounded like the card while it is small and square once it has arrived.
 *
 * Returns null (plain navigation fades) without a [key], outside a shared transition layout and
 * with system animations off.
 */
@Composable
fun detailsPosterSharedBounds(key: PosterSharedElementKey?): Modifier? {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    if (key == null || sharedTransitionScope == null || animatedVisibilityScope == null) {
        return null
    }
    if (!rememberSystemMotionEnabled()) return null
    val tapOrigin = LocalPosterTapOrigin.current
    val density = LocalDensity.current
    val heroHeightPx = with(density) { posterHeroHeight().toPx() }
    with(sharedTransitionScope) {
        val corner =
            animatedVisibilityScope.transition.animateDp(label = "detailsPosterCorner") { state ->
                when (state) {
                    EnterExitState.PreEnter -> LionLayout.CardRadius
                    EnterExitState.Visible -> 0.dp
                    EnterExitState.PostExit -> LionLayout.CardRadius
                }
            }
        // Read while drawing, so the corner animation never recomposes the hero.
        val clip =
            remember(tapOrigin, key.origin, heroHeightPx, density) {
                PosterOverlayClip(
                    { with(density) { corner.value.toPx() } },
                    tapOrigin,
                    key.origin,
                    heroHeightPx,
                )
            }
        return Modifier.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = PosterBoundsTransform,
            zIndexInOverlay = POSTER_OVERLAY_Z,
            clipInOverlayDuringTransition = clip,
        )
    }
}

/**
 * The poster's flight between its card and the hero, timed by [PosterTiming]. It travels on an arc
 * rather than a straight line: it sweeps sideways as it leaves the card and rises into place, so a
 * card on the right swings in from the right, one on the left barely curves, and the way back
 * retraces the same path. Fixed durations (not a spring), so a back gesture can scrub it.
 *
 * Going back, the poster holds briefly while the page body fades away under it, then flies home
 * straight from wherever it is: from a page scrolled down to its episodes it comes in from above
 * the screen.
 */
@OptIn(ExperimentalAnimationSpecApi::class)
private val PosterBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    val motion = PosterTiming
    val opening = targetBounds.width > initialBounds.width
    keyframes {
        if (opening) {
            durationMillis = motion.openMs
            initialBounds at 0 using ArcMode.ArcBelow using LionMotion.Easing
            targetBounds at motion.openMs
        } else {
            durationMillis = motion.closeHoldMs + motion.closeMs
            initialBounds at 0 using LinearEasing
            initialBounds at motion.closeHoldMs using ArcMode.ArcBelow using FastOutSlowInEasing
            targetBounds at motion.closeHoldMs + motion.closeMs
        }
    }
}

/**
 * Clip for a flying poster: its rounded rectangle, cut at the top of its card's viewport while the
 * poster is near card size, so it slides in under a fixed bar exactly as its card sits. The cut
 * lifts as the poster grows towards the hero ([heroHeightPx] tall). Without a viewport for [origin]
 * it is the rounded rectangle alone.
 */
private class PosterOverlayClip(
    private val cornerPx: () -> Float,
    private val tapOrigin: PosterTapOrigin?,
    private val origin: String,
    private val heroHeightPx: Float,
) : SharedTransitionScope.OverlayClip {
    private val shape = Path()
    private val cut = Path()
    private val result = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        shape.reset()
        shape.addRoundRect(RoundRect(bounds, CornerRadius(cornerPx())))
        val viewport = tapOrigin?.viewportOf(origin) ?: return shape
        val card = tapOrigin.cardSizeOf(origin) ?: return shape
        // 0 at hero size, 1 from a little above card size: the poster is already fully under the
        // bar for the last frames of its flight, not just on the very last one.
        val span = (heroHeightPx - card.height * FULL_CUT_SCALE).coerceAtLeast(1f)
        val nearCard = ((heroHeightPx - bounds.height) / span).coerceIn(0f, 1f)
        val cutTop = viewport.top * nearCard
        if (cutTop <= bounds.top) return shape
        cut.reset()
        cut.addRect(Rect(bounds.left, cutTop, bounds.right, bounds.bottom))
        result.reset()
        result.op(shape, cut, PathOperation.Intersect)
        return result
    }
}

/**
 * Height of the portrait Details hero: a full-width 2:3 poster on phones, capped on large screens.
 */
@Composable
internal fun posterHeroHeight(): Dp =
    (LocalConfiguration.current.screenWidthDp.dp * POSTER_HERO_RATIO).coerceIn(420.dp, 760.dp)

private const val POSTER_HERO_RATIO = 1.5f

/** A flying poster is cut at its viewport in full once it is within this multiple of card size. */
private const val FULL_CUT_SCALE = 1.6f

/** How quickly the card's own poster fades under the hero's copy as the poster leaves. */
private const val CARD_COPY_FADE_MS = 80

/** It stays solid this long first, covering the frames before the hero's copy has its image. */
private const val CARD_COPY_HOLD_MS = 60

/** Overlay order: the card's poster (0) below the hero's copy, the floating bar above both. */
private const val POSTER_OVERLAY_Z = 1f
internal const val NAV_BAR_OVERLAY_Z = 2f
