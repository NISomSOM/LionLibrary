package com.singam.lionlibrary.data.remote

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.data.mapper.toProviderMs
import com.singam.lionlibrary.data.mapper.toSkipDbSeconds
import com.singam.lionlibrary.data.mapper.toSkipSegments
import com.singam.lionlibrary.data.remote.dto.*
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentType.CREDITS
import com.singam.lionlibrary.domain.model.SkipSegmentType.INTRO
import com.singam.lionlibrary.domain.model.SkipSource
import org.junit.jupiter.api.Test

/**
 * Break vectors: ms/s unit confusion and rounding at the half second, NaN/infinite/negative/huge
 * provider numbers, a present but unusable start that must not become "from the beginning", the
 * release duration missing or off by more than two seconds, SkipDB `outro` naming, shifted or
 * adjusted SkipDB matches, and recap/preview data that must be ignored.
 */
class SkipSegmentMappersTest {
    private val hour = 3_600_000L

    @Test
    fun `durations go to SkipDB in rounded whole seconds`() {
        assertThat(1_499L.toSkipDbSeconds()).isEqualTo(1L)
        assertThat(1_500L.toSkipDbSeconds()).isEqualTo(2L)
        assertThat(hour.toSkipDbSeconds()).isEqualTo(3_600L)
    }

    @Test
    fun `provider milliseconds reject non-finite, negative and absurd numbers`() {
        assertThat(1_234.9.toProviderMs()).isEqualTo(1_234L)
        assertThat(Double.NaN.toProviderMs()).isNull()
        assertThat(Double.POSITIVE_INFINITY.toProviderMs()).isNull()
        assertThat((-1.0).toProviderMs()).isNull()
        assertThat(1e300.toProviderMs()).isNull()
        assertThat((null as Double?).toProviderMs()).isNull()
    }

    @Test
    fun `TheIntroDB segments for the matching release are validated and kept`() {
        val dto =
            TheIntroDbMediaDto(
                groupDurationMs = hour + 1_500.0,
                intro = listOf(TheIntroDbSegmentDto(null, 23_000.0, 0.87, 12)),
                credits = listOf(TheIntroDbSegmentDto(hour - 100_000.0, null, 0.6, 3)),
            )
        assertThat(dto.toSkipSegments(hour))
            .containsExactly(
                SkipSegment(INTRO, 0, 23_000, SkipSource.THEINTRODB, 0.87),
                SkipSegment(CREDITS, hour - 100_000, null, SkipSource.THEINTRODB, 0.6),
            )
    }

    @Test
    fun `a different release or missing details yield nothing`() {
        val segments = listOf(TheIntroDbSegmentDto(null, 23_000.0, 0.9, 5))
        assertThat(TheIntroDbMediaDto(hour + 2_001.0, segments).toSkipSegments(hour)).isEmpty()
        assertThat(TheIntroDbMediaDto(null, segments).toSkipSegments(hour)).isEmpty()
        val noConfidence = listOf(TheIntroDbSegmentDto(null, 23_000.0, null, 5))
        assertThat(TheIntroDbMediaDto(hour.toDouble(), noConfidence).toSkipSegments(hour)).isEmpty()
    }

    @Test
    fun `a present but unusable intro start is not read as the beginning`() {
        val dto =
            TheIntroDbMediaDto(
                hour.toDouble(),
                intro = listOf(TheIntroDbSegmentDto(Double.NaN, 23_000.0, 0.9, 5)),
            )
        assertThat(dto.toSkipSegments(hour)).isEmpty()
    }

    private fun skipDb(
        match: String = "exact",
        adjusted: Boolean? = false,
        confidence: Double = 0.6,
    ) = SkipDbSegmentDto(hour - 120_000.0, hour - 10_000.0, match, adjusted, confidence)

    @Test
    fun `SkipDB outro maps to credits`() {
        val dto = SkipDbResponseDto(SkipDbSegmentsDto(outro = skipDb()))
        assertThat(dto.toSkipSegments(hour))
            .containsExactly(
                SkipSegment(CREDITS, hour - 120_000, hour - 10_000, SkipSource.SKIPDB, 0.6)
            )
    }

    @Test
    fun `SkipDB shifted, adjusted or weak matches are ignored`() {
        listOf(skipDb(match = "shifted"), skipDb(adjusted = true), skipDb(confidence = 0.4))
            .forEach { outro ->
                assertThat(SkipDbResponseDto(SkipDbSegmentsDto(outro = outro)).toSkipSegments(hour))
                    .isEmpty()
            }
    }

    @Test
    fun `SkipDB with no segments object yields nothing`() {
        assertThat(SkipDbResponseDto(null).toSkipSegments(hour)).isEmpty()
    }
}
