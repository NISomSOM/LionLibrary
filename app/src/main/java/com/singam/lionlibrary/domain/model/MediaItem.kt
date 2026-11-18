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
    val originalLanguage: String? = null,
)

val MediaItem.isSingleFile: Boolean
    get() = mediaType == MediaType.MOVIE || (isUnidentified && filePath != null)

/** The comma-separated genres as a list: trimmed, blanks dropped, each once. */
val MediaItem.genreList: List<String>
    get() = genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

/** True when [selected] is empty or the title has at least one of them (case-insensitive). */
fun MediaItem.hasAnyGenre(selected: Collection<String>): Boolean =
    selected.isEmpty() ||
        genreList.any { own -> selected.any { it.equals(own, ignoreCase = true) } }
