package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.WatchProgress
import com.singam.lionlibrary.domain.model.WatchProgressPolicy
import org.junit.jupiter.api.Test

/**
 * Break vectors: specials against regular seasons; zero-position "started" rows; history for
 * episodes no longer present; recency ties; everything completed; no episodes at all.
 */
class WatchProgressPolicyTest {
    private fun ep(id: Long, season: Int, number: Int) =
        Episode(id, 1, season, number, null, null, null, null, null, "content://fixture/$id")

    private fun progress(
        episodeId: Long,
        watched: Long,
        position: Long = 500,
        completed: Boolean = false,
    ) = WatchProgress(1, episodeId, 0.5f, position, 1_000, watched, completed)

    private val special = ep(1, 0, 1)
    private val first = ep(2, 1, 1)
    private val second = ep(3, 1, 2)
    private val episodes = listOf(special, first, second)

    @Test
    fun `most recent unfinished regular episode wins and specials are ignored`() {
        val history = listOf(progress(2, 10), progress(3, 30), progress(1, 99))
        assertThat(WatchProgressPolicy.resumeEpisode(episodes, history)).isEqualTo(second)
    }

    @Test
    fun `a started row without a position falls through to the first unfinished episode`() {
        val history = listOf(progress(2, 10, completed = true), progress(3, 20, position = 0))
        assertThat(WatchProgressPolicy.resumeEpisode(episodes, history)).isEqualTo(second)
    }

    @Test
    fun `history for a missing episode is ignored and ties keep the first record`() {
        val history = listOf(progress(42, 50), progress(2, 20), progress(3, 20))
        assertThat(WatchProgressPolicy.resumeEpisode(episodes, history)).isEqualTo(first)
    }

    @Test
    fun `specials-only shows use specials, and a fully watched show restarts`() {
        assertThat(WatchProgressPolicy.resumeEpisode(listOf(special), emptyList()))
            .isEqualTo(special)
        val allDone = listOf(progress(2, 1, completed = true), progress(3, 2, completed = true))
        assertThat(WatchProgressPolicy.resumeEpisode(episodes, allDone)).isEqualTo(first)
        assertThat(WatchProgressPolicy.resumeEpisode(emptyList(), allDone)).isNull()
    }
}
