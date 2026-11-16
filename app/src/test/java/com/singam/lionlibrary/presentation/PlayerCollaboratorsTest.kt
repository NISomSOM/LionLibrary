package com.singam.lionlibrary.presentation

import android.net.Uri
import android.view.ViewGroup
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.singam.lionlibrary.domain.model.EngineType
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.PlaybackChapter
import com.singam.lionlibrary.domain.model.PlaybackTarget
import com.singam.lionlibrary.domain.model.SavedTrack
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentType
import com.singam.lionlibrary.domain.model.SkipSource
import com.singam.lionlibrary.domain.model.TrackPreference
import com.singam.lionlibrary.presentation.player.PlayerProgressPolicy
import com.singam.lionlibrary.presentation.player.PlayerTrackDefaults
import com.singam.lionlibrary.presentation.player.chapterAt
import com.singam.lionlibrary.presentation.player.chapterLabelAt
import com.singam.lionlibrary.presentation.player.chapterMarkFractions
import com.singam.lionlibrary.presentation.player.engine.EngineState
import com.singam.lionlibrary.presentation.player.engine.EngineTrackInfo
import com.singam.lionlibrary.presentation.player.engine.LionPlayerEngine
import com.singam.lionlibrary.presentation.player.formatSeekOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

class PlayerCollaboratorsTest {
    // Break vectors: chapter lookup before the first chapter / exactly on a start / unsorted input;
    // marks at 0, at or past the end, duplicates, unknown duration; seek offset sign and zero;
    // snapshots for an untouched file, a completed file at 0, engine vs last values, no target;
    // skip button gating; remembered tracks winning over language rules, "off" winning over a
    // remembered subtitle, and manual choices never overridden.

    private val chapters =
        listOf(
            PlaybackChapter(600_000, null, "Ending"),
            PlaybackChapter(0, 90_000, "Opening"),
            PlaybackChapter(90_000, 600_000, "Part A"),
        )

    @Test
    fun `chapter lookup uses the last start at or before the position`() {
        assertThat(chapterAt(chapters, 90_000)?.title).isEqualTo("Part A")
        assertThat(chapterAt(chapters, 89_999)?.title).isEqualTo("Opening")
        assertThat(chapterAt(listOf(PlaybackChapter(5_000, null, "Late")), 0)).isNull()
    }

    @Test
    fun `chapter label trims padding from file titles and hides blank ones`() {
        assertThat(chapterLabelAt(listOf(PlaybackChapter(0, null, " Prologue	")), 10))
            .isEqualTo("Prologue")
        assertThat(chapterLabelAt(listOf(PlaybackChapter(0, null, "  ")), 10)).isNull()
        assertThat(chapterLabelAt(listOf(PlaybackChapter(0, null, null)), 10)).isNull()
        assertThat(chapterLabelAt(emptyList(), 10)).isNull()
    }

    @Test
    fun `marks skip the first frame, the end and duplicates`() {
        val marks =
            chapterMarkFractions(
                chapters +
                    PlaybackChapter(90_000, null, "Dup") +
                    PlaybackChapter(1_000_000, null, "X"),
                1_000_000,
            )

        assertThat(marks).isEqualTo(listOf(.09f, .6f))
        assertThat(chapterMarkFractions(chapters, 0)).isEqualTo(emptyList())
    }

    @Test
    fun `seek offset carries its sign`() {
        assertThat(formatSeekOffset(65_000)).isEqualTo("+01:05")
        assertThat(formatSeekOffset(-30_000)).isEqualTo("−00:30")
        assertThat(formatSeekOffset(0)).isEqualTo("+00:00")
    }

    private val target =
        PlaybackTarget(1, 11, MediaType.TV_SHOW, "Show", "content://x", null, "EXOPLAYER")

