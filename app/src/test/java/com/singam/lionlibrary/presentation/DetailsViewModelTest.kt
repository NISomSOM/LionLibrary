package com.singam.lionlibrary.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.Season
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.details.DetailsAction
import com.singam.lionlibrary.presentation.details.DetailsEvent
import com.singam.lionlibrary.presentation.details.DetailsViewModel
import com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter
import com.singam.lionlibrary.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Break vectors: Room collected while the player covers Details, a loading state (and so a replayed
 * loaded fade) after returning, a watchlist toggle that is not reflected or needs a re-read, rows
 * removed while open, an id of 0 from a broken route, a restored season that no longer exists,
 * Letterboxd fetched again on every return or for shows, playing a movie with no file, and watched
 * toggles/"up to here" writing the wrong episode ids.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val media = FakeMediaRepository()
    private val history = FakeWatchProgressRepository()
    private val letterboxd = FakeLetterboxdRepository()
    private val settings = FakeSettingsRepository()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: Long, season: Int? = null) =
        DetailsViewModel(
            SavedStateHandle(
                buildMap {
                    put("mediaId", id)
                    if (season != null) put("season", season)
                }
            ),
            media,
            history,
            UnusedLaunchPlayer(),
            letterboxd,
            ExternalPlaybackRouter(settings, media, history, UnusedLaunchPlayer()),
            FakeDeletionRepository(),
        )

    private fun show(id: Long = 3) {
        media.put(media(id, MediaType.TV_SHOW))
        media.seasons.value =
            mapOf(id to listOf(Season(1, id, 1, "S1", null), Season(2, id, 2, "S2", null)))
        media.episodes.value =
            mapOf(
                id to
                    listOf(
                        episode(31, id, 1, 1, end = 2),
                        episode(33, id, 1, 3),
                        episode(41, id, 2, 1),
                    )
            )
    }

    @Test
    fun `nothing is observed until collected and observation stops after the grace period`() =
        runTest(dispatcher) {
            media.put(media(7))
            val vm = viewModel(7)
            assertThat(media.subscriptions.active.value).isEqualTo(0)

            val collector = backgroundScope.launch { vm.state.collect {} }
            assertThat(vm.state.value.media?.id).isEqualTo(7L)
            collector.cancel()
            advanceTimeBy(5_001)

            assertThat(media.subscriptions.active.value).isEqualTo(0)
            assertThat(history.subscriptions.active.value).isEqualTo(0)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `returning from playback shows cached content first, then fresh progress`() =
        runTest(dispatcher) {
            media.put(media(7))
            val vm = viewModel(7)
            val first = backgroundScope.launch { vm.state.collect {} }
            first.cancel()
            advanceTimeBy(10_000)
            history.seed(7, 0, positionMs = 65_000)

            vm.state.test {
                val cached = awaitItem()
                assertThat(cached.isLoading).isFalse()
                assertThat(cached.media?.id).isEqualTo(7L)
                cancelAndIgnoreRemainingEvents()
            }
            // StateFlow may conflate the cached and fresh values; the fresh one must win.
            backgroundScope.launch { vm.state.collect {} }
            assertThat(vm.state.value.moviePositionMs).isEqualTo(65_000L)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `watchlist toggle arrives through the observed row`() =
        runTest(dispatcher) {
            media.put(media(7))
            val vm = viewModel(7)
            backgroundScope.launch { vm.state.collect {} }

            vm.onAction(DetailsAction.ToggleWatchlist)

            assertThat(media.toggled).containsExactly(7L)
            assertThat(vm.state.value.media?.isWatchlisted).isEqualTo(true)
            assertThat(vm.state.value.isLoading).isFalse()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `history written elsewhere updates watched state live`() =
        runTest(dispatcher) {
            media.put(media(7))
            val vm = viewModel(7)
            backgroundScope.launch { vm.state.collect {} }
            assertThat(vm.state.value.isMovieWatched).isFalse()

            history.seed(7, 0, positionMs = 0, completed = true)

            assertThat(vm.state.value.isMovieWatched).isTrue()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `row removed while open shows the missing-file error`() =
        runTest(dispatcher) {
            media.put(media(7))
            val vm = viewModel(7)
            backgroundScope.launch { vm.state.collect {} }

            media.items.update { it - 7L }

            assertThat(vm.state.value.media).isNull()
            assertThat(vm.state.value.error).isEqualTo(UiText.Resource(R.string.file_access_error))
            vm.viewModelScope.cancel()
        }

    @Test
    fun `zero id from a broken route is an error, not an endless spinner`() =
        runTest(dispatcher) {
            val vm = viewModel(0)
            backgroundScope.launch { vm.state.collect {} }

            assertThat(vm.state.value.isLoading).isFalse()
            assertThat(vm.state.value.error).isEqualTo(UiText.Resource(R.string.file_access_error))
            assertThat(letterboxd.refreshed).isEmpty()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `restored season that no longer exists falls back to the first season`() =
        runTest(dispatcher) {
            show()
            val vm = viewModel(3, season = 9)
            backgroundScope.launch { vm.state.collect {} }

            assertThat(vm.state.value.selectedSeasonNumber).isEqualTo(1)
            assertThat(vm.state.value.episodes.map { it.id }).containsExactly(31L, 33L)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `season choice is saved and switches the episode list`() =
        runTest(dispatcher) {
            show()
            val handleVm = viewModel(3)
            backgroundScope.launch { handleVm.state.collect {} }

            handleVm.onAction(DetailsAction.OnSeasonSelected(2))

            assertThat(handleVm.state.value.selectedSeasonNumber).isEqualTo(2)
            assertThat(handleVm.state.value.episodes.map { it.id }).containsExactly(41L)
            handleVm.viewModelScope.cancel()
        }

    @Test
    fun `letterboxd is refreshed once for an identified movie and never for shows`() =
        runTest(dispatcher) {
            media.put(media(7))
            show()
            val movie = viewModel(7)
            val collector = backgroundScope.launch { movie.state.collect {} }
            collector.cancel()
            advanceTimeBy(10_000)
            backgroundScope.launch { movie.state.collect {} }
            val series = viewModel(3)

            assertThat(letterboxd.refreshed).containsExactly(7L)
            movie.viewModelScope.cancel()
            series.viewModelScope.cancel()
        }

    @Test
    fun `play movie marks it started before navigating, and a missing file is an error`() =
        runTest(dispatcher) {
            media.put(media(7), media(8, filePath = null))
            val playable = viewModel(7)
            backgroundScope.launch { playable.state.collect {} }
            playable.events.test {
                playable.onAction(DetailsAction.OnPlayMovie)
                assertThat(awaitItem()).isEqualTo(DetailsEvent.NavigateToPlayer("MOVIE", 7))
            }
            assertThat(history.calls).containsExactly("started:7:0")

            val missing = viewModel(8)
            backgroundScope.launch { missing.state.collect {} }
            missing.events.test {
                missing.onAction(DetailsAction.OnPlayMovie)
                assertThat(awaitItem())
                    .isEqualTo(DetailsEvent.ShowError(UiText.Resource(R.string.file_access_error)))
            }
            playable.viewModelScope.cancel()
            missing.viewModelScope.cancel()
        }

    @Test
    fun `episode watched toggle flips the exact episode both ways`() =
        runTest(dispatcher) {
            show()
            val vm = viewModel(3)
            backgroundScope.launch { vm.state.collect {} }

            vm.onAction(DetailsAction.OnMarkEpisodeWatchedToggle(33))
            assertThat(vm.state.value.watchedEpisodeIds).containsOnly(33L)
            vm.onAction(DetailsAction.OnMarkEpisodeWatchedToggle(33))

            assertThat(history.calls).containsExactly("watched:3:33", "unwatched:3:33")
            assertThat(vm.state.value.watchedEpisodeIds).isEmpty()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `watched up to includes the combined first file and earlier seasons only`() =
        runTest(dispatcher) {
            show()
            val vm = viewModel(3)
            backgroundScope.launch { vm.state.collect {} }

            vm.onAction(DetailsAction.OnMarkWatchedUpTo(episode(33, 3, 1, 3)))

            assertThat(history.calls).containsExactly("watchedAll:3:31,33")
            vm.viewModelScope.cancel()
        }

    @Test
    fun `resume target follows history, next unwatched after a finished combined file`() =
        runTest(dispatcher) {
            show()
            history.seed(3, 31, positionMs = 0, completed = true)
            val vm = viewModel(3)
            backgroundScope.launch { vm.state.collect {} }

            assertThat(vm.state.value.nextEpisodeToWatch?.id).isEqualTo(33L)
            vm.viewModelScope.cancel()
        }
}
