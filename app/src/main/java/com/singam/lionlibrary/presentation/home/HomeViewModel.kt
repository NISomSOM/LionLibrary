package com.singam.lionlibrary.presentation.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


@Stable
data class HomeState(
    val featuredItem: MediaItem? = null,
    val jumpBackInItems: List<JumpBackInItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val tvShows: List<MediaItem> = emptyList(),
    val anime: List<MediaItem> = emptyList(),
    val recentlyAdded: List<MediaItem> = emptyList(),
    val genresContent: Map<String, List<MediaItem>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)


sealed interface HomeAction {
    data class OnMediaClick(val mediaId: Long, val mediaType: MediaType) : HomeAction
    data class OnPlayClick(val mediaId: Long, val mediaType: MediaType) : HomeAction
    data class OnJumpBackInClick(val filePath: String) : HomeAction
}


sealed interface HomeEvent {
    data class NavigateToMovieDetails(val mediaId: Long) : HomeEvent
    data class NavigateToShowDetails(val mediaId: Long) : HomeEvent
    data class ShowError(val message: String) : HomeEvent
    data class LaunchPlayer(val intent: android.content.Intent) : HomeEvent

}


class HomeViewModel(
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val launchPlayerUseCase: LaunchPlayerUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadContent()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnMediaClick -> {
                viewModelScope.launch {
                    if (action.mediaType == MediaType.MOVIE) {
                        _events.send(HomeEvent.NavigateToMovieDetails(action.mediaId))
                    } else {
                        _events.send(HomeEvent.NavigateToShowDetails(action.mediaId))
                    }
                }
            }
            is HomeAction.OnPlayClick -> {
                // For now, Play simply navigates to details where actual playback logic will live
                viewModelScope.launch {
                    if (action.mediaType == MediaType.MOVIE) {
                        _events.send(HomeEvent.NavigateToMovieDetails(action.mediaId))
                    } else {
                        _events.send(HomeEvent.NavigateToShowDetails(action.mediaId))
                    }
                }
            }
            is HomeAction.OnJumpBackInClick -> {
                if (action.filePath.isNotBlank()) {
                    try {
                        val uri = android.net.Uri.parse(action.filePath)
                        viewModelScope.launch {
                            val intent = launchPlayerUseCase(uri, 0L)
                            _events.send(HomeEvent.LaunchPlayer(intent))
                        }
                    } catch (e: Exception) {
                        viewModelScope.launch {
                            _events.send(HomeEvent.ShowError("Could not launch player"))
                        }
                    }
                } else {
                    viewModelScope.launch {
                        _events.send(HomeEvent.ShowError("File path not found"))
                    }
                }
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            getHomeContentUseCase()
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    _events.send(HomeEvent.ShowError(e.message ?: "Failed to load home content"))
                }
                .collect { content ->
                    _state.update { currentState ->
                        // Pick featured item once per session if not already picked
                        val allMedia = content.movies + content.tvShows + content.anime
                        val featured = currentState.featuredItem ?: allMedia.randomOrNull()
                        
                        val genreMap = mutableMapOf<String, MutableList<MediaItem>>()
                        allMedia.forEach { item ->
                            item.genres?.split(",")?.forEach { genre ->
                                val trimmedGenre = genre.trim()
                                if (trimmedGenre.isNotEmpty()) {
                                    genreMap.getOrPut(trimmedGenre) { mutableListOf() }.add(item)
                                }
                            }
                        }

                        currentState.copy(
                            featuredItem = featured,
                            jumpBackInItems = content.jumpBackInItems,
                            movies = content.movies,
                            tvShows = content.tvShows,
                            anime = content.anime,
                            recentlyAdded = content.recentlyAdded,
                            genresContent = genreMap.mapValues { it.value.toList() }.toSortedMap(),
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }
}

