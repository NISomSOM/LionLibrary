package com.singam.lionlibrary.presentation.player.engine

import android.net.Uri
import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

// ---------------------------------------------------------------------------
// Engine-agnostic state
// ---------------------------------------------------------------------------

enum class EnginePlaybackState {
    IDLE, BUFFERING, READY, ENDED
}

data class EngineState(
    val isPlaying: Boolean = false,
    val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

data class EngineTrackInfo(
    val id: String,
    val label: String,
    val isSelected: Boolean
)

enum class EngineType {
    EXOPLAYER, LIBVLC
}

// ---------------------------------------------------------------------------
// Engine interface
// ---------------------------------------------------------------------------

/**
 * Interface for video playback engines (ExoPlayer or libVLC).
 */
interface LionPlayerEngine {

    /** Emits playback state changes. Collect in PlayerViewModel. */
    val state: StateFlow<EngineState>

    /** Which engine backs this instance — used by PlayerScreen to branch the AndroidView. */
    val engineType: EngineType

    /** Load media and optional subtitle without starting playback. */
    fun setMedia(uri: Uri, subtitleUri: Uri?)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** Returns available audio tracks. Empty list when engine is in IDLE state. */
    fun getAudioTracks(): List<EngineTrackInfo>

    /** Selects an audio track by its [id] (as provided by [getAudioTracks]). */
    fun selectAudioTrack(id: String)

    /** Returns available subtitle/text tracks. */
    fun getSubtitleTracks(): List<EngineTrackInfo>

    /** Reset track selection to default to recover from playback errors. */
    fun resetToDefaultTrackSelection()

    /** Select a subtitle track, or null to disable. */
    fun selectSubtitleTrack(id: String?)

    /** Attach rendering surface to the view container after media is loaded. */
    fun attachToView(container: ViewGroup)

    /** Current playback position in milliseconds. Used for progress polling. */
    val currentPositionMs: Long

    /** Current media duration in milliseconds. */
    val durationMs: Long

    /** Stop playback without releasing resources. Used when switching episodes. */
    fun stop()

    /** Release all engine resources. Must be called in ViewModel.onCleared(). */
    fun release()
}
