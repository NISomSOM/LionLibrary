package com.singam.lionlibrary.presentation.player.engine

import android.net.Uri
import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

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

// Video engine interface.
interface LionPlayerEngine {

    // Playback state.
    val state: StateFlow<EngineState>

    // Engine type.
    val engineType: EngineType

    // Load media.
    fun setMedia(uri: Uri, subtitleUri: Uri?)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    // Audio tracks.
    fun getAudioTracks(): List<EngineTrackInfo>

    // Select audio track.
    fun selectAudioTrack(id: String)

    // Subtitle tracks.
    fun getSubtitleTracks(): List<EngineTrackInfo>

    // Reset tracks.
    fun resetToDefaultTrackSelection()

    // Select subtitle track.
    fun selectSubtitleTrack(id: String?)

    // Attach to view.
    fun attachToView(container: ViewGroup)

    // Current position.
    val currentPositionMs: Long

    // Duration.
    val durationMs: Long

    // Stop playback.
    fun stop()

    // Release resources.
    fun release()
}
