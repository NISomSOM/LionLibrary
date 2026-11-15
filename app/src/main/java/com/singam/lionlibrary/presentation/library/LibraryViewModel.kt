package com.singam.lionlibrary.presentation.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.presentation.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryState(
    val query: LibraryQuery = LibraryQuery(),
    val yearInput: String = "",
    val filtersVisible: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val genres: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface LibraryAction {
    data class QueryChanged(val value: String) : LibraryAction

    data class TypeChanged(val value: MediaFilter) : LibraryAction

    data class SortChanged(val value: LibrarySort) : LibraryAction

    data class WatchChanged(val value: WatchFilter) : LibraryAction

    data class GenreChanged(val value: String) : LibraryAction

    data class YearChanged(val value: String) : LibraryAction

    data object ToggleWatchlist : LibraryAction

    data object ToggleFilters : LibraryAction

    data class ToggleItemWatchlist(val id: Long) : LibraryAction

    data object Reset : LibraryAction
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(private val media: MediaRepository, private val saved: SavedStateHandle) :
    ViewModel() {
    private val initial =
        LibraryQuery(
            text = saved["query"] ?: "",
            type =
                MediaFilter.entries.find { it.name == saved.get<String>("type") }
                    ?: MediaFilter.ALL,
            genre = saved["genre"] ?: "",
            year = saved.get<String>("year")?.toIntOrNull(),
            watch =
                WatchFilter.entries.find { it.name == saved.get<String>("watch") }
                    ?: WatchFilter.ALL,
            sort =
                LibrarySort.entries.find { it.name == saved.get<String>("sort") }
                    ?: LibrarySort.TITLE_ASC,
            watchlistOnly = saved["watchlist"] ?: false,
        )
    // Screen-local query/filter choices. Results are merged in only while the screen collects.
    private val local =
        MutableStateFlow(LibraryState(query = initial, yearInput = saved["year"] ?: ""))

    private var lastGenres: List<String> = emptyList()
    private var lastResult: BrowseResult? = null

    private val genres: Flow<List<String>> =
        media
            .browse(LibraryQuery())
            .map { items ->
                items
                    .flatMap { it.genres?.split(",").orEmpty() }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
            }
            .distinctUntilChanged()
            .catch { emit(emptyList()) }
            .onEach { lastGenres = it }
            // A restarted collection starts from the last answer, never from an empty placeholder.
            .onStart { emit(lastGenres) }

    private val results: Flow<BrowseResult?> =
        local
            .map { it.query }
            .distinctUntilChanged()
            .debounce(200)
            .flatMapLatest { query ->
                media
                    .browse(query)
                    .map<List<MediaItem>, BrowseResult?> { BrowseResult(query, it, null) }
                    .catch {
                        emit(
                            BrowseResult(
                                query,
                                emptyList(),
                                UiText.Resource(R.string.error_unknown),
                            )
                        )
                    }
            }
            .onEach { lastResult = it }
            .onStart { emit(lastResult) }

    /**
     * Collected only while the UI is subscribed (5 s grace). Loading means "no result yet for the
     * current query", so resubscribing after playback keeps showing the cached results instead of a
     * loading state, and scroll position/filters stay as they were.
     */
    val state: StateFlow<LibraryState> =
        combine(local, genres, results) { screen, genreList, result ->
                val current = result?.query == screen.query
                screen.copy(
                    genres = genreList,
                    items = result?.items ?: screen.items,
                    isLoading = !current,
                    error = screen.error ?: result?.error.takeIf { current },
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                local.value,
            )

    fun setInitialFilter(filter: MediaFilter) {
        if (!saved.contains("type")) updateQuery { it.copy(type = filter) }
    }

    private fun updateQuery(transform: (LibraryQuery) -> LibraryQuery) {
        local.update { it.copy(query = transform(it.query), error = null) }
        val query = local.value.query
        saved["query"] = query.text
        saved["type"] = query.type.name
        saved["genre"] = query.genre
        saved["watch"] = query.watch.name
        saved["sort"] = query.sort.name
        saved["watchlist"] = query.watchlistOnly
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.ToggleItemWatchlist ->
                viewModelScope.launch {
                    try {
                        media.toggleWatchlist(action.id)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        local.update { it.copy(error = UiText.Resource(R.string.error_storage)) }
                    }
                }
            is LibraryAction.QueryChanged -> updateQuery { it.copy(text = action.value) }
            is LibraryAction.TypeChanged -> updateQuery { it.copy(type = action.value) }
            is LibraryAction.SortChanged -> updateQuery { it.copy(sort = action.value) }
            is LibraryAction.WatchChanged -> updateQuery { it.copy(watch = action.value) }
            is LibraryAction.GenreChanged -> updateQuery { it.copy(genre = action.value) }
            is LibraryAction.YearChanged -> {
                saved["year"] = action.value
                local.update { it.copy(yearInput = action.value) }
                updateQuery { it.copy(year = action.value.toIntOrNull()) }
            }
            LibraryAction.ToggleWatchlist ->
                updateQuery { it.copy(watchlistOnly = !it.watchlistOnly) }
            LibraryAction.ToggleFilters ->
                local.update { it.copy(filtersVisible = !it.filtersVisible) }
            LibraryAction.Reset -> {
                saved["year"] = ""
                local.update { it.copy(yearInput = "") }
                updateQuery { LibraryQuery(watchlistOnly = it.watchlistOnly) }
            }
        }
    }
}

private data class BrowseResult(
    val query: LibraryQuery,
    val items: List<MediaItem>,
    val error: UiText?,
)
