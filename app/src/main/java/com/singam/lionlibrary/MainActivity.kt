package com.singam.lionlibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.singam.lionlibrary.presentation.navigation.BottomNavBar
import com.singam.lionlibrary.presentation.navigation.LionLibraryNavHost
import com.singam.lionlibrary.presentation.navigation.NavigationRailBar
import com.singam.lionlibrary.presentation.navigation.Routes
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LionLibraryTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val snackbarHostState = remember { SnackbarHostState() }

                // Determine UI visibility.
                val baseRoute = currentRoute?.substringBefore("?")
                val showNavElements = baseRoute in listOf(
                    Routes.HOME,
                    Routes.SEARCH,
                    Routes.SETTINGS
                )
                
                val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
                val useNavigationRail = isTablet

                androidx.compose.runtime.DisposableEffect(navController, isTablet) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                        val isPlayer = destination.route?.startsWith("player/") == true
                        requestedOrientation = if (isPlayer) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            if (isTablet) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    }
                    navController.addOnDestinationChangedListener(listener)
                    
                    // Initial orientation.
                    val isPlayer = navController.currentDestination?.route?.startsWith("player/") == true
                    requestedOrientation = if (isPlayer) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        if (isTablet) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }

                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showNavElements && !useNavigationRail) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        if (showNavElements && useNavigationRail) {
                            NavigationRailBar(navController)
                        }
                        LionLibraryNavHost(
                            navController = navController,
                            snackbarHostState = snackbarHostState,
                            windowSizeClass = windowSizeClass,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
