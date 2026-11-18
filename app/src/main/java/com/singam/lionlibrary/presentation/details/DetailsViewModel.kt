package com.singam.lionlibrary.presentation.details

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.LibraryQuery
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.Season
import com.singam.lionlibrary.domain.model.WatchProgress
import com.singam.lionlibrary.domain.model.WatchProgressPolicy
import com.singam.lionlibrary.domain.model.isSingleFile
import com.singam.lionlibrary.domain.repository.DeletionTarget
import com.singam.lionlibrary.domain.repository.LetterboxdRepository
import com.singam.lionlibrary.domain.repository.MediaDeletionRepository
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.relatedMedia
import com.singam.lionlibrary.presentation.common.DeletionPresenter
import com.singam.lionlibrary.presentation.common.DeletionUiState
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.player.ExternalPlaybackRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    /** Other library titles sharing a genre with this one, closest first. */
    val relatedMedia: List<MediaItem> = emptyList(),
    val selectedEpisodeForOptions: Episode? = null,
    val deletion: DeletionUiState = DeletionUiState(),
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

    data class NavigateToPlayer(val mediaType: MediaType, val mediaId: Long) : DetailsEvent

    data class ShowError(val message: UiText) : DetailsEvent

    data class LaunchPlayer(val intent: Intent) : DetailsEvent

    data class OpenUrl(val url: String) : DetailsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val letterboxdRepository: LetterboxdRepository,
    private val externalPlaybackRouter: ExternalPlaybackRouter,
    deletionRepository: MediaDeletionRepository,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<Long>("mediaId") ?: 0L

    // Screen-local choices; library content below is merged in only while the screen collects.
    private val local = MutableStateFlow(DetailsState())

    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()
    private val deletion =
        DeletionPresenter(
            deletionRepository,
            viewModelScope,
            { if (mediaId in it.removedMediaIds) _events.send(DetailsEvent.NavigateBack) },
            { _events.send(DetailsEvent.ShowMessage(it)) },
        )

    private val selectedSeasonFlow = MutableStateFlow(savedStateHandle["season"] ?: 1)

    /**
     * Room flows run only while the UI collects (5 s grace for rotation), so playback on top of
     * Details does not keep rebuilding it. The last value stays cached, so returning shows the
     * previous content immediately: no loading state and no replayed loaded fade.
     */
    val state: StateFlow<DetailsState> =
        combine(local, contentFlow()) { screen, content -> content.mergeInto(screen) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState())

    init {
        viewModelScope.launch {
            deletion.state.collect { value -> local.update { it.copy(deletion = value) } }
        }
        refreshLetterboxdOnce()
    }

    private fun refreshLetterboxdOnce() {
        if (mediaId == 0L) return
        viewModelScope.launch {
            val media = mediaRepository.getById(mediaId) ?: return@launch
            // The refreshed rating is written to Room and arrives through the observed media row.
            if (media.mediaType == MediaType.MOVIE && media.tmdbId != null)
                letterboxdRepository.refreshRating(mediaId)
        }
    }

    private fun contentFlow(): Flow<DetailsContent> {
        if (mediaId == 0L) return flowOf(DetailsContent.missing())
        val media = mediaRepository.observeById(mediaId)
        // Watchlist/rating/artwork changes re-emit the row; only a change of shape (missing,
        // single file, show) re-subscribes the history and episode flows.
        val body =
            media
                .map { item ->
                    when {
                        item == null -> DetailsShape.MISSING
                        item.isSingleFile -> DetailsShape.SINGLE_FILE
                        else -> DetailsShape.SHOW
                    }
                }
                .distinctUntilChanged()
                .flatMapLatest { shape ->
                    when (shape) {
                        DetailsShape.MISSING -> flowOf(DetailsContent.missing())
                        DetailsShape.SINGLE_FILE ->
                            watchProgressRepository.getProgressForMedia(mediaId).map {
                                DetailsContent().withProgress(it)
                            }
                        DetailsShape.SHOW -> showContent()
                    }
                }
        // Titles sharing a genre with this one; an unchanged answer does not rebuild the page.
        val related =
            combine(media, allIdentified()) { item, all ->
                    item?.let { relatedMedia(it, all) }.orEmpty()
                }
                .distinctUntilChanged()
        return combine(media, body, related) { item, content, relatedItems ->
                content.copy(media = item, relatedMedia = relatedItems)
            }
            .catch { emit(DetailsContent(error = UiText.Resource(R.string.error_unknown))) }
    }

    /** Every title in the library, for the related row; a failure only leaves the row empty. */
    private fun allIdentified(): Flow<List<MediaItem>> =
        mediaRepository.browse(LibraryQuery()).catch { emit(emptyList()) }

    private fun showContent(): Flow<DetailsContent> {
        val seasons =
            mediaRepository.getSeasonsForShow(mediaId).onEach { seasons ->
                if (
                    seasons.isNotEmpty() &&
                        seasons.none { it.seasonNumber == selectedSeasonFlow.value }
                )
                    selectedSeasonFlow.value = seasons.first().seasonNumber
            }
        val episodes = selectedSeasonFlow.flatMapLatest { seasonNumber ->
            mediaRepository
                .getEpisodesForSeason(mediaId, seasonNumber)
                .map { seasonNumber to it }
                .catch {
                    _events.send(DetailsEvent.ShowError(UiText.Resource(R.string.error_unknown)))
                    emit(seasonNumber to emptyList())
                }
        }
        val progress = watchProgressRepository.getProgressForMedia(mediaId)
        val resume =
            combine(mediaRepository.getAllEpisodesForShow(mediaId), progress) { all, history ->
                WatchProgressPolicy.resumeEpisode(all, history)
            }
        return combine(seasons, episodes, progress, resume) {
            seasonList,
            (seasonNumber, episodeList),
            history,
            nextEp ->
            DetailsContent(
                    seasons = seasonList,
                    selectedSeasonNumber = seasonNumber,
                    episodes = episodeList,
                    nextEpisodeToWatch = nextEp,
                )
                .withProgress(history)
        }
    }

    // In-app player, unless "Always use external player" is on.
    private suspend fun navigateToPlayer(mediaType: MediaType, targetId: Long) {
        val external = externalPlaybackRouter.intentIfForced(mediaType, targetId)
        if (external != null) {
            _events.send(DetailsEvent.LaunchPlayer(external))
        } else {
            _events.send(DetailsEvent.NavigateToPlayer(mediaType, targetId))
        }
    }

    fun onAction(action: DetailsAction) {
        when (action) {
            is DetailsAction.RequestDeleteEpisode -> {
                local.update { it.copy(selectedEpisodeForOptions = null) }
                deletion.request(
                    DeletionTarget(
                        mediaId,
                        action.episodeId,
                    )
                )
            }
            DetailsAction.ConfirmDelete -> deletion.confirm()
            DetailsAction.DismissDelete -> deletion.dismiss()
            is DetailsAction.SelectOptions ->
                local.update { it.copy(selectedEpisodeForOptions = action.episode) }
            DetailsAction.ToggleWatchlist ->
                // The observed media row carries the new flag; no re-read needed.
                viewModelScope.launch { mediaRepository.toggleWatchlist(mediaId) }
            is DetailsAction.OnSeasonSelected -> {
                savedStateHandle["season"] = action.seasonNumber
                selectedSeasonFlow.value = action.seasonNumber
            }
            is DetailsAction.OnOpenLetterboxd -> {
                val tmdbId = state.value.media?.tmdbId ?: return
                viewModelScope.launch {
                    _events.send(DetailsEvent.OpenUrl(letterboxdRepository.filmUrl(tmdbId)))
                }
            }
            is DetailsAction.OnPlayMovie -> {
                val media = state.value.media
                if (media != null && media.filePath != null) {
                    viewModelScope.launch {
                        watchProgressRepository.markAsStarted(mediaId, 0L)
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
                val nextEp = state.value.nextEpisodeToWatch
                if (nextEp != null) {
                    viewModelScope.launch {
                        watchProgressRepository.markAsStarted(mediaId, nextEp.id)
                        navigateToPlayer(
                            state.value.media?.mediaType ?: MediaType.TV_SHOW,
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
                    watchProgressRepository.markAsStarted(mediaId, action.episodeId)
                    navigateToPlayer(
                        state.value.media?.mediaType ?: MediaType.TV_SHOW,
                        action.episodeId,
                    )
                }
            }
            is DetailsAction.OnMarkMovieWatchedToggle -> {
                viewModelScope.launch {
                    if (state.value.isMovieWatched) {
                        watchProgressRepository.markAsUnwatched(mediaId, 0L)
                    } else {
                        watchProgressRepository.markAsWatched(mediaId, 0L)
                    }
                }
            }
            is DetailsAction.OnMarkEpisodeWatchedToggle -> {
                viewModelScope.launch {
                    if (state.value.watchedEpisodeIds.contains(action.episodeId)) {
                        watchProgressRepository.markAsUnwatched(mediaId, action.episodeId)
                    } else {
                        watchProgressRepository.markAsWatched(mediaId, action.episodeId)
                    }
                }
            }
            is DetailsAction.OnMarkWatchedUpTo -> {
                viewModelScope.launch {
                    val allEpisodesFlow = mediaRepository.getAllEpisodesForShow(mediaId)
                    val allEpisodes = allEpisodesFlow.firstOrNull() ?: return@launch

                    watchProgressRepository.markAllAsWatched(
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
                        } catch (cancelled: CancellationException) {
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

private enum class DetailsShape {
    MISSING,
    SINGLE_FILE,
    SHOW,
}

/** Library-derived part of [DetailsState]; `mergeInto` keeps the screen-local fields. */
private data class DetailsContent(
    val media: MediaItem? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val nextEpisodeToWatch: Episode? = null,
    val isMovieWatched: Boolean = false,
    val moviePositionMs: Long = 0,
    val resumeEpisodeIds: Set<Long> = emptySet(),
    val watchedEpisodeIds: Set<Long> = emptySet(),
    val episodeProgressMap: Map<Long, Float> = emptyMap(),
    val relatedMedia: List<MediaItem> = emptyList(),
    val error: UiText? = null,
) {
    fun withProgress(progressList: List<WatchProgress>): DetailsContent {
        val movieProgress = progressList.find { it.episodeId == 0L }
        return copy(
            isMovieWatched = movieProgress?.completed == true,
            moviePositionMs = movieProgress?.lastPositionMs ?: 0,
            resumeEpisodeIds =
                progressList
                    .filter { !it.completed && it.lastPositionMs > 0 }
                    .mapTo(HashSet()) { it.episodeId },
            watchedEpisodeIds =
                progressList.filter { it.completed }.mapTo(HashSet()) { it.episodeId },
            episodeProgressMap = progressList.associate { it.episodeId to it.progress },
        )
    }

    fun mergeInto(screen: DetailsState) =
        screen.copy(
            media = media,
            seasons = seasons,
            selectedSeasonNumber = selectedSeasonNumber ?: screen.selectedSeasonNumber,
            episodes = episodes,
            nextEpisodeToWatch = nextEpisodeToWatch,
            isMovieWatched = isMovieWatched,
            moviePositionMs = moviePositionMs,
            resumeEpisodeIds = resumeEpisodeIds,
            watchedEpisodeIds = watchedEpisodeIds,
            episodeProgressMap = episodeProgressMap,
            relatedMedia = relatedMedia,
            isLoading = false,
            error = error,
        )

    companion object {
        fun missing() = DetailsContent(error = UiText.Resource(R.string.file_access_error))
    }
}
