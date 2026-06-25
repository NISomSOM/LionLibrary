package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EpisodeDto(
    @SerialName("id") val id: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null
)

