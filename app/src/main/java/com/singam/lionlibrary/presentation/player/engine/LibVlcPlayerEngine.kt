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

/**
 * [LionPlayerEngine] implementation backed by libVLC.
 *
 * Key design decisions that solve Android-specific libVLC problems:
 *
 * 1. **SAF `content://` URIs**: LibVLC's native layer cannot open Android SAF
 *    URIs. We pass the raw [java.io.FileDescriptor] directly via JNI.
 *    The [ParcelFileDescriptor] is kept alive for the duration of playback.
 *
 * 2. **Deferred initialization**: LibVLC requires a strict ordering:
 *      Create Engine → Attach View → **Surface Created** → Set Media → Play.
 *    But the ViewModel calls [setMedia]+[play] before the Composable even
 *    renders the AndroidView. So all media operations are buffered until
 *    the [IVLCVout.Callback.onSurfacesCreated] fires, confirming the
 *    Android Surface is ready in the hardware compositor.
 *
 * 3. **Deferred seek**: VLC can only seek on a playing, seekable stream.
 *    If [seekTo] is called before playback starts, the position is buffered
 *    and applied when the first [MediaPlayer.Event.Playing] fires.
 *
 * This engine is created once per player-screen lifecycle (in PlayerViewModel),
 * never recreated inside a Composable.
 */
class LibVlcPlayerEngine(private val context: Context) : LionPlayerEngine {

    override val engineType: EngineType = EngineType.LIBVLC

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    // Step 1: Create LibVLC instance with hardware acceleration options
    // NOTE: Do NOT set --vout=android-display here. VLCVideoLayout manages
    // its own vout module internally. Forcing it causes conflicts.
    private val libVLC: LibVLC = LibVlcProvider.getSharedInstance(context)
    
