package com.singam.lionlibrary.presentation.home

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.UpdateWatchProgressUseCase
import com.singam.lionlibrary.presentation.common.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class HomeState(
    val carouselItems: List<MediaItem> = emptyList(),
    val jumpBackInItems: List<JumpBackInItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val tvShows: List<MediaItem> = emptyList(),
    val anime: List<MediaItem> = emptyList(),
    val recentlyAdded: List<MediaItem> = emptyList(),
    val genresContent: Map<String, List<MediaItem>> = emptyMap(),
    val selectedJumpBackInItem: JumpBackInItem? = null,
    val selectedMediaForOptions: MediaItem? = null,
    val deletion: com.singam.lionlibrary.presentation.common.DeletionUiState =
        com.singam.lionlibrary.presentation.common.DeletionUiState(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface HomeAction {
    data class MediaOptions(val item: MediaItem?) : HomeAction

    data class RequestDelete(val mediaId: Long) : HomeAction

    data object ConfirmDelete : HomeAction

    data object DismissDelete : HomeAction

    data class SelectOptions(val item: JumpBackInItem?) : HomeAction

    data class OnJumpBackInClick(val item: JumpBackInItem) : HomeAction

    data class OnPlayExternal(val item: JumpBackInItem) : HomeAction

    data class OnStartFromBeginning(val item: JumpBackInItem) : HomeAction

    data class OnRemoveWatchProgress(val item: JumpBackInItem) : HomeAction
}

// Plain click navigation (details, Library categories) is handled directly by HomeScreen.
sealed interface HomeEvent {
    data class ShowMessage(val message: UiText) : HomeEvent

    data class NavigateToPlayer(val mediaType: String, val mediaId: Long) : HomeEvent

    data class ShowError(val message: UiText) : HomeEvent

    data class LaunchPlayer(val intent: android.content.Intent) : HomeEvent
}

class HomeViewModel(
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val updateWatchProgressUseCase: UpdateWatchProgressUseCase,
    private val externalPlaybackRouter:
        com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter,
    deletionRepository: com.singam.lionlibrary.domain.repository.MediaDeletionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()
    private val deletion =
        com.singam.lionlibrary.presentation.common.DeletionPresenter(
            deletionRepository,
            viewModelScope,
            {},
            { _events.send(HomeEvent.ShowMessage(it)) },
        )

    init {
        viewModelScope.launch {
            deletion.state.collect { value -> _state.update { it.copy(deletion = value) } }
        }
        loadContent()
    }

    // In-app player, unless "Always use external player" is on.
    private suspend fun navigateToPlayer(
        mediaType: MediaType,
        targetId: Long,
        fromStart: Boolean = false,
    ) {
        val external = externalPlaybackRouter.intentIfForced(mediaType, targetId, fromStart)
        if (external != null) {
            _events.send(HomeEvent.LaunchPlayer(external))
        } else {
            _events.send(HomeEvent.NavigateToPlayer(mediaType.name, targetId))
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.MediaOptions ->
                _state.update { it.copy(selectedMediaForOptions = action.item) }
            is HomeAction.RequestDelete -> {
                _state.update {
                    it.copy(selectedMediaForOptions = null, selectedJumpBackInItem = null)
                }
                deletion.request(
                    com.singam.lionlibrary.domain.repository.DeletionTarget(action.mediaId)
                )
            }
            HomeAction.ConfirmDelete -> deletion.confirm()
            HomeAction.DismissDelete -> deletion.dismiss()
            is HomeAction.SelectOptions ->
                _state.update { it.copy(selectedJumpBackInItem = action.item) }
            is HomeAction.OnJumpBackInClick -> {
                val targetId =
                    if (action.item.mediaType == MediaType.MOVIE) action.item.mediaId
                    else (action.item.episodeId ?: 0L)
                if (targetId > 0L) {
                    viewModelScope.launch { navigateToPlayer(action.item.mediaType, targetId) }
                } else {
                    viewModelScope.launch {
                        _events.send(
                            HomeEvent.ShowError(UiText.Resource(R.string.file_access_error))
                        )
                    }
                }
            }
            is HomeAction.OnPlayExternal -> {
                val path = action.item.filePath
                if (path != null) {
                    viewModelScope.launch {
                        try {
                            val intent =
                                launchPlayerUseCase(
                                    Uri.parse(path),
                                    action.item.positionMs,
                                )
                            _events.send(HomeEvent.LaunchPlayer(intent))
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            _events.send(
                                HomeEvent.ShowError(UiText.Resource(R.string.external_launch_error))
                            )
                        }
                    }
                }
            }
            is HomeAction.OnStartFromBeginning -> {
                val targetId =
                    if (action.item.mediaType == MediaType.MOVIE) action.item.mediaId
                    else (action.item.episodeId ?: 0L)
                if (targetId > 0L) {
                    viewModelScope.launch {
                        updateWatchProgressUseCase.markAsUnwatched(
                            action.item.mediaId,
                            if (action.item.mediaType == MediaType.MOVIE) 0L else targetId,
                        )
                        navigateToPlayer(action.item.mediaType, targetId, fromStart = true)
                    }
                }
            }
            is HomeAction.OnRemoveWatchProgress -> {
                viewModelScope.launch {
                    val targetId =
                        if (action.item.mediaType == MediaType.MOVIE) 0L
                        else (action.item.episodeId ?: 0L)
                    updateWatchProgressUseCase.removeWatchProgress(action.item.mediaId, targetId)
                }
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            getHomeContentUseCase()
                .catch { e ->
                    _state.update {
                        it.copy(isLoading = false, error = UiText.Resource(R.string.error_unknown))
                    }
                    _events.send(HomeEvent.ShowError(UiText.Resource(R.string.error_unknown)))
                }
                .collect { content ->
                    // Built once outside update {}, which may re-run its lambda and reshuffle.
                    // Only this collector writes carouselItems, so reading it here is stable.
                    val allMedia =
                        (content.movies + content.tvShows + content.anime).distinctBy { it.id }
                    val previousCarousel = _state.value.carouselItems
                    // 10 random items for hero.
                    val randomCarousel =
                        if (previousCarousel.isNotEmpty()) {
                            val byId = allMedia.associateBy { it.id }
                            previousCarousel.mapNotNull { previous -> byId[previous.id] }
                        } else {
                            allMedia.shuffled().take(10)
                        }

                    val genreMap = mutableMapOf<String, MutableList<MediaItem>>()
                    allMedia.forEach { item ->
                        item.genres?.split(",")?.forEach { genre ->
                            val trimmedGenre = genre.trim()
                            if (trimmedGenre.isNotEmpty()) {
                                genreMap.getOrPut(trimmedGenre) { mutableListOf() }.add(item)
                            }
                        }
                    }
                    val genresContent = genreMap.mapValues { it.value.toList() }.toSortedMap()

                    _state.update { currentState ->
                        currentState.copy(
                            carouselItems = randomCarousel,
                            jumpBackInItems = content.jumpBackInItems,
                            movies = content.movies,
                            tvShows = content.tvShows,
                            anime = content.anime,
                            recentlyAdded = content.recentlyAdded,
                            genresContent = genresContent,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }
}
