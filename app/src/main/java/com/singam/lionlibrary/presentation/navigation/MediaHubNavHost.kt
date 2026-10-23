package com.singam.lionlibrary.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    NavHost(
        navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(LionMotion.Standard, easing = LionMotion.Easing)) },
        exitTransition = {
            val openingMedia =
                targetState.destination.hasRoute<DetailsRoute>() ||
                    targetState.destination.hasRoute<PlayerRoute>()
            fadeOut(tween(if (openingMedia) 90 else LionMotion.Fast, easing = LionMotion.Easing))
        },
        popEnterTransition = { fadeIn(tween(LionMotion.Standard, easing = LionMotion.Easing)) },
        popExitTransition = { fadeOut(tween(LionMotion.Fast, easing = LionMotion.Easing)) },
    ) {
        composable<HomeRoute> {
            HomeRoot(
                snackbarHostState = snackbarHostState,
                windowSizeClass = windowSizeClass,
                onNavigateToMovieDetails = { navController.navigate(DetailsRoute(it)) },
                onNavigateToShowDetails = { navController.navigate(DetailsRoute(it)) },
                onNavigateToPlayer = { type, id -> navController.navigate(PlayerRoute(type, id)) },
                onNavigateToLibrary = {
                    navController.navigate(LibraryRoute(it.name)) {
                        popUpTo<HomeRoute> { saveState = true }
                        launchSingleTop = true
                    }
                },
                onSettingsClick = { navController.navigate(SettingsRoute) },
            )
        }
        composable<LibraryRoute> { entry ->
            val filter =
                entry.toRoute<LibraryRoute>().filter?.let { value ->
                    MediaFilter.entries.find { it.name == value }
                }
            LibraryRoot(
                initialFilter = filter,
                onMediaClick = { id, _ -> navController.navigate(DetailsRoute(id)) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> { SettingsRoot(snackbarHostState, windowSizeClass) }
        composable<DetailsRoute>(
            enterTransition = { fadeIn(tween(240, delayMillis = 70, easing = LionMotion.Easing)) },
            exitTransition = { fadeOut(tween(100, easing = LionMotion.Easing)) },
            popEnterTransition = { fadeIn(tween(240, easing = LionMotion.Easing)) },
            popExitTransition = { fadeOut(tween(100, easing = LionMotion.Easing)) },
        ) { entry ->
            DetailsRoot(
                snackbarHostState = snackbarHostState,
                windowSizeClass = windowSizeClass,
                onNavigateToPlayer = { type, id -> navController.navigate(PlayerRoute(type, id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PlayerRoute>(
            enterTransition = { fadeIn(tween(240, delayMillis = 70, easing = LionMotion.Easing)) }
        ) {
            PlayerRoot(onBack = { navController.popBackStack() })
        }
    }
}
