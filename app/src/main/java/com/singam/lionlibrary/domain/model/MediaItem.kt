package com.singam.lionlibrary.domain.model

data class MediaItem(
    val id: Long = 0,
    val tmdbId: Int?,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val logoPath: String? = null,
    val genres: String?,
    val rating: Float?,
    val year: Int?,
    val mediaType: MediaType,
    val matchConfidence: Float,
    val isUnidentified: Boolean,
    val duration: Int? = null,
    val certification: String? = null,
    val lastUpdated: Long,
    val filePath: String? = null,
    val preferredEngine: String = "EXOPLAYER",
    val letterboxdRating: Float? = null,
    val isWatchlisted: Boolean = false,
    val addedAt: Long = lastUpdated,
    val externalSubtitlePath: String? = null,
    val letterboxdUpdatedAt: Long? = null,
    val sourceShowTitle: String? = null,
    val sourceSeason: Int? = null,
    val sourceEpisode: Int? = null,
    val sourceEpisodeEnd: Int? = null,
    val identificationFailure: String? = null,
)

val MediaItem.isSingleFile: Boolean
    get() = mediaType == MediaType.MOVIE || (isUnidentified && filePath != null)
