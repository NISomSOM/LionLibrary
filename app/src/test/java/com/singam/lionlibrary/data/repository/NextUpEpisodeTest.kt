package com.singam.lionlibrary.data.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import org.junit.jupiter.api.Test

class NextUpEpisodeTest {
    // Break vectors: combined file range (E1-E2 then E3), season boundary, specials (season 0),
    // unsorted input, gaps in numbering, duplicate season/episode rows, last episode, empty list,
    // Int.MAX_VALUE episode numbers.

    private fun episode(id: Long, season: Int, number: Int, end: Int? = null) =
        EpisodeEntity(
            id = id,
            showId = 1,
            seasonNumber = season,
            episodeNumber = number,
            episodeEndNumber = end,
            title = null,
            overview = null,
            runtime = null,
            airDate = null,
            thumbnailPath = null,
            filePath = "content://fixture/$id",
        )

    @Test
    fun `combined range end number skips the episodes inside the file`() {
        val episodes = listOf(episode(1, 1, 1, end = 2), episode(3, 1, 3))

        assertThat(nextEpisodeAfter(episodes, season = 1, lastEpisode = 2)?.id).isEqualTo(3L)
    }

    @Test
    fun `last episode of a season moves to the lowest episode of the next season`() {
        val episodes = listOf(episode(30, 3, 1), episode(21, 2, 5), episode(20, 2, 2))

        assertThat(nextEpisodeAfter(episodes, season = 1, lastEpisode = 12)?.id).isEqualTo(20L)
    }

    @Test
    fun `finished special moves to the first regular episode`() {
        val episodes = listOf(episode(10, 1, 1), episode(2, 0, 2), episode(1, 0, 1))

        assertThat(nextEpisodeAfter(episodes, season = 0, lastEpisode = 2)?.id).isEqualTo(10L)
    }

    @Test
    fun `numbering gap picks the next existing episode`() {
        val episodes = listOf(episode(9, 1, 9), episode(4, 1, 4))

        assertThat(nextEpisodeAfter(episodes, season = 1, lastEpisode = 2)?.id).isEqualTo(4L)
    }

    @Test
    fun `duplicate season and episode rows resolve to the lower id`() {
        val episodes = listOf(episode(8, 1, 2), episode(5, 1, 2))

        assertThat(nextEpisodeAfter(episodes, season = 1, lastEpisode = 1)?.id).isEqualTo(5L)
    }

    @Test
    fun `no later episode or empty list yields null`() {
        assertThat(nextEpisodeAfter(listOf(episode(1, 1, 1)), 1, 1)).isNull()
        assertThat(nextEpisodeAfter(emptyList(), 1, 1)).isNull()
        assertThat(nextEpisodeAfter(listOf(episode(1, 1, Int.MAX_VALUE)), 1, Int.MAX_VALUE))
            .isNull()
    }
}
