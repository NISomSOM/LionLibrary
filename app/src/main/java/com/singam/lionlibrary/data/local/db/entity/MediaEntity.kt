package com.singam.lionlibrary.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices =
        [
            Index(value = ["mediaType"]),
            Index(value = ["title"]),
            Index(value = ["tmdbId"]),
            Index(value = ["filePath"], unique = true),
        ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val mediaType: String,
    val matchConfidence: Float,
    val isUnidentified: Boolean,
    val duration: Int? = null,
    val certification: String? = null,
    val lastUpdated: Long,
    val filePath: String? = null,
    val externalSubtitlePath: String? = null,
    val preferredEngine: String = "EXOPLAYER",
    val letterboxdRating: Float? = null, // Out of 5; fetched lazily on Details open.
    val letterboxdUpdatedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val isWatchlisted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val addedAt: Long = lastUpdated,
    val sourceShowTitle: String? = null,
    val sourceSeason: Int? = null,
    val sourceEpisode: Int? = null,
    val sourceEpisodeEnd: Int? = null,
    val identificationFailure: String? = null,
    // TMDB ISO 639-1 code; "" once checked with none, null until fetched.
    val originalLanguage: String? = null,
)
