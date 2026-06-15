package com.example.mediahub.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvImagesResponseDto(
    @SerialName("id") val id: Int,
    @SerialName("logos") val logos: List<LogoDto> = emptyList()
)

@Serializable
data class LogoDto(
    @SerialName("file_path") val filePath: String,
    @SerialName("iso_639_1") val iso6391: String? = null
)
