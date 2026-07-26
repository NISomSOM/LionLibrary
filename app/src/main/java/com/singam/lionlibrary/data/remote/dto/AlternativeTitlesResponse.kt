package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlternativeTitlesResponse(
    @SerialName("id")
    val id: Int,
    @SerialName("results")
    val results: List<AlternativeTitleDto>? = null,
    // TMDB uses 'titles' for movies and 'results' for TV.
    @SerialName("titles")
    val titles: List<AlternativeTitleDto>? = null
)

@Serializable
data class AlternativeTitleDto(
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    @SerialName("title")
    val title: String,
    @SerialName("type")
    val type: String? = null
)
