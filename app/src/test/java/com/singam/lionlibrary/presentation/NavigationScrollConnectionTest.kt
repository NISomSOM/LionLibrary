package com.singam.lionlibrary.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.singam.lionlibrary.presentation.navigation.NavigationScrollConnection
import org.junit.jupiter.api.Test

class NavigationScrollConnectionTest {
    // Break vectors: jitter below the threshold, direction reversal mid-gesture, horizontal rows,
    // a pull past the top, repeated identical reports, and reset on a new destination.
    private val events = mutableListOf<Boolean>()
    private val connection = NavigationScrollConnection(thresholdPx = 24f) { events += it }

    private fun scroll(dy: Float, availableY: Float = 0f, dx: Float = 0f) =
        connection.onPostScroll(
            Offset(dx, dy),
            Offset(0f, availableY),
            NestedScrollSource.UserInput,
        )

    @Test
    fun `small jitter does nothing, sustained scroll down compacts once`() {
        scroll(-10f)
        scroll(8f)
        scroll(-10f)
        assertThat(events).isEqualTo(emptyList<Boolean>())

        scroll(-20f)
        scroll(-40f)
        scroll(-40f)
        assertThat(events).isEqualTo(listOf(true))
    }

    @Test
    fun `scrolling up restores and a pull at the top restores`() {
        scroll(-30f)
        scroll(30f)
        assertThat(events).isEqualTo(listOf(true, false))

        scroll(-30f)
        scroll(0f, availableY = 5f)
        assertThat(events).isEqualTo(listOf(true, false, true, false))
    }

    @Test
    fun `horizontal rows are ignored and nothing is consumed`() {
        val result = scroll(0f, dx = -300f)

        assertThat(result).isEqualTo(Offset.Zero)
        assertThat(events).isEqualTo(emptyList<Boolean>())
    }

    @Test
    fun `reset starts a new destination expanded`() {
        scroll(-30f)
        connection.reset()
        scroll(-30f)

        assertThat(events).isEqualTo(listOf(true, true))
    }
}
