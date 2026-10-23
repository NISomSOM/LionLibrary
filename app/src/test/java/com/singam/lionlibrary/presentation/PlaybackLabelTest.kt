package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.presentation.details.DetailsState
import com.singam.lionlibrary.presentation.details.playbackLabel
import org.junit.jupiter.api.Test

/**
 * Break vectors: untouched show; non-first completion; resume/replay precedence; movie-history ID.
 */
class PlaybackLabelTest {
    private val episode = Episode(40, 1, 2, 4, null, null, null, null, null, "content://fixture/40")

    @Test
    fun `unstarted later-season episode still says Play`() {
        assertThat(DetailsState(nextEpisodeToWatch = episode).playbackLabel())
            .isEqualTo(R.string.play)
    }

    @Test
    fun `completion of any other episode permits Next Episode`() {
        assertThat(
                DetailsState(nextEpisodeToWatch = episode, watchedEpisodeIds = setOf(49))
                    .playbackLabel()
            )
            .isEqualTo(R.string.next_episode)
    }

    @Test
    fun `partial target takes Resume precedence over completed history`() {
        assertThat(
                DetailsState(
                        nextEpisodeToWatch = episode,
                        watchedEpisodeIds = setOf(49),
                        resumeEpisodeIds = setOf(40),
                    )
                    .playbackLabel()
            )
            .isEqualTo(R.string.resume)
    }

    @Test
    fun `completed target says Replay even with stale partial history`() {
        assertThat(
                DetailsState(
                        nextEpisodeToWatch = episode,
                        watchedEpisodeIds = setOf(40),
                        resumeEpisodeIds = setOf(40),
                    )
                    .playbackLabel()
            )
            .isEqualTo(R.string.replay)
    }

    @Test
    fun `movie sentinel history is not completed episode evidence`() {
        assertThat(
                DetailsState(nextEpisodeToWatch = episode, watchedEpisodeIds = setOf(0))
                    .playbackLabel()
            )
            .isEqualTo(R.string.play)
    }
}
