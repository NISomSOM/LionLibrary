package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ExoPlayer implementation.
class ExoPlayerEngine(context: Context) : LionPlayerEngine {

    override val engineType: EngineType = EngineType.EXOPLAYER

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
    ).build().apply {
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage("en")
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()

        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val enginePlaybackState = when (playbackState) {
                    Player.STATE_IDLE -> EnginePlaybackState.IDLE
                    Player.STATE_BUFFERING -> EnginePlaybackState.BUFFERING
                    Player.STATE_READY -> EnginePlaybackState.READY
                    Player.STATE_ENDED -> EnginePlaybackState.ENDED
                    else -> EnginePlaybackState.IDLE
                }
                _state.update { it.copy(playbackState = enginePlaybackState) }
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = this@apply.duration.coerceAtLeast(0L)) }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.update { it.copy(error = error.message ?: "Playback error") }
            }
        })
    }

    override fun setMedia(uri: Uri, subtitleUri: Uri?) {
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        if (subtitleUri != null) {
            val ext = subtitleUri.path?.substringAfterLast('.')?.lowercase() ?: "srt"
            val mimeType = when (ext) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun getAudioTracks(): List<EngineTrackInfo> {
        val tracks = exoPlayer.currentTracks
        val result = mutableListOf<EngineTrackInfo>()
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    result.add(
                        EngineTrackInfo(
                            id = "${group.mediaTrackGroup.id}_$i",
                            label = format.language ?: format.label ?: "Audio Track ${result.size + 1}",
                            isSelected = isSelected
                        )
                    )
                }
            }
        return result
    }

    override fun selectAudioTrack(id: String) {
        val tracks = exoPlayer.currentTracks
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .forEach { group ->
                for (i in 0 until group.length) {
                    val trackId = "${group.mediaTrackGroup.id}_$i"
                    if (trackId == id) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                        return
                    }
                }
            }
    }

    override fun getSubtitleTracks(): List<EngineTrackInfo> {
        val tracks = exoPlayer.currentTracks
        val isDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes
            .contains(C.TRACK_TYPE_TEXT)
        val result = mutableListOf<EngineTrackInfo>()
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i) && !isDisabled
                    result.add(
                        EngineTrackInfo(
                            id = "${group.mediaTrackGroup.id}_$i",
                            label = format.language ?: format.label ?: "Subtitle Track ${result.size + 1}",
                            isSelected = isSelected
                        )
                    )
                }
            }
        return result
    }

    override fun resetToDefaultTrackSelection() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .build()
    }

    override fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        val tracks = exoPlayer.currentTracks
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .forEach { group ->
                for (i in 0 until group.length) {
                    val trackId = "${group.mediaTrackGroup.id}_$i"
                    if (trackId == id) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                            .build()
                        return
                    }
                }
            }
    }

    override fun attachToView(container: ViewGroup) {
        val playerView = container as PlayerView
        playerView.player = exoPlayer
    }

    override fun release() {
        exoPlayer.release()
    }

    // Current position.
    override val currentPositionMs: Long get() = exoPlayer.currentPosition

    // Duration.
    override val durationMs: Long get() = exoPlayer.duration.coerceAtLeast(0L)

    // Stop playback.
    override fun stop() = exoPlayer.stop()
}
