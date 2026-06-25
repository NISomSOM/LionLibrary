package com.singam.lionlibrary.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["title"]),
        Index(value = ["tmdbId"]),
        Index(value = ["filePath"], unique = true)
    ]
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
    val filePath: String? = null
)

