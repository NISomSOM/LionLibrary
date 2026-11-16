package com.singam.lionlibrary.presentation.navigation

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.singam.lionlibrary.domain.model.MediaFilter
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
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    // Remembers the tapped poster card so Details can zoom out of it (see zoomFromPoster).
    val zoomOrigin = rememberPosterZoomOrigin()
    // Every route into Details passes through here, so the tapped card (if any) is adopted once.
    val view = LocalView.current
    DisposableEffect(zoomOrigin, view) {
        zoomOrigin.requestBackdrop = {
            val windowSize = IntSize(view.rootView.width, view.rootView.height)
            captureWindow(view, BACKDROP_SCALE) { zoomOrigin.onBackdropReady(it, windowSize) }
        }
        onDispose { zoomOrigin.requestBackdrop = null }
    }
    val openDetails: (Long) -> Unit = { id ->
        zoomOrigin.onDetailsOpening()
        navController.navigate(DetailsRoute(id))
    }
    CompositionLocalProvider(LocalPosterZoomOrigin provides zoomOrigin) {
        NavHost(
            navController,
            startDestination = HomeRoute,
            modifier = modifier,
            enterTransition = {
                fadeIn(tween(LionMotion.Standard, easing = LionMotion.Easing))
            },
            exitTransition = {
                // The page dims behind Details as it zooms or fades in; the player still
                // cuts in quickly.
                val duration =
                    when {
                        targetState.destination.hasRoute<DetailsRoute>() -> LionMotion.Medium
                        targetState.destination.hasRoute<PlayerRoute>() -> 90
                        else -> LionMotion.Fast
                    }
                // A Details page that zooms out of a poster draws a still picture of this page
                // behind itself, so this page is only kept for the moment the picture needs.
                // Navigation then counts Details as arrived at once, which is what lets a quick
                // Back play the full close animation.
                val zooming =
                    zoomOrigin.zoomActive && targetState.destination.hasRoute<DetailsRoute>()
                if (zooming) {
                    fadeOut(tween(ZOOM_KEEP_MS, easing = LinearEasing), targetAlpha = .99f)
                } else {
                    fadeOut(tween(duration, easing = LionMotion.Easing))
                }
            },
            popEnterTransition = {
                val fromDetails = initialState.destination.hasRoute<DetailsRoute>()
                val zooming = fromDetails && zoomOrigin.zoomActive
                fadeIn(
                    tween(
                        if (fromDetails && !zooming) LionMotion.Medium else LionMotion.Standard,
                        easing = LionMotion.Easing,
                    ),
                    // A page shrinking back into its card lands on the dimmed page it left.
                    initialAlpha = if (zooming) BACKDROP_BRIGHTNESS else 0f,
                )
            },
            popExitTransition = { fadeOut(tween(LionMotion.Fast, easing = LionMotion.Easing)) },
        ) {
            composable<HomeRoute> {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    HomeRoot(
                        snackbarHostState = snackbarHostState,
                        windowSizeClass = windowSizeClass,
                        onNavigateToMovieDetails = openDetails,
                        onNavigateToShowDetails = openDetails,
                        onNavigateToPlayer = { type, id ->
                            navController.navigate(PlayerRoute(type, id))
                        },
                        onNavigateToLibrary = {
                            navController.navigate(LibraryRoute(it.name)) {
                                popUpTo<HomeRoute> { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<LibraryRoute> { entry ->
                val filter =
                    entry.toRoute<LibraryRoute>().filter?.let { value ->
                        MediaFilter.entries.find { it.name == value }
                    }
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    LibraryRoot(
                        initialFilter = filter,
                        onMediaClick = { id, _ -> openDetails(id) },
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                    )
                }
            }
            composable<SettingsRoute> { SettingsRoot(snackbarHostState) }
            composable<DetailsRoute>(
                // From a tapped poster the page zooms itself (zoomFromPoster) and handles its
                // own fade, so navigation only keeps it alive. Otherwise: plain crossfades
                // (carousel, long-press preview, and to/from the player).
                enterTransition = {
                    if (zoomOrigin.zoomActive) EnterTransition.None
                    else fadeIn(tween(LionMotion.Medium, easing = LionMotion.Easing))
                },
                exitTransition = { fadeOut(tween(100, easing = LionMotion.Easing)) },
                popEnterTransition = { fadeIn(tween(240, easing = LionMotion.Easing)) },
                popExitTransition = {
                    if (zoomOrigin.zoomActive) ExitTransition.KeepUntilTransitionsFinished
                    else fadeOut(tween(LionMotion.Medium, easing = LionMotion.Easing))
                },
            ) { entry ->
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                    DetailsRoot(
                        snackbarHostState = snackbarHostState,
                        windowSizeClass = windowSizeClass,
                        onNavigateToPlayer = { type, id ->
                            zoomOrigin.onPlayerOpening()
                            navController.navigate(PlayerRoute(type, id))
                        },
                        onBack = { navController.popBackStack() },
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

/** How long the old page is kept under an opening zoom: long enough to take its picture. */
private const val ZOOM_KEEP_MS = 120

/** The picture is dimmed and replaced within a third of a second: half resolution is plenty. */
private const val BACKDROP_SCALE = .5f
