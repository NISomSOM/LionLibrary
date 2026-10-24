package com.singam.lionlibrary.data.mapper

import com.singam.lionlibrary.data.remote.dto.SkipDbResponseDto
import com.singam.lionlibrary.data.remote.dto.SkipDbSegmentDto
import com.singam.lionlibrary.data.remote.dto.TheIntroDbMediaDto
import com.singam.lionlibrary.data.remote.dto.TheIntroDbSegmentDto
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentPolicy
import com.singam.lionlibrary.domain.model.SkipSegmentType
import com.singam.lionlibrary.domain.model.SkipSource

// Ten trillion ms (~317 years) rejects absurd or overflowing provider numbers before Long math.
private const val MaximumProviderMs = 10_000_000_000_000.0

/** A provider millisecond value as a Long, or null when absent, fractional garbage or absurd. */
internal fun Double?.toProviderMs(): Long? =
    this?.takeIf { it.isFinite() && it >= 0 && it <= MaximumProviderMs }?.toLong()

/** SkipDB matches on whole seconds; round to the nearest one. */
internal fun Long.toSkipDbSeconds(): Long = (this + 500) / 1_000

/** Eligible, validated TheIntroDB segments; recaps and previews are ignored. */
fun TheIntroDbMediaDto.toSkipSegments(fileDurationMs: Long): List<SkipSegment> {
    val groupDuration = groupDurationMs?.toProviderMs()
    fun List<TheIntroDbSegmentDto>?.valid(type: SkipSegmentType) =
        orEmpty().mapNotNull { dto ->
            if (
                !SkipSegmentPolicy.isTheIntroDbEligible(
                    dto.confidence,
                    groupDuration,
                    fileDurationMs,
                )
            )
                return@mapNotNull null
            // A present-but-unusable start must not be read as "from the beginning".
            if (dto.startMs != null && dto.startMs.toProviderMs() == null) return@mapNotNull null
            if (dto.endMs != null && dto.endMs.toProviderMs() == null) return@mapNotNull null
            SkipSegmentPolicy.validateOnline(
                type,
                dto.startMs.toProviderMs(),
                dto.endMs.toProviderMs(),
                fileDurationMs,
                SkipSource.THEINTRODB,
                dto.confidence,
            )
        }
    return intro.valid(SkipSegmentType.INTRO) + credits.valid(SkipSegmentType.CREDITS)
}

/** Eligible, validated SkipDB segments; `outro` maps to credits, recap/preview are ignored. */
fun SkipDbResponseDto.toSkipSegments(fileDurationMs: Long): List<SkipSegment> {
    fun SkipDbSegmentDto?.valid(type: SkipSegmentType): SkipSegment? {
        val dto = this ?: return null
        if (!SkipSegmentPolicy.isSkipDbEligible(dto.confidence, dto.match, dto.adjusted))
            return null
        if (dto.startMs != null && dto.startMs.toProviderMs() == null) return null
        if (dto.endMs != null && dto.endMs.toProviderMs() == null) return null
        return SkipSegmentPolicy.validateOnline(
            type,
            dto.startMs.toProviderMs(),
            dto.endMs.toProviderMs(),
            fileDurationMs,
            SkipSource.SKIPDB,
            dto.confidence,
        )
    }
    return listOfNotNull(
        segments?.intro.valid(SkipSegmentType.INTRO),
        segments?.outro.valid(SkipSegmentType.CREDITS),
    )
}
