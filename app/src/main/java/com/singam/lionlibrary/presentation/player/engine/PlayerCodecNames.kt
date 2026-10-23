package com.singam.lionlibrary.presentation.player.engine

/** Short, human-readable codec names for the player's track and info panels. */
object PlayerCodecNames {
    private val mimeNames =
        mapOf(
            "video/avc" to "H.264",
            "video/hevc" to "HEVC",
            "video/av01" to "AV1",
            "video/x-vnd.on2.vp8" to "VP8",
            "video/x-vnd.on2.vp9" to "VP9",
            "video/mp4v-es" to "MPEG-4",
            "video/mpeg2" to "MPEG-2",
            "audio/mp4a-latm" to "AAC",
            "audio/ac3" to "AC3",
            "audio/eac3" to "E-AC3",
            "audio/eac3-joc" to "E-AC3 Atmos",
            "audio/ac4" to "AC4",
            "audio/true-hd" to "TrueHD",
            "audio/vnd.dts" to "DTS",
            "audio/vnd.dts.hd" to "DTS-HD",
            "audio/opus" to "Opus",
            "audio/vorbis" to "Vorbis",
            "audio/flac" to "FLAC",
            "audio/mpeg" to "MP3",
            "audio/raw" to "PCM",
            "application/x-subrip" to "SRT",
            "text/x-ssa" to "ASS",
            "text/vtt" to "WebVTT",
            "application/pgs" to "PGS",
            "application/vobsub" to "VobSub",
            "application/dvbsubs" to "DVB",
            "application/x-media3-cues" to "Text",
        )

    // libVLC reports four-character codes, sometimes space padded.
    private val fourccNames =
        mapOf(
            "h264" to "H.264",
            "avc1" to "H.264",
            "hevc" to "HEVC",
            "h265" to "HEVC",
            "av01" to "AV1",
            "vp80" to "VP8",
            "vp90" to "VP9",
            "mp4v" to "MPEG-4",
            "mpgv" to "MPEG-2",
            "mp4a" to "AAC",
            "a52" to "AC3",
            "eac3" to "E-AC3",
            "mlp" to "TrueHD",
            "trhd" to "TrueHD",
            "dts" to "DTS",
            "opus" to "Opus",
            "vorb" to "Vorbis",
            "flac" to "FLAC",
            "mpga" to "MP3",
            "mp3" to "MP3",
            "subt" to "SRT",
            "ssa" to "ASS",
            "webv" to "WebVTT",
            "pgs" to "PGS",
            "pgss" to "PGS",
            "spu" to "VobSub",
            "dvbs" to "DVB",
            "tx3g" to "Text",
        )

    fun fromMime(mime: String?): String? {
        val value = mime?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return mimeNames[value] ?: value.substringAfter('/').removePrefix("x-").uppercase()
    }

    fun fromFourcc(code: String?): String? {
        val value = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return fourccNames[value] ?: value.uppercase()
    }
}
