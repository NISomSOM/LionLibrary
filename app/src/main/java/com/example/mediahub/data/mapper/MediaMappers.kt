package com.example.mediahub.data.mapper

import com.example.mediahub.data.local.db.entity.EpisodeEntity
import com.example.mediahub.data.local.db.entity.MediaEntity
import com.example.mediahub.data.local.db.entity.SeasonEntity
import com.example.mediahub.data.local.db.entity.WatchProgressEntity
import com.example.mediahub.data.remote.dto.MovieDetailsDto
import com.example.mediahub.data.remote.dto.TvDetailsDto
import com.example.mediahub.domain.model.Episode
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.model.JumpBackInItem
import com.example.mediahub.domain.model.Season
import com.example.mediahub.domain.model.WatchProgress
import com.example.mediahub.data.local.db.entity.JumpBackInEntity


fun MediaEntity.toMediaItem(): MediaItem = MediaItem(
    id = id,
    tmdbId = tmdbId,
    title = title,
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    genres = genres,
    rating = rating,
    year = year,
    mediaType = MediaType.valueOf(mediaType),
    matchConfidence = matchConfidence,
    isUnidentified = isUnidentified,
    lastUpdated = lastUpdated,
    filePath = filePath,
    logoPath = logoPath
)

fun MediaItem.toMediaEntity(): MediaEntity = MediaEntity(
    id = id,
    tmdbId = tmdbId,
    title = title,
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    genres = genres,
    rating = rating,
    year = year,
    mediaType = mediaType.name,
    matchConfidence = matchConfidence,
    isUnidentified = isUnidentified,
    lastUpdated = lastUpdated,
    filePath = filePath,
    logoPath = logoPath
)

fun EpisodeEntity.toEpisode(): Episode = Episode(
    id = id,
    showId = showId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    overview = overview,
    runtime = runtime,
    thumbnailPath = thumbnailPath,
    filePath = filePath
)

fun Episode.toEpisodeEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    showId = showId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    overview = overview,
    runtime = runtime,
    thumbnailPath = thumbnailPath,
    filePath = filePath
)

fun SeasonEntity.toSeason(): Season = Season(
    id = id,
    showId = showId,
    seasonNumber = seasonNumber,
    name = name,
    posterPath = posterPath
)

fun Season.toSeasonEntity(): SeasonEntity = SeasonEntity(
    id = id,
    showId = showId,
    seasonNumber = seasonNumber,
    name = name,
    posterPath = posterPath
)

fun WatchProgressEntity.toWatchProgress(): WatchProgress = WatchProgress(
    mediaId = mediaId,
    episodeId = episodeId,
    progress = progress,
    lastPositionMs = lastPositionMs,
    durationMs = durationMs,
    lastWatched = lastWatched,
    completed = completed
)

fun JumpBackInEntity.toJumpBackInItem(): JumpBackInItem = JumpBackInItem(
    mediaId = mediaId,
    mediaType = MediaType.valueOf(mediaType),
    mediaTitle = mediaTitle,
    posterPath = posterPath,
    episodeId = episodeId,
    episodeTitle = episodeTitle,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    filePath = filePath,
    lastWatched = lastWatched
)


fun MovieDetailsDto.toMediaEntity(
    mediaType: MediaType,
    confidence: Float,
    posterLocalPath: String?,
    backdropLocalPath: String?,
    filePath: String? = null
): MediaEntity = MediaEntity(
    tmdbId = id,
    title = title,
    originalTitle = originalTitle,
    overview = overview,
    posterPath = posterLocalPath,
    backdropPath = backdropLocalPath,
    genres = genres?.joinToString(",") { it.name },
    rating = voteAverage,
    year = releaseDate?.take(4)?.toIntOrNull(),
    mediaType = mediaType.name,
    matchConfidence = confidence,
    isUnidentified = false,
    lastUpdated = System.currentTimeMillis(),
    filePath = filePath
)

fun TvDetailsDto.toMediaEntity(
    mediaType: MediaType,
    confidence: Float,
    posterLocalPath: String?,
    backdropLocalPath: String?
): MediaEntity = MediaEntity(
    tmdbId = id,
    title = name,
    originalTitle = originalName,
    overview = overview,
    posterPath = posterLocalPath,
    backdropPath = backdropLocalPath,
    genres = genres?.joinToString(",") { it.name },
    rating = voteAverage,
    year = firstAirDate?.take(4)?.toIntOrNull(),
    mediaType = mediaType.name,
    matchConfidence = confidence,
    isUnidentified = false,
    lastUpdated = System.currentTimeMillis(),
    logoPath = null
)
