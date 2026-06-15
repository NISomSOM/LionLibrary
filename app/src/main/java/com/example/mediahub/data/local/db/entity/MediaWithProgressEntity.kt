package com.example.mediahub.data.local.db.entity

/**
 * POJO for the JOIN query result in [com.example.mediahub.data.local.db.dao.WatchProgressDao.getContinueWatching].
 * Room will map the columns from the JOIN query into this class.
 */
data class MediaWithProgressEntity(
    // From media table
    val id: Long,
    val tmdbId: Int?,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val genres: String?,
    val rating: Float?,
    val year: Int?,
    val mediaType: String,
    val matchConfidence: Float,
    val isUnidentified: Boolean,
    val lastUpdated: Long,
    val filePath: String?,
    // From watch_progress table
    val progress: Float,
    val lastWatched: Long
)
