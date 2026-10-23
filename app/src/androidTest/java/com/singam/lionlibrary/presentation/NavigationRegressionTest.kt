package com.singam.lionlibrary.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.singam.lionlibrary.presentation.navigation.*
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Break vector: a parameterized Library entry saved under Home must not hijack Home restoration.
 */
class NavigationRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reselectingHomeDoesNotDispatchNavigationOrReplaceItsContent() {
        lateinit var controller: androidx.navigation.NavHostController
        var navigationChanges = 0
        compose.setContent {
            LionLibraryTheme {
                controller = rememberNavController()
                Box(Modifier.fillMaxSize()) {
                    NavHost(controller, startDestination = HomeRoute) {
                        composable<HomeRoute> {
                            var position by remember { mutableIntStateOf(0) }
                            Text("Position: $position", Modifier.clickable { position++ })
                        }
                    }
                    BottomNavBar(controller, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.onNodeWithText("Position: 0").performClick()
        lateinit var original: androidx.navigation.NavBackStackEntry
        compose.runOnIdle {
            original = controller.currentBackStackEntry!!
            controller.addOnDestinationChangedListener { _, _, _ -> navigationChanges++ }
        }
        val initialChanges = navigationChanges
        repeat(3) { compose.onNodeWithText("Home").performClick() }
        compose.onNodeWithText("Position: 1").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(original, controller.currentBackStackEntry)
            assertEquals(initialChanges, navigationChanges)
        }
    }

    @Test
    fun filteredLibraryCanAlwaysReturnToHomeAcrossSavedTabStacks() {
        lateinit var controller: androidx.navigation.NavHostController
        compose.setContent {
            LionLibraryTheme {
                controller = rememberNavController()
                Box(Modifier.fillMaxSize()) {
                    NavHost(controller, startDestination = HomeRoute) {
                        composable<HomeRoute> {
                            Text(
                                "Open Movies",
                                Modifier.clickable {
                                    controller.navigate(LibraryRoute("MOVIES")) {
                                        popUpTo<HomeRoute> { saveState = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable<LibraryRoute> { entry ->
                            Text("Library filter: ${entry.toRoute<LibraryRoute>().filter}")
                        }
                        composable<SettingsRoute> { Text("Settings page") }
                    }
                    BottomNavBar(controller, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        repeat(2) {
            compose.onNodeWithText("Open Movies").performClick()
            compose.onNodeWithText("Library filter: MOVIES").assertExists()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Library").performClick()
            compose.onNodeWithText("Home").performClick()
            compose.onNodeWithText("Open Movies").assertIsDisplayed()
            compose.runOnIdle { assertTrue(controller.currentDestination!!.hasRoute<HomeRoute>()) }
        }
    }
}
