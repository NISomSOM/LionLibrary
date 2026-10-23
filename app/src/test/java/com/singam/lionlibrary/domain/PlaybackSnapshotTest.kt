package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.PlaybackSnapshot
import org.junit.jupiter.api.Test

class PlaybackSnapshotTest {
    @Test
    fun `unknown duration cannot mark arbitrary progress complete`() {
        val value = PlaybackSnapshot(1, 2, 30000, 0, recordedAt = 42).toProgress()
        assertThat(value.completed).isFalse()
        assertThat(value.progress).isEqualTo(0f)
        assertThat(value.lastPositionMs).isEqualTo(30000L)
    }

    @Test
    fun `negative decoder values are bounded without inventing duration`() {
        val value = PlaybackSnapshot(1, 2, -1, -1, recordedAt = 42).toProgress()
        assertThat(value.lastPositionMs).isEqualTo(0L)
        assertThat(value.durationMs).isEqualTo(0L)
        assertThat(value.completed).isFalse()
    }

    @Test
    fun `completion boundary is exactly 95 percent`() {
        assertThat(PlaybackSnapshot(1, 2, 949, 1000, recordedAt = 1).toProgress().completed)
            .isFalse()
        assertThat(PlaybackSnapshot(1, 2, 950, 1000, recordedAt = 1).toProgress().completed)
            .isTrue()
    }

    @Test
    fun `ended unknown duration stores completion and zero resume`() {
        val value = PlaybackSnapshot(1, 2, 0, 0, ended = true, recordedAt = 1).toProgress()
        assertThat(value.completed).isTrue()
        assertThat(value.progress).isEqualTo(1f)
        assertThat(value.lastPositionMs).isEqualTo(0L)
    }

    @Test
    fun `long max position cannot overflow or create progress above one`() {
        val value = PlaybackSnapshot(7, 9, Long.MAX_VALUE, 1000, recordedAt = 1).toProgress()
        assertThat(value.mediaId).isEqualTo(7L)
        assertThat(value.episodeId).isEqualTo(9L)
        assertThat(value.progress).isEqualTo(1f)
        assertThat(value.lastPositionMs).isEqualTo(1000L)
    }
}
