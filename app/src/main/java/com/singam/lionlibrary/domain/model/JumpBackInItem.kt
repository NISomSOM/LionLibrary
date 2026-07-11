package com.singam.lionlibrary.domain.model

data class JumpBackInItem(
    val mediaId: Long,
    val mediaType: MediaType,
    val mediaTitle: String,
    val posterPath: String?,
    val backdropPath: String?,
    val episodeId: Long?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val thumbnailPath: String?,
    val progress: Float?,
    val isNextUp: Boolean = false,
    val filePath: String?,
    val lastWatched: Long
)

