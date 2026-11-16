package com.singam.lionlibrary.presentation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.home.HomeAction
import com.singam.lionlibrary.presentation.home.HomeEvent
import com.singam.lionlibrary.presentation.home.HomeViewModel
import com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter
import com.singam.lionlibrary.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Break vectors: Room collected while playback covers Home, a loading flash or reshuffled hero
 * after returning, a hero pinned short by the first scan emission, a use-case failure that leaves a
 * spinner without a message, Jump Back In playing the wrong id (show vs episode) or no id, "remove"
 * deleting progress without hiding the title, and "start over" navigating before the reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val media = FakeMediaRepository()
    private val history = FakeWatchProgressRepository()
    private val settings = FakeSettingsRepository()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        HomeViewModel(
            GetHomeContentUseCase(media, history, UnconfinedTestDispatcher(testScheduler)),
            UnusedLaunchPlayer(),
            history,
            ExternalPlaybackRouter(settings, media, history, UnusedLaunchPlayer()),
            FakeDeletionRepository(),
        )

    private fun jump(mediaId: Long, type: MediaType, episodeId: Long?) =
        JumpBackInItem(
            mediaId = mediaId,
            mediaType = type,
            mediaTitle = "Show",
            posterPath = null,
            backdropPath = null,
            episodeId = episodeId,
            episodeTitle = null,
            seasonNumber = 1,
            episodeNumber = 1,
            thumbnailPath = null,
            progress = .5f,
            filePath = "content://fixture/x",
            lastWatched = 1,
        )

    @Test
    fun `room is observed only while the screen collects`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceTimeBy(1_000)
            assertThat(media.subscriptions.active.value).isEqualTo(0)

            val collector = backgroundScope.launch { vm.state.collect {} }
            assertThat(media.subscriptions.active.value).isGreaterThan(0)
            collector.cancel()
            advanceTimeBy(5_001)

            assertThat(media.subscriptions.active.value).isEqualTo(0)
            assertThat(history.subscriptions.active.value).isEqualTo(0)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `returning keeps content and hero order without a loading state`() =
        runTest(dispatcher) {
            media.byType = mapOf(MediaType.MOVIE to (1L..20L).map { media(it, genres = "Drama") })
            val vm = viewModel()
            val first = backgroundScope.launch { vm.state.collect {} }
            val hero = vm.state.value.carouselItems.map { it.id }
            assertThat(hero).hasSize(10)
            first.cancel()
            advanceTimeBy(10_000)

            vm.state.test {
                val resumed = awaitItem()
                assertThat(resumed.isLoading).isFalse()
                assertThat(resumed.carouselItems.map { it.id }).isEqualTo(hero)
                assertThat(resumed.genresContent.keys).containsOnly("Drama")
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(vm.state.value.carouselItems.map { it.id }).isEqualTo(hero)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `hero grows to ten when the first scan adds titles later`() =
        runTest(dispatcher) {
            media.byTypeFlow.value = mapOf(MediaType.MOVIE to listOf(media(1), media(2)))
            val vm = viewModel()
            backgroundScope.launch { vm.state.collect {} }
            val first = vm.state.value.carouselItems.map { it.id }
            assertThat(first).hasSize(2)

            media.byTypeFlow.value = mapOf(MediaType.MOVIE to (1L..30L).map { media(it) })

            val grown = vm.state.value.carouselItems.map { it.id }
            assertThat(grown).hasSize(10)
            assertThat(grown.take(2)).isEqualTo(first)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `use case failure stops loading and reports an error`() =
        runTest(dispatcher) {
            media.typeFailure = IllegalStateException("db closed")
            val vm = viewModel()

            vm.events.test {
                backgroundScope.launch { vm.state.collect {} }
                assertThat(awaitItem())
                    .isEqualTo(HomeEvent.ShowError(UiText.Resource(R.string.error_unknown)))
            }
            assertThat(vm.state.value.isLoading).isFalse()
            assertThat(vm.state.value.error).isEqualTo(UiText.Resource(R.string.error_unknown))
            vm.viewModelScope.cancel()
        }

    @Test
    fun `jump back in plays the movie id for movies and the episode id for shows`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.events.test {
                vm.onAction(HomeAction.OnJumpBackInClick(jump(7, MediaType.MOVIE, episodeId = 0)))
                assertThat(awaitItem()).isEqualTo(HomeEvent.NavigateToPlayer(MediaType.MOVIE, 7))
                vm.onAction(HomeAction.OnJumpBackInClick(jump(3, MediaType.ANIME, episodeId = 44)))
                assertThat(awaitItem()).isEqualTo(HomeEvent.NavigateToPlayer(MediaType.ANIME, 44))
            }
            vm.viewModelScope.cancel()
        }

    @Test
    fun `jump back in without an episode id shows an error instead of playing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.events.test {
                vm.onAction(HomeAction.OnJumpBackInClick(jump(3, MediaType.TV_SHOW, null)))
                assertThat(awaitItem())
                    .isEqualTo(HomeEvent.ShowError(UiText.Resource(R.string.file_access_error)))
            }
            vm.viewModelScope.cancel()
        }

    @Test
    fun `remove deletes that progress row and then hides the title`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onAction(HomeAction.OnRemoveWatchProgress(jump(3, MediaType.TV_SHOW, 44)))
            vm.onAction(HomeAction.OnRemoveWatchProgress(jump(7, MediaType.MOVIE, 99)))

            assertThat(history.calls)
                .containsExactly("delete:3:44", "hide:3", "delete:7:0", "hide:7")
            vm.viewModelScope.cancel()
        }

    @Test
    fun `start over resets the exact row before navigating`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.events.test {
                vm.onAction(HomeAction.OnStartFromBeginning(jump(3, MediaType.TV_SHOW, 44)))
                assertThat(history.calls).containsExactly("unwatched:3:44")
                assertThat(awaitItem()).isEqualTo(HomeEvent.NavigateToPlayer(MediaType.TV_SHOW, 44))
            }
            vm.viewModelScope.cancel()
        }

    @Test
    fun `options selection is screen state that survives content updates`() =
        runTest(dispatcher) {
            media.byTypeFlow.value = mapOf(MediaType.MOVIE to listOf(media(1)))
            val vm = viewModel()
            backgroundScope.launch { vm.state.collect {} }

            vm.onAction(HomeAction.MediaOptions(media(1)))
            media.byTypeFlow.value = mapOf(MediaType.MOVIE to listOf(media(1), media(2)))

            assertThat(vm.state.value.selectedMediaForOptions?.id).isEqualTo(1L)
            vm.onAction(HomeAction.MediaOptions(null))
            assertThat(vm.state.value.selectedMediaForOptions).isNull()
            vm.viewModelScope.cancel()
        }
}
