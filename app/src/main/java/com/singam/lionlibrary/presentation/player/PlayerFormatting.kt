package com.singam.lionlibrary.presentation.player

import com.singam.lionlibrary.presentation.player.engine.EngineTrackInfo
import java.util.Locale
import kotlin.math.roundToInt

/** Subtitle languages with how many tracks each has, in first-seen order. */
internal data class SubtitleLanguageGroup(val language: String, val count: Int)

/**
 * Word-bearing unit templates. Defaults are the English strings; the UI builds them from string
 * resources with [rememberPlayerFormatLabels] so they can be translated.
 */
internal data class PlayerFormatLabels(
    val mono: String = "Mono",
    val stereo: String = "Stereo",
    /** %1$d = channel count. */
    val channels: String = "%1\$d ch",
    /** %1$s = number. */
    val kilohertz: String = "%1\$s kHz",
    val kilobits: String = "%1\$s kbps",
    val megabits: String = "%1\$s Mbps",
    val framesPerSecond: String = "%1\$s fps",
    val speed: String = "%1\$sx",
    val seconds: String = "%1\$s s",
)

/** Pure formatting for the player panels; no Android or engine access. */
internal object PlayerFormatting {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** Key used for a track's language: lowercase code, or "" when it has none. */
    fun languageKey(track: EngineTrackInfo): String = track.language?.trim()?.lowercase().orEmpty()

    fun subtitleLanguages(tracks: List<EngineTrackInfo>): List<SubtitleLanguageGroup> =
        tracks.groupingBy(::languageKey).eachCount().map { (language, count) ->
            SubtitleLanguageGroup(language, count)
        }

    fun filterSubtitles(tracks: List<EngineTrackInfo>, language: String?): List<EngineTrackInfo> =
        if (language == null) tracks else tracks.filter { languageKey(it) == language }

    /** Display name for an ISO 639 code, or null when the code is blank. */
    fun languageName(code: String?, locale: Locale = Locale.getDefault()): String? {
        val value = code?.trim()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
        val name = Locale.forLanguageTag(value).getDisplayLanguage(locale)
        return name
            .takeIf { it.isNotBlank() && !it.equals(value, ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(locale) } ?: value.uppercase(locale)
    }

    fun channels(count: Int?, labels: PlayerFormatLabels = PlayerFormatLabels()): String? =
        when (count) {
            null -> null
            1 -> labels.mono
            2 -> labels.stereo
            6 -> "5.1"
            8 -> "7.1"
            else -> if (count > 0) labels.channels.format(Locale.US, count) else null
        }

    fun sampleRate(hz: Int?, labels: PlayerFormatLabels = PlayerFormatLabels()): String? {
        if (hz == null || hz <= 0) return null
        val khz = hz / 1000f
        val number =
            if (khz % 1f == 0f) khz.toInt().toString() else String.format(Locale.US, "%.1f", khz)
        return labels.kilohertz.format(Locale.US, number)
    }

    fun bitrate(bitsPerSecond: Int?, labels: PlayerFormatLabels = PlayerFormatLabels()): String? {
        if (bitsPerSecond == null || bitsPerSecond <= 0) return null
        return if (bitsPerSecond >= 1_000_000)
            labels.megabits.format(
                Locale.US,
                String.format(Locale.US, "%.1f", bitsPerSecond / 1_000_000f),
            )
        else labels.kilobits.format(Locale.US, (bitsPerSecond / 1000f).roundToInt().toString())
    }

    fun frameRate(fps: Float?, labels: PlayerFormatLabels = PlayerFormatLabels()): String? {
        if (fps == null || fps <= 0f || fps.isNaN()) return null
        val rounded = (fps * 1000).roundToInt() / 1000f
        val text =
            if (rounded % 1f == 0f) rounded.toInt().toString()
            else String.format(Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.')
        return labels.framesPerSecond.format(Locale.US, text)
    }

    /** "1920 × 1080 (1080p)"; the class follows the long side so letterboxed encodes read right. */
    fun resolution(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val long = maxOf(width, height)
        val label =
            when {
                long >= 3800 -> "4K"
                long >= 2500 -> "1440p"
                long >= 1900 -> "1080p"
                long >= 1270 -> "720p"
                else -> "${minOf(width, height)}p"
            }
        return "$width × $height ($label)"
    }

    fun speed(speed: Float, labels: PlayerFormatLabels = PlayerFormatLabels()): String {
        val text =
            if (speed % 1f == 0f) speed.toInt().toString()
            else String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
        return labels.speed.format(Locale.US, text)
    }

    fun subtitleDelay(delayMs: Long, labels: PlayerFormatLabels = PlayerFormatLabels()): String {
        val seconds = delayMs / 1000f
        val sign = if (delayMs > 0) "+" else ""
        return sign + labels.seconds.format(Locale.US, String.format(Locale.US, "%.2f", seconds))
    }

    fun subtitleScale(scale: Float): String = "${(scale * 100).roundToInt()}%"
}

@androidx.compose.runtime.Composable
internal fun rememberPlayerFormatLabels(): PlayerFormatLabels {
    val resources = androidx.compose.ui.platform.LocalResources.current
    return androidx.compose.runtime.remember(resources) {
        PlayerFormatLabels(
            mono = resources.getString(com.singam.lionlibrary.R.string.player_audio_mono),
            stereo = resources.getString(com.singam.lionlibrary.R.string.player_audio_stereo),
            channels = resources.getString(com.singam.lionlibrary.R.string.player_audio_channels),
            kilohertz = resources.getString(com.singam.lionlibrary.R.string.player_unit_khz),
            kilobits = resources.getString(com.singam.lionlibrary.R.string.player_unit_kbps),
            megabits = resources.getString(com.singam.lionlibrary.R.string.player_unit_mbps),
            framesPerSecond = resources.getString(com.singam.lionlibrary.R.string.player_unit_fps),
            speed = resources.getString(com.singam.lionlibrary.R.string.player_unit_speed),
            seconds = resources.getString(com.singam.lionlibrary.R.string.player_unit_seconds),
        )
    }
}
