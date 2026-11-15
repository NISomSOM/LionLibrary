package com.singam.lionlibrary.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.presentation.library.*
import com.singam.lionlibrary.testing.FakeMediaRepository
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
            val vm = LibraryViewModel(repository, handle)
            backgroundScope.launch { vm.state.collect {} }

            vm.setInitialFilter(MediaFilter.MOVIES)
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
            val vm = LibraryViewModel(repo, SavedStateHandle())
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
            val vm = LibraryViewModel(repo, SavedStateHandle())
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
            val vm = LibraryViewModel(repo, SavedStateHandle())
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
            val vm = LibraryViewModel(repo, SavedStateHandle())
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
}
