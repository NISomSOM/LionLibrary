package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import com.singam.lionlibrary.domain.model.PlaybackChapter
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.VLCVideoLayout

/** Native/view operations are main-thread owned; SAF preparation runs on IO. */
class LibVlcPlayerEngine(private val context: Context) : LionPlayerEngine {
    override val engineType = EngineType.LIBVLC
    private val mutableState = MutableStateFlow(EngineState())
    override val state = mutableState.asStateFlow()
    private val libVLC = LibVlcProvider.getSharedInstance(context)
    private val mediaPlayer = MediaPlayer(libVLC)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val commands = PendingPlayback()
    private var surfaceReady = false
    private var mediaLoaded = false
    private var chaptersRead = false
    // While hidden the native track is off; this keeps the chosen track (-1: none) to restore.
    private var subtitlesHidden = false
    private var hiddenSpuTrack = -1
    private var released = false
    private var currentLayout: VLCVideoLayout? = null
    private var pendingMedia: PreparedMedia? = null
    private var activeMedia: PreparedMedia? = null
    private var mediaGeneration = 0L
    private var playbackSpeed = 1f
    private var aspectMode = AspectMode.FIT
    private var subtitleDelayMs = 0L

    private val surfaceCallback =
        object : IVLCVout.Callback {
            override fun onSurfacesCreated(vlcVout: IVLCVout) {
                mainHandler.post {
                    if (!released) {
                        surfaceReady = true
                        loadPreparedMedia()
                    }
                }
            }

