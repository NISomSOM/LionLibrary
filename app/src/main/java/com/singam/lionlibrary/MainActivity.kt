package com.singam.lionlibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.singam.lionlibrary.presentation.navigation.BottomNavBar
import com.singam.lionlibrary.presentation.navigation.LionLibraryNavHost
import com.singam.lionlibrary.presentation.navigation.Routes
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LionLibraryTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val snackbarHostState = remember { SnackbarHostState() }

                //to figure out where to show bottombar and where not to
                val showBottomBar = currentRoute in listOf(
                    Routes.HOME,
                    Routes.SEARCH,
                    Routes.SETTINGS
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    LionLibraryNavHost(
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
            }
        }
    }
}

