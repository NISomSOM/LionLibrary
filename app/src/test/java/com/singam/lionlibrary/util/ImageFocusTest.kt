package com.singam.lionlibrary.util

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ImageFocusTest {
    // Break vectors: flat image, subject on the left/right edge (must not pin to the edge), tiny or
    // truncated pixel arrays, pure black, and symmetric detail.
    private fun image(width: Int, height: Int, pixel: (Int, Int) -> Int) =
        IntArray(width * height) { pixel(it % width, it / width) }

    private val grey = 0xFF808080.toInt()

    @Test
    fun `flat and black images stay centred`() {
        assertThat(ImageFocus.horizontalFocus(image(64, 36) { _, _ -> grey }, 64, 36))
            .isEqualTo(.5f)
        assertThat(ImageFocus.horizontalFocus(IntArray(64 * 36) { 0xFF000000.toInt() }, 64, 36))
            .isEqualTo(.5f)
    }

    @Test
    fun `detailed subject on one side pulls the focus toward it but not to the edge`() {
        val left =
            image(64, 36) { x, y -> if (x < 16 && (x + y) % 2 == 0) 0xFFFF2000.toInt() else grey }
        val right =
            image(64, 36) { x, y -> if (x > 47 && (x + y) % 2 == 0) 0xFF20FF00.toInt() else grey }

        assertThat(ImageFocus.horizontalFocus(left, 64, 36)).isBetween(ImageFocus.MIN_FOCUS, .35f)
        assertThat(ImageFocus.horizontalFocus(right, 64, 36)).isBetween(.65f, ImageFocus.MAX_FOCUS)
    }

    @Test
    fun `symmetric detail stays near the centre`() {
        val both =
            image(64, 36) { x, y -> if ((x < 10 || x > 53) && (x + y) % 2 == 0) -1 else grey }

        assertThat(ImageFocus.horizontalFocus(both, 64, 36)).isBetween(.45f, .55f)
    }

    @Test
    fun `degenerate inputs are centred`() {
        assertThat(ImageFocus.horizontalFocus(IntArray(4), 2, 2)).isEqualTo(.5f)
        assertThat(ImageFocus.horizontalFocus(IntArray(10), 64, 36)).isEqualTo(.5f)
    }
}
