package com.singam.lionlibrary.data.local.db.entity

data class JumpBackInEntity(
    val mediaId: Long,
    val mediaTitle: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String,
    val episodeId: Long?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val thumbnailPath: String?,
    val progress: Float?,
    val completed: Boolean,
    val filePath: String?,
    val lastWatched: Long
)

