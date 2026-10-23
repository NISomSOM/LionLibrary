package com.singam.lionlibrary.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.presentation.components.JumpBackInCard
import com.singam.lionlibrary.presentation.home.HeroBannerCarousel
import com.singam.lionlibrary.presentation.home.MediaRow
import com.singam.lionlibrary.presentation.library.*
import com.singam.lionlibrary.presentation.settings.*
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Break vectors: enlarged text over fixed artwork; swipe/action identity races; nested card actions
 * triggering playback; Unicode query clearing; shelf-title navigation lost during restyling;
 * disabled playback override becoming interactive. These are device UI tests, separate from the
 * in-memory unit suites.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
class PolishUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun detailsFadeStartsAfterDelayedLoadingAndDoesNotRestartOnHistoryUpdate() {
        var state by
            androidx.compose.runtime.mutableStateOf(
                com.singam.lionlibrary.presentation.details.DetailsState(isLoading = true)
            )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LionLibraryTheme {
                Box(
                    Modifier.background(
                        androidx.compose.material3.MaterialTheme.colorScheme.background
                    )
                ) {
                    com.singam.lionlibrary.presentation.details.DetailsScreen(
                        state,
                        WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp)),
                        {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { state = state.copy(media = film, isLoading = false) }
        fun brightness(): Float {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var total = 0f
            var count = 0
            for (y in 0 until pixels.height step 8) {
                for (x in 0 until pixels.width step 8) {
                    val color = pixels[x, y]
                    total += color.red + color.green + color.blue
                    count++
                }
            }
            return total / count
        }
        compose.mainClock.advanceTimeBy(32)
        val initial = brightness()
        snapshot("details-fade-start")
        compose.mainClock.advanceTimeBy(64)
        val midway = brightness()
        snapshot("details-fade-midway")
        compose.mainClock.advanceTimeBy(240)
        val finished = brightness()
        snapshot("details-fade-finished")
        assertTrue("Loaded content does not fade in", midway > initial + .01f)
        assertTrue("Fade completed immediately", finished > midway + .001f)
        compose.runOnIdle { state = state.copy(moviePositionMs = 1000) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("History updates restart the entrance fade", brightness() > finished * .9f)
    }

    @Test
    fun longHeroGenresUseOneCompleteLineAtNormalAndEnlargedText() {
        var scale by androidx.compose.runtime.mutableFloatStateOf(1.15f)
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, scale)
                ) {
                    Box(Modifier.width(328.dp)) {
                        com.singam.lionlibrary.presentation.home.HeroMetadataLine(
                            "2023",
                            "Animation, Science Fiction and Fantasy Adventure",
                            "★ 8.1",
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1.15f, 2f)) {
            compose.runOnIdle { scale = fontScale }
            val node = compose.onNode(hasText("2023", substring = true))
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            ) {
                it(layouts)
            }
            val layout = layouts.single()
            assertEquals(1, layout.lineCount)
            assertFalse(layout.hasVisualOverflow)
            assertFalse(layout.layoutInput.text.text.contains("Science Fiction"))
            node.assertIsDisplayed()
        }
        snapshot("hero-one-line-metadata")
    }

