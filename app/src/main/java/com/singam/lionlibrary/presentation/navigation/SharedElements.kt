package com.singam.lionlibrary.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled

/** Provided by LionLibraryNavHost around its NavHost; null in previews and tests. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The destination's own enter/exit scope, provided inside each nav `composable {}`. */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val PosterBoundsTransform = BoundsTransform { _, _ ->
    tween(LionMotion.Emphasized, easing = LionMotion.Easing)
}

/**
 * Shares a title's artwork bounds between a poster card and the Details hero, so opening a title
 * grows the poster into the hero (and Back or predictive back shrinks it back). The two images
 * crossfade inside the moving bounds. Without a navigation scope (previews, tests) or with system
 * animations off, this is a no-op and the destination fade applies as before.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTitleArtwork(mediaId: Long): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    if (!rememberSystemMotionEnabled()) return this
    return with(transition) {
        this@sharedTitleArtwork.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "title-artwork-$mediaId"),
            animatedVisibilityScope = visibility,
            boundsTransform = PosterBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
