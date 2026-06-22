package com.example.mediahub.presentation.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediahub.domain.model.MediaFilter
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.usecase.SearchMediaUseCase
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
    private val searchMediaUseCase: SearchMediaUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private val _events = Channel<SearchEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val queryFlow = _state.map { it.query }.distinctUntilChanged().debounce(300)
            val filterFlow = _state.map { it.activeFilter }.distinctUntilChanged()

            combine(queryFlow, filterFlow) { query, filter ->
                Pair(query, filter)
            }
            .flatMapLatest { (query, filter) ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    _state.update { it.copy(isSearching = true) }
                    searchMediaUseCase(query, filter)
                }
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
