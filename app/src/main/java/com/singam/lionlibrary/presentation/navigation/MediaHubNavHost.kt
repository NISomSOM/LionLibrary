package com.singam.lionlibrary.presentation.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.singam.lionlibrary.presentation.settings.SettingsRoot

import com.singam.lionlibrary.presentation.home.HomeRoot

import com.singam.lionlibrary.presentation.details.DetailsRoot
import com.singam.lionlibrary.presentation.search.SearchRoot
import com.singam.lionlibrary.presentation.player.PlayerRoot

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LionLibraryNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        // Define Home route
        composable(Routes.HOME) {
            HomeRoot(
                snackbarHostState = snackbarHostState,
                windowSizeClass = windowSizeClass,
                onNavigateToMovieDetails = { mediaId ->
                    navController.navigate(Routes.MOVIE_DETAILS.replace("{mediaId}", mediaId.toString()))
                },
                onNavigateToShowDetails = { mediaId ->
                    navController.navigate(Routes.SHOW_DETAILS.replace("{mediaId}", mediaId.toString()))
                },
                onNavigateToPlayer = { mediaType, mediaId ->
                    navController.navigate(Routes.player(mediaType, mediaId))
                },
                onNavigateToSearch = { filter ->
                    navController.navigate(Routes.searchWithFilter(filter.name)) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Define Search route
        composable(
            route = Routes.SEARCH_ROUTE,
            arguments = listOf(navArgument("filter") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            SearchRoot(
                windowSizeClass = windowSizeClass,
                onNavigateToMovieDetails = { mediaId ->
                    navController.navigate(Routes.MOVIE_DETAILS.replace("{mediaId}", mediaId.toString()))
                },
                onNavigateToShowDetails = { mediaId ->
                    navController.navigate(Routes.SHOW_DETAILS.replace("{mediaId}", mediaId.toString()))
                }
            )
        }

        // Define Settings route
        composable(Routes.SETTINGS) {
            SettingsRoot(
                snackbarHostState = snackbarHostState,
                windowSizeClass = windowSizeClass
            )
        }

        // Define Movie Details route
        composable(
            route = Routes.MOVIE_DETAILS,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { _ ->
            DetailsRoot(
                snackbarHostState = snackbarHostState,
                windowSizeClass = windowSizeClass,
                onNavigateToPlayer = { mediaType, mediaId ->
                    navController.navigate(Routes.player(mediaType, mediaId))
                }
            )
        }

        // Define Show Details route
        composable(
            route = Routes.SHOW_DETAILS,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { _ ->
            DetailsRoot(
                snackbarHostState = snackbarHostState,
                windowSizeClass = windowSizeClass,
                onNavigateToPlayer = { mediaType, mediaId ->
                    navController.navigate(Routes.player(mediaType, mediaId))
                }
            )
        }

        // Define Player route
        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.LongType }
            )
        ) { _ ->
            PlayerRoot(navController = navController)
        }
    }
}

