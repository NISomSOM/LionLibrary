package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieReleaseDatesResponseDto(
    @SerialName("results") val results: List<MovieReleaseDateResultDto> = emptyList()
)

@Serializable
data class MovieReleaseDateResultDto(
    @SerialName("iso_3166_1") val iso31661: String,
    @SerialName("release_dates") val releaseDates: List<MovieReleaseDateDto> = emptyList()
)

@Serializable
data class MovieReleaseDateDto(
    @SerialName("certification") val certification: String? = null
)

@Serializable
data class TvContentRatingsResponseDto(
    @SerialName("results") val results: List<TvContentRatingResultDto> = emptyList()
)

@Serializable
data class TvContentRatingResultDto(
    @SerialName("iso_3166_1") val iso31661: String,
    @SerialName("rating") val rating: String? = null
)