    @Test
    fun `untouched file saves nothing, a completed one always saves`() {
        assertThat(PlayerProgressPolicy.snapshot(target, 0, 0, 100, 100, false, 5)).isNull()
        assertThat(PlayerProgressPolicy.snapshot(target, -50, 0, 100, 100, false, 5)).isNull()
        val done = PlayerProgressPolicy.snapshot(target, 0, 0, 100, 100, true, 5)!!
        assertThat(done.ended).isEqualTo(true)
        assertThat(PlayerProgressPolicy.snapshot(null, 50, 50, 100, 100, true, 5)).isNull()
    }

    @Test
    fun `engine values win over the last ticked ones`() {
        val live = PlayerProgressPolicy.snapshot(target, 700, 500, 2_000, 1_000, false, 9)!!
        assertThat(live.positionMs).isEqualTo(700L)
        assertThat(live.durationMs).isEqualTo(2_000L)
        val released = PlayerProgressPolicy.snapshot(target, null, 500, null, 1_000, false, 9)!!
        assertThat(released.positionMs).isEqualTo(500L)
        assertThat(released.episodeId).isEqualTo(11L)
    }

    @Test
    fun `skip button only while seekable, not ended and not switching`() {
        val intro = listOf(SkipSegment(SkipSegmentType.INTRO, 10_000, 70_000, SkipSource.CHAPTER))
        fun button(seekable: Boolean = true, ended: Boolean = false, switching: Boolean = false) =
            PlayerProgressPolicy.skipButton(intro, 20_000, 1_400_000, seekable, ended, switching)

        assertThat(button()).isEqualTo(SkipSegmentType.INTRO)
        assertThat(button(seekable = false)).isNull()
        assertThat(button(ended = true)).isNull()
        assertThat(button(switching = true)).isNull()
    }

    @Test
    fun `remembered tracks win over language rules and off wins over a remembered subtitle`() {
        val engine = TracksEngine()
        val defaults = PlayerTrackDefaults()
        defaults.reset(
            TrackPreference(
                audio = SavedTrack("en", "English"),
                subtitle = SavedTrack("en", "Full"),
                subtitlesOff = true,
            )
        )

        defaults.apply(engine, MediaType.ANIME)

        assertThat(engine.audio).isEqualTo("en")
        assertThat(engine.subtitle).isEqualTo("<off>")
    }

    @Test
    fun `manual choice is not overridden by later discovery and is remembered`() {
        val engine = TracksEngine()
        val defaults = PlayerTrackDefaults()
        defaults.reset(null)
        defaults.apply(engine, MediaType.ANIME)
        assertThat(engine.audio).isEqualTo("ja")

        val saved = defaults.audioChosen(engine.getAudioTracks().first { it.id == "en" })
        engine.audio = "en"
        defaults.apply(engine, MediaType.ANIME)

        assertThat(engine.audio).isEqualTo("en")
        assertThat(saved?.audio?.label).isEqualTo("English")
        assertThat(defaults.subtitleChosen(null, null)?.subtitlesOff).isEqualTo(true)
    }

    private class TracksEngine : LionPlayerEngine {
        var audio: String? = null
        var subtitle: String? = null
        override val state = MutableStateFlow(EngineState())
        override val engineType = EngineType.EXOPLAYER
        override val currentPositionMs = 0L
        override val durationMs = 0L

        override fun getAudioTracks() =
            listOf(
                EngineTrackInfo("ja", "Japanese", false, language = "ja"),
                EngineTrackInfo("en", "English", false, language = "en"),
            )

        override fun getSubtitleTracks() =
            listOf(EngineTrackInfo("full", "Full", false, language = "en"))

        override fun selectAudioTrack(id: String) {
            audio = id
        }

        override fun selectSubtitleTrack(id: String?) {
            subtitle = id ?: "<off>"
        }

        override suspend fun setMedia(uri: Uri, subtitleUri: Uri?) {}

        override fun play() {}

        override fun pause() {}

        override fun seekTo(positionMs: Long) {}

        override fun resetToDefaultTrackSelection() {}

        override fun attachToView(container: ViewGroup) {}

        override fun stop() {}

        override fun release() {}
    }
}
