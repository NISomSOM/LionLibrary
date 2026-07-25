package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.MetadataRetriever
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Check if the device hardware can decode all tracks in a media file.
 */
object CodecCapabilityChecker {

    private const val TAG = "CodecCapabilityChecker"

    /** Timeout for probing each file. MetadataRetriever usually takes 100-500ms. */
    private const val PROBE_TIMEOUT_MS = 5_000L

    private val codecInfos by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
    }

    /**
     * Cache video capability results so we don't query MediaCodecList repeatedly for the same formats.
     */
    private val videoCapabilityCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    // -----------------------------------------------------------------------
    // Blocked Audio MIMEs: Codecs ExoPlayer can't decode without the FFmpeg extension.
    // We check these against Format.sampleMimeType.
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
     * Checks if the device includes a hardware decoder for this video format.
     */
    private fun isVideoHardwareSupported(format: androidx.media3.common.Format): Boolean {
        val mime = format.sampleMimeType ?: return false
        
        // Use MIME, width, and height for the cache key
        val cacheKey = "$mime|${format.width}x${format.height}"
        videoCapabilityCache[cacheKey]?.let { return it }

        val isSupported = try {
            // Verify if any hardware decoder supports the format
            codecInfos.any { info ->
                if (info.isEncoder) return@any false
                
                // Check for hardware acceleration, falling back for pre-API 29
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

    /** Check if all tracks in the media file can be decoded by hardware. */
    suspend fun canHardwareDecode(context: Context, uri: Uri): Boolean {
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                val mediaItem = MediaItem.Builder().setUri(uri).build()
                
                // Parse tracks using MetadataRetriever instead of creating renderers
                val trackGroups = MetadataRetriever.retrieveMetadata(context, mediaItem).await()

                val audioGroups = mutableListOf<TrackGroup>()
                val videoGroups = mutableListOf<TrackGroup>()

                for (i in 0 until trackGroups.length) {
                    val group = trackGroups.get(i)
                    if (group.type == C.TRACK_TYPE_AUDIO) audioGroups.add(group)
                    if (group.type == C.TRACK_TYPE_VIDEO) videoGroups.add(group)
                }

                if (audioGroups.isEmpty() && videoGroups.isEmpty()) {
                    Log.d(TAG, "Probe: NO TRACKS FOUND in $uri ??? fail safe to LIBVLC")
                    return@withTimeoutOrNull false
                }

                var allTracksSupported = true

                // Check video tracks against hardware decoders
                for (group in videoGroups) {
                    for (i in 0 until group.length) {
                        val format = group.getFormat(i)
                        val mime = format.sampleMimeType ?: ""
                        
                        Log.d(TAG, "Probe: video track mime='$mime' ${format.width}x${format.height} in $uri")
                        
                        if (!isVideoHardwareSupported(format)) {
                            Log.d(TAG, "Probe: NO HW DECODER for video mime='$mime' -> will assign LIBVLC")
                            allTracksSupported = false
                        }
                    }
                }

                // Check audio tracks against the blocklist
                for (group in audioGroups) {
                    for (i in 0 until group.length) {
                        val format = group.getFormat(i)
                        val mime = format.sampleMimeType ?: ""
                        val lang = format.language ?: "?"

                        Log.d(TAG, "Probe: audio track mime='$mime' lang='$lang' in $uri")

                        if (mime in UNSUPPORTED_AUDIO_MIMES) {
                            Log.d(TAG, "Probe: BLOCKED audio mime='$mime' -> will assign LIBVLC")
                            allTracksSupported = false
                        }
                    }
                }

                allTracksSupported
            } catch (e: Exception) {
                Log.d(TAG, "Probe error for $uri: ${e.message}")
                false
            }
        } ?: false // Return false on timeout
    }

    /** Await completion of a ListenableFuture. */
    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            { command -> command.run() } // Run on direct executor
        )
    }
}
