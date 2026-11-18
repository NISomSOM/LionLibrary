package com.singam.lionlibrary.presentation.navigation

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.presentation.components.LocalLongPressOrigin
import com.singam.lionlibrary.presentation.components.LongPressOrigin
import com.singam.lionlibrary.presentation.details.DetailsRoot
import com.singam.lionlibrary.presentation.home.HomeRoot
import com.singam.lionlibrary.presentation.library.LibraryRoot
import com.singam.lionlibrary.presentation.player.PlayerRoot
import com.singam.lionlibrary.presentation.settings.SettingsRoot
import com.singam.lionlibrary.ui.theme.LionMotion

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LionLibraryNavHost(
    navController: NavHostController,
    // Off: no poster flight; Details fades in with a banner hero.
    posterTransitions: Boolean,
    // Off: Details sits on solid black instead of its blurred artwork.
    detailsBlur: Boolean,
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    // A tapped poster card leaves its shelf and poster here; every route into Details passes
    // through openDetails, which puts them in the route so the page can share that poster.
    val tapOrigin = remember { PosterTapOrigin() }
    // The poster only flies on a phone held upright (the only layout whose hero is the poster);
    // elsewhere every Details page crossfades.
    val configuration = LocalConfiguration.current
    val posterFlight =
        posterTransitions &&
            configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            configuration.smallestScreenWidthDp < 600
    val openDetails: (Long) -> Unit = { id ->
        val tapped = tapOrigin.consume()?.takeIf { posterFlight }
        // A tap that lands while a page is still opening or closing (on a card of the page
        // fading out behind it) is dropped, so one quick tap never opens a second title.
        if (navController.isSettled()) {
            navController.navigate(DetailsRoute(id, tapped?.first, tapped?.second))
        }
    }
    // A shelf arrow (Home categories, Details related titles) opens Library as the top-level
    // destination, filtered by a type and/or genres.
    val openLibrary: (MediaFilter?, List<String>) -> Unit = { filter, genres ->
        if (navController.isSettled()) {
            navController.navigate(LibraryRoute(filter?.name, genres)) {
                popUpTo<HomeRoute> { saveState = true }
                launchSingleTop = true
            }
        }
    }
    // Where a long-pressed card sits, for the preview that lifts out of it.
    val longPressOrigin = remember { LongPressOrigin() }
    CompositionLocalProvider(
        LocalPosterTapOrigin provides tapOrigin,
        LocalPosterTransitions provides posterTransitions,
        LocalDetailsBlur provides detailsBlur,
        LocalLongPressOrigin provides longPressOrigin,
    ) {
        NavHost(
            navController,
            startDestination = HomeRoute,
            modifier = modifier,
            enterTransition = {
                fadeIn(tween(LionMotion.Standard, easing = LionMotion.Easing))
            },
            exitTransition = {
                // Crossfade with Details, quicker when a poster flies out of this page (as in
                // Jetsnack, the page gets out of the poster's way); the player cuts in quickly.
                val duration =
                    when {
                        targetState.opensFromPoster() -> LionMotion.Fast
                        targetState.destination.hasRoute<DetailsRoute>() -> LionMotion.Medium
                        targetState.destination.hasRoute<PlayerRoute>() -> 90
                        else -> LionMotion.Fast
                    }
                fadeOut(tween(duration, easing = LionMotion.Easing))
            },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit(tapOrigin) },
            // Navigation's own predictive back shrinks the leaving page to 70%; Android 16 runs it
            // for a back button press too (seen on the tablet as a slow zoom out). Going back looks
            // the same however it is triggered.
            predictivePopEnterTransition = { popEnter() },
            predictivePopExitTransition = { popExit(tapOrigin) },
        ) {
            composable<HomeRoute> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    HomeRoot(
                        snackbarHostState = snackbarHostState,
                        windowSizeClass = windowSizeClass,
                        onNavigateToMovieDetails = openDetails,
                        onNavigateToShowDetails = openDetails,
                        onNavigateToPlayer = { type, id ->
                            if (navController.isSettled()) {
                                navController.navigate(PlayerRoute(type, id))
                            }
                        },
                        onNavigateToLibrary = { openLibrary(it, emptyList()) },
                        onNavigateToGenre = { openLibrary(null, listOf(it)) },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<LibraryRoute> { entry ->
                val route = entry.toRoute<LibraryRoute>()
                val filter =
                    route.filter?.let { value -> MediaFilter.entries.find { it.name == value } }
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    LibraryRoot(
                        snackbarHostState = snackbarHostState,
                        initialFilter = filter,
                        initialGenres = route.genres,
                        onMediaClick = { id, _ -> openDetails(id) },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<SettingsRoute> { SettingsRoot(snackbarHostState) }
            composable<DetailsRoute>(
                // A page opened from a poster shares that poster with the card: the poster
                // flies in the shared transition overlay while the page itself only fades,
                // quickly. Other pages (carousel, long-press preview) crossfade.
                enterTransition = {
                    fadeIn(
                        tween(
                            if (targetState.opensFromPoster()) LionMotion.Fast
                            else LionMotion.Medium,
                            easing = LionMotion.Easing,
                        )
                    )
                },
                exitTransition = { fadeOut(tween(100, easing = LionMotion.Easing)) },
                // Must be named here: a destination's pop transitions default to its own enter and
                // exit (the quick fade above), which would hide the poster close and the shrink.
                popEnterTransition = { popEnter() },
                popExitTransition = { popExit(tapOrigin) },
            ) { entry ->
                val route = entry.toRoute<DetailsRoute>()
                // Going back to its card means the page on top is this one (a back gesture in
                // progress) or the one it was opened from; anything else is a forward move.
                val top by navController.currentBackStackEntryAsState()
                val below = remember(entry.id) { navController.previousBackStackEntry?.id }
                val posterTransition =
                    if (route.origin != null && route.posterPath != null) {
                        PosterTransition(
                            PosterSharedElementKey(route.mediaId, route.origin),
                            route.posterPath,
                            closesToCard = top?.id == entry.id || top?.id == below,
                        )
                    } else {
                        null
                    }
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    DetailsRoot(
                        snackbarHostState = snackbarHostState,
                        windowSizeClass = windowSizeClass,
                        onNavigateToPlayer = { type, id ->
                            navController.navigate(PlayerRoute(type, id))
                        },
                        onBack = { navController.popBackStack() },
                        onMediaClick = openDetails,
                        onRelatedGenresClick = { openLibrary(null, it) },
                        posterTransition = posterTransition,
                    )
                }
            }
            composable<PlayerRoute>(
                enterTransition = {
                    fadeIn(tween(240, delayMillis = 70, easing = LionMotion.Easing))
                }
            ) {
                PlayerRoot(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Going back to a page: Details fades in quickly over the closing player; other pages fade in. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition {
    val duration =
        when {
            targetState.destination.hasRoute<DetailsRoute>() -> 240
            initialState.destination.hasRoute<DetailsRoute>() && !initialState.opensFromPoster() ->
                LionMotion.Medium
            else -> LionMotion.Standard
        }
    return fadeIn(tween(duration, easing = LionMotion.Easing))
}

/**
 * Leaving a page by going back. A poster page stays solid while its body fades away under the
 * poster, then its backdrop fades quickly as the poster flies home; other Details pages crossfade;
 * the rest fade out quickly.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(
    tapOrigin: PosterTapOrigin
): ExitTransition =
    when {
        // Shrinking whole into its card: solid until it is there, then it hands over to the card.
        initialState.posterKey()?.let(tapOrigin::shrinksOnClose) == true ->
            fadeOut(
                tween(
                    PosterTiming.shrinkFadeMs,
                    delayMillis = PosterTiming.shrinkMs - PosterTiming.shrinkFadeMs,
                    easing = LinearEasing,
                )
            )
        initialState.opensFromPoster() ->
            fadeOut(
                tween(
                    LionMotion.Fast,
                    delayMillis = PosterTiming.closeBodyMs,
                    easing = LionMotion.Easing,
                )
            )
        initialState.destination.hasRoute<DetailsRoute>() ->
            fadeOut(tween(LionMotion.Medium, easing = LionMotion.Easing))
        else -> fadeOut(tween(LionMotion.Fast, easing = LionMotion.Easing))
    }

/**
 * True once the page on top has finished its enter (or pop-enter) transition: Navigation only
 * resumes an entry when its animation ends.
 */
private fun NavHostController.isSettled(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

/** The poster key of a Details page opened from a poster card, else null. */
private fun NavBackStackEntry.posterKey(): PosterSharedElementKey? =
    if (destination.hasRoute<DetailsRoute>()) {
        toRoute<DetailsRoute>().let { route ->
            route.origin?.let { PosterSharedElementKey(route.mediaId, it) }
        }
    } else {
        null
    }

/** True for a Details page opened by tapping a poster card (see [PosterSharedElementKey]). */
private fun NavBackStackEntry.opensFromPoster(): Boolean =
    destination.hasRoute<DetailsRoute>() && toRoute<DetailsRoute>().origin != null
