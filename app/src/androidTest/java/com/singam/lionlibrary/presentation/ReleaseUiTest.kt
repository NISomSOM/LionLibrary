package com.singam.lionlibrary.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.components.ArtworkTitle
import com.singam.lionlibrary.presentation.library.*
import com.singam.lionlibrary.presentation.player.*
import com.singam.lionlibrary.presentation.settings.*
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReleaseUiTest {
    @get:Rule val compose = createComposeRule()

    // Attack enlarged labels, hours-long duration and scrub/option independence after the layout
    // move.
    @Test
    fun playerFooterKeepsTimesBelowSeekbarAndAllExistingActionsReachable() {
        var position by androidx.compose.runtime.mutableLongStateOf(90_000)
        var fontScale by androidx.compose.runtime.mutableFloatStateOf(1f)
        var scrubFinished = 0
        val options = mutableListOf<String>()
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.scrim)
                    ) {
                        PlayerBottomControls(
                            title = "Example title",
                            subtitle = "S1 E2",
                            currentPositionMs = position,
                            durationMs = 5_400_000,
                            isEpisode = true,
                            hasPrevious = true,
                            hasNext = true,
                            playbackSpeed = 1f,
                            aspectMode =
                                com.singam.lionlibrary.presentation.player.engine.AspectMode.FIT,
                            onScrub = { position = it },
                            onScrubEnd = { scrubFinished++ },
                            onPreviousClick = { options += "previous" },
                            onSeekForward90Click = { options += "90" },
                            onSubtitleClick = { options += "subs" },
                            onAudioClick = { options += "audio" },
                            onSpeedClick = { options += "speed" },
                            onAspectClick = { options += "aspect" },
                            onInfoClick = { options += "info" },
                            onNextClick = { options += "next" },
                            modifier =
                                Modifier.align(Alignment.BottomCenter)
                                    .safeDrawingPadding()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle {
                fontScale = scale
                position = 90_000
            }
            val slider =
                compose.onNode(
                    SemanticsMatcher.keyIsDefined(
                        androidx.compose.ui.semantics.SemanticsActions.SetProgress
                    )
                )
            val elapsed = compose.onNodeWithText("01:30").assertIsDisplayed().fetchSemanticsNode()
            val duration =
                compose.onNodeWithText("1:30:00").assertIsDisplayed().fetchSemanticsNode()
            val sliderBounds = slider.fetchSemanticsNode().boundsInRoot
            assertTrue(elapsed.boundsInRoot.top >= sliderBounds.bottom)
            assertTrue(duration.boundsInRoot.top >= sliderBounds.bottom)
            val density = compose.density.density
            val actions =
                listOf(
                    "Previous episode" to "previous",
                    "+90s" to "90",
                    "Subtitles" to "subs",
                    "Audio Tracks" to "audio",
                    "Info" to "info",
                    "Next episode" to "next",
                )
            for ((label, expected) in actions) {
                val option =
                    if (label == "+90s") compose.onNodeWithText(label)
                    else compose.onNodeWithContentDescription(label)
                option.assertIsDisplayed()
                assertTrue(option.fetchSemanticsNode().boundsInRoot.height >= 48 * density - 1)
                option.performClick()
                compose.runOnIdle { assertEquals(expected, options.last()) }
            }
            snapshot("player-footer-${scale.toInt()}x")
            slider.performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.SetProgress
            ) {
                it(.5f)
            }
            compose.onNodeWithText("45:00").assertIsDisplayed()
            compose.runOnIdle { assertTrue(scrubFinished > 0) }
        }
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder =
            java.io
                .File(instrumentation.targetContext.getExternalFilesDir(null), "reviewshots")
                .apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use {
            instrumentation.uiAutomation
                .takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun emptyLibraryOffersSettings() {
        var selected = false
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    LibraryScreen(
                        LibraryState(isLoading = false),
                        {},
                        { _, _ -> },
                        { selected = true },
                    )
                }
            }
        }
        compose.onNodeWithText("Open Settings").performClick()
        compose.runOnIdle { assertTrue(selected) }
        snapshot("library-large-text")
    }

    @Test
    fun watchlistActionIsVisibleAndIndependentOfOpeningTitle() {
        val item =
            MediaItem(
                id = 4,
                tmdbId = null,
                title = "Example",
                originalTitle = null,
                overview = null,
                posterPath = null,
                backdropPath = null,
                genres = null,
                rating = null,
                year = null,
                mediaType = MediaType.MOVIE,
                matchConfidence = 1f,
                isUnidentified = false,
                lastUpdated = 1,
            )
        var action: LibraryAction? = null
        var opened = false
        compose.setContent {
            LionLibraryTheme {
                LibraryScreen(
                    LibraryState(isLoading = false, items = listOf(item)),
                    { action = it },
                    { _, _ -> opened = true },
                    {},
                )
            }
        }
        compose.onNodeWithContentDescription("Add to watchlist").performClick()
        compose.onAllNodesWithText("Example").assertCountEquals(1)
        compose.runOnIdle {
            assertEquals(LibraryAction.ToggleItemWatchlist(4), action)
            assertFalse(opened)
        }
    }

    @Test
    fun settingsKeepOnlyTwoFoldersAndNoRepairOrOnboarding() {
        compose.setContent { LionLibraryTheme { SettingsScreen(SettingsState(), {}, {}, {}) } }
        compose.onNodeWithText("Movies folder").assertExists()
        compose.onNodeWithText("Shows folder").assertExists()
        compose.onNodeWithText("Set up library").assertDoesNotExist()
        compose.onNodeWithText("Library health").assertDoesNotExist()
        snapshot("settings-simple")
    }

    // Attack narrow Details with enlarged text and all metadata fields, then verify
    // the secondary action's actual text layout and behavior, not just its existence.
    @OptIn(
        androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class
    )
    @Test
    fun movieRatingsReflowWithoutBreakingLetterboxdIntoFragments() {
        val item =
            MediaItem(
                id = 4,
                tmdbId = 7,
                title = "Example movie",
                originalTitle = null,
                overview = "Example overview",
                posterPath = null,
                backdropPath = null,
                genres = "Animation, Drama",
                rating = 7.7f,
                year = 2023,
                mediaType = MediaType.MOVIE,
                matchConfidence = 1f,
                isUnidentified = false,
                lastUpdated = 1,
                duration = 75,
                certification = "NR",
                letterboxdRating = 4.1f,
            )
        var action: com.singam.lionlibrary.presentation.details.DetailsAction? = null
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.width(360.dp)) {
                        com.singam.lionlibrary.presentation.details.DetailsScreen(
                            com.singam.lionlibrary.presentation.details.DetailsState(
                                media = item,
                                isLoading = false,
                            ),
                            androidx.compose.material3.windowsizeclass.WindowSizeClass
                                .calculateFromSize(androidx.compose.ui.unit.DpSize(360.dp, 800.dp)),
                            { action = it },
                        )
                    }
                }
            }
        }
        val node = compose.onNodeWithText("Letterboxd", useUnmergedTree = true)
        node.performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        assertEquals(1, layouts.single().lineCount)
        snapshot("details-large-text-ratings")
        val layout = layouts.single()
        val lastGlyph = layout.getBoundingBox("Letterboxd".lastIndex)
        val geometry =
            "size=${layout.size}, visibleEnd=${layout.getLineEnd(0, true)}, lastGlyph=$lastGlyph"
        assertEquals(geometry, "Letterboxd".length, layout.getLineEnd(0, true))
        // Float glyph advances can exceed their rounded Int layout width by a fraction
        // of a pixel. Check real clipping, with at most one raster pixel of tolerance.
        assertTrue(geometry, lastGlyph.right <= layout.size.width + 1f)
        compose.onNodeWithText("Letterboxd").performClick()
        compose.runOnIdle {
            assertEquals(
                com.singam.lionlibrary.presentation.details.DetailsAction.OnOpenLetterboxd,
                action,
            )
        }
        snapshot("details-large-text-ratings")
    }

    @Test
    fun missingLogoKeepsReadableTitle() {
        compose.setContent {
            LionLibraryTheme { ArtworkTitle("Knapsack Kid", "/missing/logo.png") }
        }
        compose.onNodeWithText("Knapsack Kid").assertIsDisplayed()
    }

    @Test
    fun artworkPublishedAtTheSamePathReloadsAndCorruptLogoFallsBack() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("logo-ui-", ".png", context.cacheDir)
        file.delete()
        val revision = androidx.compose.runtime.mutableLongStateOf(0L)
        try {
            compose.setContent {
                LionLibraryTheme(artworkRevision = revision.longValue) {
                    ArtworkTitle("Example title", file.path)
                }
            }
            compose.onNodeWithText("Example title").assertIsDisplayed()
            val image =
                android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
            file.outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
            compose.runOnIdle { revision.longValue++ }
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("Example title").fetchSemanticsNodes().isEmpty()
            }
            // Publishing an unrelated image leaves this file/request unchanged.
            // The prior implementation reset its fallback but received no new success callback.
            compose.runOnIdle { revision.longValue++ }
            compose.waitForIdle()
            compose.onNodeWithText("Example title").assertDoesNotExist()
            file.writeText("corrupt image")
            compose.runOnIdle { revision.longValue++ }
            compose.onNodeWithText("Example title").assertIsDisplayed()
        } finally {
            check(file.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            file.delete()
        }
    }

    @Test
    fun playbackFailureHasRetryAndExternalRecovery() {
        var action: PlayerAction? = null
        compose.setContent {
            LionLibraryTheme {
                PlayerScreen(
                    PlayerState(failure = UiText.Resource(R.string.playback_failure)),
                    {},
                    { action = it },
                    {},
                )
            }
        }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(PlayerAction.Retry, action) }
        compose.onNodeWithText("Open externally").assertHasClickAction()
        snapshot("player-error")
    }
}
