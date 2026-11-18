package com.singam.lionlibrary.presentation.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.DeletionTarget
import com.singam.lionlibrary.domain.repository.MediaDeletionRepository
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.usecase.MarkMediaWatchedUseCase
import com.singam.lionlibrary.presentation.common.DeletionPresenter
import com.singam.lionlibrary.presentation.common.DeletionUiState
import com.singam.lionlibrary.presentation.common.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
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
    val selectedMediaForOptions: MediaItem? = null,
    val deletion: DeletionUiState = DeletionUiState(),
)

sealed interface LibraryEvent {
    data class ShowMessage(val message: UiText) : LibraryEvent
}

sealed interface LibraryAction {
    /** Opens (or with null closes) the long-press options of a poster. */
    data class MediaOptions(val item: MediaItem?) : LibraryAction

    data class MarkWatched(val item: MediaItem) : LibraryAction

    data class RequestDelete(val mediaId: Long) : LibraryAction

    data object ConfirmDelete : LibraryAction

    data object DismissDelete : LibraryAction

    data class QueryChanged(val value: String) : LibraryAction

    data class TypeChanged(val value: MediaFilter) : LibraryAction

    data class SortChanged(val value: LibrarySort) : LibraryAction

    data class WatchChanged(val value: WatchFilter) : LibraryAction

    /** Selects or clears one genre chip; several can be selected at once. */
    data class GenreToggled(val genre: String) : LibraryAction

    data class YearChanged(val value: String) : LibraryAction

    data object ToggleWatchlist : LibraryAction

    data object ToggleFilters : LibraryAction

    data class ToggleItemWatchlist(val id: Long) : LibraryAction

    data object Reset : LibraryAction
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val media: MediaRepository,
    private val saved: SavedStateHandle,
    private val markMediaWatched: MarkMediaWatchedUseCase,
    deletionRepository: MediaDeletionRepository,
) : ViewModel() {
    private val _events = Channel<LibraryEvent>()
    val events = _events.receiveAsFlow()
    private val deletion =
        DeletionPresenter(
            deletionRepository,
            viewModelScope,
            {},
            { _events.send(LibraryEvent.ShowMessage(it)) },
        )

    private val initial =
        LibraryQuery(
            text = saved["query"] ?: "",
            type =
                MediaFilter.entries.find { it.name == saved.get<String>("type") }
                    ?: MediaFilter.ALL,
            genres =
                saved.get<ArrayList<String>>(GENRES_KEY)?.toSet()
                    // A single genre saved before multi-select.
                    ?: setOfNotNull(saved.get<String>("genre")?.takeIf { it.isNotBlank() }),
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
        MutableStateFlow(
            LibraryState(
                query = initial,
                yearInput = saved["year"] ?: "",
                filtersVisible = saved[FILTERS_VISIBLE_KEY] ?: false,
            )
        )

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

    init {
        viewModelScope.launch {
            deletion.state.collect { value -> local.update { it.copy(deletion = value) } }
        }
    }

    /**
     * Applies the type and genres a page was opened with (a Home shelf arrow, a Details related
     * row), once: a restored page keeps whatever the viewer changed since. Genres open the filter
     * panel so the selected chips are visible and can be cleared.
     */
    fun setInitialFilter(filter: MediaFilter?, genres: List<String>) {
        if ((filter == null && genres.isEmpty()) || saved.contains("type")) return
        if (genres.isNotEmpty()) setFiltersVisible(true)
        updateQuery { it.copy(type = filter ?: it.type, genres = genres.toSet()) }
    }

    private fun setFiltersVisible(visible: Boolean) {
        saved[FILTERS_VISIBLE_KEY] = visible
        local.update { it.copy(filtersVisible = visible) }
    }

    /** Clears everything the filter panel sets: sort, watch state, genres and year. */
    private fun clearPanelFilters() {
        saved["year"] = ""
        local.update { it.copy(yearInput = "") }
        updateQuery {
            it.copy(
                genres = emptySet(),
                year = null,
                watch = WatchFilter.ALL,
                sort = LibrarySort.TITLE_ASC,
            )
        }
    }

    private fun updateQuery(transform: (LibraryQuery) -> LibraryQuery) {
        local.update { it.copy(query = transform(it.query), error = null) }
        val query = local.value.query
        saved["query"] = query.text
        saved["type"] = query.type.name
        saved[GENRES_KEY] = ArrayList(query.genres)
        saved["watch"] = query.watch.name
        saved["sort"] = query.sort.name
        saved["watchlist"] = query.watchlistOnly
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.MediaOptions ->
                local.update { it.copy(selectedMediaForOptions = action.item) }
            is LibraryAction.MarkWatched -> {
                local.update { it.copy(selectedMediaForOptions = null) }
                viewModelScope.launch {
                    try {
                        markMediaWatched(action.item)
                        _events.send(
                            LibraryEvent.ShowMessage(UiText.Resource(R.string.marked_as_watched))
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        local.update { it.copy(error = UiText.Resource(R.string.error_storage)) }
                    }
                }
            }
            is LibraryAction.RequestDelete -> {
                local.update { it.copy(selectedMediaForOptions = null) }
                deletion.request(DeletionTarget(action.mediaId))
            }
            LibraryAction.ConfirmDelete -> deletion.confirm()
            LibraryAction.DismissDelete -> deletion.dismiss()
            is LibraryAction.ToggleItemWatchlist -> {
                local.update { it.copy(selectedMediaForOptions = null) }
                viewModelScope.launch {
                    try {
                        media.toggleWatchlist(action.id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        local.update { it.copy(error = UiText.Resource(R.string.error_storage)) }
                    }
                }
            }
            is LibraryAction.QueryChanged -> updateQuery { it.copy(text = action.value) }
            is LibraryAction.TypeChanged -> updateQuery { it.copy(type = action.value) }
            is LibraryAction.SortChanged -> updateQuery { it.copy(sort = action.value) }
            is LibraryAction.WatchChanged -> updateQuery { it.copy(watch = action.value) }
            is LibraryAction.GenreToggled ->
                updateQuery {
                    val selected = it.genres.any { genre -> genre.equals(action.genre, true) }
                    it.copy(
                        genres =
                            if (selected)
                                it.genres
                                    .filterNot { genre ->
                                        genre.equals(action.genre, true)
                                    }
                                    .toSet()
                            else it.genres + action.genre
                    )
                }
            is LibraryAction.YearChanged -> {
                saved["year"] = action.value
                local.update { it.copy(yearInput = action.value) }
                updateQuery { it.copy(year = action.value.toIntOrNull()) }
            }
            LibraryAction.ToggleWatchlist ->
                updateQuery { it.copy(watchlistOnly = !it.watchlistOnly) }
            LibraryAction.ToggleFilters -> {
                val closing = local.value.filtersVisible
                // Turning the chip off removes what its panel applied.
                if (closing) clearPanelFilters()
                setFiltersVisible(!closing)
            }
            LibraryAction.Reset -> {
                saved["year"] = ""
                local.update { it.copy(yearInput = "") }
                updateQuery { LibraryQuery(watchlistOnly = it.watchlistOnly) }
            }
        }
    }
}

// Not "genres": that is the route argument, which Navigation stores in the same handle as an array.
private const val GENRES_KEY = "selectedGenres"
private const val FILTERS_VISIBLE_KEY = "filtersVisible"

private data class BrowseResult(
    val query: LibraryQuery,
    val items: List<MediaItem>,
    val error: UiText?,
)
