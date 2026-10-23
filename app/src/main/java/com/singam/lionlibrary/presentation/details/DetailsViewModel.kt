package com.singam.lionlibrary.presentation.details

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.Season
import com.singam.lionlibrary.domain.model.WatchProgressPolicy
import com.singam.lionlibrary.domain.model.isSingleFile
import com.singam.lionlibrary.domain.repository.LetterboxdRepository
import com.singam.lionlibrary.domain.usecase.GetMediaDetailsUseCase
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.UpdateWatchProgressUseCase
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class DetailsState(
    val media: MediaItem? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonNumber: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val nextEpisodeToWatch: Episode? = null,
    val isMovieWatched: Boolean = false,
    val moviePositionMs: Long = 0,
    val resumeEpisodeIds: Set<Long> = emptySet(),
    val watchedEpisodeIds: Set<Long> = emptySet(),
    val episodeProgressMap: Map<Long, Float> = emptyMap(),
    val selectedEpisodeForOptions: Episode? = null,
    val deletion: com.singam.lionlibrary.presentation.common.DeletionUiState =
        com.singam.lionlibrary.presentation.common.DeletionUiState(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
)

sealed interface DetailsAction {
    data class RequestDeleteEpisode(val episodeId: Long) : DetailsAction

    data object ConfirmDelete : DetailsAction

    data object DismissDelete : DetailsAction

    data class OnSeasonSelected(val seasonNumber: Int) : DetailsAction

    data class SelectOptions(val episode: Episode?) : DetailsAction

    data object ToggleWatchlist : DetailsAction

    data object OnPlayMovie : DetailsAction

    data object OnResumeTvShow : DetailsAction

    data class OnPlayEpisode(val episodeId: Long, val filePath: String) : DetailsAction

    data object OnMarkMovieWatchedToggle : DetailsAction

    data class OnMarkEpisodeWatchedToggle(val episodeId: Long) : DetailsAction

    data class OnMarkWatchedUpTo(val episode: Episode) : DetailsAction

    data class OnPlayEpisodeExternal(val episode: Episode) : DetailsAction

    data object OnOpenLetterboxd : DetailsAction
}

sealed interface DetailsEvent {
    data object NavigateBack : DetailsEvent

    data class ShowMessage(val message: UiText) : DetailsEvent

    data class NavigateToPlayer(val mediaType: String, val mediaId: Long) : DetailsEvent

    data class ShowError(val message: UiText) : DetailsEvent

    data class LaunchPlayer(val intent: Intent) : DetailsEvent

    data class OpenUrl(val url: String) : DetailsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: com.singam.lionlibrary.domain.repository.MediaRepository,
    private val getMediaDetailsUseCase: GetMediaDetailsUseCase,
    private val updateWatchProgressUseCase: UpdateWatchProgressUseCase,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val letterboxdRepository: LetterboxdRepository,
    private val externalPlaybackRouter: ExternalPlaybackRouter,
    deletionRepository: com.singam.lionlibrary.domain.repository.MediaDeletionRepository,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<Long>("mediaId") ?: 0L

    private val _state = MutableStateFlow(DetailsState())
    val state = _state.asStateFlow()

    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()
    private val deletion =
        com.singam.lionlibrary.presentation.common.DeletionPresenter(
            deletionRepository,
            viewModelScope,
            { if (mediaId in it.removedMediaIds) _events.send(DetailsEvent.NavigateBack) },
            { _events.send(DetailsEvent.ShowMessage(it)) },
        )

    private val selectedSeasonFlow = MutableStateFlow(savedStateHandle["season"] ?: 1)

    init {
        viewModelScope.launch {
            deletion.state.collect { value -> _state.update { it.copy(deletion = value) } }
        }
        loadMediaDetails()
    }

    private fun loadMediaDetails() {
        if (mediaId == 0L) {
            _state.update {
                it.copy(isLoading = false, error = UiText.Resource(R.string.file_access_error))
            }
            return
        }

        viewModelScope.launch {
            try {
                val details = getMediaDetailsUseCase(mediaId)
                if (details == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.Resource(R.string.file_access_error),
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(media = details.media, isLoading = false) }

                if (details.media.mediaType == MediaType.MOVIE && details.media.tmdbId != null) {
                    launch {
                        letterboxdRepository.refreshRating(mediaId)?.let { refreshed ->
                            _state.update {
                                it.copy(
                                    media =
                                        it.media?.copy(
                                            letterboxdRating = refreshed.letterboxdRating
                                        )
                                )
                            }
                        }
                    }
                }

                if (!details.media.isSingleFile) {
                    launch {
                        getMediaDetailsUseCase.getSeasons(mediaId).collect { seasons ->
                            _state.update { it.copy(seasons = seasons) }
                            if (
                                seasons.isNotEmpty() &&
                                    !seasons.any { it.seasonNumber == selectedSeasonFlow.value }
                            ) {
                                selectedSeasonFlow.value = seasons.first().seasonNumber
                            }
                        }
                    }
                    launch {
                        combine(
                                getMediaDetailsUseCase.getAllEpisodesForShow(mediaId),
                                getMediaDetailsUseCase.getProgress(mediaId),
                            ) { allEpisodes, progressList ->
                                WatchProgressPolicy.resumeEpisode(allEpisodes, progressList)
                            }
                            .collect { nextEp ->
                                _state.update { it.copy(nextEpisodeToWatch = nextEp) }
                            }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = UiText.Resource(R.string.error_unknown))
                }
            }
        }

        // Reactively monitor progress.
        viewModelScope.launch {
            getMediaDetailsUseCase.getProgress(mediaId).collect { progressList ->
                val movieProgress = progressList.find { it.episodeId == 0L }
                val completedEpisodeIds =
                    progressList.filter { it.completed }.map { it.episodeId }.toSet()
                _state.update {
                    it.copy(
                        isMovieWatched = movieProgress?.completed == true,
                        moviePositionMs = movieProgress?.lastPositionMs ?: 0,
                        resumeEpisodeIds =
                            progressList
                                .filter { !it.completed && it.lastPositionMs > 0 }
                                .map { it.episodeId }
                                .toSet(),
                        watchedEpisodeIds = completedEpisodeIds,
                        episodeProgressMap =
                            progressList.associate { progress ->
                                progress.episodeId to progress.progress
                            },
                    )
                }
            }
        }

        // Reactively monitor episodes.
        viewModelScope.launch {
            selectedSeasonFlow
                .flatMapLatest { seasonNumber ->
                    if (mediaId > 0L) {
                        getMediaDetailsUseCase.getEpisodesForSeason(mediaId, seasonNumber)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .catch { e ->
                    _events.send(DetailsEvent.ShowError(UiText.Resource(R.string.error_unknown)))
                }
                .collect { episodes ->
                    _state.update {
                        it.copy(
                            episodes = episodes,
                            selectedSeasonNumber = selectedSeasonFlow.value,
                        )
                    }
                }
        }
    }

    // In-app player, unless "Always use external player" is on.
    private suspend fun navigateToPlayer(mediaType: MediaType, targetId: Long) {
        val external = externalPlaybackRouter.intentIfForced(mediaType, targetId)
        if (external != null) {
            _events.send(DetailsEvent.LaunchPlayer(external))
        } else {
            _events.send(DetailsEvent.NavigateToPlayer(mediaType.name, targetId))
        }
    }

    fun onAction(action: DetailsAction) {
        when (action) {
            is DetailsAction.RequestDeleteEpisode -> {
                _state.update { it.copy(selectedEpisodeForOptions = null) }
                deletion.request(
                    com.singam.lionlibrary.domain.repository.DeletionTarget(
                        mediaId,
                        action.episodeId,
                    )
                )
            }
            DetailsAction.ConfirmDelete -> deletion.confirm()
            DetailsAction.DismissDelete -> deletion.dismiss()
            is DetailsAction.SelectOptions ->
                _state.update { it.copy(selectedEpisodeForOptions = action.episode) }
            DetailsAction.ToggleWatchlist ->
                viewModelScope.launch {
                    mediaRepository.toggleWatchlist(mediaId)
                    val updated = mediaRepository.getById(mediaId)
                    _state.update { it.copy(media = updated) }
                }
            is DetailsAction.OnSeasonSelected -> {
                savedStateHandle["season"] = action.seasonNumber
                selectedSeasonFlow.value = action.seasonNumber
            }
            is DetailsAction.OnOpenLetterboxd -> {
                val tmdbId = _state.value.media?.tmdbId ?: return
                viewModelScope.launch {
                    _events.send(DetailsEvent.OpenUrl(letterboxdRepository.filmUrl(tmdbId)))
                }
            }
            is DetailsAction.OnPlayMovie -> {
                val media = _state.value.media
                if (media != null && media.filePath != null) {
                    viewModelScope.launch {
                        updateWatchProgressUseCase.markAsStarted(mediaId, 0L)
                        navigateToPlayer(MediaType.MOVIE, mediaId)
                    }
                } else {
                    viewModelScope.launch {
                        _events.send(
                            DetailsEvent.ShowError(UiText.Resource(R.string.file_access_error))
                        )
                    }
                }
            }
            is DetailsAction.OnResumeTvShow -> {
                val nextEp = _state.value.nextEpisodeToWatch
                if (nextEp != null) {
                    viewModelScope.launch {
                        updateWatchProgressUseCase.markAsStarted(mediaId, nextEp.id)
                        navigateToPlayer(
                            _state.value.media?.mediaType ?: MediaType.TV_SHOW,
                            nextEp.id,
                        )
                    }
                } else {
                    viewModelScope.launch {
                        _events.send(
                            DetailsEvent.ShowError(UiText.Resource(R.string.file_access_error))
                        )
                    }
                }
            }
            is DetailsAction.OnPlayEpisode -> {
                viewModelScope.launch {
                    updateWatchProgressUseCase.markAsStarted(mediaId, action.episodeId)
                    navigateToPlayer(
                        _state.value.media?.mediaType ?: MediaType.TV_SHOW,
                        action.episodeId,
                    )
                }
            }
            is DetailsAction.OnMarkMovieWatchedToggle -> {
                viewModelScope.launch {
                    if (_state.value.isMovieWatched) {
                        updateWatchProgressUseCase.markAsUnwatched(mediaId, 0L)
                    } else {
                        updateWatchProgressUseCase.markAsWatched(mediaId, 0L)
                    }
                }
            }
            is DetailsAction.OnMarkEpisodeWatchedToggle -> {
                viewModelScope.launch {
                    if (_state.value.watchedEpisodeIds.contains(action.episodeId)) {
                        updateWatchProgressUseCase.markAsUnwatched(mediaId, action.episodeId)
                    } else {
                        updateWatchProgressUseCase.markAsWatched(mediaId, action.episodeId)
                    }
                }
            }
            is DetailsAction.OnMarkWatchedUpTo -> {
                viewModelScope.launch {
                    val allEpisodesFlow = getMediaDetailsUseCase.getAllEpisodesForShow(mediaId)
                    val allEpisodes = allEpisodesFlow.firstOrNull() ?: return@launch

                    updateWatchProgressUseCase.markAllAsWatched(
                        mediaId,
                        allEpisodes.idsWatchedUpTo(action.episode),
                    )
                }
            }
            is DetailsAction.OnPlayEpisodeExternal -> {
                val path = action.episode.filePath
                if (path != null) {
                    viewModelScope.launch {
                        try {
                            val intent = launchPlayerUseCase(Uri.parse(path), 0L)
                            _events.send(DetailsEvent.LaunchPlayer(intent))
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            _events.send(
                                DetailsEvent.ShowError(
                                    UiText.Resource(R.string.external_launch_error)
                                )
                            )
                        }
                    }
                } else {
                    viewModelScope.launch {
                        _events.send(
                            DetailsEvent.ShowError(UiText.Resource(R.string.file_access_error))
                        )
                    }
                }
            }
        }
    }
}
