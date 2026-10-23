package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.inspector.MetadataRetriever
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// Hardware decoding check.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object CodecCapabilityChecker {

    private const val TAG = "CodecCapabilityChecker"

    // 5s timeout for probing.
    private const val PROBE_TIMEOUT_MS = 5_000L

    // Cache video capabilities.
    private val videoCapabilityCache =
        java.util.concurrent.ConcurrentHashMap<VideoCapabilityKey, Boolean>()

    // Blocked Audio MIMEs: ExoPlayer can't decode these without extensions.
    private val UNSUPPORTED_AUDIO_MIMES =
        setOf(
            MimeTypes.AUDIO_AC3, // "audio/ac3"
            MimeTypes.AUDIO_E_AC3, // "audio/eac3"
            MimeTypes.AUDIO_E_AC3_JOC, // "audio/eac3-joc" (Dolby Atmos)
            MimeTypes.AUDIO_DTS, // "audio/vnd.dts"
            MimeTypes.AUDIO_DTS_HD, // "audio/vnd.dts.hd"
            MimeTypes.AUDIO_DTS_EXPRESS, // "audio/vnd.dts.hd;profile=lbr"
            MimeTypes.AUDIO_TRUEHD, // "audio/true-hd"
            "audio/mlp", // "audio/mlp"
        )

    private data class VideoCapabilityKey(
        val mime: String,
        val codecs: String?,
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val colorInfo: androidx.media3.common.ColorInfo?,
    )

    // Share the renderer's codec/profile/HDR and device-workaround checks, not MIME alone.
    internal fun isVideoHardwareSupported(
        context: Context,
        format: androidx.media3.common.Format,
    ): Boolean {
        val mime = format.sampleMimeType ?: return false
        val key =
            VideoCapabilityKey(
                mime,
                format.codecs,
                format.width,
                format.height,
                format.frameRate,
                format.colorInfo,
            )
        return videoCapabilityCache.getOrPut(key) {
            try {
                androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfosSoftMatch(
                        androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT,
                        format,
                        false,
                        false,
                    )
                    .any { it.hardwareAccelerated && it.isFormatSupported(context, format) }
            } catch (_: Exception) {
                false
            }
        }
    }

    // Check if all tracks are HW supported.
    suspend fun canHardwareDecode(context: Context, uri: Uri): Boolean {
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                val mediaItem = MediaItem.Builder().setUri(uri).build()

                // Parse tracks without renderers
                val trackGroups =
                    MetadataRetriever.Builder(context, mediaItem).build().use {
                        it.retrieveTrackGroups().await()
                    }

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

                // Check video tracks
                for (group in videoGroups) {
                    for (i in 0 until group.length) {
                        val format = group.getFormat(i)
                        val mime = format.sampleMimeType ?: ""

                        Log.d(
                            TAG,
                            "Probe: video track mime='$mime' ${format.width}x${format.height} in $uri",
                        )

                        if (!isVideoHardwareSupported(context, format)) {
                            Log.d(
                                TAG,
                                "Probe: NO HW DECODER for video mime='$mime' -> will assign LIBVLC",
                            )
                            allTracksSupported = false
                        }
                    }
                }

                // Check audio tracks
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.d(TAG, "Probe error for $uri: ${e.message}")
                false
            }
        } ?: false
    }

    // Await ListenableFuture.
    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel(true) }
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            { command -> command.run() },
        )
    }
}
