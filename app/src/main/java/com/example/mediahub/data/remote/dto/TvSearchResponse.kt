package com.example.mediahub.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvSearchResponse(
    @SerialName("page") val page: Int,
    @SerialName("results") val results: List<TvShowDto>,
    @SerialName("total_results") val totalResults: Int,
    @SerialName("total_pages") val totalPages: Int
)
