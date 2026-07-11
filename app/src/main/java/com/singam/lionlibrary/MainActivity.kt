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

                //to figure out where to show bottombar and where not to
                val showNavElements = currentRoute in listOf(
                    Routes.HOME,
                    Routes.SEARCH,
                    Routes.SETTINGS
                )
                
                val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

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
