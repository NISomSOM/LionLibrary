package com.singam.lionlibrary.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.singam.lionlibrary.presentation.navigation.BottomNavBarContent
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Break vectors: labels lost to accessibility when compact, and compacting at large text. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavigationBarRobolectricTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactBarHidesLabelsButKeepsTheirNames() {
        compose.setContent { LionLibraryTheme { BottomNavBarContent(0, {}, compact = true) } }

        compose.onNodeWithText("Library").assertDoesNotExist()
        compose.onNodeWithContentDescription("Library").assertExists()
    }

    @Test
    fun largeTextKeepsLabelsEvenWhenCompactIsRequested() {
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(LocalDensity provides Density(2.75f, fontScale = 2f)) {
                    BottomNavBarContent(1, {}, compact = true)
                }
            }
        }

        compose.onNodeWithText("Library").assertExists()
        compose.onNodeWithText("Settings").assertExists()
    }
}
