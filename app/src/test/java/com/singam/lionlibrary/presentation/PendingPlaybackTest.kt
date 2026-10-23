package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.presentation.player.engine.PendingPlayback
import org.junit.jupiter.api.Test

// Break vectors: pause before surface, seek before readiness/duplicate readiness, rapid seek
// replacement, negative/Long.MAX positions, stop/release with queued commands.
// Pure state only: no native engine, IO, clock or real concurrency.
class PendingPlaybackTest {
    @Test
    fun `pause cancels queued autoplay before surface arrives`() {
        val pending = PendingPlayback()
        pending.play()
        pending.pause()
        assertThat(pending.playRequested).isFalse()
    }

    @Test
    fun `seek survives any number of not seekable notifications and applies once`() {
        val pending = PendingPlayback()
        pending.seek(42_000)
        repeat(3) { assertThat(pending.positionWhenSeekable(false)).isNull() }
        assertThat(pending.positionWhenSeekable(true)).isEqualTo(42_000L)
        assertThat(pending.positionWhenSeekable(true)).isNull()
    }

    @Test
    fun `latest seek replaces previous even while paused`() {
        val pending = PendingPlayback()
        pending.seek(1_000)
        pending.seek(9_000)
        pending.pause()
        assertThat(pending.positionWhenSeekable(true)).isEqualTo(9_000L)
    }

    @Test
    fun `positions clamp negatives without overflowing large longs`() {
        val pending = PendingPlayback()
        pending.seek(Long.MIN_VALUE)
        assertThat(pending.positionWhenSeekable(true)).isEqualTo(0L)
        pending.seek(Long.MAX_VALUE)
        assertThat(pending.positionWhenSeekable(true)).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `new media discards old play and seek commands`() {
        val pending = PendingPlayback()
        pending.play()
        pending.seek(8_000)
        pending.reset()
        assertThat(pending.playRequested).isFalse()
        assertThat(pending.positionWhenSeekable(true)).isNull()
        pending.play()
        assertThat(pending.playRequested).isTrue()
    }
}
