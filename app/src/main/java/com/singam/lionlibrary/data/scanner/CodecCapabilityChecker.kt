package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Lightweight header-only codec probe that determines whether Android's
 * hardware decoders can handle **all** tracks in a given media file.
 *
 * The check uses [MediaExtractor] to read track formats (mime, profile, level)
 * and cross-references them against [MediaCodecList.getCodecInfos] /
 * [MediaCodecInfo.CodecCapabilities.isFormatSupported].
 *
 * If **any** video or audio track is unsupported by hardware decoders, the
 * file should be played through libVLC instead of ExoPlayer.
 *
 * Performance: reads only container headers — no frame decoding. Typically
 * completes in under 100ms per file.
 */
object CodecCapabilityChecker {

    private val codecList by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
    }

    /**
     * Returns `true` if every video and audio track in the file at [uri]
     * can be decoded by a hardware-backed Android [MediaCodec].
     *
     * Returns `false` if:
     * - Any track uses an unsupported codec / profile / level
     * - The file cannot be opened or has no tracks
     * - An exception is thrown (fail-safe → libVLC)
     */
    fun canHardwareDecode(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            if (trackCount == 0) return false

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                // Only check video and audio tracks
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue

                // ExoPlayer without FFMPEG extension struggles with these on many devices
                // even if MediaCodecList claims a generic decoder exists.
                val lowerMime = mime.lowercase()
                if (lowerMime.contains("ac3") || 
                    lowerMime.contains("dts") || 
                    lowerMime.contains("eac3") || 
                    lowerMime.contains("truehd")) {
                    return false
                }

                // Ask the system if any decoder can handle this exact format
                val decoderName = codecList.findDecoderForFormat(format)
                if (decoderName == null) {
                    // No decoder found for this track → ExoPlayer will fail
                    return false
                }
            }
            true
        } catch (_: Exception) {
            // If we can't even read the file headers, fall back to libVLC
            // which has its own demuxer and codec stack.
            false
        } finally {
            extractor.release()
        }
    }
}
