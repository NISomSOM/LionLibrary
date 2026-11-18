package com.singam.lionlibrary.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.usecase.MarkMediaWatchedUseCase
import com.singam.lionlibrary.presentation.library.*
import com.singam.lionlibrary.testing.FakeDeletionRepository
import com.singam.lionlibrary.testing.FakeMediaRepository
import com.singam.lionlibrary.testing.FakeWatchProgressRepository
import com.singam.lionlibrary.testing.media
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Break vectors: restored filters overwritten by the route default, a slow query not cancelled by a
 * newer one, Room observed while no screen collects (playback on top), a loading flash and lost
 * results when the screen collects again, a failing query that leaves a spinner, a stale error that
 * survives a new query, and unicode/wildcard text reaching the repository unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `restored filters are not overwritten by the original route`() =
        runTest(dispatcher) {
            val repository = FakeMediaRepository()
            val handle =
                SavedStateHandle(mapOf("type" to "ANIME", "query" to "😀 %_", "watchlist" to true))
            val vm =
                LibraryViewModel(
                    repository,
                    handle,
                    MarkMediaWatchedUseCase(repository, FakeWatchProgressRepository()),
                    FakeDeletionRepository(),
                )
            backgroundScope.launch { vm.state.collect {} }

            vm.setInitialFilter(MediaFilter.MOVIES, listOf("Drama"))
            advanceTimeBy(201)
            runCurrent()

            assertThat(repository.queries.last())
                .isEqualTo(
                    LibraryQuery(text = "😀 %_", type = MediaFilter.ANIME, watchlistOnly = true)
                )
            vm.viewModelScope.cancel()
        }

    @Test
    fun `rapid searches cancel the previous database collection`() =
        runTest(dispatcher) {
            val repo = FakeMediaRepository()
            val vm =
                LibraryViewModel(
                    repo,
                    SavedStateHandle(),
                    MarkMediaWatchedUseCase(repo, FakeWatchProgressRepository()),
                    FakeDeletionRepository(),
                )
            backgroundScope.launch { vm.state.collect {} }

            vm.onAction(LibraryAction.QueryChanged("slow"))
            advanceTimeBy(201)
            runCurrent()
            vm.onAction(LibraryAction.QueryChanged("latest"))
            advanceTimeBy(201)
            runCurrent()

            assertThat(repo.cancelled).contains("slow")
            assertThat(repo.queries.last().text).isEqualTo("latest")
            vm.viewModelScope.cancel()
        }

    @Test
    fun `nothing is observed until the screen collects and it stops after the grace period`() =
        runTest(dispatcher) {
            val repo = FakeMediaRepository()
            val vm =
                LibraryViewModel(
                    repo,
                    SavedStateHandle(),
                    MarkMediaWatchedUseCase(repo, FakeWatchProgressRepository()),
                    FakeDeletionRepository(),
                )
            advanceTimeBy(1_000)
            assertThat(repo.subscriptions.active.value).isEqualTo(0)

            val collector = backgroundScope.launch { vm.state.collect {} }
            advanceTimeBy(201)
            assertThat(repo.subscriptions.active.value).isGreaterThan(0)

            collector.cancel()
            advanceTimeBy(4_999)
            assertThat(repo.subscriptions.active.value).isGreaterThan(0)
            advanceTimeBy(2)
            assertThat(repo.subscriptions.active.value).isEqualTo(0)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `collecting again after playback shows cached results without a loading state`() =
        runTest(dispatcher) {
            val repo = FakeMediaRepository().apply { browseAnswer = { listOf(media(1)) } }
            val vm =
                LibraryViewModel(
                    repo,
                    SavedStateHandle(),
                    MarkMediaWatchedUseCase(repo, FakeWatchProgressRepository()),
                    FakeDeletionRepository(),
                )
            val first = backgroundScope.launch { vm.state.collect {} }
            advanceTimeBy(201)
            assertThat(vm.state.value.items.map { it.id }).containsExactly(1L)
            first.cancel()
            advanceTimeBy(10_000)

            vm.state.test {
                val resumed = awaitItem()
                assertThat(resumed.isLoading).isFalse()
                assertThat(resumed.items.map { it.id }).containsExactly(1L)
                advanceTimeBy(201)
                expectNoEvents()
            }
            vm.viewModelScope.cancel()
        }

    @Test
    fun `failed query clears the spinner and a new query clears the error`() =
        runTest(dispatcher) {
            val repo = FakeMediaRepository().apply { browseFailure = IllegalStateException("db") }
            val vm =
                LibraryViewModel(
                    repo,
                    SavedStateHandle(),
                    MarkMediaWatchedUseCase(repo, FakeWatchProgressRepository()),
                    FakeDeletionRepository(),
                )
            backgroundScope.launch { vm.state.collect {} }
            advanceTimeBy(201)

            assertThat(vm.state.value.isLoading).isFalse()
            assertThat(vm.state.value.error).isNotNull()

            repo.browseFailure = null
            vm.onAction(LibraryAction.QueryChanged("again"))
            assertThat(vm.state.value.error).isNull()
            assertThat(vm.state.value.isLoading).isTrue()
            advanceTimeBy(201)
            assertThat(vm.state.value.isLoading).isFalse()
            vm.viewModelScope.cancel()
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): LibraryViewModel {
        val repo = FakeMediaRepository()
        return LibraryViewModel(
            repo,
            handle,
            MarkMediaWatchedUseCase(repo, FakeWatchProgressRepository()),
            FakeDeletionRepository(),
        )
    }

    @Test
    fun `genre chips add up and a second tap in another case removes one`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.state.collect {} }
            vm.onAction(LibraryAction.GenreToggled("Drama"))
            vm.onAction(LibraryAction.GenreToggled("Science Fiction 🚀"))
            assertThat(vm.state.value.query.genres).containsOnly("Drama", "Science Fiction 🚀")

            vm.onAction(LibraryAction.GenreToggled("drama"))
            assertThat(vm.state.value.query.genres).containsOnly("Science Fiction 🚀")
            vm.viewModelScope.cancel()
        }

    @Test
    fun `turning the filters chip off removes what its panel applied and nothing else`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.state.collect {} }
            vm.onAction(LibraryAction.QueryChanged("frieren"))
            vm.onAction(LibraryAction.TypeChanged(MediaFilter.ANIME))
            vm.onAction(LibraryAction.ToggleWatchlist)
            vm.onAction(LibraryAction.ToggleFilters)
            vm.onAction(LibraryAction.SortChanged(LibrarySort.RATING))
            vm.onAction(LibraryAction.WatchChanged(WatchFilter.WATCHED))
            vm.onAction(LibraryAction.GenreToggled("Fantasy"))
            vm.onAction(LibraryAction.YearChanged("2023"))

            vm.onAction(LibraryAction.ToggleFilters)

            assertThat(vm.state.value.filtersVisible).isFalse()
            assertThat(vm.state.value.yearInput).isEmpty()
            assertThat(vm.state.value.query)
                .isEqualTo(
                    LibraryQuery(text = "frieren", type = MediaFilter.ANIME, watchlistOnly = true)
                )
            vm.viewModelScope.cancel()
        }

    @Test
    fun `genres from a shelf arrow open the panel with those chips selected`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.state.collect {} }
            vm.setInitialFilter(null, listOf("Drama", "Crime"))
            assertThat(vm.state.value.filtersVisible).isTrue()
            assertThat(vm.state.value.query.genres).containsOnly("Drama", "Crime")
            assertThat(vm.state.value.query.type).isEqualTo(MediaFilter.ALL)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `selected genres and the open panel survive process death`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            val collector = backgroundScope.launch { first.state.collect {} }
            first.onAction(LibraryAction.ToggleFilters)
            first.onAction(LibraryAction.GenreToggled("Drama"))
            first.onAction(LibraryAction.GenreToggled("Comedy"))
            collector.cancel()
            first.viewModelScope.cancel()

            val restored =
                viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
            backgroundScope.launch { restored.state.collect {} }
            assertThat(restored.state.value.query.genres).containsOnly("Drama", "Comedy")
            assertThat(restored.state.value.filtersVisible).isTrue()
            restored.viewModelScope.cancel()
        }

    @Test
    fun `a single genre saved before multi-select is restored as one chip`() =
        runTest(dispatcher) {
            val vm = viewModel(SavedStateHandle(mapOf("genre" to "Animation")))
            backgroundScope.launch { vm.state.collect {} }
            assertThat(vm.state.value.query.genres).containsOnly("Animation")
            vm.viewModelScope.cancel()
        }
}