    private val film =
        MediaItem(
            id = 1,
            tmdbId = null,
            title = "First film",
            originalTitle = null,
            overview = null,
            posterPath = null,
            backdropPath = null,
            genres = "Animation, Drama",
            rating = 8f,
            year = 2023,
            mediaType = MediaType.MOVIE,
            matchConfidence = 1f,
            isUnidentified = false,
            lastUpdated = 1,
        )

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder =
            java.io
                .File(instrumentation.targetContext.getExternalFilesDir(null), "reviewshots")
                .apply { mkdirs() }
        java.io.File(folder, "polish-$name.png").outputStream().use {
            instrumentation.uiAutomation
                .takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun heroLargeTextStaysAboveActionAndSwipeOpensSettledTitle() {
        var opened: Long? = null
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    Box(Modifier.width(360.dp)) {
                        HeroBannerCarousel(
                            listOf(film, film.copy(id = 2, title = "Second film")),
                            { id, _ -> opened = id },
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText("First film").assertIsDisplayed().fetchSemanticsNode()
        val action = compose.onNodeWithText("View Details").assertIsDisplayed().fetchSemanticsNode()
        assertTrue(
            "Hero title overlaps its action",
            title.boundsInRoot.bottom <= action.boundsInRoot.top,
        )
        snapshot("hero-large-text")
        compose.onNode(hasScrollAction()).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Second film").assertIsDisplayed()
        compose.onNodeWithText("View Details").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2L, opened) }
    }

    @Test
    fun enlargedResumeCardKeepsCaptionAndLongPressIndependentOfPlay() {
        var played = 0
        var options = 0
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    JumpBackInCard(
                        JumpBackInItem(
                            1,
                            MediaType.ANIME,
                            "Frieren",
                            null,
                            null,
                            2,
                            "A new journey",
                            2,
                            1,
                            null,
                            .5f,
                            filePath = "content://fixture/episode",
                            lastWatched = 1,
                        ),
                        { played++ },
                        onLongClick = { options++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Frieren").assertIsDisplayed()
        compose.onNodeWithText("S2 E1").assertIsDisplayed()
        snapshot("resume-large-text")
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
        compose.onNodeWithText("A new journey").assertIsDisplayed()
        compose.onNodeWithText("Frieren").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, options)
            assertEquals(0, played)
        }
        compose.onNodeWithText("Frieren").performClick()
        compose.runOnIdle { assertEquals(1, played) }
    }

    @Test
    fun unicodeSearchCanBeClearedWithoutChangingCategory() {
        var action: LibraryAction? = null
        compose.setContent {
            LionLibraryTheme {
                LibraryScreen(
                    LibraryState(
                        query = LibraryQuery(text = "葬送のフリーレン 🌙", type = MediaFilter.ANIME),
                        isLoading = false,
                    ),
                    { action = it },
                    { _, _ -> },
                    {},
                )
            }
        }
        compose.onNodeWithText("Anime").assertIsSelected()
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.runOnIdle { assertEquals(LibraryAction.QueryChanged(""), action) }
        compose.onNodeWithText("Anime").assertIsSelected()
    }

    @Test
    fun entireShelfHeadingStillOpensItsCategory() {
        var opened = false
        compose.setContent {
            LionLibraryTheme { MediaRow("Movies", listOf(film), {}, { opened = true }) }
        }
        compose.onNodeWithText("Movies").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun externalOverrideKeepsVlcSwitchDisabledAtLargeText() {
        var action: SettingsAction? = null
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    SettingsScreen(
                        SettingsState(forceExternalPlayer = true),
                        { action = it },
                        {},
                        {},
                    )
                }
            }
        }
        val label = context.getString(com.singam.lionlibrary.R.string.force_vlc)
        compose.onNodeWithContentDescription(label).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertNull(action) }
    }

    @Test
    fun navigationLabelsRemainCompleteAndSelectableAtLargeText() {
        var selected: Int? = null
        compose.setContent {
            LionLibraryTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 2f)
                ) {
                    Box(Modifier.width(360.dp)) {
                        com.singam.lionlibrary.presentation.navigation.BottomNavBarContent(
                            0,
                            { selected = it },
                        )
                    }
                }
            }
        }
        for (label in listOf("Home", "Library", "Settings")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            ) {
                it(layouts)
            }
            val layout = layouts.single()
            assertEquals("Navigation label wraps: $label", 1, layout.lineCount)
            assertEquals(
                "Navigation label is clipped: $label",
                label.length,
                layout.getLineEnd(layout.lineCount - 1, true),
            )
        }
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Settings").performClick()
        compose.runOnIdle { assertEquals(2, selected) }
        snapshot("navigation-large-text")
    }

    @Test
    fun detailsToolbarWaitsForPrimaryActionAndHandlesJumpScroll() {
        val episode = Episode(20, 1, 1, 1, "Pilot", null, null, null, null, "content://fixture/20")
        compose.setContent {
            LionLibraryTheme {
                com.singam.lionlibrary.presentation.details.DetailsScreen(
                    com.singam.lionlibrary.presentation.details.DetailsState(
                        media =
                            film.copy(
                                mediaType = MediaType.TV_SHOW,
                                overview = "A synopsis. ".repeat(200),
                            ),
                        seasons = listOf(Season(1, 1, 1, "Season 1", null)),
                        episodes =
                            (1..20).map { episode.copy(id = it.toLong(), episodeNumber = it) },
                        nextEpisodeToWatch = episode,
                        isLoading = false,
                    ),
                    WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp)),
                    {},
                )
            }
        }
        compose.onNodeWithTag("details-toolbar-hidden").assertExists()
        compose.onNodeWithTag("details-list").performTouchInput {
            swipe(start = center, end = center.copy(y = center.y - 80f), durationMillis = 500)
        }
        compose.onNodeWithTag("details-toolbar-hidden").assertExists()
        compose.onNodeWithTag("details-list").performScrollToIndex(1)
        compose.onNodeWithTag("details-toolbar-visible").assertExists()
        compose.onNodeWithTag("details-list").performScrollToIndex(0)
        compose.onNodeWithTag("details-toolbar-hidden").assertExists()
    }
}
