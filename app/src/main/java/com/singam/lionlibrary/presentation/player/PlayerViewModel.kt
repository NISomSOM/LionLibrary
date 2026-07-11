package com.singam.lionlibrary.presentation.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.data.local.db.dao.WatchProgressDao
import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showControls: Boolean = true,
    val isExternalFallbackLoading: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
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
    private val launchPlayerUseCase: LaunchPlayerUseCase
) : ViewModel() {

    private val mediaTypeString: String = checkNotNull(savedStateHandle["mediaType"])
    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])
    private val mediaType = MediaType.valueOf(mediaTypeString)

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private val _events = Channel<PlayerEvent>()
    val events = _events.receiveAsFlow()

    val player: ExoPlayer = ExoPlayer.Builder(
        application,
        androidx.media3.exoplayer.DefaultRenderersFactory(application).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
    ).build().apply {
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage("en")
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .build()
    }

    private var uiUpdateJob: Job? = null
    private var persistenceJob: Job? = null
    
    private var currentMedia: MediaEntity? = null
    private var currentEpisode: EpisodeEntity? = null
    private var showIdForEpisode: Long = -1L

    private var isScrubbing = false

    init {
        setupPlayer()
        loadInitialMedia()
    }

    private fun setupPlayer() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startUiUpdateJob()
                    startPersistenceJob()
                } else {
                    stopUiUpdateJob()
                    persistProgress()
                    stopPersistenceJob()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(playbackState = playbackState) }
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = player.duration.coerceAtLeast(0L)) }
                } else if (playbackState == Player.STATE_ENDED) {
                    persistProgress(markAsCompleted = true)
                    onAction(PlayerAction.OnPlayNext)
                }
            }
        })
    }

    private fun loadInitialMedia() {
        viewModelScope.launch {
            if (mediaType == MediaType.MOVIE) {
                currentMedia = mediaDao.getById(mediaId)
                currentMedia?.let { media ->
                    _state.update { it.copy(
                        title = media.title, 
                        subtitle = media.year?.toString() ?: "",
                        hasPrevious = false,
                        hasNext = false
                    ) }
                    preparePlayer(media.filePath, media.externalSubtitlePath)
                } ?: run {
                    _events.send(PlayerEvent.ShowError("Media not found"))
                    _events.send(PlayerEvent.NavigateBack)
                }
            } else {
                currentEpisode = episodeDao.getById(mediaId)
                currentEpisode?.let { ep ->
                    showIdForEpisode = ep.showId
                    val show = mediaDao.getById(showIdForEpisode)
                    _state.update { it.copy(
                        title = show?.title ?: "Unknown Show",
                        subtitle = "S${ep.seasonNumber} E${ep.episodeNumber} - ${ep.title ?: ""}"
                    ) }
                    checkNextPrevious(ep)
                    preparePlayer(ep.filePath, ep.externalSubtitlePath)
                } ?: run {
                    _events.send(PlayerEvent.ShowError("Episode not found"))
                    _events.send(PlayerEvent.NavigateBack)
                }
            }
        }
    }

    private fun preparePlayer(filePath: String?, externalSubtitlePath: String?) {
        if (filePath == null) return
        
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(filePath))
        
        if (externalSubtitlePath != null) {
            val uri = Uri.parse(externalSubtitlePath)
            val ext = uri.path?.substringAfterLast('.')?.lowercase() ?: "srt"
            val mimeType = when (ext) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        
        viewModelScope.launch {
            val progressId = if (mediaType == MediaType.MOVIE) 0L else (currentEpisode?.id ?: mediaId)
            val mediaIdToUse = if (mediaType == MediaType.MOVIE) mediaId else showIdForEpisode
            val watchProgress = watchProgressDao.getProgress(mediaIdToUse, progressId)
            if (watchProgress != null && watchProgress.lastPositionMs > 5000L && !watchProgress.completed) {
                player.seekTo(watchProgress.lastPositionMs)
                player.playWhenReady = true
                _state.update { 
                    it.copy(
                        currentPositionMs = watchProgress.lastPositionMs
                    ) 
                }
            } else {
                player.playWhenReady = true
            }
        }
    }
    
    private suspend fun checkNextPrevious(ep: EpisodeEntity) {
        val next = episodeDao.getNextEpisode(ep.showId, ep.seasonNumber, ep.episodeNumber)
        val prev = episodeDao.getPreviousEpisode(ep.showId, ep.seasonNumber, ep.episodeNumber)
        _state.update { it.copy(
            hasNext = next != null,
            hasPrevious = prev != null
        ) }
    }

    fun getAvailableTracks(): androidx.media3.common.Tracks = player.currentTracks

    fun onAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.OnPlayPause -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            is PlayerAction.OnSeek -> {
                player.seekTo(action.positionMs)
                _state.update { it.copy(currentPositionMs = action.positionMs) }
            }
            is PlayerAction.OnSeekForward -> {
                val newPos = (player.currentPosition + 10_000).coerceAtMost(player.duration)
                player.seekTo(newPos)
                _state.update { it.copy(currentPositionMs = newPos) }
            }
            is PlayerAction.OnSeekBackward -> {
                val newPos = (player.currentPosition - 10_000).coerceAtLeast(0)
                player.seekTo(newPos)
                _state.update { it.copy(currentPositionMs = newPos) }
            }
            is PlayerAction.OnScrub -> {
                isScrubbing = true
                _state.update { it.copy(currentPositionMs = action.positionMs) }
            }
            is PlayerAction.OnScrubEnd -> {
                isScrubbing = false
                player.seekTo(_state.value.currentPositionMs)
            }
            is PlayerAction.OnControlsVisibilityChanged -> {
                _state.update { it.copy(showControls = action.isVisible) }
            }
            is PlayerAction.OnPlayNext -> {
                if (mediaType != MediaType.MOVIE && currentEpisode != null) {
                    viewModelScope.launch {
                        val next = episodeDao.getNextEpisode(showIdForEpisode, currentEpisode!!.seasonNumber, currentEpisode!!.episodeNumber)
                        next?.let { playNewEpisode(it) }
                    }
                }
            }
            is PlayerAction.OnPlayPrevious -> {
                if (mediaType != MediaType.MOVIE && currentEpisode != null) {
                    viewModelScope.launch {
                        val prev = episodeDao.getPreviousEpisode(showIdForEpisode, currentEpisode!!.seasonNumber, currentEpisode!!.episodeNumber)
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
        player.stop()
        currentEpisode = ep
        viewModelScope.launch {
            val show = mediaDao.getById(showIdForEpisode)
            _state.update { it.copy(
                title = show?.title ?: "Unknown Show",
                subtitle = "S${ep.seasonNumber} E${ep.episodeNumber} - ${ep.title ?: ""}",
                currentPositionMs = 0L
            ) }
            checkNextPrevious(ep)
            preparePlayer(ep.filePath, ep.externalSubtitlePath)
        }
    }

    private fun handleExternalFallback() {
        val path = if (mediaType == MediaType.MOVIE) currentMedia?.filePath else currentEpisode?.filePath
        if (path == null) return
        
        viewModelScope.launch {
            _state.update { it.copy(isExternalFallbackLoading = true) }
            player.pause()
            persistProgress()
            
            try {
                val progressToPass = player.currentPosition
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
                    _state.update { it.copy(currentPositionMs = player.currentPosition) }
                }
                delay(500)
            }
        }
    }

    private fun stopUiUpdateJob() {
        uiUpdateJob?.cancel()
        uiUpdateJob = null
        if (!isScrubbing) {
            _state.update { it.copy(currentPositionMs = player.currentPosition) }
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
        val currentPos = player.currentPosition
        val dur = player.duration.coerceAtLeast(1L)
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
        player.release()
    }
}
