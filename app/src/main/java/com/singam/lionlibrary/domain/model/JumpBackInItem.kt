package com.singam.lionlibrary.domain.model

data class JumpBackInItem(
    val mediaId: Long,
    val mediaType: MediaType,
    val mediaTitle: String,
    val posterPath: String?,
    val episodeId: Long?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val filePath: String?,
    val lastWatched: Long
)

