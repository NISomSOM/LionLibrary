package com.singam.lionlibrary.data.scanner

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import org.junit.jupiter.api.Test

class ScanIndexTest {
    // Break vectors: an existing sidecar or range overwritten (tie-break must stay with the first
    // stored sidecar), an unknown path back-filled, a range-only or sidecar-only change, nothing
    // found by the scan, raw rows with no file path, and look-alike paths (case, encoding).

    private fun episode(path: String, end: Int? = null, subtitle: String? = null) =
        EpisodeEntity(
            showId = 1,
            seasonNumber = 1,
            episodeNumber = 1,
            title = null,
            overview = null,
            runtime = null,
            airDate = null,
            thumbnailPath = null,
            filePath = path,
            externalSubtitlePath = subtitle,
            episodeEndNumber = end,
        )

    private fun movie(path: String?, subtitle: String? = null, unidentified: Boolean = false) =
        MediaEntity(
            tmdbId = null,
            title = "M",
            originalTitle = null,
            overview = null,
            posterPath = null,
            backdropPath = null,
            genres = null,
            rating = null,
            year = null,
            mediaType = "MOVIE",
            matchConfidence = 1f,
            isUnidentified = unidentified,
            lastUpdated = 1,
            filePath = path,
            externalSubtitlePath = subtitle,
        )

    private val index =
        ScanIndex(
            media = listOf(movie("content://m/a"), movie("content://m/b", "sub-b"), movie(null)),
            episodes =
                listOf(
                    episode("content://e/1"),
                    episode("content://e/2", end = 3, subtitle = "old.srt"),
                    episode("content://e/4", end = 5),
                ),
        )

    @Test
    fun `empty fields are back-filled, existing values never change`() {
        assertThat(index.episodeBackfill("content://e/1", 2, "new.srt"))
            .isEqualTo(EpisodeBackfill(2, "new.srt"))
        assertThat(index.episodeBackfill("content://e/2", 9, "other.srt")).isNull()
        assertThat(index.episodeBackfill("content://e/4", 9, "s.srt"))
            .isEqualTo(EpisodeBackfill(null, "s.srt"))
    }

    @Test
    fun `nothing found by the scan or unknown paths yield no write`() {
        assertThat(index.episodeBackfill("content://e/1", null, null)).isNull()
        assertThat(index.episodeBackfill("content://e/99", 2, "x.srt")).isNull()
        assertThat(index.episodeBackfill("CONTENT://e/1", 2, "x.srt")).isNull()
    }

    @Test
    fun `known episodes and media are matched by exact path`() {
        assertThat(index.isKnownEpisode("content://e/1")).isTrue()
        assertThat(index.isKnownEpisode("content://e/1%20")).isFalse()
        assertThat(index.media("content://m/a")?.title).isEqualTo("M")
        assertThat(index.media("content://m/z")).isNull()
    }

    @Test
    fun `movie sidecar fills only an empty slot of a known row`() {
        assertThat(index.mediaSubtitleBackfill("content://m/a", "a.srt")).isEqualTo("a.srt")
        assertThat(index.mediaSubtitleBackfill("content://m/b", "b2.srt")).isNull()
        assertThat(index.mediaSubtitleBackfill("content://m/z", "z.srt")).isNull()
        assertThat(index.mediaSubtitleBackfill("content://m/a", null)).isNull()
    }
}
