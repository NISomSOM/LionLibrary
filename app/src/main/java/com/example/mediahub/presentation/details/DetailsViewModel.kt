package com.example.mediahub.presentation.details

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediahub.domain.model.Episode
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.model.Season
import com.example.mediahub.domain.usecase.GetMediaDetailsUseCase
import com.example.mediahub.domain.usecase.LaunchPlayerUseCase

import com.example.mediahub.domain.usecase.UpdateWatchProgressUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val watchedEpisodeIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)


sealed interface DetailsAction {
    data class OnSeasonSelected(val seasonNumber: Int) : DetailsAction
    data object OnPlayMovie : DetailsAction
    data object OnResumeTvShow : DetailsAction
    data class OnPlayEpisode(val episodeId: Long, val filePath: String) : DetailsAction
    data object OnMarkMovieWatchedToggle : DetailsAction
    data class OnMarkEpisodeWatchedToggle(val episodeId: Long) : DetailsAction
}


sealed interface DetailsEvent {
    data class LaunchPlayer(val intent: Intent) : DetailsEvent
    data class ShowError(val message: String) : DetailsEvent
}


@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val getMediaDetailsUseCase: GetMediaDetailsUseCase,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val updateWatchProgressUseCase: UpdateWatchProgressUseCase
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<Long>("mediaId") ?: 0L

    private val _state = MutableStateFlow(DetailsState())
    val state = _state.asStateFlow()

    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()

    private val selectedSeasonFlow = MutableStateFlow(1)

    init {
        loadMediaDetails()
    }

    private fun loadMediaDetails() {
        if (mediaId == 0L) {
            _state.update { it.copy(isLoading = false, error = "Invalid media ID") }
            return
        }

        viewModelScope.launch {
            try {
                val details = getMediaDetailsUseCase(mediaId)
                if (details == null) {
                    _state.update { it.copy(isLoading = false, error = "Media not found") }
                    return@launch
                }

                _state.update { 
                    it.copy(
                        media = details.media,
                        isLoading = false
                    )
                }

                if (details.media.mediaType == MediaType.TV_SHOW || details.media.mediaType == MediaType.ANIME) {
                    launch {
                        getMediaDetailsUseCase.getSeasons(mediaId).collect { seasons ->
                            _state.update { it.copy(seasons = seasons) }
                            if (seasons.isNotEmpty() && !seasons.any { it.seasonNumber == selectedSeasonFlow.value }) {
                                selectedSeasonFlow.value = seasons.first().seasonNumber
                            }
                        }
                    }
                    launch {
                        combine(
                            getMediaDetailsUseCase.getAllEpisodesForShow(mediaId),
                            getMediaDetailsUseCase.getProgress(mediaId)
                        ) { allEpisodes, progressList ->
                            val completedEpisodeIds = progressList.filter { it.completed }.map { it.episodeId }.toSet()
                            allEpisodes.firstOrNull { it.id !in completedEpisodeIds } ?: allEpisodes.lastOrNull()
                        }.collect { nextEp ->
                            _state.update { it.copy(nextEpisodeToWatch = nextEp) }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }

        // Load progress reactively
        viewModelScope.launch {
            getMediaDetailsUseCase.getProgress(mediaId).collect { progressList ->
                val movieProgress = progressList.find { it.episodeId == 0L }
                val completedEpisodeIds = progressList.filter { it.completed }.map { it.episodeId }.toSet()
                _state.update {
                    it.copy(
                        isMovieWatched = movieProgress?.completed == true,
                        watchedEpisodeIds = completedEpisodeIds
                    )
                }
            }
        }

        // Load episodes reactively
        viewModelScope.launch {
            selectedSeasonFlow.flatMapLatest { seasonNumber ->
                if (mediaId > 0L) {
                    getMediaDetailsUseCase.getEpisodesForSeason(mediaId, seasonNumber)
                } else {
                    flowOf(emptyList())
                }
            }.catch { e ->
                _events.send(DetailsEvent.ShowError(e.message ?: "Failed to load episodes"))
            }.collect { episodes ->
                _state.update { 
                    it.copy(
                        episodes = episodes,
                        selectedSeasonNumber = selectedSeasonFlow.value
                    )
                }
            }
        }
    }

    fun onAction(action: DetailsAction) {
        when (action) {
            is DetailsAction.OnSeasonSelected -> {
                selectedSeasonFlow.value = action.seasonNumber
            }
            is DetailsAction.OnPlayMovie -> {
                val media = _state.value.media
                if (media != null && media.filePath != null) {
                    viewModelScope.launch {
                        updateWatchProgressUseCase.markAsStarted(mediaId, 0L)
                        val intent = launchPlayerUseCase(Uri.parse(media.filePath), 0L)
                        _events.send(DetailsEvent.LaunchPlayer(intent))
                    }
                } else {
                    viewModelScope.launch { _events.send(DetailsEvent.ShowError("File path not available")) }
                }
            }
            is DetailsAction.OnResumeTvShow -> {
                val nextEp = _state.value.nextEpisodeToWatch
                if (nextEp != null) {
                    viewModelScope.launch {
                        updateWatchProgressUseCase.markAsStarted(mediaId, nextEp.id)
                        val intent = launchPlayerUseCase(Uri.parse(nextEp.filePath), 0L)
                        _events.send(DetailsEvent.LaunchPlayer(intent))
                    }
                } else {
                    viewModelScope.launch { _events.send(DetailsEvent.ShowError("No episode available to resume")) }
                }
            }
            is DetailsAction.OnPlayEpisode -> {
                viewModelScope.launch {
                    updateWatchProgressUseCase.markAsStarted(mediaId, action.episodeId)
                    val intent = launchPlayerUseCase(Uri.parse(action.filePath), 0L)
                    _events.send(DetailsEvent.LaunchPlayer(intent))
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
        }
    }
}
