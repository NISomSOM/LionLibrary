package com.singam.lionlibrary.domain.model

data class Episode(
    val id: Long = 0,
    val showId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val overview: String?,
    val runtime: Int?,
    val thumbnailPath: String?,
    val filePath: String
)

