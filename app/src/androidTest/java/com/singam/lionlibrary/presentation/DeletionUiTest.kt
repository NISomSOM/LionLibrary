package com.singam.lionlibrary.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.DeletionPreview
import com.singam.lionlibrary.presentation.common.DeletionUiState
import com.singam.lionlibrary.presentation.components.DeletionDialog
import com.singam.lionlibrary.presentation.details.*
import com.singam.lionlibrary.presentation.home.*
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
class DeletionUiTest {
    @get:Rule val compose = createComposeRule()
    private val width = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp))

    private fun media(type: MediaType) =
        MediaItem(
            id = 1,
            tmdbId = 1,
            title = "Movie 🦁",
            originalTitle = null,
            overview = null,
            posterPath = null,
            backdropPath = null,
            genres = null,
            rating = null,
            year = 2026,
            mediaType = type,
            matchConfidence = 1f,
            isUnidentified = false,
            lastUpdated = 1,
        )

    @Test
    fun homePosterLongPressOffersDeleteWithoutConfirmingIt() {
        val item = media(MediaType.MOVIE)
        var state by mutableStateOf(HomeState(movies = listOf(item), isLoading = false))
        val actions = mutableListOf<HomeAction>()
        compose.setContent {
            LionLibraryTheme {
                HomeScreen(
                    state,
                    width,
                    { action ->
                        actions += action
                        if (action is HomeAction.MediaOptions)
                            state = state.copy(selectedMediaForOptions = action.item)
                    },
                )
            }
        }
        compose.onNodeWithText(item.title).performTouchInput { longClick() }
        compose.onNodeWithText("Delete from device").assertIsDisplayed().performClick()
        assertTrue(actions.contains(HomeAction.RequestDelete(1)))
        assertFalse(actions.contains(HomeAction.ConfirmDelete))
    }

    @Test
    fun heroLongPressTargetsCurrentItemWithoutOpeningPlayback() {
        val selected = mutableListOf<Long>()
        compose.setContent {
            LionLibraryTheme {
                HeroBannerCarousel(
                    listOf(media(MediaType.MOVIE)),
                    { _, _ -> fail("Unexpected navigation") },
                    onItemLongClick = { selected += it.id },
                )
            }
        }
        compose.onNodeWithTag("hero-interaction").performTouchInput {
            longClick(androidx.compose.ui.geometry.Offset(center.x, height * .25f))
        }
        assertEquals(listOf(1L), selected)
    }

    @Test
    fun episodeContextMenuKeepsExistingActionsAndRequestsOnlySelectedEpisode() {
        val episode =
            Episode(11, 1, 1, 1, "Episode", null, null, null, null, "content://fixture/episode")
        val actions = mutableListOf<DetailsAction>()
        compose.setContent {
            LionLibraryTheme {
                DetailsScreen(
                    DetailsState(
                        media = media(MediaType.ANIME),
                        episodes = listOf(episode),
                        selectedEpisodeForOptions = episode,
                        isLoading = false,
                    ),
                    width,
                    { actions += it },
                )
            }
        }
        compose.onNodeWithText("Play in external player").assertIsDisplayed()
        compose.onNodeWithText("Delete from device").performClick()
        assertTrue(actions.contains(DetailsAction.RequestDeleteEpisode(11)))
        assertFalse(actions.contains(DetailsAction.ConfirmDelete))
    }

    @Test
    fun fileConfirmationNamesExactPathAndCancelIsIndependentAtLargeText() {
        var confirms = 0
        var cancels = 0
        compose.setContent {
            LionLibraryTheme {
                val typography = androidx.compose.material3.MaterialTheme.typography
                // Dialog owns a separate Android window. Enlarge its inherited typography
                // explicitly; no phone/emulator system setting is changed.
                androidx.compose.material3.MaterialTheme(
                    typography =
                        typography.copy(
                            headlineSmall =
                                typography.headlineSmall.copy(
                                    fontSize = typography.headlineSmall.fontSize * 2,
                                    lineHeight = typography.headlineSmall.lineHeight * 2,
                                ),
                            titleMedium =
                                typography.titleMedium.copy(
                                    fontSize = typography.titleMedium.fontSize * 2,
                                    lineHeight = typography.titleMedium.lineHeight * 2,
                                ),
                            bodyMedium =
                                typography.bodyMedium.copy(
                                    fontSize = typography.bodyMedium.fontSize * 2,
                                    lineHeight = typography.bodyMedium.lineHeight * 2,
                                ),
                            labelLarge =
                                typography.labelLarge.copy(
                                    fontSize = typography.labelLarge.fontSize * 2,
                                    lineHeight = typography.labelLarge.lineHeight * 2,
                                ),
                        )
                ) {
                    DeletionDialog(
                        DeletionUiState(
                            DeletionPreview("token", "映画 🦁", "Movies/映画.mkv", false, 1)
                        ),
                        { confirms++ },
                        { cancels++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Movies/映画.mkv").assertExists()
        screenshot("delete-file-large-text")
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test
    fun deletingFolderDisablesRepeatConfirmationAndCancel() {
        compose.setContent {
            LionLibraryTheme {
                DeletionDialog(
                    DeletionUiState(
                        DeletionPreview("token", "Show", "Shows/Show", true, 4),
                        deleting = true,
                    ),
                    { fail("duplicate") },
                    { fail("cancel") },
                )
            }
        }
        compose.onNodeWithText("Deleting…").assertIsNotEnabled()
        screenshot("delete-show-busy")
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    private fun screenshot(name: String) {
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
