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
 * Abstraction over the concrete video-engine (ExoPlayer or libVLC).
 *
 * Rules enforced by the dual-engine design:
 *  - The engine instance is created once per player-screen lifecycle (in
 *    PlayerViewModel), never recreated inside a Composable.
 *  - Engine selection happens once at player launch and is never changed
 *    mid-session.
 *  - Watch progress is always keyed on mediaId/episodeId, never on engine.
 */
interface LionPlayerEngine {

    /** Emits playback state changes. Collect in PlayerViewModel. */
    val state: StateFlow<EngineState>

    /** Which engine backs this instance — used by PlayerScreen to branch the AndroidView. */
    val engineType: EngineType

    /**
     * Load media from [uri], optionally with a sidecar subtitle at [subtitleUri].
     * Does NOT start playback; call [play] separately.
     */
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

    /**
     * Selects a subtitle track by [id], or pass `null` to disable subtitles.
     */
    fun selectSubtitleTrack(id: String?)

    /**
     * Attaches the engine's rendering surface to [container].
     * For ExoPlayer: [container] is a [PlayerView].
     * For libVLC:    [container] is a [VLCVideoLayout].
     *
     * Must be called AFTER [setMedia] is called and the engine has prepared
     * the player — never before media is set.
     */
    fun attachToView(container: ViewGroup)

    /** Release all engine resources. Must be called in ViewModel.onCleared(). */
    fun release()
}
