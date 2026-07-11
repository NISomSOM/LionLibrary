package com.singam.lionlibrary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlternativeTitlesResponse(
    @SerialName("id")
    val id: Int,
    @SerialName("results")
    val results: List<AlternativeTitleDto>? = null,
    // Movie aliases are sometimes under "titles" instead of "results", let's check TMDB spec.
    // Wait, TMDB movie alternative titles is "titles", but TV is "results". 
    // Let's support both.
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
