package com.singam.lionlibrary.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.singam.lionlibrary.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchOpensHomeAndTypedTopLevelNavigation() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Library").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Set up later").assertDoesNotExist()
        compose.onAllNodesWithText("Library").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Filters and sorting").assertExists()
        compose.onAllNodesWithText("Settings").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("TMDB API key").assertExists()
        compose.onAllNodesWithText("Search").assertCountEquals(0)
        compose.onAllNodesWithText("Home").filter(hasClickAction()).onFirst().performClick()
        compose.onAllNodesWithText("Library").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Search titles, genres, or overview").assertExists()
    }
}
