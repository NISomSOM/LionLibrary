package com.singam.lionlibrary.ui

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.singam.lionlibrary.domain.model.SkipSegmentType
import com.singam.lionlibrary.domain.repository.DeletionPreview
import com.singam.lionlibrary.presentation.common.DeletionUiState
import com.singam.lionlibrary.presentation.components.DeletionDialog
import com.singam.lionlibrary.presentation.player.PlayerSkipButton
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM Compose checks through Robolectric (no emulator). A plain Application keeps Koin and the
 * native VLC prewarm out of these tests. Break vectors: a dialog that lets a second confirm through
 * while deleting, a combined-file warning that disappears, and a skip button without its own label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ComposeComponentsRobolectricTest {
    @get:Rule val compose = createComposeRule()

    private val preview =
        DeletionPreview(
            token = "t",
            title = "Episode 1-2 🎬",
            location = "Shows/Show/S01E01-E02.mkv",
            isFolder = false,
            fileCount = 1,
            combinedEpisode = true,
        )

    @Test
    fun deletionDialogShowsExactPathAndCombinedWarningAndConfirmsOnce() {
        var confirms = 0
        compose.setContent {
            LionLibraryTheme {
                DeletionDialog(DeletionUiState(preview), onConfirm = { confirms++ }, onDismiss = {})
            }
        }

        compose.onNodeWithText("Shows/Show/S01E01-E02.mkv").assertExists()
        compose.onNodeWithText("more than one episode", substring = true).assertExists()
        compose.onNodeWithText("Delete file").performClick()

        assertEquals(1, confirms)
    }

    @Test
    fun deletionDialogDisablesBothButtonsWhileDeleting() {
        compose.setContent {
            LionLibraryTheme {
                DeletionDialog(
                    DeletionUiState(preview, deleting = true),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Deleting…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun skipButtonExposesItsOwnDescriptionAndClicks() {
        var clicks = 0
        compose.setContent {
            LionLibraryTheme {
                PlayerSkipButton(SkipSegmentType.CREDITS, onSkipClick = { clicks++ })
            }
        }

        compose.onNodeWithContentDescription("credits", substring = true).performClick()

        assertEquals(1, clicks)
    }
}
