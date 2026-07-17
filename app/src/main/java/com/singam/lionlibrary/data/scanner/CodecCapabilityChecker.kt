package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Correctness-first codec probe that determines whether Android's
 * hardware decoders can handle **every** track in a given media file.
 *
 * Uses a headless [ExoPlayer] instance to `prepare()` the media and
 * then inspects all track groups — including every audio track in MKV
 * containers. ExoPlayer's own Matroska/WebM extractor reliably
 * enumerates ALL tracks, unlike Android's [android.media.MediaExtractor]
 * which often misses secondary audio tracks.
 *
 * ## Why ExoPlayer instead of libVLC?
 *
 * libVLC's native demuxer cannot parse `content://` SAF URIs during
 * metadata-only operations (returns `ParsedStatus.Skipped` with 0
 * tracks). ExoPlayer handles SAF URIs natively and its extractors are
 * the same ones used during actual playback, guaranteeing that what
 * we detect at scan time matches what the player will encounter.
 *
 * ## Lifecycle
 *
 * Callers must bracket usage with [initialize] / [shutdown]:
 *
 * ```
 * CodecCapabilityChecker.initialize(context)
 * try {
 *     // ... scan files, calling canHardwareDecode() ...
 * } finally {
 *     CodecCapabilityChecker.shutdown()
 * }
 * ```
 *
 * If detection has **any** doubt — timeout, exception, zero tracks —
 * it fails safe to LIBVLC.
 */
object CodecCapabilityChecker {

    private const val TAG = "CodecCapabilityChecker"

    /** Per-file probe timeout. ExoPlayer prepare is fast (~100-500ms). */
    private const val PROBE_TIMEOUT_MS = 5_000L

