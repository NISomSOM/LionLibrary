package com.example.mediahub.presentation.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.mediahub.presentation.settings.SettingsRoot

import com.example.mediahub.presentation.home.HomeRoot

import com.example.mediahub.presentation.details.DetailsRoot
import com.example.mediahub.presentation.search.SearchRoot

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LionLibraryNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        // Home
        composable(Routes.HOME) {
            HomeRoot(
                snackbarHostState = snackbarHostState,
                onNavigateToMovieDetails = { mediaId ->
                    navController.navigate(Routes.MOVIE_DETAILS.replace("{mediaId}", mediaId.toString()))
                },
                onNavigateToShowDetails = { mediaId ->
                    navController.navigate(Routes.SHOW_DETAILS.replace("{mediaId}", mediaId.toString()))
                }
            )
        }

        // Search
        composable(Routes.SEARCH) {
            SearchRoot(
                onNavigateToMovieDetails = { mediaId ->
                    navController.navigate(Routes.MOVIE_DETAILS.replace("{mediaId}", mediaId.toString()))
                },
                onNavigateToShowDetails = { mediaId ->
                    navController.navigate(Routes.SHOW_DETAILS.replace("{mediaId}", mediaId.toString()))
                }
            )
        }

        // Settings
        composable(Routes.SETTINGS) {
            SettingsRoot(snackbarHostState = snackbarHostState)
        }

        // Movie Details
        composable(
            route = Routes.MOVIE_DETAILS,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStackEntry ->
            DetailsRoot(snackbarHostState = snackbarHostState)
        }

        // Show Details
        composable(
            route = Routes.SHOW_DETAILS,
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
        ) { backStackEntry ->
            DetailsRoot(snackbarHostState = snackbarHostState)
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
