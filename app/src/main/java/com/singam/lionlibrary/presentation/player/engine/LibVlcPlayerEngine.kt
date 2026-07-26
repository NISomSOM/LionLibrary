package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.VLCVideoLayout

// LibVLC implementation.
class LibVlcPlayerEngine(private val context: Context) : LionPlayerEngine {

    override val engineType: EngineType = EngineType.LIBVLC

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    // 1. LibVLC instance.
    // VLCVideoLayout manages vout internally.
    private val libVLC: LibVLC = LibVlcProvider.getSharedInstance(context)
    
    // 2. MediaPlayer.
    private val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)

    // Main-thread handler.
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingUri: Uri? = null
    private var pendingSubtitleUri: Uri? = null
    private var pendingSeekMs: Long? = null
    private var pendingPlay: Boolean = false
    private var viewAttached: Boolean = false
    private var surfaceReady: Boolean = false
    private var mediaLoaded: Boolean = false

    // Active URI references.
    private var activeUri: Uri? = null
    private var activeSubtitleUri: Uri? = null

    // Track VLCVideoLayout to handle config changes.
    private var currentLayout: VLCVideoLayout? = null

    // SAF file descriptors.
    private var videoPfd: ParcelFileDescriptor? = null
    private var subtitlePfd: ParcelFileDescriptor? = null

    // Surface callback.
    private val surfaceCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout) {
            mainHandler.post {
                surfaceReady = true
                // Load queued media.
                if (!mediaLoaded) {
                    if (pendingUri == null && activeUri != null) {
                        pendingUri = activeUri
                        pendingSubtitleUri = activeSubtitleUri
                        pendingPlay = true
                    }
                    if (pendingUri != null) {
                        loadMediaInternal()
                    }
                }
            }
        }
        override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
            mainHandler.post {
                surfaceReady = false
            }
        }
    }

    init {
        // Register callback early.
        mediaPlayer.vlcVout.addCallback(surfaceCallback)

        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    _state.update {
                        it.copy(
                            isPlaying = true,
                            playbackState = EnginePlaybackState.READY,
                            error = null
                        )
                    }
                    // Apply deferred seek.
                    pendingSeekMs?.let { ms ->
                        mainHandler.postDelayed({
                            if (mediaPlayer.isSeekable) {
                                mediaPlayer.time = ms
                            }
                            pendingSeekMs = null
                        }, 300)
                    }
                }
                MediaPlayer.Event.Paused -> {
                    _state.update { it.copy(isPlaying = false) }
                }
                MediaPlayer.Event.Stopped -> {
                    _state.update {
                        it.copy(isPlaying = false, playbackState = EnginePlaybackState.IDLE)
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    _state.update {
                        it.copy(isPlaying = false, playbackState = EnginePlaybackState.ENDED)
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            playbackState = EnginePlaybackState.IDLE,
                            error = "libVLC playback error"
                        )
                    }
                }
                MediaPlayer.Event.Opening -> {
                    _state.update { it.copy(playbackState = EnginePlaybackState.BUFFERING) }
                }
                MediaPlayer.Event.Buffering -> {
                    if (event.buffering < 100f) {
                        _state.update { it.copy(playbackState = EnginePlaybackState.BUFFERING) }
                    }
                }
                MediaPlayer.Event.TimeChanged -> {
                    val posMs = event.timeChanged
                    val durMs = mediaPlayer.length
                    _state.update {
                        it.copy(
                            currentPositionMs = posMs,
                            durationMs = if (durMs > 0) durMs else it.durationMs
                        )
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    val durMs = event.lengthChanged
                    if (durMs > 0) {
                        _state.update { it.copy(durationMs = durMs) }
                    }
                }
                else -> {}
            }
        }
    }

    override fun setMedia(uri: Uri, subtitleUri: Uri?) {
        pendingUri = uri
        pendingSubtitleUri = subtitleUri
        mediaLoaded = false

        _state.update {
            it.copy(
                playbackState = EnginePlaybackState.BUFFERING,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                error = null
            )
        }

        // Load if surface ready.
        if (surfaceReady) {
            loadMediaInternal()
        }
    }

    override fun play() {
        if (!mediaLoaded) {
            pendingPlay = true
            return
        }
        mediaPlayer.play()
    }

    override fun pause() {
        if (mediaLoaded) {
            mediaPlayer.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        if (!mediaLoaded || !mediaPlayer.isPlaying) {
            // Buffer seek.
            pendingSeekMs = positionMs
            return
        }
        if (mediaPlayer.isSeekable) {
            mediaPlayer.time = positionMs
        } else {
            pendingSeekMs = positionMs
        }
    }

    override fun getAudioTracks(): List<EngineTrackInfo> {
        if (!mediaLoaded) return emptyList()
        val tracks = mediaPlayer.audioTracks ?: return emptyList()
        val selectedId = mediaPlayer.audioTrack
        return tracks.mapNotNull { track ->
            if (track.id < 0) return@mapNotNull null
            EngineTrackInfo(
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: "Audio ${track.id}",
                isSelected = track.id == selectedId
            )
        }
    }

    override fun selectAudioTrack(id: String) {
        mediaPlayer.audioTrack = id.toIntOrNull() ?: return
    }

    override fun getSubtitleTracks(): List<EngineTrackInfo> {
        if (!mediaLoaded) return emptyList()
        val tracks = mediaPlayer.spuTracks ?: return emptyList()
        val selectedId = mediaPlayer.spuTrack
        return tracks.mapNotNull { track ->
            if (track.id < 0) return@mapNotNull null
            EngineTrackInfo(
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: "Subtitle ${track.id}",
                isSelected = track.id == selectedId
            )
        }
    }

    override fun resetToDefaultTrackSelection() {
    }

    override fun selectSubtitleTrack(id: String?) {
        // null disables subs.
        mediaPlayer.spuTrack = id?.toIntOrNull() ?: -1
    }

    override fun attachToView(container: ViewGroup) {
        val layout = container as? VLCVideoLayout
            ?: throw IllegalArgumentException("LibVlcPlayerEngine requires a VLCVideoLayout")

        if (layout === currentLayout && viewAttached) return

        // New layout after config change.
        if (viewAttached) {
            surfaceReady = false
            mediaLoaded = false
            try { mediaPlayer.detachViews() } catch (_: Exception) {}
            viewAttached = false
        }

        currentLayout = layout
        viewAttached = true
        mediaPlayer.attachViews(layout, null, false, false)
        // Wait for surface.
    }

    override fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        try { mediaPlayer.stop() } catch (_: Exception) {}
        try { mediaPlayer.vlcVout.removeCallback(surfaceCallback) } catch (_: Exception) {}
        try {
            if (viewAttached) {
                mediaPlayer.detachViews()
                viewAttached = false
                surfaceReady = false
            }
        } catch (_: Exception) {}
        try { mediaPlayer.release() } catch (_: Exception) {}
        // Shared libVLC; don't release.
        closePfds()
    }

    override val currentPositionMs: Long get() {
        return if (mediaLoaded) mediaPlayer.time.coerceAtLeast(0L) else 0L
    }

    override val durationMs: Long get() {
        return if (mediaLoaded) mediaPlayer.length.coerceAtLeast(0L) else 0L
    }

    override fun stop() {
        mediaPlayer.stop()
        mediaLoaded = false
        closePfds()
    }

    // Load media.
    private fun loadMediaInternal() {
        val uri = pendingUri ?: return

        closePfds()

        // Media object.
        val media = openMediaFromUri(uri)
        if (media == null) {
            _state.update { it.copy(error = "Cannot open file", playbackState = EnginePlaybackState.IDLE) }
            return
        }

        mediaPlayer.media = media
        media.release()
        mediaLoaded = true

        activeUri = uri
        activeSubtitleUri = pendingSubtitleUri

        // Subtitle slave.
        pendingSubtitleUri?.let { subUri ->
            addSubtitleSlave(subUri)
        }

        // Auto-play.
        if (pendingPlay) {
            mediaPlayer.play()
            pendingPlay = false
        }

        pendingUri = null
        pendingSubtitleUri = null
    }

    // Open media.
    private fun openMediaFromUri(uri: Uri): Media? {
        return if (uri.scheme == "content") {
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                null
            }
            if (pfd == null) return null
            videoPfd = pfd
            // Pass FD directly.
            Media(libVLC, pfd.fileDescriptor)
        } else {
            Media(libVLC, uri)
        }
    }

    // Attach subtitles.
    private fun addSubtitleSlave(subtitleUri: Uri) {
        val uriString: String = if (subtitleUri.scheme == "content") {
            val ext = subtitleUri.path
                ?.substringAfterLast('.', "srt")
                ?.takeIf { it.length <= 4 }
                ?: "srt"
            val tempFile = java.io.File(context.cacheDir, "vlc_sub_temp.$ext")
            try {
                context.contentResolver.openInputStream(subtitleUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                "file://${tempFile.absolutePath}"
            } catch (e: Exception) {
                return
            }
        } else {
            subtitleUri.toString()
        }
        mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, uriString, true)
    }

    private fun closePfds() {
        videoPfd?.let { try { it.close() } catch (_: Exception) {} }
        videoPfd = null
        subtitlePfd?.let { try { it.close() } catch (_: Exception) {} }
        subtitlePfd = null
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.filter { it.name.startsWith("vlc_sub_temp") }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