            override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
                mainHandler.post { surfaceReady = false }
            }
        }

    init {
        mediaPlayer.vlcVout.addCallback(surfaceCallback)
    }

    private fun listenForEvents() {
        val generation = mediaGeneration
        // libVLC dispatches Java events on the main looper. Clear listener before release.
        mediaPlayer.setEventListener { event ->
            if (!released && mediaLoaded && generation == mediaGeneration) {
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        if (!commands.playRequested && mediaPlayer.isPlaying) mediaPlayer.pause()
                        mutableState.update {
                            it.copy(
                                isPlaying = commands.playRequested,
                                playbackState = EnginePlaybackState.READY,
                                error = null,
                            )
                        }
                        applyPendingSeek()
                        applyPlaybackOptions()
                        if (mediaPlayer.isSeekable) readChapters()
                    }
                    MediaPlayer.Event.SeekableChanged ->
                        if (event.seekable) {
                            applyPendingSeek()
                            readChapters()
                        }
                    MediaPlayer.Event.Paused -> mutableState.update { it.copy(isPlaying = false) }
                    MediaPlayer.Event.Stopped ->
                        mutableState.update {
                            it.copy(isPlaying = false, playbackState = EnginePlaybackState.IDLE)
                        }
                    MediaPlayer.Event.EndReached -> {
                        commands.pause()
                        mutableState.update {
                            it.copy(isPlaying = false, playbackState = EnginePlaybackState.ENDED)
                        }
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        commands.pause()
                        mutableState.update {
                            it.copy(
                                isPlaying = false,
                                playbackState = EnginePlaybackState.IDLE,
                                error = PlaybackFailure.UNKNOWN,
                            )
                        }
                    }
                    MediaPlayer.Event.Opening ->
                        mutableState.update {
                            it.copy(playbackState = EnginePlaybackState.BUFFERING)
                        }
                    MediaPlayer.Event.Buffering ->
                        mutableState.update {
                            it.copy(
                                playbackState =
                                    if (event.buffering < 100f) EnginePlaybackState.BUFFERING
                                    else EnginePlaybackState.READY
                            )
                        }
                    MediaPlayer.Event.TimeChanged ->
                        mutableState.update {
                            it.copy(
                                currentPositionMs = event.timeChanged.coerceAtLeast(0),
                                durationMs =
                                    mediaPlayer.length.takeIf { ms -> ms > 0 } ?: it.durationMs,
                            )
                        }
                    MediaPlayer.Event.LengthChanged ->
                        if (event.lengthChanged > 0)
                            mutableState.update {
                                it.copy(durationMs = event.lengthChanged)
                            }
                    MediaPlayer.Event.ESAdded,
                    MediaPlayer.Event.ESDeleted -> {
                        keepSubtitlesHidden()
                        mutableState.update {
                            it.copy(trackRevision = it.trackRevision + 1)
                        }
                    }
                    MediaPlayer.Event.ESSelected -> keepSubtitlesHidden()
                }
            }
        }
    }

    override suspend fun setMedia(uri: Uri, subtitleUri: Uri?) {
        check(!released)
        stop()
        val generation = mediaGeneration
        mutableState.update { EngineState(playbackState = EnginePlaybackState.BUFFERING) }
        var prepared: PreparedMedia? = null
        try {
            // Assign inside IO so cancellation on dispatcher return cannot lose owned resources.
            withContext(Dispatchers.IO) { prepared = prepareMedia(uri, subtitleUri) }
            currentCoroutineContext().ensureActive()
            if (released || generation != mediaGeneration) return
            pendingMedia = prepared
            prepared = null
            loadPreparedMedia()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!released && generation == mediaGeneration)
                mutableState.update {
                    it.copy(
                        error = PlaybackFailure.FILE_ACCESS,
                        playbackState = EnginePlaybackState.IDLE,
                    )
                }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { prepared?.close() }
        }
    }

    override fun play() {
        if (released) return
        commands.play()
        if (mediaLoaded && surfaceReady) mediaPlayer.play()
    }

    override fun pause() {
        if (released) return
        val wasRequested = commands.playRequested
        commands.pause()
        if (mediaLoaded && wasRequested && mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        commands.seek(positionMs)
        applyPendingSeek()
    }

    private fun applyPendingSeek() {
        if (!mediaLoaded) return
        commands.positionWhenSeekable(mediaPlayer.isSeekable)?.let { mediaPlayer.time = it }
    }

    override fun getAudioTracks(): List<EngineTrackInfo> {
        if (!mediaLoaded) return emptyList()
        val tracks = mediaPlayer.audioTracks ?: return emptyList()
        val selectedId = mediaPlayer.audioTrack
        val details = mediaTracks()
        return tracks.mapNotNull { track ->
            if (track.id < 0) return@mapNotNull null
            val detail = details[track.id] as? IMedia.AudioTrack
            EngineTrackInfo(
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: "Audio ${track.id}",
                isSelected = track.id == selectedId,
                language = detail?.language?.takeIf { it.isNotBlank() && it != "und" },
                codec = PlayerCodecNames.fromFourcc(detail?.codec),
                channels = detail?.channels?.takeIf { it > 0 },
                sampleRateHz = detail?.rate?.takeIf { it > 0 },
            )
        }
    }

    override fun selectAudioTrack(id: String) {
        mediaPlayer.audioTrack = id.toIntOrNull() ?: return
    }

    override fun getSubtitleTracks(): List<EngineTrackInfo> {
        if (!mediaLoaded) return emptyList()
        val tracks = mediaPlayer.spuTracks ?: return emptyList()
        val selectedId = if (subtitlesHidden) hiddenSpuTrack else mediaPlayer.spuTrack
        val details = mediaTracks()
        val hasSidecar = activeMedia?.subtitleUri != null
        return tracks.mapNotNull { track ->
            if (track.id < 0) return@mapNotNull null
            val detail = details[track.id]
            EngineTrackInfo(
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: "Subtitle ${track.id}",
                isSelected = track.id == selectedId,
                language = detail?.language?.takeIf { it.isNotBlank() && it != "und" },
                codec = PlayerCodecNames.fromFourcc(detail?.codec),
                // Slave inputs are not listed among the container's own elementary streams.
                isExternal = hasSidecar && detail == null,
            )
        }
    }

    override fun resetToDefaultTrackSelection() {}

    override fun selectSubtitleTrack(id: String?) {
        // null disables subs.
        val track = id?.toIntOrNull() ?: -1
        if (subtitlesHidden) hiddenSpuTrack = track else mediaPlayer.spuTrack = track
    }

    override fun setSubtitlesHidden(hidden: Boolean) {
        if (released || hidden == subtitlesHidden) return
        if (!mediaLoaded) {
            subtitlesHidden = false
            return
        }
        subtitlesHidden = hidden
        if (hidden) {
            hiddenSpuTrack = mediaPlayer.spuTrack
            if (hiddenSpuTrack != -1) mediaPlayer.spuTrack = -1
        } else if (hiddenSpuTrack != -1) {
            mediaPlayer.spuTrack = hiddenSpuTrack
        }
    }

    /**
     * libVLC may select a subtitle track by itself (input start, a new stream) while hidden; that
     * track becomes the one to restore and stays off until then.
     */
    private fun keepSubtitlesHidden() {
        if (!subtitlesHidden) return
        val selected = mediaPlayer.spuTrack
        if (selected == -1) return
        hiddenSpuTrack = selected
        mediaPlayer.spuTrack = -1
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        if (!released && mediaLoaded) mediaPlayer.rate = speed
    }

    override fun setAspectMode(mode: AspectMode) {
        aspectMode = mode
        if (!released) mediaPlayer.videoScale = mode.toScaleType()
    }

    override val supportsSubtitleDelay = true

    override fun setSubtitleDelay(delayMs: Long) {
        subtitleDelayMs = delayMs
        if (!released && mediaLoaded) mediaPlayer.spuDelay = delayMs * 1_000
    }

    override fun getVideoInfo(): EngineVideoInfo? {
        val video = mediaTracks().values.filterIsInstance<IMedia.VideoTrack>().firstOrNull()
        return video?.let {
            EngineVideoInfo(
                codec = PlayerCodecNames.fromFourcc(it.codec),
                width = it.width.coerceAtLeast(0),
                height = it.height.coerceAtLeast(0),
                frameRate =
                    if (it.frameRateDen > 0 && it.frameRateNum > 0)
                        it.frameRateNum.toFloat() / it.frameRateDen
                    else null,
                bitrate = it.bitrate.takeIf { rate -> rate > 0 },
            )
        }
    }

    /**
     * Chapters of the current title, read once per file after native seekability is known (earlier
     * reads can be empty). Uses this session's own player only; no second Media or player is
     * created.
     */
    private fun readChapters() {
        if (released || !mediaLoaded || chaptersRead) return
        chaptersRead = true
        val chapters =
            mediaPlayer
                .getChapters(-1)
                .orEmpty()
                .filter { it.timeOffset >= 0 }
                .map { chapter ->
                    PlaybackChapter(
                        startMs = chapter.timeOffset,
                        endMs =
                            (chapter.timeOffset + chapter.duration).takeIf {
                                chapter.duration > 0
                            },
                        title = chapter.name,
                    )
                }
                .distinct()
                .sortedBy { it.startMs }
        mutableState.update { it.copy(chapters = chapters, isSeekable = true) }
    }

    /** Elementary streams of the loaded media keyed by ES id, which matches track ids. */
    private fun mediaTracks(): Map<Int, IMedia.Track> {
        if (released || !mediaLoaded) return emptyMap()
        val media = mediaPlayer.media ?: return emptyMap()
        return try {
            (0 until media.trackCount).mapNotNull { media.getTrack(it) }.associateBy { it.id }
        } finally {
            media.release()
        }
    }

    // Rate and subtitle delay reset with each new input, so they are reapplied on Playing.
    private fun applyPlaybackOptions() {
        mediaPlayer.rate = playbackSpeed
        mediaPlayer.spuDelay = subtitleDelayMs * 1_000
    }

    private fun AspectMode.toScaleType() =
        when (this) {
            AspectMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            AspectMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
        }

    override fun attachToView(container: ViewGroup) {
        if (released) return
        val layout =
            container as? VLCVideoLayout
                ?: throw IllegalArgumentException("LibVlcPlayerEngine requires a VLCVideoLayout")
        if (layout === currentLayout) return
        if (currentLayout != null) mediaPlayer.detachViews()
        surfaceReady = false
        currentLayout = layout
        // SurfaceView supports direct hardware output. Reattaching never resets position/autoplays.
        mediaPlayer.attachViews(layout, null, true, false)
        mediaPlayer.videoScale = aspectMode.toScaleType()
    }

    override fun release() {
        if (released) return
        released = true
        mediaGeneration++
        commands.reset()
        mediaPlayer.setEventListener(null)
        mainHandler.removeCallbacksAndMessages(null)
        mediaPlayer.vlcVout.removeCallback(surfaceCallback)
        mediaPlayer.stop()
        if (currentLayout != null) mediaPlayer.detachViews()
        mediaPlayer.release()
        mediaLoaded = false
        surfaceReady = false
        currentLayout = null
        closeOwnedMedia()
        // Application-wide LibVLC remains alive.
    }

    override val currentPositionMs: Long
        get() = if (!released && mediaLoaded) mediaPlayer.time.coerceAtLeast(0) else 0

    override val durationMs: Long
        get() = if (!released && mediaLoaded) mediaPlayer.length.coerceAtLeast(0) else 0

    override fun stop() {
        if (released) return
        mediaGeneration++
        commands.reset()
        mediaLoaded = false
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        mediaPlayer.media = null
        closeOwnedMedia()
    }

    private fun loadPreparedMedia() {
        if (released || !surfaceReady) return
        val prepared =
            pendingMedia
                ?: run {
                    if (mediaLoaded && commands.playRequested) mediaPlayer.play()
                    return
                }
        val media = checkNotNull(prepared.media)
        mediaPlayer.media = media
        media.release()
        prepared.media = null
        pendingMedia = null
        activeMedia = prepared
        mediaLoaded = true
        chaptersRead = false
        subtitlesHidden = false
        hiddenSpuTrack = -1
        listenForEvents()
        if (commands.playRequested) mediaPlayer.play()
    }

    private fun prepareMedia(uri: Uri, subtitleUri: Uri?): PreparedMedia {
        val result = PreparedMedia()
        try {
            result.media =
                if (uri.scheme == "content") {
                    val descriptor =
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw java.io.FileNotFoundException(
                                "Video provider returned no descriptor"
                            )
                    result.descriptor = descriptor
                    Media(libVLC, descriptor.fileDescriptor)
                } else Media(libVLC, uri)
            // Prefer hardware on supported devices, retaining VLC's software fallback.
            result.media?.setHWDecoderEnabled(true, false)
            result.subtitleUri = subtitleUri?.let { subtitle ->
                if (subtitle.scheme != "content") subtitle.toString()
                else {
                    val ext =
                        subtitle.lastPathSegment
                            ?.substringAfterLast('.', "srt")
                            ?.lowercase()
                            ?.takeIf { it in setOf("srt", "ass", "ssa", "vtt", "sub") } ?: "srt"
                    val file = File.createTempFile("vlc_sub_", ".$ext", context.cacheDir)
                    result.subtitleFile = file
                    try {
                        val input =
                            context.contentResolver.openInputStream(subtitle)
                                ?: throw java.io.FileNotFoundException(
                                    "Subtitle provider returned no stream"
                                )
                        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
                        Uri.fromFile(file).toString()
                    } catch (_: Exception) {
                        file.delete()
                        result.subtitleFile = null
                        null // Subtitle failure must not prevent watching the video.
                    }
                }
            }
            result.subtitleUri?.let {
                // Register before input exists. MediaPlayer's String overload expects a
                // filesystem path and would corrupt a file:// URI.
                result.media?.addSlave(IMedia.Slave(IMedia.Slave.Type.Subtitle, 4, it))
            }
            return result
        } catch (error: Exception) {
            result.close()
            throw error
        }
    }

    private fun closeOwnedMedia() {
        pendingMedia?.close()
        activeMedia?.close()
        pendingMedia = null
        activeMedia = null
    }

    /** Snapshot used by instrumentation/profiling; never polled per-frame by the app. */
    internal fun videoStats(): IMedia.Stats? {
        if (released || !mediaLoaded) return null
        val media = mediaPlayer.media ?: return null
        return try {
            media.stats
        } finally {
            media.release()
        }
    }

    private class PreparedMedia : AutoCloseable {
        var media: Media? = null
        var descriptor: ParcelFileDescriptor? = null
        var subtitleFile: File? = null
        var subtitleUri: String? = null

        override fun close() {
            media?.release()
            media = null
            try {
                descriptor?.close()
            } finally {
                descriptor = null
                subtitleFile?.delete()
                subtitleFile = null
            }
        }
    }
}
