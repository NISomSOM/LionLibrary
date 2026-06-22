package com.singam.lionlibrary.data.local.db.entity

data class JumpBackInEntity(
    val mediaId: Long,
    val mediaTitle: String,
    val posterPath: String?,
    val mediaType: String,
    val episodeId: Long?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val filePath: String?,
    val lastWatched: Long
)