    // Step 2: Create MediaPlayer once
    private val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)

    // Main-thread handler for deferred operations that require the surface
    private val mainHandler = Handler(Looper.getMainLooper())

    // -----------------------------------------------------------------------
    // Deferred-initialization state
    // -----------------------------------------------------------------------
    private var pendingUri: Uri? = null
    private var pendingSubtitleUri: Uri? = null
    private var pendingSeekMs: Long? = null
    private var pendingPlay: Boolean = false
    private var viewAttached: Boolean = false
    private var surfaceReady: Boolean = false
    private var mediaLoaded: Boolean = false

    // Persistent URI references (survive loadMediaInternal clearing pendingUri).
    // Used to re-load media after mid-playback config changes.
    private var activeUri: Uri? = null
    private var activeSubtitleUri: Uri? = null

    // Track the actual VLCVideoLayout instance so we can detect when
    // the Activity recreates after a config change (rotation) and
    // provides a brand-new layout that needs re-attachment.
    private var currentLayout: VLCVideoLayout? = null

    // -----------------------------------------------------------------------
    // SAF file-descriptor lifecycle (kept alive while playing)
    // -----------------------------------------------------------------------
    private var videoPfd: ParcelFileDescriptor? = null
    private var subtitlePfd: ParcelFileDescriptor? = null

    init {
        // Register surface callback BEFORE attachViews is ever called.
        // This is the critical piece: media must NOT be loaded until
        // onSurfacesCreated fires, confirming the Android Surface exists.
        mediaPlayer.vlcVout.addCallback(object : IVLCVout.Callback {
            override fun onSurfacesCreated(vlcVout: IVLCVout) {
                // Post to main thread — VLC callbacks fire on the VLC event thread
                mainHandler.post {
                    surfaceReady = true
                    // If media was queued before surface was ready, load it now.
                    // Check pendingUri first; fall back to activeUri for config-change re-plays.
                    if (!mediaLoaded) {
                        if (pendingUri == null && activeUri != null) {
                            // Config change mid-playback — re-buffer the active media
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
        })

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
                    // Apply deferred seek now that VLC is playing
                    pendingSeekMs?.let { ms ->
                        // Small delay to let VLC finish decoder init
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

    // ---------------------------------------------------------------------------
    // LionPlayerEngine implementation
    // ---------------------------------------------------------------------------

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

        // Only load if the Surface is confirmed ready by IVLCVout.Callback.
        // Otherwise, onSurfacesCreated will trigger loadMediaInternal().
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
            // Buffer the seek — it will be applied when Playing event fires
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
            if (track.id < 0) return@mapNotNull null // skip "Disable" pseudo-track
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
            if (track.id < 0) return@mapNotNull null // skip "Disable" pseudo-track (-1)
            EngineTrackInfo(
                id = track.id.toString(),
                label = track.name?.takeIf { it.isNotBlank() } ?: "Subtitle ${track.id}",
                isSelected = track.id == selectedId
            )
        }
    }

    override fun resetToDefaultTrackSelection() {
        // No-op for libVLC — it handles unsupported audio tracks gracefully
        // with its own software decoder stack, so this recovery path is
        // unnecessary. Kept as no-op to satisfy the interface contract.
    }

    override fun selectSubtitleTrack(id: String?) {
        // null → disable subtitles (VLC id = -1)
        mediaPlayer.spuTrack = id?.toIntOrNull() ?: -1
    }

    override fun attachToView(container: ViewGroup) {
        val layout = container as? VLCVideoLayout
            ?: throw IllegalArgumentException("LibVlcPlayerEngine requires a VLCVideoLayout")

        // Same instance — nothing to do (recomposition, not a config change)
        if (layout === currentLayout && viewAttached) return

        // Different instance (new layout after config change / Activity recreation).
        // Detach from old layout before attaching to the new one.
        if (viewAttached) {
            surfaceReady = false
            mediaLoaded = false  // Will be re-loaded in onSurfacesCreated via activeUri
            try { mediaPlayer.detachViews() } catch (_: Exception) {}
            viewAttached = false
        }

        currentLayout = layout
        viewAttached = true
        mediaPlayer.attachViews(layout, null, false, false)
        // Do NOT load media here. Wait for onSurfacesCreated callback.
    }

    override fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        try { mediaPlayer.stop() } catch (_: Exception) {}
        try { mediaPlayer.vlcVout.removeCallback(mediaPlayer.vlcVout as? IVLCVout.Callback) } catch (_: Exception) {}
        try {
            if (viewAttached) {
                mediaPlayer.detachViews()
                viewAttached = false
                surfaceReady = false
            }
        } catch (_: Exception) {}
        try { mediaPlayer.release() } catch (_: Exception) {}
        // DO NOT release libVLC here. It is a shared singleton — recreating
        // it would re-trigger fontconfig initialization.
        closePfds()
    }

    // -----------------------------------------------------------------------
    // Helpers used by PlayerViewModel for position polling
    // -----------------------------------------------------------------------

    val currentPositionMs: Long get() {
        return if (mediaLoaded) mediaPlayer.time.coerceAtLeast(0L) else 0L
    }

    val durationMs: Long get() {
        return if (mediaLoaded) mediaPlayer.length.coerceAtLeast(0L) else 0L
    }

    val isPlaying: Boolean get() {
        return if (mediaLoaded) mediaPlayer.isPlaying else false
    }

    fun stop() {
        mediaPlayer.stop()
        mediaLoaded = false
        closePfds()
    }

    // -----------------------------------------------------------------------
    // Internal: deferred media loading
    // -----------------------------------------------------------------------

    /**
     * Actually sets the media on [mediaPlayer]. Called either from [setMedia]
     * (if view is already attached) or from [attachToView] (deferred path).
     *
     * At this point [viewAttached] is guaranteed `true`.
     */
    private fun loadMediaInternal() {
        val uri = pendingUri ?: return

        // Clean up previous file descriptors
        closePfds()

        // --- Build the Media object ---
        val media = openMediaFromUri(uri)
        if (media == null) {
            _state.update { it.copy(error = "Cannot open file", playbackState = EnginePlaybackState.IDLE) }
            return
        }

        mediaPlayer.media = media
        media.release() // MediaPlayer retains its own reference
        mediaLoaded = true

        // Store active URI for possible config-change re-plays
        activeUri = uri
        activeSubtitleUri = pendingSubtitleUri

        // --- Subtitle slave ---
        pendingSubtitleUri?.let { subUri ->
            addSubtitleSlave(subUri)
        }

        // --- Auto-play if requested ---
        if (pendingPlay) {
            mediaPlayer.play()
            pendingPlay = false
        }

        pendingUri = null
        pendingSubtitleUri = null
    }

    /**
     * Opens a [Media] from [uri], handling `content://` SAF URIs by passing
     * the raw [java.io.FileDescriptor] directly to libVLC via JNI.
     *
     * This avoids the `/proc/self/fd/<N>` path approach, which is blocked
     * by SELinux on modern Android devices.
     */
    private fun openMediaFromUri(uri: Uri): Media? {
        return if (uri.scheme == "content") {
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                null
            }
            if (pfd == null) return null
            videoPfd = pfd
            // Pass the FileDescriptor directly — libVLC's nativeNewFromFd()
            // receives the fd via JNI, so no path resolution or SELinux check.
            Media(libVLC, pfd.fileDescriptor)
        } else {
            Media(libVLC, uri)
        }
    }

    /**
     * Adds a subtitle file as a slave to the media player.
     *
     * For `content://` URIs, copies the subtitle to a temp file in the app's
     * cache directory (subtitle files are tiny — a few hundred KB) so libVLC
     * can open it with a regular `file://` URI.
     */
    private fun addSubtitleSlave(subtitleUri: Uri) {
        val uriString: String = if (subtitleUri.scheme == "content") {
            // Determine extension from the original path
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
        // Clean up any temp subtitle file
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.filter { it.name.startsWith("vlc_sub_temp") }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}

