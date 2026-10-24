package com.singam.lionlibrary.presentation.player

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.SkipSegmentSession
import com.singam.lionlibrary.presentation.common.*
import com.singam.lionlibrary.presentation.player.engine.*
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayerState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val showControls: Boolean = true,
    val isExternalFallbackLoading: Boolean = false,
    val engineType: EngineType = EngineType.EXOPLAYER,
    val session: Long = 0,
    val failure: UiText? = null,
    val audioTracks: List<EngineTrackInfo> = emptyList(),
    val subtitleTracks: List<EngineTrackInfo> = emptyList(),
    val isEpisode: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isLocked: Boolean = false,
    val panel: PlayerPanel = PlayerPanel.NONE,
    val playbackSpeed: Float = 1f,
    val aspectMode: AspectMode = AspectMode.FIT,
    val subtitleDelayMs: Long = 0,
    val subtitleScale: Float = 1f,
    val supportsSubtitleDelay: Boolean = false,
    val supportsSubtitleScale: Boolean = false,
    /** null shows every language; "" groups tracks without a language tag. */
    val subtitleLanguageFilter: String? = null,
    val info: PlayerInfoUi? = null,
    val logoPath: String? = null,
    val overview: String? = null,
    /** Skip button for the segment the position is inside; shown even while controls are hidden. */
    val skipButton: SkipSegmentType? = null,
) {
    /** The paused "You're watching" card: subtitles and the skip button step aside for it. */
    val isPauseCardVisible: Boolean
        get() =
            !showControls &&
                !isLocked &&
                !isPlaying &&
                playbackState == EnginePlaybackState.READY &&
                panel == PlayerPanel.NONE &&
                failure == null &&
                durationMs > 0
}

enum class PlayerPanel {
    NONE,
    SUBTITLES,
    AUDIO,
    SPEED,
    INFO,
}

/** Snapshot for the info overlay, taken when it opens. */
data class PlayerInfoUi(
    val fileName: String,
    val engineType: EngineType,
    val video: EngineVideoInfo?,
    val audio: EngineTrackInfo?,
    val subtitle: EngineTrackInfo?,
)

sealed interface PlayerAction {
    data object OnPlayPause : PlayerAction

    data object Pause : PlayerAction

    data class OnSeek(val positionMs: Long) : PlayerAction

    data object OnSeekForward : PlayerAction

    data object OnSeekBackward : PlayerAction

    data class OnScrub(val positionMs: Long) : PlayerAction

    data object OnScrubEnd : PlayerAction

    data class OnControlsVisibilityChanged(val isVisible: Boolean) : PlayerAction

    data object OnPlayNext : PlayerAction

    data object OnPlayPrevious : PlayerAction

    data object OnExternalFallback : PlayerAction

    data class ExternalLaunchResult(val succeeded: Boolean) : PlayerAction

    data class SelectAudio(val id: String) : PlayerAction

    data class SelectSubtitle(val id: String?) : PlayerAction

    data object RefreshTracks : PlayerAction

    data object Retry : PlayerAction

    data class OnLockChange(val isLocked: Boolean) : PlayerAction

    data class OnPanelChange(val panel: PlayerPanel) : PlayerAction

    data class OnSpeedSelect(val speed: Float) : PlayerAction

    data object OnAspectCycle : PlayerAction

    data class OnSubtitleDelayChange(val deltaMs: Long) : PlayerAction

    data object OnSubtitleDelayReset : PlayerAction

    data class OnSubtitleScaleChange(val delta: Float) : PlayerAction

    data class OnSubtitleLanguageFilter(val language: String?) : PlayerAction

    data object OnSkipSegment : PlayerAction
}

sealed interface PlayerEvent {
    data object NavigateBack : PlayerEvent

    data class LaunchExternalPlayer(val intent: Intent) : PlayerEvent

    data class ShowError(val message: UiText) : PlayerEvent
}

class PlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaybackRepository,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
    private val settings: SettingsRepository,
    private val factory: PlayerEngineFactory,
    private val focus: PlaybackAudioFocus,
    private val trackPreferences: TrackPreferenceRepository,
    skipSegments: SkipSegmentRepository,
) : ViewModel() {
    private val mediaType =
        MediaType.valueOf(checkNotNull(savedStateHandle.get<String>("mediaType")))
    private val mediaId = checkNotNull(savedStateHandle.get<Long>("mediaId"))
    private val mutableState = MutableStateFlow(PlayerState())
    val state = mutableState.asStateFlow()
    private val mutableEvents = Channel<PlayerEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    var engine: LionPlayerEngine? = null
        private set

    private var target: PlaybackTarget? = null
    private var observer: Job? = null
    private var ticker: Job? = null
    private var scrubbing = false
    private var audioDefaultApplied = false
    private var subtitleDefaultApplied = false
    private var trackRevision = -1L
    private var recovered = false
    private var ended = false
    private var transitioning = false
    private var pausedByLifecycle = false
    /** Remembered choice for the current title; null until loaded or when there is none. */
    private var trackPreference: TrackPreference? = null
    private val transition = Mutex()
    private val skipSession = SkipSegmentSession(skipSegments, viewModelScope)
    private var engineSeekable = false

    init {
        viewModelScope.launch {
            settings.onlineSkipTimes.distinctUntilChanged().collect {
                skipSession.setOnlineEnabled(it)
            }
        }
        viewModelScope.launch { skipSession.segments.collect { refreshSkipButton() } }
        viewModelScope.launch {
            // Keyed by session so the next file's engine is hidden too when the card stays up.
            state
                .map { it.session to it.isPauseCardVisible }
                .distinctUntilChanged()
                .collect { (_, hidden) -> engine?.setSubtitlesHidden(hidden) }
        }
        viewModelScope.launch {
            transition.withLock {
                try {
                    val initial = repository.target(mediaType, mediaId)
                    if (initial == null)
                        mutableState.update {
                            it.copy(failure = UiText.Resource(R.string.file_access_error))
                        }
                    else open(initial)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update {
                        it.copy(failure = UiText.Resource(R.string.playback_failure))
                    }
                }
            }
        }
    }

    private suspend fun open(next: PlaybackTarget) {
        transitioning = true
        try {
            // Previous progress has already been captured. Detach its observer before releasing it.
            observer?.cancelAndJoin()
            ticker?.cancelAndJoin()
            focus.abandon()
            engine?.release()
            engine = null
            target = next
            ended = false
            recovered = false
            audioDefaultApplied = false
            subtitleDefaultApplied = false
            trackRevision = -1L
            engineSeekable = false
            skipSession.start(next.skipLookupKey())
            trackPreference = trackPreferences.get(next.mediaId)
            val preferred =
                if (settings.forceLibVlc.first()) EngineType.LIBVLC
                else
                    EngineType.entries.find { it.name == next.preferredEngine }
                        ?: EngineType.EXOPLAYER
            val selected = factory.create(preferred)
            engine = selected
            val previous = state.value
            selected.setPlaybackSpeed(previous.playbackSpeed)
            selected.setAspectMode(previous.aspectMode)
            selected.setSubtitleScale(previous.subtitleScale)
            mutableState.value =
                PlayerState(
                    title = next.title,
                    subtitle =
                        next.season?.let { season ->
                            "S$season E${next.episode}" +
                                (next.episodeEnd?.let { "–E$it" } ?: "") +
                                next.episodeTitle?.let { " · $it" }.orEmpty()
                        } ?: next.year?.toString().orEmpty(),
                    engineType = preferred,
                    session = state.value.session + 1,
                    isEpisode = next.episodeId != 0L,
                    logoPath = next.logoPath,
                    overview = next.overview,
                    isLocked = previous.isLocked,
                    playbackSpeed = previous.playbackSpeed,
                    aspectMode = previous.aspectMode,
                    subtitleScale = previous.subtitleScale,
                    supportsSubtitleDelay = selected.supportsSubtitleDelay,
                    supportsSubtitleScale = selected.supportsSubtitleScale,
                )
            if (next.episodeId != 0L) {
                val hasNext = repository.adjacent(next, true) != null
                val hasPrevious = repository.adjacent(next, false) != null
                mutableState.update { it.copy(hasNext = hasNext, hasPrevious = hasPrevious) }
            }
            val path = next.filePath
            if (path == null) {
                mutableState.update {
                    it.copy(failure = UiText.Resource(R.string.file_access_error))
                }
                return
            }
            val progress = repository.progress(next)
            selected.setMedia(Uri.parse(path), next.subtitlePath?.let(Uri::parse))
            if (progress != null && !progress.completed && progress.lastPositionMs > 0) {
                selected.seekTo(progress.lastPositionMs)
                mutableState.update { it.copy(currentPositionMs = progress.lastPositionMs) }
            }
            observe(selected)
            ticker = viewModelScope.launch {
                var ticks = 0
                while (isActive) {
                    delay(500)
                    if (!scrubbing) {
                        mutableState.update {
                            it.copy(currentPositionMs = selected.currentPositionMs.coerceAtLeast(0))
                        }
                        refreshSkipButton()
                    }
                    if (state.value.isPlaying && ++ticks % 16 == 0)
                        save()?.let { checkSave(it.await()) }
                }
            }
            if (!pausedByLifecycle) play()
        } finally {
            transitioning = false
        }
    }

    private fun observe(selected: LionPlayerEngine) {
        observer = viewModelScope.launch {
            selected.state.collect { value ->
                // Completion is captured before exposing ENDED to lifecycle/UI observers.
                val newlyEnded = value.playbackState == EnginePlaybackState.ENDED && !ended
                val completion =
                    if (newlyEnded) {
                        ended = true
                        save(true)
                    } else null
                mutableState.update {
                    it.copy(
                        isPlaying = value.isPlaying,
                        playbackState = value.playbackState,
                        durationMs = value.durationMs.coerceAtLeast(0),
                    )
                }
                engineSeekable = value.isSeekable
                // Starts the online lookup once a duration is known; playback never waits for it.
                skipSession.onPlayback(value.durationMs, value.chapters)
                refreshSkipButton()
                if (
                    value.playbackState == EnginePlaybackState.READY &&
                        trackRevision != value.trackRevision
                ) {
                    applyDefaults()
                    refreshTracks()
                    trackRevision = value.trackRevision
                }
                if (newlyEnded) advanceAfterCompletion(completion)
                value.error?.let { failure ->
                    if (failure == PlaybackFailure.TRACK && !recovered) {
                        recovered = true
                        val position = selected.currentPositionMs.coerceAtLeast(0)
                        val current = target
                        selected.resetToDefaultTrackSelection()
                        current?.filePath?.let {
                            selected.setMedia(
                                Uri.parse(it),
                                current.subtitlePath?.let(Uri::parse),
                            )
                            selected.seekTo(position)
                            play()
                        }
                        mutableEvents.send(
                            PlayerEvent.ShowError(UiText.Resource(R.string.audio_track_recovered))
                        )
                    } else {
                        selected.pause()
                        focus.abandon()
                        mutableState.update {
                            it.copy(
                                failure =
                                    UiText.Resource(
                                        if (failure == PlaybackFailure.FILE_ACCESS)
                                            R.string.file_access_error
                                        else R.string.playback_failure
                                    ),
                                showControls = true,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The single guarded path after a file completes, whether it reached ENDED or its terminal
     * credits were skipped: wait for the completion write, then open the next episode once.
     */
    private fun advanceAfterCompletion(completion: Deferred<OperationResult<Unit, DataError>>?) {
        if (transitioning) return
        transitioning = true
        val completedTarget = target
        runTransition {
            completion?.let { checkSave(it.await()) }
            val current = target
            if (current != null && current == completedTarget) {
                repository.adjacent(current, true)?.let { open(it) } ?: focus.abandon()
            }
        }
    }

    private fun refreshSkipButton() {
        val current = state.value
        val type =
            if (!engineSeekable || ended || transitioning) null
            else
                SkipSegmentPolicy.active(
                        skipSession.segments.value,
                        current.currentPositionMs,
                        current.durationMs,
                    )
                    ?.type
        if (type != current.skipButton) mutableState.update { it.copy(skipButton = type) }
    }

    private fun skipSegment() {
        val current = state.value
        if (!engineSeekable || ended || transitioning) return
        val segment =
            SkipSegmentPolicy.active(
                skipSession.segments.value,
                current.currentPositionMs,
                current.durationMs,
            ) ?: return
        val end = segment.endMs
        if (end == null || SkipSegmentPolicy.isTerminal(segment, current.durationMs)) {
            // Finishing the file is not a seek to its end: complete it exactly like ENDED. A paused
            // viewer stays paused because pause() already set pausedByLifecycle.
            engine?.pause()
            ended = true
            refreshSkipButton()
            advanceAfterCompletion(save(true))
        } else {
            seek(end)
        }
    }

    private fun play() {
        if (focus.request { onAction(PlayerAction.Pause) }) engine?.play()
    }

    private fun pause() {
        pausedByLifecycle = true
        engine?.pause()
        focus.abandon()
        save()
    }

    private fun applyDefaults() {
        val saved = trackPreference
        val audioTracks = engine?.getAudioTracks().orEmpty()
        if (!audioDefaultApplied)
            saved
                ?.audio
                ?.let { SavedTrackMatcher.find(audioTracks, it) }
                ?.let {
                    engine?.selectAudioTrack(it.id)
                    audioDefaultApplied = true
                }
        val subtitleTracks = engine?.getSubtitleTracks().orEmpty()
        if (!subtitleDefaultApplied && saved?.subtitlesOff == true && subtitleTracks.isNotEmpty()) {
            engine?.selectSubtitleTrack(null)
            subtitleDefaultApplied = true
        }
        if (!subtitleDefaultApplied)
            saved
                ?.subtitle
                ?.let { SavedTrackMatcher.find(subtitleTracks, it) }
                ?.let {
                    engine?.selectSubtitleTrack(it.id)
                    subtitleDefaultApplied = true
                }
        // Without a remembered match, fall back to the language defaults.
        if (!audioDefaultApplied)
            TrackLanguageMatcher.findBestAudioTrack(
                    engine?.getAudioTracks().orEmpty(),
                    if (target?.preferJapaneseAudio == true) MediaType.ANIME
                    else target?.type ?: mediaType,
                )
                ?.let {
                    engine?.selectAudioTrack(it.id)
                    audioDefaultApplied = true
                }
        if (!subtitleDefaultApplied)
            TrackLanguageMatcher.findBestSubtitleTrack(engine?.getSubtitleTracks().orEmpty())?.let {
                engine?.selectSubtitleTrack(it.id)
                subtitleDefaultApplied = true
            }
    }

    private fun refreshTracks() {
        mutableState.update {
            it.copy(
                audioTracks = engine?.getAudioTracks().orEmpty(),
                subtitleTracks = engine?.getSubtitleTracks().orEmpty(),
            )
        }
    }

    fun onAction(action: PlayerAction) {
        when (action) {
            PlayerAction.OnPlayPause -> {
                if (ended) {
                    onAction(PlayerAction.Retry)
                } else if (state.value.isPlaying) {
                    pause()
                } else {
                    pausedByLifecycle = false
                    play()
                }
            }
            PlayerAction.Pause -> pause()
            is PlayerAction.OnSeek -> seek(action.positionMs)
            PlayerAction.OnSeekForward -> seek(state.value.currentPositionMs + 10_000)
            PlayerAction.OnSeekBackward -> seek(state.value.currentPositionMs - 10_000)
            is PlayerAction.OnScrub -> {
                scrubbing = true
                mutableState.update {
                    it.copy(currentPositionMs = action.positionMs.coerceAtLeast(0))
                }
            }
            PlayerAction.OnScrubEnd -> {
                scrubbing = false
                seek(state.value.currentPositionMs)
            }
            is PlayerAction.OnControlsVisibilityChanged ->
                mutableState.update { it.copy(showControls = action.isVisible) }
            PlayerAction.OnPlayNext -> move(true)
            PlayerAction.OnPlayPrevious -> move(false)
            PlayerAction.OnExternalFallback -> external()
            is PlayerAction.ExternalLaunchResult ->
                viewModelScope.launch {
                    if (action.succeeded) mutableEvents.send(PlayerEvent.NavigateBack)
                    else
                        mutableEvents.send(
                            PlayerEvent.ShowError(UiText.Resource(R.string.external_launch_error))
                        )
                }
            is PlayerAction.SelectAudio -> {
                audioDefaultApplied = true
                val chosen = state.value.audioTracks.firstOrNull { it.id == action.id }
                engine?.selectAudioTrack(action.id)
                refreshTracks()
                chosen?.let { rememberTracks { copy(audio = SavedTrackMatcher.toSaved(it)) } }
            }
            is PlayerAction.SelectSubtitle -> {
                subtitleDefaultApplied = true
                val chosen = state.value.subtitleTracks.firstOrNull { it.id == action.id }
                engine?.selectSubtitleTrack(action.id)
                refreshTracks()
                if (action.id == null) rememberTracks { copy(subtitlesOff = true) }
                else
                    chosen?.let {
                        rememberTracks {
                            copy(subtitle = SavedTrackMatcher.toSaved(it), subtitlesOff = false)
                        }
                    }
            }
            PlayerAction.RefreshTracks -> refreshTracks()
            is PlayerAction.OnLockChange ->
                mutableState.update {
                    it.copy(
                        isLocked = action.isLocked,
                        panel = PlayerPanel.NONE,
                        showControls = !action.isLocked,
                    )
                }
            is PlayerAction.OnPanelChange -> openPanel(action.panel)
            is PlayerAction.OnSpeedSelect -> {
                val speed = action.speed.coerceIn(MIN_SPEED, MAX_SPEED)
                engine?.setPlaybackSpeed(speed)
                mutableState.update { it.copy(playbackSpeed = speed) }
            }
            PlayerAction.OnAspectCycle -> {
                val next =
                    AspectMode.entries[
                            (state.value.aspectMode.ordinal + 1) % AspectMode.entries.size]
                engine?.setAspectMode(next)
                mutableState.update { it.copy(aspectMode = next) }
            }
            is PlayerAction.OnSubtitleDelayChange ->
                setSubtitleDelay(state.value.subtitleDelayMs + action.deltaMs)
            PlayerAction.OnSubtitleDelayReset -> setSubtitleDelay(0)
            is PlayerAction.OnSubtitleScaleChange -> {
                val scale = ((state.value.subtitleScale + action.delta) * 100).roundToInt() / 100f
                val bounded = scale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)
                engine?.setSubtitleScale(bounded)
                mutableState.update { it.copy(subtitleScale = bounded) }
            }
            is PlayerAction.OnSubtitleLanguageFilter ->
                mutableState.update { it.copy(subtitleLanguageFilter = action.language) }
            PlayerAction.OnSkipSegment -> skipSegment()
            PlayerAction.Retry -> {
                if (!transitioning) {
                    transitioning = true
                    val snapshot = save()
                    runTransition {
                        snapshot?.let { checkSave(it.await()) }
                        target?.let {
                            pausedByLifecycle = false
                            open(it)
                        }
                    }
                }
            }
        }
    }

    /** Saves the viewer's track choice for the whole movie or show. */
    private fun rememberTracks(change: TrackPreference.() -> TrackPreference) {
        val mediaId = target?.mediaId ?: return
        val updated = (trackPreference ?: TrackPreference()).change()
        trackPreference = updated
        viewModelScope.launch { trackPreferences.save(mediaId, updated) }
    }

    private fun openPanel(panel: PlayerPanel) {
        if (panel == PlayerPanel.SUBTITLES || panel == PlayerPanel.AUDIO) refreshTracks()
        mutableState.update {
            it.copy(
                panel = panel,
                info = if (panel == PlayerPanel.INFO) infoSnapshot() else null,
                // Keep controls up underneath so closing a panel returns to them.
                showControls = it.showControls || panel != PlayerPanel.NONE,
            )
        }
    }

    private fun infoSnapshot(): PlayerInfoUi? {
        val current = target ?: return null
        val selected = engine ?: return null
        return PlayerInfoUi(
            fileName =
                current.filePath
                    ?.let { it.toUri().lastPathSegment }
                    ?.substringAfterLast('/')
                    ?.ifBlank { null } ?: current.title,
            engineType = selected.engineType,
            video = selected.getVideoInfo(),
            audio = selected.getAudioTracks().firstOrNull { it.isSelected },
            subtitle = selected.getSubtitleTracks().firstOrNull { it.isSelected },
        )
    }

    private fun setSubtitleDelay(delayMs: Long) {
        val bounded = delayMs.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
        engine?.setSubtitleDelay(bounded)
        mutableState.update { it.copy(subtitleDelayMs = bounded) }
    }

    private fun seek(position: Long) {
        val duration = state.value.durationMs
        val bounded =
            if (duration > 0) position.coerceIn(0, duration) else position.coerceAtLeast(0)
        engine?.seekTo(bounded)
        mutableState.update { it.copy(currentPositionMs = bounded) }
        refreshSkipButton()
    }

    private fun move(next: Boolean) {
        if (transitioning) return
        transitioning = true
        // Capture immediately, before querying or switching identity.
        val snapshot = save()
        runTransition {
            snapshot?.let { checkSave(it.await()) }
            val current = target
            if (current != null)
                repository.adjacent(current, next)?.let {
                    pausedByLifecycle = false
                    open(it)
                }
        }
    }

    private fun runTransition(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                transition.withLock { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                engine?.pause()
                focus.abandon()
                mutableState.update {
                    it.copy(
                        isPlaying = false,
                        failure = UiText.Resource(R.string.playback_failure),
                        showControls = true,
                    )
                }
            } finally {
                transitioning = false
            }
        }
    }

    private fun external() {
        if (state.value.isExternalFallbackLoading) return
        val current = target ?: return
        val path = current.filePath ?: return
        pause()
        mutableState.update { it.copy(isExternalFallbackLoading = true) }
        viewModelScope.launch {
            try {
                mutableEvents.send(
                    PlayerEvent.LaunchExternalPlayer(
                        launchPlayerUseCase(Uri.parse(path), state.value.currentPositionMs)
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableEvents.send(
                    PlayerEvent.ShowError(UiText.Resource(R.string.external_launch_error))
                )
            } finally {
                mutableState.update { it.copy(isExternalFallbackLoading = false) }
            }
        }
    }

    private fun save(completed: Boolean = ended): Deferred<OperationResult<Unit, DataError>>? {
        val current = target ?: return null
        val position = (engine?.currentPositionMs ?: state.value.currentPositionMs).coerceAtLeast(0)
        if (position == 0L && !completed) return null
        return repository.save(
            PlaybackSnapshot(
                current.mediaId,
                current.episodeId,
                position,
                engine?.durationMs ?: state.value.durationMs,
                completed,
                System.currentTimeMillis(),
            )
        )
    }

    private suspend fun checkSave(result: OperationResult<Unit, DataError>) {
        if (result is OperationResult.Failure)
            mutableEvents.send(PlayerEvent.ShowError(result.error.toUiText()))
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
        const val MIN_SUBTITLE_SCALE = 0.5f
        const val MAX_SUBTITLE_SCALE = 2f
        const val MAX_SUBTITLE_DELAY_MS = 60_000L
    }

    override fun onCleared() {
        save()
        observer?.cancel()
        ticker?.cancel()
        focus.abandon()
        engine?.release()
        engine = null
    }
}