    private val codecList by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
    }

    /**
     * Cache for video capability results to avoid repeated MediaCodecList lookups
     * for common MIME types and profiles.
     */
    private val videoCapabilityCache = mutableMapOf<String, Boolean>()

    // -----------------------------------------------------------------------
    // Shared ExoPlayer probe instance — created on the main thread,
    // reused across all files in a scan session.
    // -----------------------------------------------------------------------

    private var probePlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private val probeMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Initialise the shared probe player. Must be called on the **main
     * thread** (ExoPlayer requires main-thread construction).
     *
     * Safe to call multiple times — only the first call allocates.
     */
    fun initialize(context: Context) {
        if (probePlayer != null) return
        appContext = context.applicationContext
        probePlayer = ExoPlayer.Builder(context.applicationContext).build()
        Log.d(TAG, "Shared ExoPlayer probe instance initialized")
    }

    /** Release the shared probe player. Call when the scan session ends. */
    fun shutdown() {
        probePlayer?.release()
        probePlayer = null
        appContext = null
        Log.d(TAG, "Shared ExoPlayer probe instance released")
    }

    // -----------------------------------------------------------------------
    // Audio MIME blocklist — codecs ExoPlayer cannot decode without the
    // FFmpeg extension. Checked against Format.sampleMimeType.
    // -----------------------------------------------------------------------
    private val UNSUPPORTED_AUDIO_MIMES = setOf(
        MimeTypes.AUDIO_AC3,          // "audio/ac3"
        MimeTypes.AUDIO_E_AC3,        // "audio/eac3"
        MimeTypes.AUDIO_E_AC3_JOC,    // "audio/eac3-joc" (Dolby Atmos)
        MimeTypes.AUDIO_DTS,          // "audio/vnd.dts"
        MimeTypes.AUDIO_DTS_HD,       // "audio/vnd.dts.hd"
        MimeTypes.AUDIO_DTS_EXPRESS,  // "audio/vnd.dts.hd;profile=lbr"
        MimeTypes.AUDIO_TRUEHD,       // "audio/true-hd"
        "audio/mlp",                  // "audio/mlp"
    )

    /**
     * Checks if the device has a hardware decoder for the given video format.
     */
    private fun isVideoHardwareSupported(format: androidx.media3.common.Format): Boolean {
        val mime = format.sampleMimeType ?: return false
        
        // Cache key includes MIME, width, and height
        val cacheKey = "$mime|${format.width}x${format.height}"
        videoCapabilityCache[cacheKey]?.let { return it }

        val isSupported = try {
            // Check if ANY hardware decoder supports this format
            codecList.codecInfos.any { info ->
                if (info.isEncoder) return@any false
                
                // Hardware acceleration check with API 29 fallback
                val isHardware = if (android.os.Build.VERSION.SDK_INT >= 29) {
                    info.isHardwareAccelerated
                } else {
                    val name = info.name.lowercase()
                    !(name.startsWith("omx.google.") || 
                      name.startsWith("c2.android.") || 
                      name.startsWith("omx.ffmpeg."))
                }

                if (!isHardware) return@any false
                
                info.supportedTypes.contains(mime) && try {
                    val caps = info.getCapabilitiesForType(mime)
                    val mediaFormat = MediaFormat().apply {
                        setString(MediaFormat.KEY_MIME, mime)
                        setInteger(MediaFormat.KEY_WIDTH, format.width)
                        setInteger(MediaFormat.KEY_HEIGHT, format.height)
                    }
                    caps.isFormatSupported(mediaFormat)
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }

        videoCapabilityCache[cacheKey] = isSupported
        return isSupported
    }

    /**
     * Returns `true` if every audio and video track in the file at [uri] can be
     * decoded by Android's built-in hardware decoders (suitable for ExoPlayer).
     *
     * Returns `false` (→ assign LIBVLC) if:
     * - Any audio track uses a MIME type in [UNSUPPORTED_AUDIO_MIMES]
     * - Any video track has no matching hardware decoder
     * - The file cannot be opened or has no tracks
     * - The probe times out or throws an exception
     * - [initialize] was not called
     * - Any ambiguity at all — fail safe to LIBVLC
     */
    suspend fun canHardwareDecode(context: Context, uri: Uri): Boolean = probeMutex.withLock {
        val player = probePlayer
        if (player == null) {
            Log.d(TAG, "Probe player not initialized → fail safe to LIBVLC")
            return false
        }

        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            // ExoPlayer listener callbacks fire on the main thread.
            // Use suspendCancellableCoroutine to bridge async → suspend.
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : Player.Listener {
                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            player.removeListener(this)
                            
                            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }

                            Log.d(TAG, "Probe: onTracksChanged, groups=${tracks.groups.size} in $uri")

                            if (audioGroups.isEmpty() && videoGroups.isEmpty()) {
                                Log.d(TAG, "Probe: NO TRACKS FOUND in $uri ??? fail safe to LIBVLC")
                                cleanupAndResume(false)
                                return
                            }

                            var allTracksSupported = true

                            // Validate Video Tracks (Hardware Decoder Check)
                            for (group in videoGroups) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val mime = format.sampleMimeType ?: ""
                                    
                                    Log.d(TAG, "Probe: video track mime='$mime' ${format.width}x${format.height} in $uri")
                                    
                                    if (!isVideoHardwareSupported(format)) {
                                        Log.d(TAG, "Probe: NO HW DECODER for video mime='$mime' -> will assign LIBVLC")
                                        allTracksSupported = false
                                    }
                                }
                            }

                            // Validate Audio Tracks (Blocklist Check)
                            for (group in audioGroups) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val mime = format.sampleMimeType ?: ""
                                    val lang = format.language ?: "?"

                                    Log.d(TAG, "Probe: audio track mime='$mime' lang='$lang' in $uri")

                                    if (mime in UNSUPPORTED_AUDIO_MIMES) {
                                        Log.d(TAG, "Probe: BLOCKED audio mime='$mime' -> will assign LIBVLC")
                                        allTracksSupported = false
                                    }
                                }
                            }

                            cleanupAndResume(allTracksSupported)
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            player.removeListener(this)
                            Log.d(TAG, "Probe error for $uri: ${error.message}")
                            cleanupAndResume(false)
                        }

                        private fun cleanupAndResume(result: Boolean) {
                            player.stop()
                            player.clearMediaItems()
                            if (cont.isActive) cont.resume(result)
                        }
                    }

                    // Aggressive reset before starting new prepare
                    player.stop()
                    player.clearMediaItems()
                    
                    player.addListener(listener)
                    player.setMediaItem(MediaItem.fromUri(uri))
                    player.prepare()

                    cont.invokeOnCancellation {
                        player.removeListener(listener)
                        player.stop()
                        player.clearMediaItems()
                    }
                }
            }
        } ?: run {
            Log.d(TAG, "Probe timed out for $uri → fail safe to LIBVLC")
            // Clean up the player on timeout
            withContext(Dispatchers.Main) {
                player.stop()
                player.clearMediaItems()
            }
            false
        }
    }
}
