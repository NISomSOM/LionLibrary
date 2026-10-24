package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TheIntroDB v4 `GET /media` with `details=true`. Times are milliseconds. Decoded as numbers so an
 * unexpected fractional value is range-checked by the mapper instead of failing the request.
 */
@Serializable
data class TheIntroDbMediaDto(
    @SerialName("group_duration_ms") val groupDurationMs: Double? = null,
    val intro: List<TheIntroDbSegmentDto>? = null,
    val credits: List<TheIntroDbSegmentDto>? = null,
)

@Serializable
data class TheIntroDbSegmentDto(
    @SerialName("start_ms") val startMs: Double? = null,
    @SerialName("end_ms") val endMs: Double? = null,
    val confidence: Double? = null,
    @SerialName("submission_count") val submissionCount: Int? = null,
)

/** SkipDB `GET /api/segments`. Times are milliseconds; `outro` is the end credits. */
@Serializable data class SkipDbResponseDto(val segments: SkipDbSegmentsDto? = null)

@Serializable
data class SkipDbSegmentsDto(
    val intro: SkipDbSegmentDto? = null,
    val outro: SkipDbSegmentDto? = null,
)

@Serializable
data class SkipDbSegmentDto(
    @SerialName("start_ms") val startMs: Double? = null,
    @SerialName("end_ms") val endMs: Double? = null,
    val match: String? = null,
    val adjusted: Boolean? = null,
    val confidence: Double? = null,
)

/** TMDB `movie|tv/{id}/external_ids`; only the IMDb id is used. */
@Serializable data class ExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null)
