package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.model.SkipSegmentType.CREDITS
import com.singam.lionlibrary.domain.model.SkipSegmentType.INTRO
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Break vectors: label case/numbering/full-width forms, labels that only contain a keyword
 * (previews, recaps, "Chapter 3", emoji), chapters without ends or past the duration, a "chapter"
 * covering half the file, confidence and duration exactly at/over each boundary, NaN confidence,
 * reversed and overshooting intervals, "no segment" zero sentinels, intro window on short and long
 * files, credits outside the final third or longer than 30 minutes, source precedence per type,
 * overlapping answers from one source, exclusive segment ends, and identity edge cases (combined
 * episodes, raw files, specials, negative numbers).
 */
class SkipSegmentPolicyTest {
    private val hour = 3_600_000L

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "Opening",
                "OPENING",
                "OP",
                "op 2",
                "OP1",
                "Opening 01",
                "Opening Credits",
                "Intro",
                "intro.",
                "ＯＰ", // Full-width forms normalise to OP.
            ]
    )
    fun `intro labels are recognised regardless of case and numbering`(label: String) {
        assertThat(SkipSegmentPolicy.chapterType(label)).isEqualTo(INTRO)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Ending", "ED", "ed2", "ED 3", "End Credits", "Credits", "Outro"])
    fun `credits labels are recognised regardless of case and numbering`(label: String) {
        assertThat(SkipSegmentPolicy.chapterType(label)).isEqualTo(CREDITS)
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "Chapter 3",
                "Episode",
                "Part A",
                "Prologue",
                "Ending Preview",
                "Next Episode Preview",
                "OP Recap",
                "Introduction",
                "Opening scene of the long journey",
                "Edward",
                "Coda",
                "🎬",
                "",
                "   ",
                "07",
            ]
    )
    fun `generic or other-part labels create no button`(label: String) {
        assertThat(SkipSegmentPolicy.chapterType(label)).isNull()
    }

    @Test
    fun `an Opening chapter outranks an earlier Intro cold open`() {
        val chapters =
            listOf(
                PlaybackChapter(0, 60_000, "Intro"),
                PlaybackChapter(60_000, 150_000, "Opening"),
                PlaybackChapter(150_000, 1_200_000, "Part A"),
            )
        assertThat(SkipSegmentPolicy.fromChapters(chapters, 1_440_000))
            .containsExactly(SkipSegment(INTRO, 60_000, 150_000, SkipSource.CHAPTER))
    }

    @Test
    fun `missing chapter ends come from the next chapter or the duration`() {
        val chapters =
            listOf(
                PlaybackChapter(0, null, "Prologue"),
                PlaybackChapter(30_000, null, "OP"),
                PlaybackChapter(120_000, null, "Episode"),
                PlaybackChapter(1_300_000, null, "ED"),
            )
        assertThat(SkipSegmentPolicy.fromChapters(chapters, 1_420_000))
            .containsExactly(
                SkipSegment(INTRO, 30_000, 120_000, SkipSource.CHAPTER),
                SkipSegment(CREDITS, 1_300_000, 1_420_000, SkipSource.CHAPTER),
            )
    }

    @Test
    fun `last credits chapter without any end stays open-ended until the duration is known`() {
        val chapters = listOf(PlaybackChapter(1_300_000, null, "Ending"))
        assertThat(SkipSegmentPolicy.fromChapters(chapters, durationMs = 0))
            .containsExactly(SkipSegment(CREDITS, 1_300_000, null, SkipSource.CHAPTER))
    }

    @Test
    fun `adjacent credits chapters merge into one button`() {
        val chapters =
            listOf(
                PlaybackChapter(0, 1_200_000, "Episode"),
                PlaybackChapter(1_200_000, 1_290_000, "ED"),
                PlaybackChapter(1_290_000, 1_380_000, "Credits"),
            )
        assertThat(SkipSegmentPolicy.fromChapters(chapters, 1_380_000))
            .containsExactly(SkipSegment(CREDITS, 1_200_000, 1_380_000, SkipSource.CHAPTER))
    }

    @Test
    fun `an intro chapter covering more than half the file is a mislabelled main part`() {
        val chapters = listOf(PlaybackChapter(0, 800_000, "Intro"))
        assertThat(SkipSegmentPolicy.fromChapters(chapters, 1_500_000)).isEmpty()
    }

    @Test
    fun `chapters at or past the end, and sub-second chapters, are ignored`() {
        val chapters =
            listOf(
                PlaybackChapter(10_000, 10_500, "OP"),
                PlaybackChapter(1_500_000, 1_600_000, "ED"),
            )
        assertThat(SkipSegmentPolicy.fromChapters(chapters, 1_500_000)).isEmpty()
    }

    @Test
    fun `TheIntroDB eligibility needs confidence of at least one half`() {
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(0.5, hour, hour)).isTrue()
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(0.4999, hour, hour)).isFalse()
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(Double.NaN, hour, hour)).isFalse()
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(null, hour, hour)).isFalse()
    }

    @Test
    fun `TheIntroDB eligibility needs the release duration within two seconds`() {
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(0.9, hour + 2_000, hour)).isTrue()
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(0.9, hour - 2_001, hour)).isFalse()
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(0.9, null, hour)).isFalse()
        assertThat(SkipSegmentPolicy.isTheIntroDbEligible(0.9, 0, 0)).isFalse()
    }

    @Test
    fun `SkipDB eligibility needs an exact unadjusted match`() {
        assertThat(SkipSegmentPolicy.isSkipDbEligible(0.5, "exact", false)).isTrue()
        assertThat(SkipSegmentPolicy.isSkipDbEligible(0.9, "shifted", false)).isFalse()
        assertThat(SkipSegmentPolicy.isSkipDbEligible(0.9, "EXACT", false)).isFalse()
        assertThat(SkipSegmentPolicy.isSkipDbEligible(0.9, "exact", true)).isFalse()
        assertThat(SkipSegmentPolicy.isSkipDbEligible(0.9, "exact", null)).isFalse()
        assertThat(SkipSegmentPolicy.isSkipDbEligible(0.49, "exact", false)).isFalse()
    }

    private fun online(type: SkipSegmentType, start: Long?, end: Long?, duration: Long = hour) =
        SkipSegmentPolicy.validateOnline(type, start, end, duration, SkipSource.THEINTRODB, 0.9)

    @Test
    fun `reversed, empty and zero-sentinel intervals are rejected`() {
        assertThat(online(INTRO, 90_000, 30_000)).isNull()
        assertThat(online(INTRO, 30_000, 30_000)).isNull()
        assertThat(online(INTRO, null, 0)).isNull()
        assertThat(online(CREDITS, 0, null)).isNull()
        assertThat(online(CREDITS, null, hour)).isNull()
    }

    @Test
    fun `a null intro start means the very beginning`() {
        assertThat(online(INTRO, null, 23_000))
            .isEqualTo(SkipSegment(INTRO, 0, 23_000, SkipSource.THEINTRODB, 0.9))
    }

    @Test
    fun `intervals starting at or past the end, or before zero, are rejected`() {
        assertThat(online(CREDITS, hour, null)).isNull()
        assertThat(online(INTRO, -1, 30_000)).isNull()
    }

    @Test
    fun `an end overshoot of up to two seconds is clamped, more is rejected`() {
        assertThat(online(CREDITS, hour - 60_000, hour + 2_000)?.endMs).isEqualTo(hour)
        assertThat(online(CREDITS, hour - 60_000, hour + 2_001)).isNull()
    }

    @Test
    fun `intro length must be between five seconds and five minutes`() {
        assertThat(online(INTRO, 0, 5_000)).isNotNull()
        assertThat(online(INTRO, 0, 4_999)).isNull()
        assertThat(online(INTRO, 60_000, 360_000)).isNotNull()
        assertThat(online(INTRO, 60_000, 360_001)).isNull()
    }

    @Test
    fun `intro must end within a quarter of a short file`() {
        val twentyMinutes = 1_200_000L
        assertThat(online(INTRO, 200_000, 300_000, twentyMinutes)).isNotNull()
        assertThat(online(INTRO, 200_000, 300_001, twentyMinutes)).isNull()
    }

    @Test
    fun `intro must end within ten minutes of a long file`() {
        val twoHours = 2 * hour
        assertThat(online(INTRO, 400_000, 600_000, twoHours)).isNotNull()
        assertThat(online(INTRO, 400_000, 600_001, twoHours)).isNull()
    }

    @Test
    fun `credits must start in the final third`() {
        val thirdBoundary = hour - hour / 3
        assertThat(online(CREDITS, thirdBoundary, null)).isNotNull()
        assertThat(online(CREDITS, thirdBoundary - 1, hour)).isNull()
    }

    @Test
    fun `credits may last thirty minutes at most, open-ended ones measured to the end`() {
        val threeHours = 3 * hour
        assertThat(online(CREDITS, threeHours - 1_800_000, null, threeHours)).isNotNull()
        assertThat(online(CREDITS, threeHours - 1_800_001, null, threeHours)).isNull()
        assertThat(online(CREDITS, threeHours - 1_900_000, threeHours - 50_000, threeHours))
            .isNull()
    }

    @Test
    fun `an unknown duration validates nothing`() {
        assertThat(online(INTRO, 0, 30_000, duration = 0)).isNull()
    }

    @Test
    fun `chapters win per type and online fills only the missing type`() {
        val chapterIntro = SkipSegment(INTRO, 10_000, 40_000, SkipSource.CHAPTER)
        val onlineIntro = SkipSegment(INTRO, 0, 90_000, SkipSource.THEINTRODB, 0.9)
        val onlineCredits = SkipSegment(CREDITS, 3_400_000, null, SkipSource.SKIPDB, 0.7)
        assertThat(
                SkipSegmentPolicy.combine(listOf(chapterIntro), listOf(onlineIntro, onlineCredits))
            )
            .containsExactly(chapterIntro, onlineCredits)
    }

    @Test
    fun `TheIntroDB wins over SkipDB for the same type`() {
        val skipDb = SkipSegment(CREDITS, 3_300_000, null, SkipSource.SKIPDB, 1.0)
        val theIntroDb = SkipSegment(CREDITS, 3_400_000, null, SkipSource.THEINTRODB, 0.5)
        assertThat(SkipSegmentPolicy.combine(emptyList(), listOf(skipDb, theIntroDb)))
            .containsExactly(theIntroDb)
    }

    @Test
    fun `overlapping answers from one source keep the more confident, separate ones both stay`() {
        val weak = SkipSegment(INTRO, 0, 60_000, SkipSource.THEINTRODB, 0.6)
        val strong = SkipSegment(INTRO, 30_000, 90_000, SkipSource.THEINTRODB, 0.8)
        val later = SkipSegment(INTRO, 120_000, 150_000, SkipSource.THEINTRODB, 0.55)
        assertThat(SkipSegmentPolicy.combine(emptyList(), listOf(weak, later, strong)))
            .containsExactly(strong, later)
    }

    @Test
    fun `the active segment excludes its end, so a finished skip hides the button`() {
        val intro = SkipSegment(INTRO, 10_000, 40_000, SkipSource.CHAPTER)
        assertThat(SkipSegmentPolicy.active(listOf(intro), 9_999, hour)).isNull()
        assertThat(SkipSegmentPolicy.active(listOf(intro), 10_000, hour)).isEqualTo(intro)
        assertThat(SkipSegmentPolicy.active(listOf(intro), 40_000, hour)).isNull()
    }

    @Test
    fun `open-ended credits stay active until the end of the file`() {
        val credits = SkipSegment(CREDITS, hour - 60_000, null, SkipSource.SKIPDB, 0.8)
        assertThat(SkipSegmentPolicy.active(listOf(credits), hour - 1, hour)).isEqualTo(credits)
        assertThat(SkipSegmentPolicy.active(listOf(credits), hour, hour)).isNull()
    }

    @Test
    fun `credits ending at or within two seconds of the end are terminal`() {
        fun credits(end: Long?) = SkipSegment(CREDITS, hour - 60_000, end, SkipSource.CHAPTER)
        assertThat(SkipSegmentPolicy.isTerminal(credits(null), hour)).isTrue()
        assertThat(SkipSegmentPolicy.isTerminal(credits(hour - 2_000), hour)).isTrue()
        assertThat(SkipSegmentPolicy.isTerminal(credits(hour - 2_001), hour)).isFalse()
        val intro = SkipSegment(INTRO, 0, hour, SkipSource.CHAPTER)
        assertThat(SkipSegmentPolicy.isTerminal(intro, hour)).isFalse()
    }

    @Test
    fun `only duration changes beyond two seconds count`() {
        assertThat(SkipSegmentPolicy.durationChanged(hour, hour + 2_000)).isFalse()
        assertThat(SkipSegmentPolicy.durationChanged(hour, hour - 2_001)).isTrue()
    }

    private fun target(
        tmdbId: Int? = 1396,
        episodeId: Long = 7,
        season: Int? = 1,
        episode: Int? = 2,
        episodeEnd: Int? = null,
    ) =
        PlaybackTarget(
            mediaId = 1,
            episodeId = episodeId,
            type = MediaType.TV_SHOW,
            title = "Show",
            filePath = "content://x",
            subtitlePath = null,
            preferredEngine = "EXOPLAYER",
            season = season,
            episode = episode,
            episodeEnd = episodeEnd,
            tmdbId = tmdbId,
        )

    @Test
    fun `episodes are addressed by show id and numbers, movies by id alone`() {
        assertThat(target().skipLookupKey()).isEqualTo(SkipLookupKey(1396, false, 1, 2))
        assertThat(target(episodeId = 0).skipLookupKey()).isEqualTo(SkipLookupKey(1396, true))
    }

    @Test
    fun `specials keep season zero`() {
        assertThat(target(season = 0, episode = 3).skipLookupKey())
            .isEqualTo(SkipLookupKey(1396, false, 0, 3))
    }

    @Test
    fun `combined-episode, unidentified and malformed files have no online identity`() {
        assertThat(target(episodeEnd = 3).skipLookupKey()).isNull()
        assertThat(target(tmdbId = null).skipLookupKey()).isNull()
        assertThat(target(tmdbId = 0).skipLookupKey()).isNull()
        assertThat(target(season = -1).skipLookupKey()).isNull()
        assertThat(target(episode = null).skipLookupKey()).isNull()
    }
}
