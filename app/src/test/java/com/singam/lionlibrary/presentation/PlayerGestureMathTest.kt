package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.singam.lionlibrary.presentation.player.DragAxis
import com.singam.lionlibrary.presentation.player.GestureExclusion
import com.singam.lionlibrary.presentation.player.GestureZone
import com.singam.lionlibrary.presentation.player.PlayerGestureMath
import org.junit.jupiter.api.Test

class PlayerGestureMathTest {
    // Break vectors: duration boundaries (exactly 30/60 min), zero/unknown duration or width, NaN
    // drags, seeking past either end, a diagonal swipe that must not change brightness, movement
    // inside the slop, zone boundaries, starts inside each system edge, and level direction.

    @Test
    fun `seek window steps at exactly thirty and sixty minutes`() {
        assertThat(PlayerGestureMath.seekWindowMs(29 * 60_000L + 59_999)).isEqualTo(60_000L)
        assertThat(PlayerGestureMath.seekWindowMs(30 * 60_000L)).isEqualTo(90_000L)
        assertThat(PlayerGestureMath.seekWindowMs(60 * 60_000L - 1)).isEqualTo(90_000L)
        assertThat(PlayerGestureMath.seekWindowMs(60 * 60_000L)).isEqualTo(120_000L)
        assertThat(PlayerGestureMath.seekWindowMs(Long.MAX_VALUE)).isEqualTo(120_000L)
    }

    @Test
    fun `full width drag covers the window in either direction`() {
        val twoHours = 2 * 60 * 60_000L

        assertThat(PlayerGestureMath.seekOffsetMs(1000f, 1000f, twoHours)).isEqualTo(120_000L)
        assertThat(PlayerGestureMath.seekOffsetMs(-500f, 1000f, 20 * 60_000L)).isEqualTo(-30_000L)
    }

    @Test
    fun `degenerate surfaces and durations never seek`() {
        assertThat(PlayerGestureMath.seekOffsetMs(500f, 0f, 1_000_000)).isEqualTo(0L)
        assertThat(PlayerGestureMath.seekOffsetMs(500f, 1000f, 0)).isEqualTo(0L)
        assertThat(PlayerGestureMath.seekOffsetMs(Float.NaN, 1000f, 1_000_000)).isEqualTo(0L)
    }

    @Test
    fun `seek target stays inside the file`() {
        assertThat(PlayerGestureMath.seekTarget(5_000, -60_000, 100_000)).isEqualTo(0L)
        assertThat(PlayerGestureMath.seekTarget(95_000, 60_000, 100_000)).isEqualTo(100_000L)
        assertThat(PlayerGestureMath.seekTarget(-10, 500, 0)).isEqualTo(500L)
    }

    @Test
    fun `axis needs movement past the slop and a clear vertical lead`() {
        assertThat(PlayerGestureMath.axisFor(5f, 7f, 8f)).isNull()
        assertThat(PlayerGestureMath.axisFor(10f, 14f, 8f)).isNull() // diagonal: no lead yet
        assertThat(PlayerGestureMath.axisFor(10f, 15f, 8f)).isEqualTo(DragAxis.VERTICAL)
        assertThat(PlayerGestureMath.axisFor(-12f, 12f, 8f)).isEqualTo(DragAxis.HORIZONTAL)
        assertThat(PlayerGestureMath.axisFor(0f, -9f, 8f)).isEqualTo(DragAxis.VERTICAL)
    }

    @Test
    fun `zones split the width in thirds`() {
        assertThat(PlayerGestureMath.zoneOf(0f, 900f)).isEqualTo(GestureZone.LEFT)
        assertThat(PlayerGestureMath.zoneOf(300f, 900f)).isEqualTo(GestureZone.CENTER)
        assertThat(PlayerGestureMath.zoneOf(600f, 900f)).isEqualTo(GestureZone.CENTER)
        assertThat(PlayerGestureMath.zoneOf(601f, 900f)).isEqualTo(GestureZone.RIGHT)
        assertThat(PlayerGestureMath.zoneOf(10f, 0f)).isEqualTo(GestureZone.CENTER)
    }

    @Test
    fun `starts inside any system edge are excluded`() {
        val exclusion = GestureExclusion(leftPx = 40f, rightPx = 40f, topPx = 60f, bottomPx = 60f)
        fun excluded(x: Float, y: Float) =
            PlayerGestureMath.startsInSystemZone(x, y, 1000f, 500f, exclusion)

        assertThat(excluded(39f, 250f)).isTrue()
        assertThat(excluded(961f, 250f)).isTrue()
        assertThat(excluded(500f, 59f)).isTrue()
        assertThat(excluded(500f, 441f)).isTrue()
        assertThat(excluded(40f, 60f)).isFalse()
        assertThat(excluded(500f, 250f)).isFalse()
    }

    @Test
    fun `dragging up raises the level, full height changes it twice over`() {
        assertThat(PlayerGestureMath.levelDelta(-500f, 1000f)).isEqualTo(1f)
        assertThat(PlayerGestureMath.levelDelta(250f, 1000f)).isEqualTo(-.5f)
        assertThat(PlayerGestureMath.levelDelta(100f, 0f)).isEqualTo(0f)
    }
}
