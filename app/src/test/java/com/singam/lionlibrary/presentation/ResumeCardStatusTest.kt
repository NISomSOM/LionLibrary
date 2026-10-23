package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.presentation.components.ResumeCardStatus
import com.singam.lionlibrary.presentation.components.resumeCardStatus
import org.junit.jupiter.api.Test

/**
 * Break vectors: invalid fractions; unknown duration; stale prior-episode timing; overflow/bounds.
 */
class ResumeCardStatusTest {
    private val item =
        JumpBackInItem(
            1,
            MediaType.MOVIE,
            "Film",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            .5f,
            filePath = "content://fixture/1",
            lastWatched = 1,
        )

    @Test
    fun `next-up suppresses timing left over from the previous episode`() {
        assertThat(
                item
                    .copy(isNextUp = true, durationMs = 3_600_000, positionMs = 600_000)
                    .resumeCardStatus()
            )
            .isEqualTo(ResumeCardStatus.NextUp)
    }

    @Test
    fun `nonfinite or empty progress never produces a numeric badge`() {
        for (fraction in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) assertThat(
                item.copy(progress = fraction).resumeCardStatus()
            )
            .isEqualTo(ResumeCardStatus.NextUp)
    }

    @Test
    fun `unknown duration uses percentage rather than invented minutes`() {
        assertThat(item.resumeCardStatus()).isEqualTo(ResumeCardStatus.Watched(50))
    }

    @Test
    fun `captured position takes precedence over rounded fraction`() {
        assertThat(item.copy(durationMs = 3_600_000, positionMs = 600_000).resumeCardStatus())
            .isEqualTo(ResumeCardStatus.Remaining(50))
    }

    @Test
    fun `missing position falls back to progress with safe long arithmetic`() {
        assertThat(item.copy(durationMs = Long.MAX_VALUE).resumeCardStatus())
            .isEqualTo(
                ResumeCardStatus.Remaining(
                    (Long.MAX_VALUE - (Long.MAX_VALUE * .5).toLong()) / 60_000
                )
            )
    }

    @Test
    fun `out-of-range position cannot underflow remaining time`() {
        assertThat(item.copy(durationMs = 1000, positionMs = Long.MAX_VALUE).resumeCardStatus())
            .isEqualTo(ResumeCardStatus.Remaining(1))
    }
}
