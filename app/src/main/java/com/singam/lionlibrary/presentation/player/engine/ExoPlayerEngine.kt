package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.singam.lionlibrary.domain.model.PlaybackChapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ExoPlayer implementation.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoPlayerEngine(context: Context) : LionPlayerEngine {

    override val engineType: EngineType = EngineType.EXOPLAYER

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    private var released = false
    private var attachedView: PlayerView? = null
    private var subtitlesHidden = false
    private var aspectMode = AspectMode.FIT
    private var subtitleScale = 1f

    val exoPlayer: ExoPlayer =
        ExoPlayer.Builder(
                context,
                DefaultRenderersFactory(context).apply {
                    setEnableDecoderFallback(true)
                    // Platform decoders are preferred; VLC supplies the alternate codec path.
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                },
            )
            .build()
            .apply {
                trackSelectionParameters =
                    trackSelectionParameters
                        .buildUpon()
                        .setPreferredTextLanguage("en")
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()

                addListener(
                    object : Player.Listener {
                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            val chapters = chapters(tracks)
                            _state.update {
                                it.copy(trackRevision = it.trackRevision + 1, chapters = chapters)
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _state.update { it.copy(isPlaying = isPlaying) }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val enginePlaybackState =
                                when (playbackState) {
                                    Player.STATE_IDLE -> EnginePlaybackState.IDLE
                                    Player.STATE_BUFFERING -> EnginePlaybackState.BUFFERING
                                    Player.STATE_READY -> EnginePlaybackState.READY
                                    Player.STATE_ENDED -> EnginePlaybackState.ENDED
                                    else -> EnginePlaybackState.IDLE
                                }
                            _state.update { it.copy(playbackState = enginePlaybackState) }
                            if (playbackState == Player.STATE_READY) {
                                _state.update {
                                    it.copy(
                                        durationMs = this@apply.duration.coerceAtLeast(0L),
                                        isSeekable = isCurrentMediaItemSeekable,
                                    )
                                }
                            }
                        }

                        override fun onPlayerError(
                            error: androidx.media3.common.PlaybackException
                        ) {
                            val failure =
                                when (error.errorCode) {
                                    androidx.media3.common.PlaybackException
                                        .ERROR_CODE_IO_FILE_NOT_FOUND,
                                    androidx.media3.common.PlaybackException
                                        .ERROR_CODE_IO_NO_PERMISSION -> PlaybackFailure.FILE_ACCESS
                                    androidx.media3.common.PlaybackException
                                        .ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                                    androidx.media3.common.PlaybackException
                                        .ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                                        PlaybackFailure.TRACK
                                    androidx.media3.common.PlaybackException
                                        .ERROR_CODE_DECODER_INIT_FAILED,
                                    androidx.media3.common.PlaybackException
                                        .ERROR_CODE_DECODING_FAILED -> PlaybackFailure.DECODER
                                    else -> PlaybackFailure.UNKNOWN
                                }
                            _state.update { it.copy(isPlaying = false, error = failure) }
                        }
                    }
                )
            }

    override suspend fun setMedia(uri: Uri, subtitleUri: Uri?) {
        _state.value = EngineState(playbackState = EnginePlaybackState.BUFFERING)
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        if (subtitleUri != null) {
            val ext = subtitleUri.path?.substringAfterLast('.')?.lowercase() ?: "srt"
            val mimeType =
                when (ext) {
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass",
                    "ssa" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
            val subtitleConfig =
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(mimeType)
                    .setId(SIDECAR_SUBTITLE_ID)
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
                            label = trackLabel(format, "Audio Track ${result.size + 1}"),
                            isSelected = isSelected,
                            language = format.language?.takeIf { it.isNotBlank() && it != "und" },
                            codec = PlayerCodecNames.fromMime(format.sampleMimeType),
                            channels = format.channelCount.takeIf { it > 0 },
                            sampleRateHz = format.sampleRate.takeIf { it > 0 },
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
                        exoPlayer.trackSelectionParameters =
                            exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                                .build()
                        return
                    }
                }
            }
    }

    override fun getSubtitleTracks(): List<EngineTrackInfo> {
        val tracks = exoPlayer.currentTracks
        val isDisabled =
            exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
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
                            label = trackLabel(format, "Subtitle Track ${result.size + 1}"),
                            isSelected = isSelected,
                            language = format.language?.takeIf { it.isNotBlank() && it != "und" },
                            codec = PlayerCodecNames.fromMime(format.sampleMimeType),
                            isExternal = format.id?.endsWith(SIDECAR_SUBTITLE_ID) == true,
                        )
                    )
                }
            }
        return result
    }

    override fun resetToDefaultTrackSelection() {
        exoPlayer.trackSelectionParameters =
            exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
    }

    override fun selectSubtitleTrack(id: String?) {
        if (id == null) {
            exoPlayer.trackSelectionParameters =
                exoPlayer.trackSelectionParameters
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
                        exoPlayer.trackSelectionParameters =
                            exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                                .build()
                        return
                    }
                }
            }
    }

    override fun attachToView(container: ViewGroup) {
        if (released) return
        val playerView = container as PlayerView
        if (attachedView !== playerView) attachedView?.player = null
        attachedView = playerView
        playerView.player = exoPlayer
        applyAspectMode()
        applySubtitleScale()
        applySubtitlesHidden()
    }

    override fun setSubtitlesHidden(hidden: Boolean) {
        subtitlesHidden = hidden
        applySubtitlesHidden()
    }

    private fun applySubtitlesHidden() {
        attachedView?.subtitleView?.visibility =
            if (subtitlesHidden) View.INVISIBLE else View.VISIBLE
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (!released) exoPlayer.setPlaybackSpeed(speed)
    }

    override fun setAspectMode(mode: AspectMode) {
        aspectMode = mode
        applyAspectMode()
    }

    override val supportsSubtitleScale = true

    override fun setSubtitleScale(scale: Float) {
        subtitleScale = scale
        applySubtitleScale()
    }

    override fun getVideoInfo(): EngineVideoInfo? {
        if (released) return null
        val format = exoPlayer.videoFormat ?: return null
        return EngineVideoInfo(
            codec = PlayerCodecNames.fromMime(format.sampleMimeType),
            width = format.width.coerceAtLeast(0),
            height = format.height.coerceAtLeast(0),
            frameRate = format.frameRate.takeIf { it > 0f },
            bitrate =
                listOf(format.averageBitrate, format.bitrate, format.peakBitrate).firstOrNull {
                    it > 0
                },
        )
    }

    private fun applyAspectMode() {
        attachedView?.resizeMode =
            when (aspectMode) {
                AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
    }

    private fun applySubtitleScale() {
        attachedView
            ?.subtitleView
            ?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_SUBTITLE_SP * subtitleScale)
    }

    override fun release() {
        if (released) return
        released = true
        attachedView?.player = null
        attachedView = null
        exoPlayer.release()
    }

    // Current position.
    override val currentPositionMs: Long
        get() = exoPlayer.currentPosition

    // Duration.
    override val durationMs: Long
        get() = exoPlayer.duration.coerceAtLeast(0L)

    // Stop playback.
    override fun stop() = exoPlayer.stop()

    /**
     * Media3 attaches MKV chapters and MP4 Nero/QuickTime chapters to the track formats' metadata;
     * every track repeats them, so duplicates are removed. A missing end becomes null.
     */
    private fun chapters(tracks: androidx.media3.common.Tracks): List<PlaybackChapter> =
        tracks.groups
            .flatMap { group ->
                (0 until group.length).mapNotNull { group.getTrackFormat(it).metadata }
            }
            .flatMap { metadata -> metadata.getEntriesOfType(Chapter::class.java) }
            .filterNot { it.isHidden }
            .map { chapter ->
                PlaybackChapter(
                    startMs = chapter.startTimeMs.coerceAtLeast(0),
                    endMs =
                        chapter.endTimeMs.takeIf {
                            it != C.TIME_UNSET && it > chapter.startTimeMs
                        },
                    title = chapter.title?.value,
                )
            }
            .distinct()
            .sortedBy { it.startMs }

    private fun trackLabel(format: androidx.media3.common.Format, fallback: String): String =
        listOfNotNull(
                format.label?.takeIf { it.isNotBlank() },
                format.language?.takeIf { it.isNotBlank() },
            )
            .distinct()
            .joinToString(" · ")
            .ifBlank { fallback }

    private companion object {
        const val SIDECAR_SUBTITLE_ID = "lion-sidecar"
        // Matches the size PlayerVideoSurface configures on the subtitle view.
        const val BASE_SUBTITLE_SP = 18f
    }
}
