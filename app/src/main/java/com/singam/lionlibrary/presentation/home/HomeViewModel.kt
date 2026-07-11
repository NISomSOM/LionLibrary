package com.singam.lionlibrary.presentation.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.UpdateWatchProgressUseCase
import android.net.Uri

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
    data class OnJumpBackInClick(val item: JumpBackInItem) : HomeAction
    data class OnPlayExternal(val item: JumpBackInItem) : HomeAction
    data class OnStartFromBeginning(val item: JumpBackInItem) : HomeAction
    data class OnRemoveWatchProgress(val item: JumpBackInItem) : HomeAction
}


sealed interface HomeEvent {
    data class NavigateToMovieDetails(val mediaId: Long) : HomeEvent
    data class NavigateToShowDetails(val mediaId: Long) : HomeEvent
    data class NavigateToPlayer(val mediaType: String, val mediaId: Long) : HomeEvent
    data class ShowError(val message: String) : HomeEvent
    data class LaunchPlayer(val intent: android.content.Intent) : HomeEvent

}


class HomeViewModel(
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val updateWatchProgressUseCase: UpdateWatchProgressUseCase
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
                val targetId = if (action.item.mediaType == MediaType.MOVIE) action.item.mediaId else (action.item.episodeId ?: 0L)
                if (targetId > 0L) {
                    viewModelScope.launch {
                        _events.send(HomeEvent.NavigateToPlayer(action.item.mediaType.name, targetId))
                    }
                } else {
                    viewModelScope.launch {
                        _events.send(HomeEvent.ShowError("File path not found"))
                    }
                }
            }
            is HomeAction.OnPlayExternal -> {
                val path = action.item.filePath
                if (path != null) {
                    viewModelScope.launch {
                        try {
                            val intent = launchPlayerUseCase(Uri.parse(path), (action.item.progress ?: 0f).toLong())
                            _events.send(HomeEvent.LaunchPlayer(intent))
                        } catch (e: Exception) {
                            _events.send(HomeEvent.ShowError("Failed to launch external player"))
                        }
                    }
                }
            }
            is HomeAction.OnStartFromBeginning -> {
                val targetId = if (action.item.mediaType == MediaType.MOVIE) action.item.mediaId else (action.item.episodeId ?: 0L)
                if (targetId > 0L) {
                    viewModelScope.launch {
                        updateWatchProgressUseCase.markAsUnwatched(action.item.mediaId, if (action.item.mediaType == MediaType.MOVIE) 0L else targetId)
                        _events.send(HomeEvent.NavigateToPlayer(action.item.mediaType.name, targetId))
                    }
                }
            }
            is HomeAction.OnRemoveWatchProgress -> {
                viewModelScope.launch {
                    val targetId = if (action.item.mediaType == MediaType.MOVIE) 0L else (action.item.episodeId ?: 0L)
                    updateWatchProgressUseCase.markAsUnwatched(action.item.mediaId, targetId)
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

