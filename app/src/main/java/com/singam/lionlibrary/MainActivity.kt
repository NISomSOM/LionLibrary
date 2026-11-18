package com.singam.lionlibrary

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.singam.lionlibrary.domain.repository.ArtworkRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.usecase.ScanCoordinator
import com.singam.lionlibrary.presentation.navigation.*
import com.singam.lionlibrary.presentation.navigation.BottomNavBar
import com.singam.lionlibrary.presentation.navigation.LionLibraryNavHost
import com.singam.lionlibrary.presentation.navigation.NavigationScrollConnection
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import org.koin.android.ext.android.inject

private const val ARTWORK_SAMPLE_MS = 300L

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, FlowPreview::class)
class MainActivity : ComponentActivity() {

    private val artworkRepository: ArtworkRepository by inject()
    private val scanCoordinator: ScanCoordinator by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val posterTransitions by lazy {
        settingsRepository.posterTransitions.stateIn(
            lifecycleScope,
            SharingStarted.WhileSubscribed(),
            true,
        )
    }

    // A scan publishes artwork in bursts; sampling turns hundreds of publications into a few
    // artwork refreshes. The latest revision always arrives.
    private val artworkRevision by lazy {
        artworkRepository.revision
            .sample(ARTWORK_SAMPLE_MS)
            .stateIn(
                lifecycleScope,
                SharingStarted.WhileSubscribed(),
                artworkRepository.revision.value,
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Once per process; no-op without a TMDB key, folders or internet.
        scanCoordinator.autoScanOnLaunch()
        setContent {
            val revision by artworkRevision.collectAsStateWithLifecycle()
            val posterAnimation by posterTransitions.collectAsStateWithLifecycle()
            LionLibraryTheme(artworkRevision = revision) {
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
                val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600

                DisposableEffect(navController, isTablet) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        val isPlayer = destination.hasRoute<PlayerRoute>()
                        requestedOrientation =
                            if (isPlayer) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                if (isTablet) {
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }
                    }
                    navController.addOnDestinationChangedListener(listener)

                    // Initial orientation.
                    val isPlayer = navController.currentDestination?.hasRoute<PlayerRoute>() == true
                    requestedOrientation =
                        if (isPlayer) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            if (isTablet) {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }

                    onDispose { navController.removeOnDestinationChangedListener(listener) }
                }

                // Floating bar compacts while content scrolls down, restores on scroll up / at top.
                var navCompact by remember { mutableStateOf(false) }
                val navScrollConnection = remember {
                    NavigationScrollConnection { compact -> navCompact = compact }
                }
                val navEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(navEntry?.destination?.id) {
                    navScrollConnection.reset()
                    navCompact = false
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        Box(
                            Modifier.windowInsetsPadding(
                                WindowInsets(
                                    bottom =
                                        if (showNavElements && !keyboardVisible)
                                            with(density) { floatingBarHeightPx.toDp() }
                                        else 0.dp
                                )
                            )
                        ) {
                            SnackbarHost(snackbarHostState)
                        }
                    },
                ) { innerPadding ->
                    // Phones and tablets alike use the floating bottom bar.
                    val floating = showNavElements && !keyboardVisible
                    CompositionLocalProvider(
                        LocalFloatingNavigationInset provides
                            if (floating) with(density) { floatingBarHeightPx.toDp() } else 0.dp
                    ) {
                        // One shared transition layout around the pages and the floating bar
                        // (as in Jetsnack): a poster card and its Details page share their bounds
                        // in its overlay, and the bar is drawn above them there.
                        SharedTransitionLayout(
                            Modifier.fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            CompositionLocalProvider(
                                LocalSharedTransitionScope provides this@SharedTransitionLayout
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    LionLibraryNavHost(
                                        navController = navController,
                                        posterTransitions = posterAnimation,
                                        snackbarHostState = snackbarHostState,
                                        windowSizeClass = windowSizeClass,
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .nestedScroll(navScrollConnection),
                                    )
                                    // Fades with the page it belongs to instead of cutting in over
                                    // a Details or player transition, and stays above a page that
                                    // is growing out of or shrinking into a poster card.
                                    AnimatedVisibility(
                                        visible = floating,
                                        modifier =
                                            Modifier.align(Alignment.BottomCenter)
                                                .renderInSharedTransitionScopeOverlay(
                                                    zIndexInOverlay = NAV_BAR_OVERLAY_Z
                                                ),
                                        enter =
                                            fadeIn(
                                                tween(LionMotion.Medium, easing = LionMotion.Easing)
                                            ),
                                        exit =
                                            fadeOut(
                                                tween(LionMotion.Fast, easing = LionMotion.Easing)
                                            ),
                                    ) {
                                        BottomNavBar(
                                            navController,
                                            Modifier.onSizeChanged {
                                                // Keep the full-size inset while compact, so lists
                                                // do not shift as the bar narrows during scrolling.
                                                if (!navCompact) floatingBarHeightPx = it.height
                                            },
                                            compact = navCompact,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
