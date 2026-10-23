package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.presentation.player.PlayerFormatting
import com.singam.lionlibrary.presentation.player.SubtitleLanguageGroup
import com.singam.lionlibrary.presentation.player.engine.EngineTrackInfo
import com.singam.lionlibrary.presentation.player.engine.PlayerCodecNames
import java.util.Locale
import org.junit.jupiter.api.Test

// Break vectors: missing/blank/"und"/mixed-case language tags, unknown codecs and padded VLC
// fourccs, zero/negative/NaN stream values, ultrawide and portrait resolutions, fractional
// speeds and negative delays. Pure functions only; locale is passed in where it matters.
class PlayerFormattingTest {
    private fun track(id: String, language: String?) =
        EngineTrackInfo(id, id, isSelected = false, language = language)

    @Test
    fun `languages group case-insensitively and untagged tracks share one bucket`() {
        val tracks =
            listOf(
                track("1", "en"),
                track("2", "EN"),
                track("3", null),
                track("4", " "),
                track("5", "ja"),
            )
        assertThat(PlayerFormatting.subtitleLanguages(tracks))
            .containsExactly(
                SubtitleLanguageGroup("en", 2),
                SubtitleLanguageGroup("", 2),
                SubtitleLanguageGroup("ja", 1),
            )
    }

    @Test
    fun `filter null keeps every track and empty key selects only untagged ones`() {
        val tracks = listOf(track("1", "en"), track("2", null), track("3", "fr"))
        assertThat(PlayerFormatting.filterSubtitles(tracks, null)).isEqualTo(tracks)
        assertThat(PlayerFormatting.filterSubtitles(tracks, "").map { it.id }).containsExactly("2")
        assertThat(PlayerFormatting.filterSubtitles(tracks, "de")).isEmpty()
    }

    @Test
    fun `language names resolve two and three letter codes and fall back to the code`() {
        assertThat(PlayerFormatting.languageName("ja", Locale.ENGLISH)).isEqualTo("Japanese")
        assertThat(PlayerFormatting.languageName("eng", Locale.ENGLISH)).isEqualTo("English")
        assertThat(PlayerFormatting.languageName("und", Locale.ENGLISH)).isNull()
        assertThat(PlayerFormatting.languageName("  ", Locale.ENGLISH)).isNull()
        assertThat(PlayerFormatting.languageName("qaa", Locale.ENGLISH)).isEqualTo("QAA")
    }

    @Test
    fun `channel layouts use familiar names and reject non-positive counts`() {
        assertThat(PlayerFormatting.channels(2)).isEqualTo("Stereo")
        assertThat(PlayerFormatting.channels(6)).isEqualTo("5.1")
        assertThat(PlayerFormatting.channels(3)).isEqualTo("3 ch")
        assertThat(PlayerFormatting.channels(0)).isNull()
        assertThat(PlayerFormatting.channels(-1)).isNull()
    }

    @Test
    fun `sample rate and bitrate format across unit boundaries`() {
        assertThat(PlayerFormatting.sampleRate(48_000)).isEqualTo("48 kHz")
        assertThat(PlayerFormatting.sampleRate(44_100)).isEqualTo("44.1 kHz")
        assertThat(PlayerFormatting.sampleRate(0)).isNull()
        assertThat(PlayerFormatting.bitrate(999_999)).isEqualTo("1000 kbps")
        assertThat(PlayerFormatting.bitrate(1_000_000)).isEqualTo("1.0 Mbps")
        assertThat(PlayerFormatting.bitrate(Int.MAX_VALUE)).isEqualTo("2147.5 Mbps")
        assertThat(PlayerFormatting.bitrate(-5)).isNull()
    }

    @Test
    fun `frame rates keep NTSC precision and drop trailing zeros`() {
        assertThat(PlayerFormatting.frameRate(23.976f)).isEqualTo("23.976 fps")
        assertThat(PlayerFormatting.frameRate(25f)).isEqualTo("25 fps")
        assertThat(PlayerFormatting.frameRate(29.97f)).isEqualTo("29.97 fps")
        assertThat(PlayerFormatting.frameRate(Float.NaN)).isNull()
        assertThat(PlayerFormatting.frameRate(0f)).isNull()
    }

    @Test
    fun `resolution labels follow the picture class including letterboxed encodes`() {
        assertThat(PlayerFormatting.resolution(1920, 1080)).isEqualTo("1920 × 1080 (1080p)")
        assertThat(PlayerFormatting.resolution(1920, 800)).isEqualTo("1920 × 800 (1080p)")
        assertThat(PlayerFormatting.resolution(3840, 1608)).isEqualTo("3840 × 1608 (4K)")
        assertThat(PlayerFormatting.resolution(2560, 1440)).isEqualTo("2560 × 1440 (1440p)")
        assertThat(PlayerFormatting.resolution(1080, 1920)).isEqualTo("1080 × 1920 (1080p)")
        assertThat(PlayerFormatting.resolution(640, 480)).isEqualTo("640 × 480 (480p)")
        assertThat(PlayerFormatting.resolution(0, 1080)).isNull()
    }

    @Test
    fun `speed delay and scale labels`() {
        assertThat(PlayerFormatting.speed(1f)).isEqualTo("1x")
        assertThat(PlayerFormatting.speed(1.25f)).isEqualTo("1.25x")
        assertThat(PlayerFormatting.speed(0.5f)).isEqualTo("0.5x")
        assertThat(PlayerFormatting.subtitleDelay(250)).isEqualTo("+0.25 s")
        assertThat(PlayerFormatting.subtitleDelay(-1_500)).isEqualTo("-1.50 s")
        assertThat(PlayerFormatting.subtitleDelay(0)).isEqualTo("0.00 s")
        assertThat(PlayerFormatting.subtitleScale(1.1f)).isEqualTo("110%")
    }

    @Test
    fun `codec names map known types and degrade readably for unknown ones`() {
        assertThat(PlayerCodecNames.fromMime("video/hevc")).isEqualTo("HEVC")
        assertThat(PlayerCodecNames.fromMime("AUDIO/MP4A-LATM")).isEqualTo("AAC")
        assertThat(PlayerCodecNames.fromMime("audio/x-unknown")).isEqualTo("UNKNOWN")
        assertThat(PlayerCodecNames.fromMime("")).isNull()
        assertThat(PlayerCodecNames.fromFourcc("a52 ")).isEqualTo("AC3")
        assertThat(PlayerCodecNames.fromFourcc("h264")).isEqualTo("H.264")
        assertThat(PlayerCodecNames.fromFourcc("wxyz")).isEqualTo("WXYZ")
        assertThat(PlayerCodecNames.fromFourcc(null)).isNull()
    }
}
