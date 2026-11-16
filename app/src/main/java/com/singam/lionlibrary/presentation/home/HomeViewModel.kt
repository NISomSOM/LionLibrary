package com.singam.lionlibrary.presentation.home

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.repository.DeletionTarget
import com.singam.lionlibrary.domain.repository.MediaDeletionRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.presentation.common.DeletionPresenter
import com.singam.lionlibrary.presentation.common.DeletionUiState
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    val deletion: DeletionUiState = DeletionUiState(),
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

    data class NavigateToPlayer(val mediaType: MediaType, val mediaId: Long) : HomeEvent

    data class ShowError(val message: UiText) : HomeEvent

    data class LaunchPlayer(val intent: Intent) : HomeEvent
}

class HomeViewModel(
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val watchProgressRepository: WatchProgressRepository,
    private val externalPlaybackRouter: ExternalPlaybackRouter,
    deletionRepository: MediaDeletionRepository,
) : ViewModel() {

    // Screen-local selections; library content is merged in only while the screen collects.
    private val local = MutableStateFlow(HomeState())

    private val _events = Channel<HomeEvent>()
    val events = _events.receiveAsFlow()
    private val deletion =
        DeletionPresenter(
            deletionRepository,
            viewModelScope,
            {},
            { _events.send(HomeEvent.ShowMessage(it)) },
        )

    // Last library answer: a restarted collection resumes from it instead of a loading state, and
    // the hero keeps its titles.
    private var lastLibrary = HomeLibrary()

    private val library: Flow<HomeLibrary> =
        getHomeContentUseCase()
            .map { content ->
                HomeLibrary(
                    carouselItems =
                        selectHeroCarousel(
                            lastLibrary.carouselItems,
                            content.allMedia,
                            Random.Default,
                        ),
                    jumpBackInItems = content.jumpBackInItems,
                    movies = content.movies,
                    tvShows = content.tvShows,
                    anime = content.anime,
                    recentlyAdded = content.recentlyAdded,
                    genresContent = content.genres,
                    isLoading = false,
                )
            }
            .catch {
                _events.send(HomeEvent.ShowError(UiText.Resource(R.string.error_unknown)))
                emit(
                    lastLibrary.copy(
                        isLoading = false,
                        error = UiText.Resource(R.string.error_unknown),
                    )
                )
            }
            .onEach { lastLibrary = it }
            .onStart { emit(lastLibrary) }

    /**
     * Room flows run only while the UI collects (5 s grace for rotation), so Home is not rebuilt
     * during playback. Genre shelves are grouped off the main thread in [GetHomeContentUseCase].
     */
    val state: StateFlow<HomeState> =
        combine(local, library) { screen, content -> content.mergeInto(screen) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch {
            deletion.state.collect { value -> local.update { it.copy(deletion = value) } }
        }
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
            _events.send(HomeEvent.NavigateToPlayer(mediaType, targetId))
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.MediaOptions ->
                local.update { it.copy(selectedMediaForOptions = action.item) }
            is HomeAction.RequestDelete -> {
                local.update {
                    it.copy(selectedMediaForOptions = null, selectedJumpBackInItem = null)
                }
                deletion.request(DeletionTarget(action.mediaId))
            }
            HomeAction.ConfirmDelete -> deletion.confirm()
            HomeAction.DismissDelete -> deletion.dismiss()
            is HomeAction.SelectOptions ->
                local.update { it.copy(selectedJumpBackInItem = action.item) }
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
                        } catch (cancelled: CancellationException) {
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
                        watchProgressRepository.markAsUnwatched(
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
                    watchProgressRepository.deleteProgress(action.item.mediaId, targetId)
                    watchProgressRepository.hideMediaFromJumpBackIn(action.item.mediaId)
                }
            }
        }
    }
}

private data class HomeLibrary(
    val carouselItems: List<MediaItem> = emptyList(),
    val jumpBackInItems: List<JumpBackInItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val tvShows: List<MediaItem> = emptyList(),
    val anime: List<MediaItem> = emptyList(),
    val recentlyAdded: List<MediaItem> = emptyList(),
    val genresContent: Map<String, List<MediaItem>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
) {
    fun mergeInto(screen: HomeState) =
        screen.copy(
            carouselItems = carouselItems,
            jumpBackInItems = jumpBackInItems,
            movies = movies,
            tvShows = tvShows,
            anime = anime,
            recentlyAdded = recentlyAdded,
            genresContent = genresContent,
            isLoading = isLoading,
            error = error,
        )
}
