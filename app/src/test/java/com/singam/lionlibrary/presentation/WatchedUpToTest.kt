package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.presentation.details.idsWatchedUpTo
import org.junit.jupiter.api.Test

/**
 * Break vectors: inclusive target boundary; a later season's lower episode number; specials
 * (season 0) before a regular target and a specials target; combined ranges compared by their first
 * number; caller order; numeric extremes.
 */
class WatchedUpToTest {
    private fun ep(id: Long, season: Int, number: Int, end: Int? = null) =
        Episode(
            id,
            1,
            season,
            number,
            null,
            null,
            null,
            null,
            null,
            "content://fixture/$id",
            episodeEndNumber = end,
        )

    @Test
    fun `includes the target and earlier seasons but not a later season's lower number`() {
        val all = listOf(ep(1, 1, 9), ep(2, 2, 1), ep(3, 2, 2), ep(4, 2, 3), ep(5, 3, 1))
        assertThat(all.idsWatchedUpTo(all[2])).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `specials precede a regular target, and a specials target excludes regular seasons`() {
        val all = listOf(ep(10, 0, 1), ep(11, 0, 2), ep(12, 1, 1), ep(13, 1, 2))
        assertThat(all.idsWatchedUpTo(all[2])).containsExactly(10L, 11L, 12L)
        assertThat(all.idsWatchedUpTo(all[0])).containsExactly(10L)
    }

    @Test
    fun `a combined file is compared by its first episode number`() {
        val combined = ep(20, 1, 1, end = 2)
        val all = listOf(combined, ep(21, 1, 3))
        assertThat(all.idsWatchedUpTo(combined)).containsExactly(20L)
    }

    @Test
    fun `keeps caller order and handles empty and extreme values`() {
        val all = listOf(ep(31, 1, Int.MAX_VALUE), ep(30, 1, Int.MIN_VALUE))
        assertThat(all.idsWatchedUpTo(all[0])).containsExactly(31L, 30L)
        assertThat(emptyList<Episode>().idsWatchedUpTo(all[0])).isEmpty()
    }
}
