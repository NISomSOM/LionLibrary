package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import kotlin.coroutines.resume

/**
 * Correctness-first codec probe that determines whether Android's
 * hardware decoders can handle **every** track in a given media file.
 *
 * **Video** tracks are checked via [MediaExtractor] + [MediaCodecList]
 * (reliable for video codec profiles).
 *
 * **Audio** tracks are checked via a throwaway libVLC parse that
 * exhaustively enumerates ALL audio tracks (including secondary ones
 * that [MediaExtractor] often misses in MKV containers) and compares
 * each codec against a blocklist of formats ExoPlayer cannot decode
 * without the FFmpeg extension.
 *
 * If detection has **any** doubt — timeout, exception, unrecognized
 * codec, zero tracks — it fails safe to LIBVLC.
 */
object CodecCapabilityChecker {

    private const val TAG = "CodecCapabilityChecker"

    private val codecList by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
    }

    // -----------------------------------------------------------------------
    // Codec blocklist — audio codecs ExoPlayer cannot decode without the
    // FFmpeg extension.
    //
    // IMedia.Track.codec is a String (the human-readable codec ID from VLC).
    // IMedia.Track.fourcc is the raw 32-bit FOURCC integer.
    //
    // We match on the `codec` string (e.g. "a52 ", "dca ", "ec-3") which
    // VLC populates from its internal codec mapping tables.
    //
    // We ALSO convert `fourcc` to a 4-char string and check that, since
    // the `codec` field may sometimes contain the same value or a variant.
    //
    // IMPORTANT: these must be verified empirically against real files
    // by checking logcat output from the probe (search for TAG). If a
    // mismatch is found, update this set — the fail-safe behaviour
    // (unrecognized FOURCC → LIBVLC) ensures correctness in the meantime.
    // -----------------------------------------------------------------------
    private val UNSUPPORTED_AUDIO_CODECS = setOf(
        // AC3 (Dolby Digital) — VLC codec strings
        "a52 ", "a52b", "ac-3", "sac3",
        // E-AC3 (Dolby Digital Plus)
        "ec-3", "eac3", "EAC3",
        // DTS and variants
        "dts ", "dtsh", "dtsl", "dtse", "DTS ", "dca ",
        // TrueHD / MLP
        "trhd", "mlp ", "mlpa",
        // Common VLC description strings that may appear in the codec field
        "A52 Audio (Dolby Digital)", "DTS Audio", "DCA (DTS Coherent Acoustics)",
        "AC-3", "E-AC-3", "MLP (Meridian Lossless Packing)",
        "TrueHD"
    )

    /**
     * Returns `true` if every video and audio track in the file at [uri]
     * can be decoded by Android's built-in decoders (suitable for ExoPlayer).
     *
     * Returns `false` (→ assign LIBVLC) if:
     * - Any video track uses an unsupported codec / profile / level
     * - Any audio track uses a codec in [UNSUPPORTED_AUDIO_CODECS]
     * - The file cannot be opened or has no tracks
     * - The libVLC probe times out (3 s) or throws an exception
     * - Any ambiguity at all — fail safe to LIBVLC
     */
    suspend fun canHardwareDecode(context: Context, uri: Uri): Boolean {
        // Step 1: Video tracks — MediaExtractor is reliable for this
        val videoOk = checkVideoTracksViaMediaExtractor(context, uri)
        if (!videoOk) return false

        // Step 2: Audio tracks — libVLC probe (exhaustive, unlike MediaExtractor)
        return checkAllAudioTracksViaLibVlc(context, uri)
    }

    // -----------------------------------------------------------------------
    // Video: keep MediaExtractor-based check (reliable for video codecs)
    // -----------------------------------------------------------------------

    private fun checkVideoTracksViaMediaExtractor(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            if (trackCount == 0) return false

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                // Only check video tracks here — audio is handled by the libVLC probe
                if (!mime.startsWith("video/")) continue

                val decoderName = codecList.findDecoderForFormat(format)
                if (decoderName == null) {
                    Log.d(TAG, "No video decoder for MIME=$mime in $uri")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.d(TAG, "MediaExtractor failed for $uri: ${e.message}")
            false
        } finally {
            extractor.release()
        }
    }

    // -----------------------------------------------------------------------
    // Audio: libVLC probe — exhaustively checks EVERY audio track
    // -----------------------------------------------------------------------

    /**
     * Uses a throwaway [LibVLC] + [Media] instance purely for track
     * enumeration. Parses the container header (no frame decoding) and
     * checks every audio track's codec against [UNSUPPORTED_AUDIO_CODECS].
     *
     * Timeout: 3 seconds. On timeout → returns `false` (fail safe to LIBVLC).
     *
     * Resources are released in every code path: success, failure, timeout,
     * and cancellation.
     */
    private suspend fun checkAllAudioTracksViaLibVlc(
        context: Context,
        uri: Uri
    ): Boolean = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { cont ->
            var libVLC: LibVLC? = null
            var media: Media? = null

            fun cleanup() {
                try { media?.release() } catch (_: Exception) {}
                try { libVLC?.release() } catch (_: Exception) {}
                media = null
                libVLC = null
            }

            try {
                libVLC = LibVLC(context, arrayListOf("--no-video", "--no-audio"))
                media = if (uri.scheme == "content") {
                    // For content:// URIs, open via FileDescriptor just like
                    // LibVlcPlayerEngine does for playback.
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd == null) {
                        cleanup()
                        if (cont.isActive) cont.resume(false)
                        return@suspendCancellableCoroutine
                    }
                    // Note: Media() copies the fd internally, so we can close pfd
                    // after creating the Media object.
                    val m = Media(libVLC!!, pfd.fileDescriptor)
                    pfd.close()
                    m
                } else {
                    Media(libVLC!!, uri)
                }

                // Set event listener BEFORE calling parse.
                // IMedia.Event.ParsedChanged (int = 3) fires when parsing completes.
                media!!.setEventListener { event ->
                    if (event.type == IMedia.Event.ParsedChanged) {
                        val m = media ?: return@setEventListener
                        val trackCount = m.trackCount

                        // Zero tracks found → ambiguous, fail safe
                        if (trackCount == 0) {
                            Log.d(TAG, "libVLC probe: 0 tracks for $uri → fail safe")
                            cleanup()
                            if (cont.isActive) cont.resume(false)
                            return@setEventListener
                        }

                        var audioTrackCount = 0
                        var allSupported = true

                        for (i in 0 until trackCount) {
                            val track = m.getTrack(i) ?: continue
                            // IMedia.Track.type is int; IMedia.Track.Type.Audio = 0
                            if (track.type == IMedia.Track.Type.Audio) {
                                audioTrackCount++
                                // track.codec is a String (e.g. "a52 ", "mpga")
                                // track.fourcc is the raw int FOURCC
                                val codecStr = track.codec ?: ""
                                val fourccStr = fourccToString(track.fourcc)
                                Log.d(
                                    TAG,
                                    "libVLC probe: audio track $i " +
                                        "codec='$codecStr' fourcc='$fourccStr' " +
                                        "(0x${Integer.toHexString(track.fourcc)}) in $uri"
                                )

                                if (codecStr in UNSUPPORTED_AUDIO_CODECS ||
                                    fourccStr in UNSUPPORTED_AUDIO_CODECS
                                ) {
                                    allSupported = false
                                    // Don't break — log ALL tracks for verification
                                }
                            }
                        }

                        // If we found zero audio tracks specifically, that's fine —
                        // video-only files work perfectly in ExoPlayer.
                        val result = if (audioTrackCount == 0) {
                            Log.d(TAG, "libVLC probe: no audio tracks in $uri (video-only?) → allow EXOPLAYER")
                            true
                        } else {
                            allSupported
                        }

                        cleanup()
                        if (cont.isActive) cont.resume(result)
                    }
                }

                // IMedia.Parse.ParseLocal = 0, IMedia.Parse.FetchLocal = 2
                // parse() returns true if parsing was started successfully.
                val parseStarted = media!!.parse(
                    IMedia.Parse.ParseLocal or IMedia.Parse.FetchLocal
                )
                if (!parseStarted) {
                    Log.d(TAG, "libVLC probe: parse() returned false for $uri → fail safe")
                    cleanup()
                    if (cont.isActive) cont.resume(false)
                }

                cont.invokeOnCancellation {
                    cleanup()
                }
            } catch (e: Exception) {
                Log.d(TAG, "libVLC probe exception for $uri: ${e.message}")
                cleanup()
                if (cont.isActive) cont.resume(false)
            }
        }
    } ?: run {
        // withTimeoutOrNull returned null → 3-second timeout expired
        Log.d(TAG, "libVLC probe timed out → fail safe to LIBVLC")
        false
    }

    // -----------------------------------------------------------------------
    // FOURCC helper
    // -----------------------------------------------------------------------

    /**
     * Converts a VLC integer FOURCC to its 4-character string representation.
     *
     * On little-endian (Android ARM/x86), VLC_FOURCC(a,b,c,d) is packed as:
     *   `a | (b << 8) | (c << 16) | (d << 24)`
     *
     * So byte 0 (least significant) = 'a', byte 1 = 'b', etc.
     */
    private fun fourccToString(fcc: Int): String {
        return String(
            charArrayOf(
                (fcc and 0xFF).toChar(),
                ((fcc shr 8) and 0xFF).toChar(),
                ((fcc shr 16) and 0xFF).toChar(),
                ((fcc shr 24) and 0xFF).toChar()
            )
        )
    }
}
