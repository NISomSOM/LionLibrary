package com.singam.lionlibrary.data.storage

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity
import org.junit.jupiter.api.Test

/**
 * Break vectors: no existing episode history; completion against recency in both directions; equal
 * timestamps; the winner's hidden flag; identity always rewritten to the episode.
 */
class ProgressMergeTest {
    private fun progress(
        position: Long,
        watched: Long,
        completed: Boolean = false,
        hidden: Boolean = false,
    ) = WatchProgressEntity(99, 0, 0.5f, position, 1_000, watched, completed, hidden)

    @Test
    fun `duplicate history moves to the episode when the episode has none`() {
        val merged = mergeDuplicateProgress(null, progress(300, 10), mediaId = 5, episodeId = 7)
        assertThat(merged).isEqualTo(progress(300, 10).copy(mediaId = 5, episodeId = 7))
    }

    @Test
    fun `completion wins over a more recent unfinished record, from either side`() {
        val completedEpisode = progress(0, 10, completed = true)
        assertThat(mergeDuplicateProgress(completedEpisode, progress(400, 20), 5, 7).completed)
            .isEqualTo(true)
        val completedDuplicate = progress(0, 10, completed = true)
        assertThat(mergeDuplicateProgress(progress(400, 20), completedDuplicate, 5, 7).completed)
            .isEqualTo(true)
    }

    @Test
    fun `newer progress wins and an exact tie keeps the episode record`() {
        assertThat(
                mergeDuplicateProgress(progress(100, 10), progress(400, 20), 5, 7).lastPositionMs
            )
            .isEqualTo(400L)
        assertThat(
                mergeDuplicateProgress(progress(100, 20), progress(400, 20), 5, 7).lastPositionMs
            )
            .isEqualTo(100L)
    }

    @Test
    fun `the winner keeps its hidden flag and takes the episode identity`() {
        val merged =
            mergeDuplicateProgress(progress(100, 10), progress(400, 20, hidden = true), 5, 7)
        assertThat(merged.isHidden).isEqualTo(true)
        assertThat(merged.mediaId to merged.episodeId).isEqualTo(5L to 7L)
    }
}
