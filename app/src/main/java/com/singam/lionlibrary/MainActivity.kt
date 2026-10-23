package com.singam.lionlibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.singam.lionlibrary.domain.repository.ArtworkRepository
import com.singam.lionlibrary.domain.usecase.ScanCoordinator
import com.singam.lionlibrary.presentation.navigation.*
import com.singam.lionlibrary.presentation.navigation.BottomNavBar
import com.singam.lionlibrary.presentation.navigation.LionLibraryNavHost
import com.singam.lionlibrary.presentation.navigation.NavigationRailBar
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.koin.android.ext.android.inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {

    private val artworkRepository: ArtworkRepository by inject()
    private val scanCoordinator: ScanCoordinator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Once per process; no-op without a TMDB key, folders or internet.
        scanCoordinator.autoScanOnLaunch()
        setContent {
            val artworkRevision by artworkRepository.revision.collectAsStateWithLifecycle()
            LionLibraryTheme(artworkRevision = artworkRevision) {
                val windowSizeClass = calculateWindowSizeClass(this)
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val density = LocalDensity.current
                val ime = WindowInsets.ime
                // Recompose on show/hide only, not on every frame of the keyboard animation.
                val keyboardVisible by
                    remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
                val snackbarHostState = remember { SnackbarHostState() }
                var floatingBarHeightPx by remember { mutableIntStateOf(0) }

                val showNavElements =
                    navBackStackEntry?.destination?.let { destination ->
                        destination.hasRoute<HomeRoute>() ||
                            destination.hasRoute<LibraryRoute>() ||
                            destination.hasRoute<SettingsRoute>()
                    } == true
                // A phone's forced landscape player can have a wide window. Orientation
                // policy must use its smallest width, independent of that temporary window.
                val isTablet =
                    androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >=
                        600
                val useNavigationRail =
                    windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

                androidx.compose.runtime.DisposableEffect(navController, isTablet) {
                    val listener =
                        androidx.navigation.NavController.OnDestinationChangedListener {
                            _,
                            destination,
                            _ ->
                            val isPlayer = destination.hasRoute<PlayerRoute>()
                            requestedOrientation =
                                if (isPlayer) {
                                    android.content.pm.ActivityInfo
                                        .SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    if (isTablet) {
                                        android.content.pm.ActivityInfo
                                            .SCREEN_ORIENTATION_UNSPECIFIED
                                    } else {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                }
                        }
                    navController.addOnDestinationChangedListener(listener)

                    // Initial orientation.
                    val isPlayer = navController.currentDestination?.hasRoute<PlayerRoute>() == true
                    requestedOrientation =
                        if (isPlayer) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            if (isTablet) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }

                    onDispose { navController.removeOnDestinationChangedListener(listener) }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        Box(
                            Modifier.windowInsetsPadding(
                                WindowInsets(
                                    bottom =
                                        if (
                                            showNavElements &&
                                                !useNavigationRail &&
                                                !keyboardVisible
                                        )
                                            with(density) { floatingBarHeightPx.toDp() }
                                        else 0.dp
                                )
                            )
                        ) {
                            SnackbarHost(snackbarHostState)
                        }
                    },
                ) { innerPadding ->
                    val floating = showNavElements && !useNavigationRail && !keyboardVisible
                    CompositionLocalProvider(
                        LocalFloatingNavigationInset provides
                            if (floating) with(density) { floatingBarHeightPx.toDp() } else 0.dp
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                if (showNavElements && useNavigationRail) {
                                    NavigationRailBar(navController)
                                }
                                LionLibraryNavHost(
                                    navController = navController,
                                    snackbarHostState = snackbarHostState,
                                    windowSizeClass = windowSizeClass,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (floating) {
                                BottomNavBar(
                                    navController,
                                    Modifier.align(Alignment.BottomCenter).onSizeChanged {
                                        floatingBarHeightPx = it.height
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
