package com.singam.lionlibrary.presentation.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.WatchProgressDao
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.presentation.player.engine.EnginePlaybackState
import com.singam.lionlibrary.presentation.player.engine.EngineState
import com.singam.lionlibrary.presentation.player.engine.EngineType
import com.singam.lionlibrary.presentation.player.engine.ExoPlayerEngine
import com.singam.lionlibrary.presentation.player.engine.LibVlcPlayerEngine
import com.singam.lionlibrary.presentation.player.engine.LionPlayerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showControls: Boolean = true,
    val isExternalFallbackLoading: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val engineType: EngineType = EngineType.EXOPLAYER
)

sealed interface PlayerAction {
    data object OnPlayPause : PlayerAction
    data class OnSeek(val positionMs: Long) : PlayerAction
    data object OnSeekForward : PlayerAction
    data object OnSeekBackward : PlayerAction
    data class OnScrub(val positionMs: Long) : PlayerAction
    data object OnScrubEnd : PlayerAction
    data class OnControlsVisibilityChanged(val isVisible: Boolean) : PlayerAction
    data object OnPlayNext : PlayerAction
    data object OnPlayPrevious : PlayerAction
    data object OnExternalFallback : PlayerAction
}

sealed interface PlayerEvent {
    data object NavigateBack : PlayerEvent
    data class LaunchExternalPlayer(val intent: Intent) : PlayerEvent
    data class ShowError(val message: String) : PlayerEvent
}

class PlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val watchProgressDao: WatchProgressDao,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val mediaTypeString: String = checkNotNull(savedStateHandle["mediaType"])
    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])
    private val mediaType = MediaType.valueOf(mediaTypeString)

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private val _events = Channel<PlayerEvent>()
    val events = _events.receiveAsFlow()

    // Engine is created lazily in loadInitialMedia() after we know which
    // engine to use (based on the file's preferredEngine + force-libVLC pref).
    var engine: LionPlayerEngine? = null
        private set

    private var uiUpdateJob: Job? = null
    private var persistenceJob: Job? = null

    private var currentMedia: MediaEntity? = null
    private var currentEpisode: EpisodeEntity? = null
    private var showIdForEpisode: Long = -1L

    private var isScrubbing = false

    init {
        loadInitialMedia()
    }

    /**
     * Determines which engine to use for a file.
     *
     * Priority:
     * 1. If "Always use libVLC" is ON in settings → LIBVLC
     * 2. Otherwise, use the per-file `preferredEngine` set at scan time
     *    by [CodecCapabilityChecker]
     */
    private suspend fun resolveEngine(preferredEngine: String): LionPlayerEngine {
        val forceLibVlc = settingsRepository.forceLibVlc.first()
        val engineType = if (forceLibVlc) {
            EngineType.LIBVLC
        } else {
            try { EngineType.valueOf(preferredEngine) } catch (_: Exception) { EngineType.EXOPLAYER }
        }

        return when (engineType) {
            EngineType.EXOPLAYER -> ExoPlayerEngine(application)
            EngineType.LIBVLC -> LibVlcPlayerEngine(application)
        }
    }

    private fun initEngine(selectedEngine: LionPlayerEngine) {
        engine = selectedEngine
        _state.update { it.copy(engineType = selectedEngine.engineType) }
        observeEngineState()
    }


    private fun observeEngineState() {
        viewModelScope.launch {
            engine?.state?.collect { engineState: EngineState ->
                _state.update {
                    it.copy(
                        isPlaying = engineState.isPlaying,
                        playbackState = engineState.playbackState,
                        durationMs = if (engineState.durationMs > 0) engineState.durationMs else it.durationMs
                    )
                }

                // React to playback state changes
                when (engineState.playbackState) {
                    EnginePlaybackState.READY -> {
                        // durationMs is already updated above
                    }
                    EnginePlaybackState.ENDED -> {
                        persistProgress(markAsCompleted = true)
                        onAction(PlayerAction.OnPlayNext)
                    }
                    else -> {}
                }

                // Start/stop position polling based on isPlaying
                if (engineState.isPlaying) {
                    startUiUpdateJob()
                    startPersistenceJob()
                } else {
                    stopUiUpdateJob()
                    persistProgress()
                    stopPersistenceJob()
                }

                // Surface engine errors — recover by reverting to default track selection
                engineState.error?.let { errorMsg ->
                    viewModelScope.launch {
                        val recoveryPositionMs = _state.value.currentPositionMs
                        val filePath = if (mediaType == MediaType.MOVIE) {
                            currentMedia?.filePath
                        } else {
                            currentEpisode?.filePath
                        }
                        val subtitlePath = if (mediaType == MediaType.MOVIE) {
                            currentMedia?.externalSubtitlePath
                        } else {
                            currentEpisode?.externalSubtitlePath
                        }

                        engine?.resetToDefaultTrackSelection()
                        if (filePath != null) {
                            engine?.setMedia(
                                Uri.parse(filePath),
                                subtitlePath?.let { Uri.parse(it) }
                            )
                            engine?.seekTo(recoveryPositionMs)
                            engine?.play()
                        }
                        _events.send(
                            PlayerEvent.ShowError(
                                "That audio track isn't supported on this device. " +
                                    "Reverted to the default track."
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadInitialMedia() {
        viewModelScope.launch {
            if (mediaType == MediaType.MOVIE) {
                currentMedia = mediaDao.getById(mediaId)
                currentMedia?.let { media ->
                    // Resolve engine based on preferredEngine + settings
                    initEngine(resolveEngine(media.preferredEngine))

                    _state.update {
                        it.copy(
                            title = media.title,
                            subtitle = media.year?.toString() ?: "",
                            hasPrevious = false,
                            hasNext = false
                        )
                    }
                    preparePlayer(media.filePath, media.externalSubtitlePath)
                } ?: run {
                    // Fallback engine so PlayerScreen doesn't crash
                    initEngine(ExoPlayerEngine(application))
                    _events.send(PlayerEvent.ShowError("Media not found"))
                    _events.send(PlayerEvent.NavigateBack)
                }
            } else {
                currentEpisode = episodeDao.getById(mediaId)
                currentEpisode?.let { ep ->
                    // Resolve engine based on preferredEngine + settings
                    initEngine(resolveEngine(ep.preferredEngine))

                    showIdForEpisode = ep.showId
                    val show = mediaDao.getById(showIdForEpisode)
                    _state.update {
                        it.copy(
                            title = show?.title ?: "Unknown Show",
                            subtitle = "S${ep.seasonNumber} E${ep.episodeNumber} - ${ep.title ?: ""}"
                        )
                    }
                    checkNextPrevious(ep)
                    preparePlayer(ep.filePath, ep.externalSubtitlePath)
                } ?: run {
                    initEngine(ExoPlayerEngine(application))
                    _events.send(PlayerEvent.ShowError("Episode not found"))
                    _events.send(PlayerEvent.NavigateBack)
                }
            }
        }
    }

    private fun preparePlayer(filePath: String?, externalSubtitlePath: String?) {
        if (filePath == null) return

        val uri = Uri.parse(filePath)
        val subtitleUri = externalSubtitlePath?.let { Uri.parse(it) }

        engine?.setMedia(uri, subtitleUri)

        viewModelScope.launch {
            val progressId = if (mediaType == MediaType.MOVIE) 0L else (currentEpisode?.id ?: mediaId)
            val mediaIdToUse = if (mediaType == MediaType.MOVIE) mediaId else showIdForEpisode
            val watchProgress = watchProgressDao.getProgress(mediaIdToUse, progressId)
            if (watchProgress != null && watchProgress.lastPositionMs > 5000L && !watchProgress.completed) {
                engine?.seekTo(watchProgress.lastPositionMs)
                _state.update { it.copy(currentPositionMs = watchProgress.lastPositionMs) }
            }
            engine?.play()
        }
    }

    private suspend fun checkNextPrevious(ep: EpisodeEntity) {
        val next = episodeDao.getNextEpisode(ep.showId, ep.seasonNumber, ep.episodeNumber)
        val prev = episodeDao.getPreviousEpisode(ep.showId, ep.seasonNumber, ep.episodeNumber)
        _state.update {
            it.copy(
                hasNext = next != null,
                hasPrevious = prev != null
            )
        }
    }

    fun getAudioTracks() = engine?.getAudioTracks() ?: emptyList()
    fun selectAudioTrack(id: String) = engine?.selectAudioTrack(id)
    fun getSubtitleTracks() = engine?.getSubtitleTracks() ?: emptyList()
    fun selectSubtitleTrack(id: String?) = engine?.selectSubtitleTrack(id)

    fun onAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.OnPlayPause -> {
                if (_state.value.isPlaying) engine?.pause() else engine?.play()
            }
            is PlayerAction.OnSeek -> {
                engine?.seekTo(action.positionMs)
                _state.update { it.copy(currentPositionMs = action.positionMs) }
            }
            is PlayerAction.OnSeekForward -> {
                val newPos = (_state.value.currentPositionMs + 10_000).coerceAtMost(_state.value.durationMs)
                engine?.seekTo(newPos)
                _state.update { it.copy(currentPositionMs = newPos) }
            }
            is PlayerAction.OnSeekBackward -> {
                val newPos = (_state.value.currentPositionMs - 10_000).coerceAtLeast(0)
                engine?.seekTo(newPos)
                _state.update { it.copy(currentPositionMs = newPos) }
            }
            is PlayerAction.OnScrub -> {
                isScrubbing = true
                _state.update { it.copy(currentPositionMs = action.positionMs) }
            }
            is PlayerAction.OnScrubEnd -> {
                isScrubbing = false
                engine?.seekTo(_state.value.currentPositionMs)
            }
            is PlayerAction.OnControlsVisibilityChanged -> {
                _state.update { it.copy(showControls = action.isVisible) }
            }
            is PlayerAction.OnPlayNext -> {
                if (mediaType != MediaType.MOVIE && currentEpisode != null) {
                    viewModelScope.launch {
                        val next = episodeDao.getNextEpisode(
                            showIdForEpisode,
                            currentEpisode!!.seasonNumber,
                            currentEpisode!!.episodeNumber
                        )
                        next?.let { playNewEpisode(it) }
                    }
                }
            }
            is PlayerAction.OnPlayPrevious -> {
                if (mediaType != MediaType.MOVIE && currentEpisode != null) {
                    viewModelScope.launch {
                        val prev = episodeDao.getPreviousEpisode(
                            showIdForEpisode,
                            currentEpisode!!.seasonNumber,
                            currentEpisode!!.episodeNumber
                        )
                        prev?.let { playNewEpisode(it) }
                    }
                }
            }
            is PlayerAction.OnExternalFallback -> {
                handleExternalFallback()
            }
        }
    }

    private fun playNewEpisode(ep: EpisodeEntity) {
        // Stop current playback before switching
        when (val e = engine) {
            is ExoPlayerEngine -> e.stop()
            is LibVlcPlayerEngine -> e.stop()
            else -> {}
        }
        currentEpisode = ep
        viewModelScope.launch {
            val show = mediaDao.getById(showIdForEpisode)
            _state.update {
                it.copy(
                    title = show?.title ?: "Unknown Show",
                    subtitle = "S${ep.seasonNumber} E${ep.episodeNumber} - ${ep.title ?: ""}",
                    currentPositionMs = 0L
                )
            }
            checkNextPrevious(ep)
            preparePlayer(ep.filePath, ep.externalSubtitlePath)
        }
    }

    private fun handleExternalFallback() {
        val path = if (mediaType == MediaType.MOVIE) currentMedia?.filePath else currentEpisode?.filePath
        if (path == null) return

        viewModelScope.launch {
            _state.update { it.copy(isExternalFallbackLoading = true) }
            engine?.pause()
            persistProgress()

            try {
                val progressToPass = _state.value.currentPositionMs
                val intent = launchPlayerUseCase(Uri.parse(path), progressToPass)
                _events.send(PlayerEvent.LaunchExternalPlayer(intent))
                _events.send(PlayerEvent.NavigateBack)
            } catch (e: Exception) {
                _events.send(PlayerEvent.ShowError("Failed to launch external player"))
            } finally {
                _state.update { it.copy(isExternalFallbackLoading = false) }
            }
        }
    }

    private fun startUiUpdateJob() {
        uiUpdateJob?.cancel()
        uiUpdateJob = viewModelScope.launch {
            while (isActive) {
                if (!isScrubbing) {
                    val pos = when (val e = engine) {
                        is ExoPlayerEngine -> e.currentPositionMs
                        is LibVlcPlayerEngine -> e.currentPositionMs
                        else -> _state.value.currentPositionMs
                    }
                    _state.update { it.copy(currentPositionMs = pos) }
                }
                delay(500)
            }
        }
    }

    private fun stopUiUpdateJob() {
        uiUpdateJob?.cancel()
        uiUpdateJob = null
        if (!isScrubbing) {
            val pos = when (val e = engine) {
                is ExoPlayerEngine -> e.currentPositionMs
                is LibVlcPlayerEngine -> e.currentPositionMs
                else -> _state.value.currentPositionMs
            }
            _state.update { it.copy(currentPositionMs = pos) }
        }
    }

    private fun startPersistenceJob() {
        persistenceJob?.cancel()
        persistenceJob = viewModelScope.launch {
            while (isActive) {
                delay(8000)
                persistProgress()
            }
        }
    }

    private fun stopPersistenceJob() {
        persistenceJob?.cancel()
        persistenceJob = null
    }

    private fun persistProgress(markAsCompleted: Boolean = false) {
        val currentPos = when (val e = engine) {
            is ExoPlayerEngine -> e.currentPositionMs
            is LibVlcPlayerEngine -> e.currentPositionMs
            else -> _state.value.currentPositionMs
        }
        val dur = (when (val e = engine) {
            is ExoPlayerEngine -> e.durationMs
            is LibVlcPlayerEngine -> e.durationMs
            else -> _state.value.durationMs
        }).coerceAtLeast(1L)
        if (currentPos == 0L && !markAsCompleted) return

        viewModelScope.launch {
            val progressId = if (mediaType == MediaType.MOVIE) 0L else (currentEpisode?.id ?: return@launch)
            val mId = if (mediaType == MediaType.MOVIE) mediaId else showIdForEpisode

            watchProgressDao.upsert(
                WatchProgressEntity(
                    mediaId = mId,
                    episodeId = progressId,
                    progress = if (markAsCompleted) 1f else (currentPos.toFloat() / dur),
                    lastPositionMs = if (markAsCompleted) 0L else currentPos,
                    durationMs = dur,
                    lastWatched = System.currentTimeMillis(),
                    completed = markAsCompleted || (currentPos >= dur * 0.95f)
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistProgress()
        engine?.release()
    }
}
