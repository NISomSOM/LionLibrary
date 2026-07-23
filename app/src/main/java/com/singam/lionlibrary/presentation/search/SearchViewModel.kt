package com.singam.lionlibrary.presentation.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.usecase.SearchMediaUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


@Stable
data class SearchState(
    val query: String = "",
    val activeFilter: MediaFilter = MediaFilter.ALL,
    val results: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false
)


sealed interface SearchAction {
    data class OnQueryChange(val query: String) : SearchAction
    data class OnFilterChange(val filter: MediaFilter) : SearchAction
    data class OnMediaClick(val mediaId: Long, val mediaType: MediaType) : SearchAction
}


sealed interface SearchEvent {
    data class NavigateToMovieDetails(val mediaId: Long) : SearchEvent
    data class NavigateToShowDetails(val mediaId: Long) : SearchEvent
}


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchMediaUseCase: SearchMediaUseCase,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private val _events = Channel<SearchEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>("filter", null).collect { filterStr ->
                val filter = filterStr?.let { str ->
                    MediaFilter.entries.find { it.name == str }
                } ?: MediaFilter.ALL
                _state.update { it.copy(activeFilter = filter) }
            }
        }

        viewModelScope.launch {
            val queryFlow = _state.map { it.query }.distinctUntilChanged().debounce(300)
            val filterFlow = _state.map { it.activeFilter }.distinctUntilChanged()
            
            // Get all items once as a single flow
            val allMediaFlow = searchMediaUseCase("", MediaFilter.ALL)

            combine(allMediaFlow, queryFlow, filterFlow) { allMedia, query, filter ->
                _state.update { it.copy(isSearching = true) }
                
                val filteredByQuery = if (query.isBlank()) {
                    allMedia
                } else {
                    allMedia.filter { 
                        it.title.contains(query, ignoreCase = true) || 
                        it.overview?.contains(query, ignoreCase = true) == true ||
                        it.genres?.contains(query, ignoreCase = true) == true
                    }
                }
                
                val filteredByFilter = when(filter) {
                    MediaFilter.ALL -> filteredByQuery
                    MediaFilter.MOVIES -> filteredByQuery.filter { it.mediaType == MediaType.MOVIE }
                    MediaFilter.TV_SHOWS -> filteredByQuery.filter { it.mediaType == MediaType.TV_SHOW }
                    MediaFilter.ANIME -> filteredByQuery.filter { it.mediaType == MediaType.ANIME }
                }
                
                filteredByFilter
            }
            .collect { results ->
                _state.update { it.copy(results = results, isSearching = false) }
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.OnQueryChange -> {
                _state.update { it.copy(query = action.query) }
            }
            is SearchAction.OnFilterChange -> {
                _state.update { it.copy(activeFilter = action.filter) }
            }
            is SearchAction.OnMediaClick -> {
                viewModelScope.launch {
                    if (action.mediaType == MediaType.MOVIE) {
                        _events.send(SearchEvent.NavigateToMovieDetails(action.mediaId))
                    } else {
                        _events.send(SearchEvent.NavigateToShowDetails(action.mediaId))
                    }
                }
            }
        }
    }
}

