package com.singam.lionlibrary.presentation.player.engine

import android.net.Uri
import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

enum class EnginePlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

enum class PlaybackFailure {
    TRACK,
    FILE_ACCESS,
    DECODER,
    UNKNOWN,
}

data class EngineState(
    val isPlaying: Boolean = false,
    val playbackState: EnginePlaybackState = EnginePlaybackState.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: PlaybackFailure? = null,
    val trackRevision: Long = 0,
)

data class EngineTrackInfo(
    val id: String,
    val label: String,
    val isSelected: Boolean,
    /** ISO 639 code as reported by the container, when present. */
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val sampleRateHz: Int? = null,
    /** True for a sidecar subtitle file loaded next to the video. */
    val isExternal: Boolean = false,
)

data class EngineVideoInfo(
    val codec: String?,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    val bitrate: Int?,
)

enum class AspectMode {
    FIT,
    FILL,
    ZOOM,
}

enum class EngineType {
    EXOPLAYER,
    LIBVLC,
}

// Video engine interface.
interface LionPlayerEngine {

    // Playback state.
    val state: StateFlow<EngineState>

    // Engine type.
    val engineType: EngineType

    // Load media.
    suspend fun setMedia(uri: Uri, subtitleUri: Uri?)

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

    // Speed and picture, kept across media changes by each engine.
    fun setPlaybackSpeed(speed: Float) {}

    fun setAspectMode(mode: AspectMode) {}

    // Subtitle style; each engine supports a different subset at runtime.
    val supportsSubtitleDelay: Boolean
        get() = false

    fun setSubtitleDelay(delayMs: Long) {}

    val supportsSubtitleScale: Boolean
        get() = false

    fun setSubtitleScale(scale: Float) {}

    // Details of the playing video stream, when the engine exposes them.
    fun getVideoInfo(): EngineVideoInfo? = null

    // Release resources.
    fun release()
}
