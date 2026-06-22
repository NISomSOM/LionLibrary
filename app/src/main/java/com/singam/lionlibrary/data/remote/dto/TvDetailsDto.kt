package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("genres") val genres: List<GenreDto>? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("seasons") val seasons: List<SeasonSummaryDto>? = null
)

@Serializable
data class SeasonSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null
)

