package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeasonDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("episodes") val episodes: List<EpisodeDto>? = null
)

