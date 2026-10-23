package com.singam.lionlibrary.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.presentation.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @Test
    fun `restored filters are not overwritten by the original route`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val repository = FakeMediaRepository()
                val handle =
                    SavedStateHandle(
                        mapOf("type" to "ANIME", "query" to "😀 %_", "watchlist" to true)
                    )
                val vm = LibraryViewModel(repository, handle)
                vm.setInitialFilter(MediaFilter.MOVIES)
                advanceTimeBy(201)
                runCurrent()
                assertThat(repository.queries.last())
                    .isEqualTo(
                        LibraryQuery(text = "😀 %_", type = MediaFilter.ANIME, watchlistOnly = true)
                    )
                vm.viewModelScope.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `rapid searches cancel the previous database collection`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val repo = FakeMediaRepository()
                val vm = LibraryViewModel(repo, SavedStateHandle())
                vm.state.test {
                    awaitItem()
                    advanceTimeBy(201)
                    runCurrent()
                    cancelAndIgnoreRemainingEvents()
                }
                vm.onAction(LibraryAction.QueryChanged("slow"))
                advanceTimeBy(201)
                runCurrent()
                vm.onAction(LibraryAction.QueryChanged("latest"))
                advanceTimeBy(201)
                runCurrent()
                assertThat(repo.cancelled).contains("slow")
                assertThat(repo.queries.last().text).isEqualTo("latest")
                vm.viewModelScope.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }
}

internal class FakeMediaRepository : MediaRepository {
    var byType: Map<MediaType, List<MediaItem>> = emptyMap()
    val queries = mutableListOf<LibraryQuery>()
    val cancelled = mutableListOf<String>()

    override fun browse(query: LibraryQuery) =
        flow<List<MediaItem>> {
            queries += query
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                cancelled += query.text
            }
        }

    override fun getUnidentified() = flowOf(emptyList<MediaItem>())

    override suspend fun toggleWatchlist(id: Long) {}

    override fun getByType(type: MediaType) = flowOf(byType[type].orEmpty())

    override fun search(query: String, filter: MediaFilter) =
        browse(LibraryQuery(text = query, type = filter))

    override fun getRecentlyAdded() = flowOf(emptyList<MediaItem>())

    override suspend fun getById(id: Long): MediaItem? = null

    override suspend fun insert(media: MediaItem) = 1L

    override suspend fun getByTmdbId(tmdbId: Int): MediaItem? = null

    override fun getSeasonsForShow(showId: Long) = flowOf(emptyList<Season>())

    override fun getEpisodesForSeason(showId: Long, seasonNumber: Int) =
        flowOf(emptyList<Episode>())

    override fun getAllEpisodesForShow(showId: Long) = flowOf(emptyList<Episode>())

    override suspend fun getEpisodeById(episodeId: Long): Episode? = null

    override suspend fun insertSeason(season: Season) = 1L

    override suspend fun insertEpisode(episode: Episode) = 1L

    override suspend fun getEpisodeByFilePath(filePath: String): Episode? = null

    override suspend fun getByFilePath(filePath: String): MediaItem? = null
}
